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
import java.util.List;
import java.util.Map;
import org.satsim.pus.PacketDecodeException;
import org.satsim.pus.tc.TcPacket;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.UncheckedIOException;

/**
 * The MCP operator gateway per ICD §8.4 [SIM-REQ-MCP-001]: five PUS-level
 * tools and three resources on top of a {@link WebApiLink}, with authority
 * bounds and an ops log. Pure ground segment — everything it knows about
 * the spacecraft passed through the link [SIM-REQ-MCP-002].
 */
public final class Gateway {

  static final String URI_ICD = "satsim://icd";
  static final String URI_OBT = "satsim://obt";
  static final String URI_STATE = "satsim://state";

  private final WebApiLink link;
  private final TmLog tmLog;
  private final Authority authority;
  private final OpsLog opsLog;
  private final GatewayConfig config;
  private final String icdText;
  private final ObjectMapper json;

  public Gateway(GatewayConfig config, WebApiLink link, TmLog tmLog, Authority authority,
      OpsLog opsLog, String icdText, ObjectMapper json) {
    this.config = config;
    this.link = link;
    this.tmLog = tmLog;
    this.authority = authority;
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
        "Compose and inject a PUS TC per ICD §8.1 structured compose. Subject to "
            + "the allowlist and TC budget. Returns the full §8.1 response.",
        COMPOSE_SCHEMA,
        args -> {
          int service = requiredInt(args, "service");
          int subtype = requiredInt(args, "subtype");
          String denial = authority.vetInjection(service, subtype).orElse(null);
          if (denial != null) {
            return error(denial);
          }
          return ok(link.submitStructured(service, subtype,
              optionalInt(args, "ackFlags"), (String) args.get("appDataHex")));
        });
  }

  private SyncToolSpecification previewTc() {
    return tool("preview_tc",
        "Preview the encoded octets of a structured compose per ICD §8.1 without "
            + "injecting and without consuming a sequence count or budget.",
        COMPOSE_SCHEMA,
        args -> ok(link.preview(requiredInt(args, "service"), requiredInt(args, "subtype"),
            optionalInt(args, "ackFlags"), (String) args.get("appDataHex"))));
  }

  private SyncToolSpecification sendRawTc() {
    return tool("send_raw_tc",
        "Inject a complete space packet verbatim (hex) per ICD §8.1 raw injection — "
            + "deliberately without gateway-side validation; negative paths reachable. "
            + "Subject to the allowlist (decoded content) and TC budget.",
        Map.of("type", "object",
            "properties", Map.of("hex", Map.of("type", "string",
                "description", "complete space packet, hex")),
            "required", List.of("hex")),
        args -> {
          String hex = (String) args.get("hex");
          if (hex == null || hex.isBlank()) {
            return error("MALFORMED_INPUT: hex is required");
          }
          Integer service = null;
          Integer subtype = null;
          try {
            TcPacket decoded = TcPacket.decode(HexFormat.of().parseHex(
                hex.replace(" ", "").toLowerCase()));
            service = decoded.secondaryHeader().serviceType();
            subtype = decoded.secondaryHeader().messageSubtype();
          } catch (PacketDecodeException | IllegalArgumentException e) {
            // Undecodable octets are permitted by the allowlist (ICD §8.4).
          }
          String denial = authority.vetInjection(service, subtype).orElse(null);
          if (denial != null) {
            return error(denial);
          }
          return ok(link.submitRaw(hex));
        });
  }

  private SyncToolSpecification getPacketLog() {
    return tool("get_packet_log",
        "Ordered ICD §8.2 tm/rejection records from the gateway ring buffer, each "
            + "with a monotonic cursor; paged from afterCursor (default: buffer start).",
        Map.of("type", "object",
            "properties", Map.of(
                "afterCursor", Map.of("type", "integer",
                    "description", "return records with cursor greater than this (default 0)"),
                "kind", Map.of("type", "string", "enum", List.of("tm", "rejection")),
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
          return ok(Map.of("records", records, "latestCursor", tmLog.latestCursor()));
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
            return ok(Map.of("timedOut", true, "timeoutMs", timeout));
          }
          return ok(Map.of("cursor", entry.cursor(), "kind", entry.kind(),
              "frame", entry.frame()));
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
            + "ring-buffer cursor bounds.",
        () -> write(Map.of(
            "allowlist", List.copyOf(config.allowlist()),
            "remainingBudget", authority.remaining(),
            "firstCursor", tmLog.firstCursor(),
            "latestCursor", tmLog.latestCursor())));
  }

  // ---- plumbing -------------------------------------------------------

  /** A tool body: §8.4 semantics in, JSON-able result out. */
  private interface ToolBody {
    CallToolResult apply(Map<String, Object> args) throws Exception;
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
      CallToolResult result;
      try {
        result = body.apply(args);
      } catch (WebApiLink.LinkException | IllegalArgumentException e) {
        result = error(e.getMessage());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        result = error("interrupted");
      } catch (Exception e) {
        result = error("internal gateway error: " + e);
      }
      opsLog.record(name, args,
          Boolean.TRUE.equals(result.isError())
              ? "error: " + textOf(result) : "ok", tmLog.obt());
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
