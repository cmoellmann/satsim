package org.satsim.sim.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.satsim.pus.time.CucTime;
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
 * ICD §8 web-API behaviors added by SCR-003: time frames, enriched TC
 * responses, rejection frames. Pacing is disabled
 * ({@code satsim.pacing.tick-millis=0}), so simulated time advances only via
 * explicit {@link SimulationService#advanceBy(long)} calls and every
 * assertion is deterministic; each test gets a fresh context (clock at 0).
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "satsim.pacing.tick-millis=0")
@DirtiesContext(classMode = ClassMode.AFTER_EACH_TEST_METHOD)
class HmiWebApiTest {

  private static final String V_TC_01_BROKEN_CRC_HEX = "1864c00000062011010000fa84";
  private static final long QUANTUM_NANOS = 100_000_000L;

  @LocalServerPort
  private int port;

  @Autowired
  private TestRestTemplate rest;

  @Autowired
  private SimulationService simulation;

  private final ObjectMapper json = new ObjectMapper();

  /** Collects complete WebSocket text messages. */
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

  /**
   * SIM-TC-027: a connecting session receives a time frame with the current
   * OBT; advancing 1 s yields strictly monotonic time frames at the 100 ms
   * simulated quantum; without an advance no further time frames arrive.
   */
  @Test
  @TestCase("SIM-TC-027")
  @Requirement("SIM-REQ-UI-005")
  void timeFramesFollowSimulatedTime() throws Exception {
    TextCollector collector = new TextCollector();
    WebSocket socket = connect(collector);
    try {
      JsonNode onConnect = nextFrame(collector);
      assertEquals("time", onConnect.get("kind").asText());
      assertEquals(0, onConnect.get("timeCoarse").asLong());
      assertEquals(0, onConnect.get("timeFine").asInt());

      simulation.advanceBy(1_000_000_000L);
      // 11 frames: 10 time frames at the 100 ms quantum plus the default
      // SID 1 TM(3,25) at T=1.0 s (M1b) — filter to the time frames; the SVS
      // criterion constrains their monotonicity, not the interleaved TM.
      List<JsonNode> timeFrames = byKind(drainFrames(collector, 11), "time");
      assertEquals(10, timeFrames.size());
      for (int i = 0; i < 10; i++) {
        JsonNode frame = timeFrames.get(i);
        CucTime expected = CucTime.ofNanos((i + 1) * QUANTUM_NANOS);
        assertEquals("time", frame.get("kind").asText());
        assertEquals(expected.coarse(), frame.get("timeCoarse").asLong(), "frame " + i);
        assertEquals(expected.fine(), frame.get("timeFine").asInt(), "frame " + i);
      }

      assertNull(collector.messages.poll(300, TimeUnit.MILLISECONDS),
          "no time frames expected without a simulated-time advance");
    } finally {
      socket.abort();
    }
  }

  /**
   * SIM-TC-028: structured compose responses carry injection OBT (= current
   * simulated time), the consumed ground sequence count and decoded fields
   * matching the submission; an undecodable raw injection yields
   * decoded=null with the first failed ICD §6.3 check.
   */
  @Test
  @TestCase("SIM-TC-028")
  @Requirement("SIM-REQ-UI-006")
  void tcResponsesCarryObtSequenceCountAndDecodedFields() {
    Map<String, Object> compose =
        Map.of("service", 17, "subtype", 1, "ackFlags", 9, "appDataHex", "");
    ResponseEntity<Map> first = rest.postForEntity("/api/tc", compose, Map.class);
    ResponseEntity<Map> second = rest.postForEntity("/api/tc", compose, Map.class);
    assertTrue(first.getStatusCode().is2xxSuccessful());

    // Injection OBT is the current simulated time: no advance has happened.
    assertEquals(0, ((Number) first.getBody().get("timeCoarse")).longValue());
    assertEquals(0, ((Number) first.getBody().get("timeFine")).intValue());
    assertEquals(0.0, ((Number) first.getBody().get("timeSeconds")).doubleValue());

    // Ground sequence count is consumed per submission.
    assertEquals(0, ((Number) first.getBody().get("sequenceCount")).intValue());
    assertEquals(1, ((Number) second.getBody().get("sequenceCount")).intValue());

    Map<?, ?> decoded = (Map<?, ?>) first.getBody().get("decoded");
    assertEquals(100, ((Number) decoded.get("apid")).intValue());
    assertEquals(2, ((Number) decoded.get("pusVersion")).intValue());
    assertEquals(9, ((Number) decoded.get("ackFlags")).intValue());
    assertEquals(17, ((Number) decoded.get("service")).intValue());
    assertEquals(1, ((Number) decoded.get("subtype")).intValue());
    assertEquals(0, ((Number) decoded.get("sourceId")).intValue());
    assertEquals("", decoded.get("appDataHex"));

    ResponseEntity<Map> broken =
        rest.postForEntity("/api/tc", Map.of("hex", V_TC_01_BROKEN_CRC_HEX), Map.class);
    assertTrue(broken.getStatusCode().is2xxSuccessful());
    assertEquals("CRC_ERROR", broken.getBody().get("decodeError"));
    assertNull(broken.getBody().get("decoded"));
    assertFalse(broken.getBody().containsKey("sequenceCount"));
  }

  /**
   * SIM-TC-029: a CRC-broken injection yields exactly one NOT_A_PACKET
   * rejection frame and no TM (ICD §6.3: no attributable request); a
   * structurally valid TC for a service outside the tailored set (TC(2,1))
   * yields one ILLEGAL_SERVICE_OR_SUBTYPE rejection frame and, from M1a
   * (SCR-002), exactly one TM(1,2) failure report [SIM-REQ-VER-003].
   */
  @Test
  @TestCase("SIM-TC-029")
  @Requirement("SIM-REQ-UI-007")
  void rejectionsArePublishedAsRejectionFrames() throws Exception {
    TextCollector collector = new TextCollector();
    WebSocket socket = connect(collector);
    try {
      // Advance below the 1.0 s default-HK boundary (M1b): the "no TM"
      // criterion refers to the discarded TC and must not be blurred by the
      // periodic SID 1 report.
      rest.postForEntity("/api/tc", Map.of("hex", V_TC_01_BROKEN_CRC_HEX), Map.class);
      simulation.advanceBy(500_000_000L);
      List<JsonNode> frames = drainFrames(collector, 6);
      List<JsonNode> rejections = byKind(frames, "rejection");
      assertEquals(1, rejections.size(), "exactly one rejection frame expected");
      assertEquals("NOT_A_PACKET", rejections.get(0).get("reason").asText());
      assertEquals(V_TC_01_BROKEN_CRC_HEX, rejections.get(0).get("hex").asText());
      assertEquals(0, rejections.get(0).get("timeCoarse").asLong());
      assertTrue(byKind(frames, "tm").isEmpty(), "a discarded TC must not produce TM");

      rest.postForEntity("/api/tc", Map.of("service", 2, "subtype", 1), Map.class);
      simulation.advanceBy(QUANTUM_NANOS);
      List<JsonNode> more = drainFrames(collector, 2);
      List<JsonNode> moreRejections = byKind(more, "rejection");
      assertEquals(1, moreRejections.size(), "exactly one rejection frame expected");
      assertEquals("ILLEGAL_SERVICE_OR_SUBTYPE", moreRejections.get(0).get("reason").asText());
      List<JsonNode> moreTms = byKind(more, "tm");
      assertEquals(1, moreTms.size(), "exactly one TM(1,2) failure report expected (M1a, SIM-REQ-VER-003)");
      JsonNode decoded = moreTms.get(0).get("decoded");
      assertEquals(1, decoded.get("service").asInt());
      assertEquals(2, decoded.get("subtype").asInt());
    } finally {
      socket.abort();
    }
  }

  private WebSocket connect(TextCollector collector) throws Exception {
    return HttpClient.newHttpClient().newWebSocketBuilder()
        .buildAsync(URI.create("ws://localhost:" + port + "/api/tm"), collector)
        .get(5, TimeUnit.SECONDS);
  }

  private JsonNode nextFrame(TextCollector collector) throws Exception {
    String frameJson = collector.messages.poll(5, TimeUnit.SECONDS);
    assertNotNull(frameJson, "no frame received on /api/tm within 5 s");
    return json.readTree(frameJson);
  }

  /** Reads at least {@code minimum} frames, then drains until 300 ms of quiet. */
  private List<JsonNode> drainFrames(TextCollector collector, int minimum) throws Exception {
    List<JsonNode> frames = new ArrayList<>();
    for (int i = 0; i < minimum; i++) {
      frames.add(nextFrame(collector));
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
}
