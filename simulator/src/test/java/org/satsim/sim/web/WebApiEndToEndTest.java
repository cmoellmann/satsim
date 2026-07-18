package org.satsim.sim.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.satsim.pus.PacketDecodeException;
import org.satsim.pus.tm.TmPacket;
import org.satsim.testsupport.Requirement;
import org.satsim.testsupport.TestCase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;

/**
 * REST/WebSocket end-to-end path without a browser: TC in via
 * {@code POST /api/tc}, TM out via the {@code /api/tm} WebSocket
 * (JDK built-in client).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebApiEndToEndTest {

  private static final HexFormat HEX = HexFormat.of();
  private static final String V_TC_01_HEX = "1864c00000062011010000fa83";

  @LocalServerPort
  private int port;

  @Autowired
  private TestRestTemplate rest;

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
   * SIM-TC-012: POST of TC(17,1) via REST yields a TM(17,2) frame on the
   * WebSocket containing raw hex and decoded fields matching ICD values.
   */
  @Test
  @TestCase("SIM-TC-012")
  @Requirement("SIM-REQ-UI-003")
  void restTcYieldsWebSocketTmFrame() throws Exception {
    TextCollector collector = new TextCollector();
    WebSocket socket = HttpClient.newHttpClient().newWebSocketBuilder()
        .buildAsync(URI.create("ws://localhost:" + port + "/api/tm"), collector)
        .get(5, TimeUnit.SECONDS);
    try {
      ResponseEntity<Map> response =
          rest.postForEntity("/api/tc", Map.of("hex", V_TC_01_HEX), Map.class);
      assertTrue(response.getStatusCode().is2xxSuccessful());
      assertEquals(V_TC_01_HEX, response.getBody().get("hex"));

      String frameJson = collector.messages.poll(5, TimeUnit.SECONDS);
      assertNotNull(frameJson, "no TM frame received on /api/tm within 5 s");
      JsonNode frame = json.readTree(frameJson);

      // Raw hex present and a decodable TM(17,2) on APID 100.
      String tmHex = frame.get("hex").asText();
      TmPacket tm = decode(HEX.parseHex(tmHex));
      assertEquals(100, tm.primaryHeader().apid());
      assertEquals(17, tm.secondaryHeader().serviceType());
      assertEquals(2, tm.secondaryHeader().messageSubtype());

      // Decoded fields in the frame match the ICD values of the packet.
      JsonNode decoded = frame.get("decoded");
      assertEquals(100, decoded.get("apid").asInt());
      assertEquals(17, decoded.get("service").asInt());
      assertEquals(2, decoded.get("subtype").asInt());
      assertEquals(0, decoded.get("destinationId").asInt());
      assertEquals(tm.primaryHeader().sequenceCount(), decoded.get("sequenceCount").asInt());
      assertEquals(
          tm.secondaryHeader().messageTypeCounter(), decoded.get("messageTypeCounter").asInt());
      assertEquals(tm.secondaryHeader().time().coarse(), decoded.get("timeCoarse").asLong());
      assertEquals(tm.secondaryHeader().time().fine(), decoded.get("timeFine").asInt());
    } finally {
      socket.abort();
    }
  }

  // Untraced unit tests (engineering hygiene, SDP §5).

  @Test
  void previewEncodesStructuredComposeWithoutConsumingSequenceCounts() {
    Map<String, Object> compose =
        Map.of("service", 17, "subtype", 1, "ackFlags", 0, "appDataHex", "");
    ResponseEntity<Map> first = rest.postForEntity("/api/tc/preview", compose, Map.class);
    ResponseEntity<Map> second = rest.postForEntity("/api/tc/preview", compose, Map.class);
    assertTrue(first.getStatusCode().is2xxSuccessful());
    // Non-consuming: two previews yield the identical packet (same seq count).
    assertEquals(first.getBody().get("hex"), second.getBody().get("hex"));
    // The preview is a well-formed TC(17,1) on APID 100.
    org.satsim.pus.tc.TcPacket tc = decodeTc(HEX.parseHex((String) first.getBody().get("hex")));
    assertEquals(100, tc.primaryHeader().apid());
    assertEquals(17, tc.secondaryHeader().serviceType());
    assertEquals(1, tc.secondaryHeader().messageSubtype());
  }

  @Test
  void invalidSubmissionsAreRejectedWith400() {
    ResponseEntity<Map> badHex =
        rest.postForEntity("/api/tc", Map.of("hex", "zz"), Map.class);
    assertEquals(400, badHex.getStatusCode().value());
    ResponseEntity<Map> missingFields =
        rest.postForEntity("/api/tc", Map.of("service", 17), Map.class);
    assertEquals(400, missingFields.getStatusCode().value());
  }

  private static TmPacket decode(byte[] packet) {
    try {
      return TmPacket.decode(packet);
    } catch (PacketDecodeException e) {
      throw new AssertionError("emitted TM must decode", e);
    }
  }

  private static org.satsim.pus.tc.TcPacket decodeTc(byte[] packet) {
    try {
      return org.satsim.pus.tc.TcPacket.decode(packet);
    } catch (PacketDecodeException e) {
      throw new AssertionError("previewed TC must decode", e);
    }
  }
}
