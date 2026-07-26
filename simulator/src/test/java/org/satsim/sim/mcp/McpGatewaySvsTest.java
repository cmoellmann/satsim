package org.satsim.sim.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.satsim.cag.CagConfirm;
import org.satsim.sim.web.SimulationService;
import org.satsim.testsupport.Requirement;
import org.satsim.testsupport.TestCase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;

/**
 * SVS SIM-TC-041..045 (SCR-008, scope M1f) and SIM-TC-046 (SCR-009, scope
 * M1g — the gateway observes another operator's commanding): the MCP
 * operator gateway per
 * ICD §8.4, driven end-to-end by a <em>scripted</em> MCP client — the
 * gateway runs as its own process and is spoken to over its real stdio
 * transport; no AI is involved. Pacing is disabled, simulated time advances
 * only via {@link SimulationService#advanceBy(long)}; each test gets a
 * fresh simulator context (clock at 0, fresh counters) and a fresh gateway.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "satsim.pacing.tick-millis=0")
@DirtiesContext(classMode = ClassMode.AFTER_EACH_TEST_METHOD)
class McpGatewaySvsTest {

  private static final long QUANTUM_NANOS = 100_000_000L;

  // ICD §6 reference vectors (authoritative; never adjusted).
  private static final String V_TC_01 = compact("18 64 C0 00 00 06 20 11 01 00 00 FA 83");
  private static final String V_NEG_01 = compact("18 64 C0 00 00 06 20 11 01 00 00 FA 84");
  private static final String V_NEG_02 = compact("18 64 C0 00 00 06 10 11 01 00 00 F6 6D");
  private static final String V_TC_03 = compact(
      "18 64 C0 00 00 12 20 03 01 00 00 00 02 00 00 13 88 00 02 00 01 00 03 8D CE");
  private static final String V_TM_08 = compact(
      "08 64 C0 00 00 14 20 01 02 00 00 00 00 00 00 00 00 00 00 18 64 C0 00 00 01 BC E4");
  private static final String V_TM_05 = compact(
      "08 64 C0 00 00 12 20 01 01 00 00 00 00 00 00 00 00 00 00 18 64 C0 00 1C BC");
  private static final String V_TM_06 = compact(
      "08 64 C0 01 00 0E 20 11 02 00 00 00 00 00 00 00 00 00 00 A4 62");
  private static final String V_TM_07 = compact(
      "08 64 C0 02 00 12 20 01 07 00 00 00 00 00 00 00 00 00 00 18 64 C0 00 A1 B1");

  @LocalServerPort
  private int port;

  @Autowired
  private SimulationService simulation;

  /** ICD §8.1 injections of "another operator" — the gateway never submits them. */
  @Autowired
  private TestRestTemplate rest;

  @TempDir
  Path tempDir;

  private final ObjectMapper json = new ObjectMapper();
  private McpSyncClient client;

  private static String compact(String spacedHex) {
    return HexFormat.of().formatHex(HexFormat.of().parseHex(spacedHex.replace(" ", "")));
  }

  /** Spawns a fresh gateway process (real stdio transport) and initializes MCP. */
  private McpSyncClient startGateway(String allow, int budget) {
    String javaBin = Path.of(System.getProperty("java.home"), "bin", "java").toString();
    ServerParameters params = ServerParameters.builder(javaBin)
        .args("-cp", System.getProperty("java.class.path"),
            // Stdout is the MCP channel; the inherited test classpath brings
            // logback, whose default appender would write there.
            "-Dlogback.configurationFile="
                + Path.of("src", "test", "resources", "logback-gateway-stderr.xml"),
            "org.satsim.mcp.GatewayMain",
            "--url", "http://localhost:" + port,
            "--allow", allow,
            "--budget", String.valueOf(budget),
            "--ops-log", opsLogPath().toString(),
            "--icd", Path.of("..", "docs", "icd.md").toString(),
            "--confirm-dir", confirmDirPath().toString())
        .build();
    StdioClientTransport transport = new StdioClientTransport(params,
        new JacksonMcpJsonMapper(new ObjectMapper()));
    client = McpClient.sync(transport)
        .requestTimeout(Duration.ofSeconds(30))
        .clientInfo(new McpSchema.Implementation("svs-scripted-client", "1.0"))
        .build();
    client.initialize();
    return client;
  }

  private Path opsLogPath() {
    return tempDir.resolve("ops-log.jsonl");
  }

  /**
   * The gate's out-of-band confirmation directory — the channel a human uses
   * and an MCP client cannot (ADR-0007 C5). Per test, so holds never leak
   * between cases.
   */
  private Path confirmDirPath() {
    return tempDir.resolve("cag-confirmations");
  }

  /**
   * Records a confirmation the way an operator does: through the real
   * {@code ops-cag} CLI, not by reaching into gateway internals. Nothing in
   * the MCP session can do this.
   */
  private void recordConfirmation(String token) throws Exception {
    int exit = CagConfirm.run(new String[] {confirmDirPath().toString(), token},
        System.err, System.err);
    assertEquals(0, exit, "cag-confirm must record the confirmation");
  }

  /** Replaces the running gateway with one under a different configuration. */
  private McpSyncClient restartGateway(String allow, int budget) {
    closeGateway();
    return startGateway(allow, budget);
  }

  @AfterEach
  void closeGateway() {
    if (client != null) {
      client.close();
      client = null;
    }
  }

  private JsonNode call(String tool, Map<String, Object> args) throws Exception {
    McpSchema.CallToolResult result =
        client.callTool(new McpSchema.CallToolRequest(tool, args));
    assertFalse(Boolean.TRUE.equals(result.isError()),
        () -> tool + " unexpectedly failed: " + text(result));
    return json.readTree(text(result));
  }

  private McpSchema.CallToolResult callExpectingError(String tool, Map<String, Object> args) {
    McpSchema.CallToolResult result =
        client.callTool(new McpSchema.CallToolRequest(tool, args));
    assertTrue(Boolean.TRUE.equals(result.isError()),
        () -> tool + " unexpectedly succeeded: " + text(result));
    return result;
  }

  private static String text(McpSchema.CallToolResult result) {
    return ((McpSchema.TextContent) result.content().get(0)).text();
  }

  /**
   * Polls get_packet_log until at least {@code expected} records arrive (the
   * WS frame reaches the gateway process asynchronously) and returns the log;
   * callers then assert the exact count.
   */
  private JsonNode pollLog(Map<String, Object> args, int expected) throws Exception {
    JsonNode log = call("get_packet_log", args);
    for (int i = 0; i < 100 && log.get("records").size() < expected; i++) {
      Thread.sleep(50);
      log = call("get_packet_log", args);
    }
    return log;
  }

  private JsonNode readResource(String uri) throws Exception {
    McpSchema.ReadResourceResult result =
        client.readResource(new McpSchema.ReadResourceRequest(uri));
    return json.readTree(
        ((McpSchema.TextResourceContents) result.contents().get(0)).text());
  }

  /**
   * SIM-TC-041: initialize succeeds; tools/list returns exactly the five
   * ICD §8.4 tools; resources/list the three §8.4 resources; the OBT
   * resource returns the current simulated time and the state resource the
   * configured allowlist and remaining budget.
   */
  @Test
  @TestCase("SIM-TC-041")
  @Requirement({"SIM-REQ-MCP-001", "SIM-REQ-MCP-004"})
  void serverContract() throws Exception {
    startGateway("3,17", 100);

    Set<String> tools = client.listTools().tools().stream()
        .map(McpSchema.Tool::name).collect(Collectors.toSet());
    assertEquals(
        Set.of("send_tc", "preview_tc", "send_raw_tc", "get_packet_log", "await_tm"),
        tools);

    Set<String> resources = client.listResources().resources().stream()
        .map(McpSchema.Resource::uri).collect(Collectors.toSet());
    assertEquals(Set.of("satsim://icd", "satsim://obt", "satsim://state"), resources);

    JsonNode obt = readResource("satsim://obt");
    assertEquals(0, obt.get("timeCoarse").asLong());
    assertEquals(0, obt.get("timeFine").asInt());

    JsonNode state = readResource("satsim://state");
    assertEquals(100, state.get("remainingBudget").asInt());
    Set<String> allowlist = new java.util.HashSet<>();
    state.get("allowlist").forEach(node -> allowlist.add(node.asText()));
    assertEquals(Set.of("3", "17"), allowlist);
  }

  /**
   * SIM-TC-042: preview_tc of the V-TC-01 field values returns hex
   * byte-identical to V-TC-01 without consuming a sequence count; send_tc
   * of the same values injects byte-identically (response per ICD §8.1)
   * and yields exactly one TM(17,2).
   */
  @Test
  @TestCase("SIM-TC-042")
  @Requirement("SIM-REQ-MCP-001")
  void byteExactSendPath() throws Exception {
    startGateway("3,17", 100);
    Map<String, Object> vTc01Fields =
        Map.of("service", 17, "subtype", 1, "ackFlags", 0, "appDataHex", "");

    JsonNode preview = call("preview_tc", vTc01Fields);
    assertEquals(V_TC_01, preview.get("hex").asText());

    JsonNode response = call("send_tc", vTc01Fields);
    assertEquals(V_TC_01, response.get("hex").asText());
    assertEquals(0, response.get("sequenceCount").asInt(),
        "preview must not have consumed a sequence count");
    assertEquals(17, response.get("decoded").get("service").asInt());
    assertEquals(1, response.get("decoded").get("subtype").asInt());
    assertEquals(0, response.get("timeCoarse").asLong());

    simulation.advanceBy(QUANTUM_NANOS);
    JsonNode log = pollLog(Map.of("kind", "tm"), 1);
    assertEquals(1, log.get("records").size(), "exactly one TM expected");
    JsonNode tm = log.get("records").get(0).get("frame").get("decoded");
    assertEquals(17, tm.get("service").asInt());
    assertEquals(2, tm.get("subtype").asInt());
  }

  /**
   * SIM-TC-043: after send_tc of the V-TC-06 field values (ack 0b1001) at
   * T=0, await_tm(1,7) returns the TM(1,7) record; get_packet_log from
   * buffer start returns TM(1,1), TM(17,2), TM(1,7) byte-identical to
   * V-TM-05/06/07 in this order; a non-matching await_tm times out with
   * the distinct timeout result.
   */
  @Test
  @TestCase("SIM-TC-043")
  @Requirement("SIM-REQ-MCP-003")
  void blockingWaitAndLogPaging() throws Exception {
    startGateway("3,17", 100);
    call("send_tc", Map.of("service", 17, "subtype", 1, "ackFlags", 9, "appDataHex", ""));
    simulation.advanceBy(QUANTUM_NANOS);

    JsonNode awaited = call("await_tm",
        Map.of("service", 1, "subtype", 7, "timeoutMs", 5000, "afterCursor", 0));
    assertEquals(V_TM_07, awaited.get("frame").get("hex").asText());

    JsonNode log = call("get_packet_log", Map.of("afterCursor", 0, "kind", "tm"));
    List<String> hexes = log.get("records").findValues("frame").stream()
        .map(frame -> frame.get("hex").asText()).toList();
    assertEquals(List.of(V_TM_05, V_TM_06, V_TM_07), hexes,
        "verification sequence byte-identical to V-TM-05/06/07 in emission order");

    JsonNode timeout = call("await_tm",
        Map.of("service", 5, "subtype", 1, "timeoutMs", 400));
    assertTrue(timeout.get("timedOut").asBoolean(),
        "non-matching await_tm must return the distinct timeout result");
  }

  /**
   * SIM-TC-044 (amended per SCR-010 / ICD Issue 8): V-NEG-01 is injected
   * verbatim over the ICD §8.1 REST interface — which the gate does not
   * mediate — with the gateway attached as a non-submitting observer; no TM
   * is emitted; the packet log subsequently contains exactly one rejection
   * record with reason NOT_A_PACKET and the offending hex.
   *
   * <p>Until M1h the injection went through {@code send_raw_tc}, which from
   * M1h the gate rejects as undecodable (SIM-REQ-CAG-003, verified by
   * SIM-TC-049). Only the injection path changes; what this case covers —
   * that a rejection frame observed on the §8.2 stream is recorded and served
   * by {@code get_packet_log} — is unchanged.
   */
  @Test
  @TestCase("SIM-TC-044")
  @Requirement({"SIM-REQ-MCP-003", "SIM-REQ-MCP-001"})
  void rejectionVisibility() throws Exception {
    startGateway("3,17", 100);
    ResponseEntity<Map> response =
        rest.postForEntity("/api/tc", Map.of("hex", V_NEG_01), Map.class);
    assertEquals("CRC_ERROR", response.getBody().get("decodeError"));

    simulation.advanceBy(500_000_000L);
    // Kind-filtered: from M1g the buffer also holds the tc record of this
    // very injection (SCR-009), which SIM-TC-046 covers.
    JsonNode log = pollLog(Map.of("kind", "rejection"), 1);
    assertEquals(1, log.get("records").size(), "exactly one rejection record expected");
    JsonNode rejection = log.get("records").get(0);
    assertEquals("rejection", rejection.get("kind").asText());
    assertEquals("NOT_A_PACKET", rejection.get("frame").get("reason").asText());
    assertEquals(V_NEG_01, rejection.get("frame").get("hex").asText());
  }

  /**
   * SIM-TC-046 (SCR-009): every ICD §8.1 injection is broadcast as one
   * {@code kind:"tc"} frame to <em>all</em> WebSocket sessions — here two
   * pure observers that submit nothing — carrying the fields of the §8.1
   * response and preceding the TM it causes; undecodable octets are
   * broadcast just as well, with a higher injectionId; a preview broadcasts
   * nothing. The gateway, attached as a third non-submitting observer,
   * serves the same injections as {@code tc} records under filter kind tc,
   * and under filter kind tm by none.
   */
  @Test
  @TestCase("SIM-TC-046")
  @Requirement({"SIM-REQ-UI-017", "SIM-REQ-MCP-003"})
  void injectionsAreBroadcastAsTcFramesAndServedByPacketLog() throws Exception {
    startGateway("3,17", 100);
    TextCollector sessionA = new TextCollector();
    TextCollector sessionB = new TextCollector();
    WebSocket socketA = connect(sessionA);
    WebSocket socketB = connect(sessionB);
    try {
      // V-TC-01 field values, submitted by neither observer nor gateway.
      ResponseEntity<Map> ping = rest.postForEntity("/api/tc",
          Map.of("service", 17, "subtype", 1, "ackFlags", 0, "appDataHex", ""), Map.class);
      simulation.advanceBy(QUANTUM_NANOS);
      // Per session: connect time frame, tc frame, TM(17,2), quantum time frame.
      List<JsonNode> framesA = drainFrames(sessionA, 4);
      List<JsonNode> framesB = drainFrames(sessionB, 4);

      for (List<JsonNode> frames : List.of(framesA, framesB)) {
        List<JsonNode> tcFrames = byKind(frames, "tc");
        assertEquals(1, tcFrames.size(), "one tc frame per injection, on every session");
        JsonNode tc = tcFrames.get(0);
        Map<?, ?> body = ping.getBody();
        // Field contents are those of the §8.1 response for this injection.
        assertEquals(((Number) body.get("injectionId")).longValue(),
            tc.get("injectionId").asLong());
        assertEquals(V_TC_01, tc.get("hex").asText());
        assertEquals(body.get("hex"), tc.get("hex").asText());
        assertEquals(((Number) body.get("timeCoarse")).longValue(), tc.get("timeCoarse").asLong());
        assertEquals(((Number) body.get("timeFine")).intValue(), tc.get("timeFine").asInt());
        assertEquals(((Number) body.get("sequenceCount")).intValue(),
            tc.get("sequenceCount").asInt());
        Map<?, ?> decoded = (Map<?, ?>) body.get("decoded");
        assertEquals(((Number) decoded.get("service")).intValue(),
            tc.get("decoded").get("service").asInt());
        assertEquals(((Number) decoded.get("subtype")).intValue(),
            tc.get("decoded").get("subtype").asInt());
        assertEquals(decoded.get("appDataHex"), tc.get("decoded").get("appDataHex").asText());
        // Ordering: the tc frame precedes every frame the injection caused.
        assertTrue(frames.indexOf(tc) < frames.indexOf(byKind(frames, "tm").get(0)),
            "tc frame must precede the TM frames it causes");
      }

      ResponseEntity<Map> raw = rest.postForEntity("/api/tc", Map.of("hex", V_NEG_01), Map.class);
      rest.postForEntity("/api/tc/preview",
          Map.of("service", 17, "subtype", 1, "ackFlags", 0, "appDataHex", ""), Map.class);
      simulation.advanceBy(QUANTUM_NANOS);
      // tc frame, rejection frame, quantum time frame — and nothing from the preview.
      List<JsonNode> rawTcFrames = byKind(drainFrames(sessionB, 3), "tc");
      assertEquals(1, rawTcFrames.size(), "a preview must not broadcast a tc frame");
      JsonNode rawTc = rawTcFrames.get(0);
      assertEquals(V_NEG_01, rawTc.get("hex").asText());
      assertEquals("CRC_ERROR", rawTc.get("decodeError").asText());
      assertNull(rawTc.get("decoded"));
      assertTrue(rawTc.get("injectionId").asLong()
              > ((Number) ping.getBody().get("injectionId")).longValue(),
          "injectionIds are strictly increasing");

      // The gateway saw the same two injections without submitting any.
      JsonNode log = pollLog(Map.of("kind", "tc"), 2);
      assertEquals(2, log.get("records").size(), "one tc record per injection expected");
      JsonNode firstFrame = log.get("records").get(0).get("frame");
      JsonNode secondFrame = log.get("records").get(1).get("frame");
      assertEquals(V_TC_01, firstFrame.get("hex").asText());
      assertEquals(V_NEG_01, secondFrame.get("hex").asText());
      assertEquals(((Number) ping.getBody().get("injectionId")).longValue(),
          firstFrame.get("injectionId").asLong());
      assertEquals(((Number) raw.getBody().get("injectionId")).longValue(),
          secondFrame.get("injectionId").asLong());

      JsonNode tmLog = pollLog(Map.of("kind", "tm"), 1);
      for (JsonNode record : tmLog.get("records")) {
        assertEquals("tm", record.get("kind").asText(), "kind filter tm must exclude tc records");
      }
    } finally {
      socketA.abort();
      socketB.abort();
    }
  }

  /** Collects complete WebSocket text messages of one observing session. */
  private static final class TextCollector implements WebSocket.Listener {
    private final BlockingQueue<String> messages = new LinkedBlockingQueue<>();
    private final StringBuilder partial = new StringBuilder();

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
      partial.append(data);
      if (last) {
        messages.add(partial.toString());
        partial.setLength(0);
      }
      webSocket.request(1);
      return null;
    }
  }

  private WebSocket connect(TextCollector collector) throws Exception {
    return HttpClient.newHttpClient().newWebSocketBuilder()
        .buildAsync(URI.create("ws://localhost:" + port + "/api/tm"), collector)
        .get(5, TimeUnit.SECONDS);
  }

  /** Reads at least {@code minimum} frames, then drains until 300 ms of quiet. */
  private List<JsonNode> drainFrames(TextCollector collector, int minimum) throws Exception {
    List<JsonNode> frames = new ArrayList<>();
    for (int i = 0; i < minimum; i++) {
      String frameJson = collector.messages.poll(5, TimeUnit.SECONDS);
      assertNotNull(frameJson, "no frame received on /api/tm within 5 s");
      frames.add(json.readTree(frameJson));
    }
    String extra;
    while ((extra = collector.messages.poll(300, TimeUnit.MILLISECONDS)) != null) {
      frames.add(json.readTree(extra));
    }
    return frames;
  }

  private static List<JsonNode> byKind(List<JsonNode> frames, String kind) {
    return frames.stream().filter(f -> kind.equals(f.get("kind").asText())).toList();
  }

  /**
   * SIM-TC-045: with an allowlist excluding ST[17], send_tc(17,1) returns a
   * tool error and injects nothing; with session budget 2, the third
   * injection returns a budget-exhausted error; the ops log holds one JSONL
   * record per tool invocation including the denied ones, each with outcome
   * and OBT.
   *
   * <p>The two halves need two gateway configurations, since one allowlist
   * cannot both exclude and permit ST[17]. Before M1h the budget half rode on
   * undecodable raw octets, which the allowlist exempted; the gate now rejects
   * those (SIM-REQ-CAG-003) and rejected calls consume no budget, so the
   * budget is exercised with the forwarding telecommand it describes. The
   * ops log spans both sessions — it is append-mode on one path.
   */
  @Test
  @TestCase("SIM-TC-045")
  @Requirement({"SIM-REQ-MCP-005", "SIM-REQ-MCP-006"})
  void authorityBoundsAndOpsLog() throws Exception {
    startGateway("3", 2);

    McpSchema.CallToolResult denied = callExpectingError("send_tc",
        Map.of("service", 17, "subtype", 1, "ackFlags", 0, "appDataHex", ""));
    assertTrue(text(denied).startsWith("ALLOWLIST_DENIED"), text(denied));

    JsonNode afterDenial = call("get_packet_log", Map.of());
    assertEquals(0, afterDenial.get("records").size(), "denied call must inject nothing");
    assertEquals(2, readResource("satsim://state").get("remainingBudget").asInt(),
        "denied call must not consume budget");

    // ST[17] permitted: a benign write forwards and consumes one budget unit.
    restartGateway("17", 2);
    Map<String, Object> ping =
        Map.of("service", 17, "subtype", 1, "ackFlags", 0, "appDataHex", "");
    call("send_tc", ping);
    call("send_tc", ping);
    McpSchema.CallToolResult exhausted = callExpectingError("send_tc", ping);
    assertTrue(text(exhausted).startsWith("BUDGET_EXHAUSTED"), text(exhausted));
    assertEquals(0, readResource("satsim://state").get("remainingBudget").asInt());

    List<String> lines = Files.readAllLines(opsLogPath());
    // 5 tool invocations above (send_tc, get_packet_log, then 3x send_tc);
    // resource reads are not tool calls and must not be counted.
    assertEquals(5, lines.size(), "one ops-log record per tool invocation");
    for (String line : lines) {
      JsonNode record = json.readTree(line);
      assertNotNull(record.get("tool"));
      assertNotNull(record.get("outcome"));
      assertNotNull(record.get("obt").get("timeCoarse"));
    }
    JsonNode deniedRecord = json.readTree(lines.get(0));
    assertEquals("send_tc", deniedRecord.get("tool").asText());
    assertTrue(deniedRecord.get("outcome").asText().startsWith("error: ALLOWLIST_DENIED"));
  }

  // ---- M1h: Command Authorization Gate (SCR-010, ADR-0007) --------------

  /**
   * SIM-TC-048: the gate classifies on the decoded octets, not on the tool
   * that submitted them. {@code send_raw_tc} declares no service or subtype
   * at all, yet ping octets forward and ST[3] octets are held; the structured
   * tool agrees; the ops log carries the tier of each.
   */
  @Test
  @TestCase("SIM-TC-048")
  @Requirement({"SIM-REQ-CAG-002", "SIM-REQ-CAG-004"})
  void classificationFollowsDecodedContent() throws Exception {
    startGateway("3,17", 100);

    // Raw path, no declared service: ping octets are a benign write.
    JsonNode rawPing = call("send_raw_tc", Map.of("hex", V_TC_01));
    assertEquals(V_TC_01, rawPing.get("hex").asText());
    assertFalse(rawPing.has("confirmationRequired"), "a ping must not be held");

    // Raw path, same tool, state-changing octets: held, nothing injected.
    JsonNode rawCreate = call("send_raw_tc", Map.of("hex", V_TC_03));
    assertTrue(rawCreate.get("confirmationRequired").asBoolean(),
        "the tier follows the octets, not the invoking tool");
    assertFalse(rawCreate.get("injected").asBoolean());

    // Structured path agrees on both tiers.
    assertFalse(call("send_tc",
            Map.of("service", 17, "subtype", 1, "ackFlags", 0, "appDataHex", ""))
        .has("confirmationRequired"));
    List<Map<String, Object>> stateChangingCommands = List.of(
        Map.of("service", 3, "subtype", 1, "ackFlags", 9,
            "appDataHex", "000200001388000200010003"),
        Map.of("service", 3, "subtype", 5, "ackFlags", 9, "appDataHex", "00010002"),
        Map.of("service", 3, "subtype", 7, "ackFlags", 9, "appDataHex", "00010002"));
    for (Map<String, Object> stateChanging : stateChangingCommands) {
      JsonNode held = call("send_tc", stateChanging);
      assertTrue(held.get("confirmationRequired").asBoolean(),
          () -> "ST[3] must be held: " + stateChanging);
      assertEquals("STATE_CHANGING_WRITE", held.get("tier").asText());
    }

    List<String> tiers = new ArrayList<>();
    for (String line : Files.readAllLines(opsLogPath())) {
      JsonNode record = json.readTree(line);
      if (record.has("authorityTier")) {
        tiers.add(record.get("authorityTier").asText());
      }
    }
    assertEquals(List.of("BENIGN_WRITE", "STATE_CHANGING_WRITE", "BENIGN_WRITE",
            "STATE_CHANGING_WRITE", "STATE_CHANGING_WRITE", "STATE_CHANGING_WRITE"),
        tiers, "ops log records the tier of every decided injection");
  }

  /**
   * SIM-TC-049: fail-closed. Undecodable, truncated and length-inconsistent
   * octets, a decodable telecommand absent from the classification table, and
   * one outside the allowlist are each rejected — nothing injected, no tc
   * frame, no TM, no budget consumed. By contrast V-NEG-02 decodes, classifies
   * as a ping and is forwarded, so the spacecraft's own rejection path stays
   * reachable: the gate rejects on undecodability, not on spacecraft-level
   * validity.
   */
  @Test
  @TestCase("SIM-TC-049")
  @Requirement("SIM-REQ-CAG-003")
  void failsClosedOnAnythingItCannotClassify() throws Exception {
    startGateway("3,17", 100);
    assertEquals(100, readResource("satsim://state").get("remainingBudget").asInt());

    Map<String, String> rejected = new java.util.LinkedHashMap<>();
    rejected.put(V_NEG_01, "UNDECODABLE");
    rejected.put(V_TC_01.substring(0, 24), "UNDECODABLE");
    rejected.put(V_TC_01 + "00", "UNDECODABLE");
    rejected.forEach((hex, expected) -> {
      McpSchema.CallToolResult result = callExpectingError("send_raw_tc", Map.of("hex", hex));
      assertTrue(text(result).startsWith(expected),
          () -> "expected " + expected + " for " + hex + ", got: " + text(result));
    });

    // Decodable, inside the allowlist, but no classification-table entry.
    McpSchema.CallToolResult gap = callExpectingError("send_tc",
        Map.of("service", 17, "subtype", 3, "ackFlags", 0, "appDataHex", ""));
    assertTrue(text(gap).startsWith("UNCLASSIFIED"), text(gap));

    assertEquals(0, call("get_packet_log", Map.of()).get("records").size(),
        "no rejected injection may reach the simulator");
    assertEquals(100, readResource("satsim://state").get("remainingBudget").asInt(),
        "rejected calls consume no budget");

    // Outside the configured allowlist.
    restartGateway("17/1", 100);
    McpSchema.CallToolResult offList = callExpectingError("send_tc",
        Map.of("service", 3, "subtype", 5, "ackFlags", 9, "appDataHex", "00010002"));
    assertTrue(text(offList).startsWith("ALLOWLIST_DENIED"), text(offList));

    // The contrast: decodable but semantically invalid still reaches the
    // spacecraft, which answers with its own TM(1,2) per ICD §10.2.
    JsonNode forwarded = call("send_raw_tc", Map.of("hex", V_NEG_02));
    assertEquals(V_NEG_02, forwarded.get("hex").asText());
    simulation.advanceBy(QUANTUM_NANOS);
    JsonNode log = pollLog(Map.of("kind", "tm"), 1);
    assertEquals(1, log.get("records").size());
    assertEquals(V_TM_08, log.get("records").get(0).get("frame").get("hex").asText(),
        "V-NEG-02 forwards and yields TM(1,2) byte-identical to V-TM-08");
  }

  /**
   * SIM-TC-050: a state-changing telecommand is held — nothing injected, the
   * commanded state change does not happen, the hold is listed in the state
   * resource, and re-submitting without a confirmation holds again. And the
   * property the whole increment rests on: the MCP surface offers no tool by
   * which the client could record the confirmation.
   */
  @Test
  @TestCase("SIM-TC-050")
  @Requirement("SIM-REQ-CAG-005")
  void stateChangingCommandIsHeldAndNothingIsForwarded() throws Exception {
    startGateway("3,17", 100);
    // Default structure SID 1 reports every 1.0 s of simulated time
    // (SIM-REQ-HK-003); disabling it is the state change under test.
    Map<String, Object> disableSid1 =
        Map.of("service", 3, "subtype", 7, "ackFlags", 9, "appDataHex", "00010001");

    JsonNode held = call("send_tc", disableSid1);
    assertTrue(held.get("confirmationRequired").asBoolean());
    assertFalse(held.get("injected").asBoolean());
    String token = held.get("confirmationToken").asText();
    assertFalse(token.isBlank(), "a non-blank confirmation token is required");
    assertEquals(3, held.get("command").get("service").asInt());
    assertEquals(7, held.get("command").get("subtype").asInt());

    JsonNode pending = readResource("satsim://state").get("pendingConfirmations");
    assertEquals(1, pending.size(), "the hold is listed in the state resource");
    assertEquals(token, pending.get(0).get("token").asText());

    // Nothing was injected and SID 1 keeps reporting: the state did not change.
    assertEquals(0, call("get_packet_log", Map.of("kind", "tc")).get("records").size(),
        "a held injection must not broadcast a tc frame");
    simulation.advanceBy(2_000_000_000L);
    JsonNode hk = pollLog(Map.of("kind", "tm", "service", 3, "subtype", 25), 2);
    assertTrue(hk.get("records").size() >= 2,
        "SID 1 must still report — the disable was not forwarded");

    // Re-submitting without any confirmation holds again, still forwarding nothing.
    JsonNode again = call("send_tc", disableSid1);
    assertTrue(again.get("confirmationRequired").asBoolean());
    assertEquals(0, call("get_packet_log", Map.of("kind", "tc")).get("records").size());

    // The barrier stated negatively: there is no confirming tool to call.
    Set<String> tools = client.listTools().tools().stream()
        .map(McpSchema.Tool::name).collect(Collectors.toSet());
    assertEquals(Set.of("send_tc", "preview_tc", "send_raw_tc", "get_packet_log", "await_tm"),
        tools, "no MCP tool may record a confirmation");
  }

  /**
   * SIM-TC-051: a recorded confirmation releases the command exactly once;
   * a further submission is held again under a new token; a confirmation for
   * a token the gate does not hold releases nothing; and nothing recorded
   * before a restart survives it.
   */
  @Test
  @TestCase("SIM-TC-051")
  @Requirement("SIM-REQ-CAG-005")
  void recordedConfirmationForwardsExactlyOnce() throws Exception {
    startGateway("3,17", 100);
    Map<String, Object> disableSid1 =
        Map.of("service", 3, "subtype", 7, "ackFlags", 9, "appDataHex", "00010001");

    String token = call("send_tc", disableSid1).get("confirmationToken").asText();
    recordConfirmation(token);

    JsonNode forwarded = call("send_tc", disableSid1);
    assertFalse(forwarded.has("confirmationRequired"), "the confirmed command must forward");
    assertEquals(3, forwarded.get("decoded").get("service").asInt());
    simulation.advanceBy(QUANTUM_NANOS);
    assertEquals(1, pollLog(Map.of("kind", "tc"), 1).get("records").size(),
        "exactly one injection per recorded confirmation");
    // Forwarded for real: SID 1 stops reporting.
    JsonNode beforeQuiet = call("get_packet_log", Map.of("kind", "tm", "service", 3));
    simulation.advanceBy(3_000_000_000L);
    Thread.sleep(200);
    assertEquals(beforeQuiet.get("records").size(),
        call("get_packet_log", Map.of("kind", "tm", "service", 3)).get("records").size(),
        "no further TM(3,25) after the confirmed disable was forwarded");

    // Single use: the same command is held again, under a different token.
    JsonNode heldAgain = call("send_tc", disableSid1);
    assertTrue(heldAgain.get("confirmationRequired").asBoolean());
    String secondToken = heldAgain.get("confirmationToken").asText();
    assertNotEquals(token, secondToken, "a consumed confirmation is not reusable");

    // A confirmation for a token the gate does not hold releases nothing and
    // is reported so it can be logged.
    recordConfirmation("H99-deadbeef");
    JsonNode stillHeld = call("send_tc", disableSid1);
    assertTrue(stillHeld.get("confirmationRequired").asBoolean(),
        "an unknown token must release nothing");
    assertTrue(Files.readAllLines(opsLogPath()).stream()
            .anyMatch(line -> line.contains("H99-deadbeef")),
        "the discarded confirmation is logged");

    // Restart: neither the hold nor a confirmation recorded before it survives.
    recordConfirmation(secondToken);
    restartGateway("3,17", 100);
    JsonNode afterRestart = call("send_tc", disableSid1);
    assertTrue(afterRestart.get("confirmationRequired").asBoolean(),
        "no confirmation may survive a gateway restart");
    assertNotEquals(secondToken, afterRestart.get("confirmationToken").asText());
  }

  /**
   * SIM-TC-052: the ops log is the gate's evidence. Every decided injection
   * carries the decision, a non-blank reason and the OBT; the tier is present
   * wherever classification completed and absent where it could not; all three
   * outcomes occur; and no injection reached the simulator without a forward
   * record.
   */
  @Test
  @TestCase("SIM-TC-052")
  @Requirement("SIM-REQ-CAG-006")
  void gateDecisionsAreLoggedAsEvidence() throws Exception {
    startGateway("3,17", 100);

    call("send_tc", Map.of("service", 17, "subtype", 1, "ackFlags", 0, "appDataHex", ""));
    callExpectingError("send_raw_tc", Map.of("hex", V_NEG_01));
    call("send_tc", Map.of("service", 3, "subtype", 7, "ackFlags", 9, "appDataHex", "00010001"));

    List<JsonNode> decided = new ArrayList<>();
    for (String line : Files.readAllLines(opsLogPath())) {
      JsonNode record = json.readTree(line);
      if (record.has("gateDecision")) {
        decided.add(record);
      }
    }
    assertEquals(3, decided.size(), "one gate decision per injection attempt");

    for (JsonNode record : decided) {
      assertFalse(record.get("gateReason").asText().isBlank(), "a reason is always recorded");
      assertNotNull(record.get("obt").get("timeCoarse"), "OBT is always stamped");
    }
    assertEquals(List.of("forward", "reject", "confirmation-required"),
        decided.stream().map(r -> r.get("gateDecision").asText()).toList(),
        "all three outcomes of ICD §8.4 occur and are named");

    assertEquals("BENIGN_WRITE", decided.get(0).get("authorityTier").asText());
    assertFalse(decided.get(1).has("authorityTier"),
        "undecodable octets have no tier, and none is invented");
    assertEquals("STATE_CHANGING_WRITE", decided.get(2).get("authorityTier").asText());
    assertFalse(decided.get(2).get("confirmationToken").asText().isBlank());

    // Exactly one injection reached the simulator, and it is the forwarded one.
    simulation.advanceBy(QUANTUM_NANOS);
    JsonNode tcRecords = pollLog(Map.of("kind", "tc"), 1);
    assertEquals(1, tcRecords.get("records").size(),
        "only the forwarded injection reached the simulator");
    assertEquals(V_TC_01, tcRecords.get("records").get(0).get("frame").get("hex").asText());
  }
}
