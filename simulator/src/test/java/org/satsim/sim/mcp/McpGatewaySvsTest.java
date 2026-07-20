package org.satsim.sim.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.satsim.sim.web.SimulationService;
import org.satsim.testsupport.Requirement;
import org.satsim.testsupport.TestCase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;

/**
 * SVS SIM-TC-041..045 (SCR-008, scope M1f): the MCP operator gateway per
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
            "--icd", Path.of("..", "docs", "icd.md").toString())
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
   * SIM-TC-044: send_raw_tc of V-NEG-01 injects the octets verbatim; no TM
   * is emitted; the packet log subsequently contains exactly one rejection
   * record with reason NOT_A_PACKET and the offending hex.
   */
  @Test
  @TestCase("SIM-TC-044")
  @Requirement({"SIM-REQ-MCP-003", "SIM-REQ-MCP-001"})
  void rejectionVisibility() throws Exception {
    startGateway("3,17", 100);
    JsonNode response = call("send_raw_tc", Map.of("hex", V_NEG_01));
    assertEquals("CRC_ERROR", response.get("decodeError").asText());

    simulation.advanceBy(500_000_000L);
    JsonNode log = pollLog(Map.of(), 1);
    assertEquals(1, log.get("records").size(), "exactly one rejection record expected");
    JsonNode rejection = log.get("records").get(0);
    assertEquals("rejection", rejection.get("kind").asText());
    assertEquals("NOT_A_PACKET", rejection.get("frame").get("reason").asText());
    assertEquals(V_NEG_01, rejection.get("frame").get("hex").asText());
  }

  /**
   * SIM-TC-045: with an allowlist excluding ST[17], send_tc(17,1) returns a
   * tool error and injects nothing; with session budget 2, the third
   * injection returns a budget-exhausted error; the ops log holds one JSONL
   * record per tool invocation including the denied ones, each with outcome
   * and OBT.
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

    // Undecodable raw octets are allowlist-exempt (ICD §8.4) and consume budget.
    call("send_raw_tc", Map.of("hex", "00"));
    call("send_raw_tc", Map.of("hex", "00"));
    McpSchema.CallToolResult exhausted = callExpectingError("send_raw_tc", Map.of("hex", "00"));
    assertTrue(text(exhausted).startsWith("BUDGET_EXHAUSTED"), text(exhausted));
    assertEquals(0, readResource("satsim://state").get("remainingBudget").asInt());

    List<String> lines = Files.readAllLines(opsLogPath());
    // 5 tool invocations above (send_tc, get_packet_log, 3x send_raw_tc);
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
}
