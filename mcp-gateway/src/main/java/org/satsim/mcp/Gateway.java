package org.satsim.mcp;

import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncResourceSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.spec.McpSchema.TextResourceContents;
import io.modelcontextprotocol.spec.McpServerTransportProvider;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.satsim.cag.ClassificationTable;
import org.satsim.cag.CommandAuthorizationGate;
import org.satsim.cag.GateDecision;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.UncheckedIOException;

/**
 * The MCP operator gateway per ICD §8.4 [SIM-REQ-MCP-001]: five PUS-level
 * tools and three resources on top of a {@link WebApiLink}, with authority
 * bounds and an ops log. Pure ground segment — everything it knows about
 * the spacecraft passed through the link [SIM-REQ-MCP-002].
 *
 * <p>Authorization is not decided here. Every injection goes to the
 * {@link CommandAuthorizationGate} first [SIM-REQ-MCP-005], and this class
 * holds no rule about what may be commanded — it composes octets, relays the
 * gate's verdict, and logs it (ADR-0007 C1). The gateway also offers no tool
 * by which a confirmation could be recorded, which is what keeps a held
 * telecommand held whatever the attached MCP client does.
 */
public final class Gateway {

  static final String URI_ICD = "satsim://icd";
  static final String URI_OBT = "satsim://obt";
  static final String URI_STATE = "satsim://state";

  private final WebApiLink link;
  private final TmLog tmLog;
  private final Authority authority;
  private final CommandAuthorizationGate gate;
  private final OpsLog opsLog;
  private final GatewayConfig config;
  private final String icdText;
  private final ObjectMapper json;

  public Gateway(GatewayConfig config, WebApiLink link, TmLog tmLog, Authority authority,
      CommandAuthorizationGate gate, OpsLog opsLog, String icdText, ObjectMapper json) {
    this.config = config;
    this.link = link;
    this.tmLog = tmLog;
    this.authority = authority;
    this.gate = gate;
    this.opsLog = opsLog;
    this.icdText = icdText;
    this.json = json.copy();
  }

  /** Builds the MCP server (stdio or any other provider) with the ICD §8.4 surface. */
  public McpSyncServer buildServer(McpServerTransportProvider transport) {
    return McpServer.sync(transport)
        .serverInfo("satsim-mcp-gateway", "0.1.0")
        .instructions("SatSim spacecraft TM/TC operator interface per ICD §8.4. "
            + "Read the satsim://icd resource — it is the authoritative manual. "
            + "Compose PUS telecommands with send_tc/preview_tc, observe telemetry "
            + "with get_packet_log/await_tm.")
        .capabilities(McpSchema.ServerCapabilities.builder()
            .tools(false)
            .resources(false, false)
            .build())
        .tools(sendTc(), previewTc(), sendRawTc(), getPacketLog(), awaitTm())
        .resources(icdResource(), obtResource(), stateResource())
        .build();
  }

  // ---- tools ----------------------------------------------------------

  private static final Map<String, Object> COMPOSE_SCHEMA = Map.of(
      "type", "object",
      "properties", Map.of(
          "service", Map.of("type", "integer", "description", "PUS service type"),
          "subtype", Map.of("type", "integer", "description", "PUS message subtype"),
          "ackFlags", Map.of("type", "integer",
              "description", "acknowledgement flags 0..15 per ICD §3 (optional)"),
          "appDataHex", Map.of("type", "string",
              "description", "application data octets as hex (optional)")),
      "required", List.of("service", "subtype"));

  private SyncToolSpecification sendTc() {
    return tool("send_tc",
        "Compose and inject a PUS TC per ICD §8.1 structured compose. Every injection "
            + "is authorized by the Command Authorization Gate on its decoded content: "
            + "state-changing telecommands are held until a human records a confirmation "
            + "out of band, which no tool of this interface can do. Returns the full "
            + "§8.1 response when forwarded.",
        COMPOSE_SCHEMA,
        args -> {
          int service = requiredInt(args, "service");
          int subtype = requiredInt(args, "subtype");
          Integer ackFlags = optionalInt(args, "ackFlags");
          String appDataHex = (String) args.get("appDataHex");
          // The gate decides on octets, not on these arguments: the same
          // compose the simulator would inject is previewed first (no sequence
          // count consumed, ICD §8.1) and those octets are what it classifies.
          String hex = (String) link.preview(service, subtype, ackFlags, appDataHex).get("hex");
          return authorize(hex,
              () -> link.submitStructured(service, subtype, ackFlags, appDataHex));
        });
  }

  private SyncToolSpecification previewTc() {
    return tool("preview_tc",
        "Preview the encoded octets of a structured compose per ICD §8.1 without "
            + "injecting and without consuming a sequence count or budget.",
        COMPOSE_SCHEMA,
        args -> plain(ok(link.preview(requiredInt(args, "service"),
            requiredInt(args, "subtype"), optionalInt(args, "ackFlags"),
            (String) args.get("appDataHex")))));
  }

  private SyncToolSpecification sendRawTc() {
    return tool("send_raw_tc",
        "Inject a complete space packet verbatim (hex) per ICD §8.1 raw injection. "
            + "Authorized on decoded content like any other injection: octets that do "
            + "not decode per ICD §3 are rejected by the gate and nothing is injected. "
            + "Octets that decode but are semantically invalid are forwarded, so the "
            + "spacecraft's own rejection paths stay reachable.",
        Map.of("type", "object",
            "properties", Map.of("hex", Map.of("type", "string",
                "description", "complete space packet, hex")),
            "required", List.of("hex")),
        args -> {
          String hex = (String) args.get("hex");
          if (hex == null || hex.isBlank()) {
            return plain(error("MALFORMED_INPUT: hex is required"));
          }
          return authorize(hex, () -> link.submitRaw(hex));
        });
  }

  /**
   * The single authorization path: gate first, budget second, inject only on a
   * forward verdict. Both send tools go through here, so there is no route to
   * the simulator that skips the gate [SIM-REQ-MCP-005].
   */
  private ToolOutcome authorize(String hex, Injection injection) throws Exception {
    byte[] octets;
    try {
      octets = HexFormat.of().parseHex(hex.replace(" ", "").toLowerCase());
    } catch (IllegalArgumentException e) {
      return plain(error("MALFORMED_INPUT: hex is not a valid octet string"));
    }

    GateDecision decision = gate.decide(octets);
    if (decision.forwards()) {
      String exhausted = authority.consumeBudget().orElse(null);
      if (exhausted != null) {
        decision = decision.rejectedInstead(exhausted);
      }
    }
    return switch (decision.outcome()) {
      case FORWARD -> new ToolOutcome(ok(injection.inject()), decision);
      case REJECT -> new ToolOutcome(error(decision.reason()), decision);
      case CONFIRMATION_REQUIRED -> new ToolOutcome(ok(heldPayload(decision)), decision);
    };
  }

  /**
   * The confirmation-required result of ICD §8.4 — a normal structured result,
   * not a tool error: nothing went wrong, the command is waiting for a human.
   *
   * <p>It carries the token and the decoded command, but deliberately not the
   * command line that would record the confirmation. That belongs to the human
   * operator and is printed on the gateway's own stderr channel; putting it in
   * a tool result would hand the party that must not confirm a ready-made
   * recipe for doing so. Containment on the tool path is what ADR-0007 C8
   * claims, and there is no reason to spend it cheaply.
   */
  private Map<String, Object> heldPayload(GateDecision decision) {
    System.err.println("CAG HOLD " + decision.token() + " — " + decision.reason()
        + System.lineSeparator()
        + "  to authorize, a human runs: java -cp <ops-cag> org.satsim.cag.CagConfirm "
        + config.confirmDirPath() + " " + decision.token());
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("confirmationRequired", true);
    payload.put("confirmationToken", decision.token());
    payload.put("tier", decision.tier().name());
    payload.put("reason", decision.reason());
    payload.put("command", Map.of(
        "service", decision.command().service(),
        "subtype", decision.command().subtype(),
        "ackFlags", decision.command().ackFlags(),
        "appDataHex", decision.command().appDataHex()));
    payload.put("injected", false);
    payload.put("howToProceed", "Nothing has been injected. A human operator must record "
        + "this confirmation out of band — no tool of this interface can record it. "
        + "Report the token and what the command does, then re-submit the identical "
        + "telecommand once confirmation is recorded; the gate forwards it exactly once.");
    return payload;
  }

  /** Injects the authorized octets and returns the ICD §8.1 response. */
  private interface Injection {
    Map<String, Object> inject() throws Exception;
  }

  private SyncToolSpecification getPacketLog() {
    return tool("get_packet_log",
        "Ordered ICD §8.2 tm/rejection/tc records from the gateway ring buffer, each "
            + "with a monotonic cursor; paged from afterCursor (default: buffer start). "
            + "tc records are the telecommands injected by any operator, including "
            + "other consoles and gateways.",
        Map.of("type", "object",
            "properties", Map.of(
                "afterCursor", Map.of("type", "integer",
                    "description", "return records with cursor greater than this (default 0)"),
                "kind", Map.of("type", "string", "enum", List.of("tm", "rejection", "tc")),
                "service", Map.of("type", "integer"),
                "subtype", Map.of("type", "integer")),
            "required", List.of()),
        args -> {
          long after = optionalLong(args, "afterCursor", 0L);
          TmLog.Filter filter = new TmLog.Filter((String) args.get("kind"),
              optionalInt(args, "service"), optionalInt(args, "subtype"));
          List<Map<String, Object>> records = new ArrayList<>();
          for (TmLog.Entry entry : tmLog.after(after, filter)) {
            records.add(Map.of("cursor", entry.cursor(), "kind", entry.kind(),
                "frame", entry.frame()));
          }
          return plain(ok(Map.of("records", records, "latestCursor", tmLog.latestCursor())));
        });
  }

  private SyncToolSpecification awaitTm() {
    return tool("await_tm",
        "Block until the first TM record matching the filter with cursor beyond "
            + "afterCursor (default: call time) arrives, or return a distinct timeout "
            + "result ({\"timedOut\": true}) after timeoutMs.",
        Map.of("type", "object",
            "properties", Map.of(
                "service", Map.of("type", "integer"),
                "subtype", Map.of("type", "integer"),
                "timeoutMs", Map.of("type", "integer"),
                "afterCursor", Map.of("type", "integer",
                    "description", "cursor to wait beyond (default: latest at call time)")),
            "required", List.of("timeoutMs")),
        args -> {
          long timeout = requiredInt(args, "timeoutMs");
          long after = optionalLong(args, "afterCursor", tmLog.latestCursor());
          TmLog.Filter filter = new TmLog.Filter("tm",
              optionalInt(args, "service"), optionalInt(args, "subtype"));
          TmLog.Entry entry = tmLog.await(after, filter, timeout);
          if (entry == null) {
            return plain(ok(Map.of("timedOut", true, "timeoutMs", timeout)));
          }
          return plain(ok(Map.of("cursor", entry.cursor(), "kind", entry.kind(),
              "frame", entry.frame())));
        });
  }

  // ---- resources ------------------------------------------------------

  private SyncResourceSpecification icdResource() {
    return resource(URI_ICD, "icd", "text/markdown",
        "The Space–Ground ICD — the authoritative TM/TC manual for this spacecraft.",
        () -> icdText);
  }

  private SyncResourceSpecification obtResource() {
    return resource(URI_OBT, "obt", "application/json",
        "Current on-board time per the latest ICD §8.2 time frame.",
        () -> write(tmLog.obt()));
  }

  private SyncResourceSpecification stateResource() {
    return resource(URI_STATE, "gateway-state", "application/json",
        "Gateway state: configured allowlist, remaining session TC budget, "
            + "ring-buffer cursor bounds, authority-tier table, and the "
            + "telecommands currently held for confirmation.",
        () -> write(Map.of(
            "allowlist", List.copyOf(config.allowlist()),
            "remainingBudget", authority.remaining(),
            "firstCursor", tmLog.firstCursor(),
            "latestCursor", tmLog.latestCursor(),
            "classifiedTelecommands", ClassificationTable.entries(),
            "pendingConfirmations", gate.pendingHolds())));
  }

  // ---- plumbing -------------------------------------------------------

  /**
   * A tool result together with the gate decision behind it, if the tool
   * authorized an injection. The decision travels with the result so the ops
   * log can record it as evidence [SIM-REQ-CAG-006] without the plumbing having
   * to guess what happened.
   */
  private record ToolOutcome(CallToolResult result, GateDecision decision) {
  }

  /** A result from a tool that authorizes nothing, so carries no gate decision. */
  private static ToolOutcome plain(CallToolResult result) {
    return new ToolOutcome(result, null);
  }

  /** A tool body: §8.4 semantics in, JSON-able result out. */
  private interface ToolBody {
    ToolOutcome apply(Map<String, Object> args) throws Exception;
  }

  private SyncToolSpecification tool(String name, String description,
      Map<String, Object> inputSchema, ToolBody body) {
    McpSchema.Tool tool = McpSchema.Tool.builder()
        .name(name)
        .description(description)
        .inputSchema(inputSchema)
        .build();
    return new SyncToolSpecification(tool, (exchange, request) -> {
      Map<String, Object> args =
          request.arguments() == null ? Map.of() : request.arguments();
      ToolOutcome outcome;
      try {
        outcome = body.apply(args);
      } catch (WebApiLink.LinkException | IllegalArgumentException e) {
        outcome = plain(error(e.getMessage()));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        outcome = plain(error("interrupted"));
      } catch (Exception e) {
        outcome = plain(error("internal gateway error: " + e));
      }
      CallToolResult result = outcome.result();
      opsLog.record(name, args,
          Boolean.TRUE.equals(result.isError())
              ? "error: " + textOf(result) : "ok", tmLog.obt(), outcome.decision());
      return result;
    });
  }

  private SyncResourceSpecification resource(String uri, String name, String mimeType,
      String description, ResourceBody body) {
    McpSchema.Resource resource = McpSchema.Resource.builder()
        .uri(uri).name(name).mimeType(mimeType).description(description).build();
    return new SyncResourceSpecification(resource, (exchange, request) ->
        new ReadResourceResult(List.of(
            new TextResourceContents(uri, mimeType, body.text()))));
  }

  private interface ResourceBody {
    String text();
  }

  private CallToolResult ok(Map<String, Object> payload) {
    return new CallToolResult(
        List.of(new McpSchema.TextContent(write(payload))), false, null, null);
  }

  private String write(Object payload) {
    try {
      return json.writeValueAsString(payload);
    } catch (JsonProcessingException e) {
      throw new UncheckedIOException("JSON serialization failed", e);
    }
  }

  private static CallToolResult error(String message) {
    return new CallToolResult(
        List.of(new McpSchema.TextContent(message)), true, null, null);
  }

  private static String textOf(CallToolResult result) {
    return result.content().isEmpty() ? ""
        : ((McpSchema.TextContent) result.content().get(0)).text();
  }

  private static int requiredInt(Map<String, Object> args, String name) {
    Object value = args.get(name);
    if (!(value instanceof Number n)) {
      throw new IllegalArgumentException("MALFORMED_INPUT: " + name + " is required");
    }
    return n.intValue();
  }

  private static Integer optionalInt(Map<String, Object> args, String name) {
    Object value = args.get(name);
    return value instanceof Number n ? n.intValue() : null;
  }

  private static long optionalLong(Map<String, Object> args, String name, long fallback) {
    Object value = args.get(name);
    return value instanceof Number n ? n.longValue() : fallback;
  }
}
