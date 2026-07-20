package org.satsim.mcp;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.UncheckedIOException;

/**
 * ICD §8.1/§8.2 adapter: TC submission via {@code POST /api/tc} and
 * {@code /api/tc/preview}, frame stream via the {@code /api/tm} WebSocket
 * (JDK {@code java.net.http}, no further dependencies).
 */
final class RestWsLink implements WebApiLink {

  private final URI baseUrl;
  private final ObjectMapper json;
  private final HttpClient http = HttpClient.newHttpClient();
  private WebSocket socket;

  RestWsLink(URI baseUrl, ObjectMapper json) {
    this.baseUrl = baseUrl;
    this.json = json;
  }

  @Override
  public Map<String, Object> submitStructured(int service, int subtype, Integer ackFlags,
      String appDataHex) throws LinkException {
    return post("/api/tc", structuredBody(service, subtype, ackFlags, appDataHex));
  }

  @Override
  public Map<String, Object> submitRaw(String hex) throws LinkException {
    return post("/api/tc", Map.of("hex", hex));
  }

  @Override
  public Map<String, Object> preview(int service, int subtype, Integer ackFlags,
      String appDataHex) throws LinkException {
    return post("/api/tc/preview", structuredBody(service, subtype, ackFlags, appDataHex));
  }

  private static Map<String, Object> structuredBody(int service, int subtype,
      Integer ackFlags, String appDataHex) {
    Map<String, Object> body = new HashMap<>();
    body.put("service", service);
    body.put("subtype", subtype);
    if (ackFlags != null) {
      body.put("ackFlags", ackFlags);
    }
    if (appDataHex != null) {
      body.put("appDataHex", appDataHex);
    }
    return body;
  }

  private Map<String, Object> post(String path, Map<String, Object> body)
      throws LinkException {
    try {
      HttpRequest request = HttpRequest.newBuilder(baseUrl.resolve(path))
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
          .build();
      HttpResponse<String> response =
          http.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        throw new LinkException(
            "HTTP " + response.statusCode() + " from " + path + ": " + response.body());
      }
      return toMap(response.body());
    } catch (LinkException e) {
      throw e;
    } catch (Exception e) {
      throw new LinkException("web API request to " + path + " failed: " + e, e);
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> toMap(String body) {
    try {
      return json.readValue(body, Map.class);
    } catch (JsonProcessingException e) {
      throw new UncheckedIOException("unparseable web-API JSON: " + body, e);
    }
  }

  @Override
  public void start(Consumer<Map<String, Object>> listener) throws LinkException {
    String ws = "ws" + baseUrl.toString().substring(baseUrl.getScheme().length());
    WebSocket.Listener frames = new WebSocket.Listener() {
      private final StringBuilder partial = new StringBuilder();

      @Override
      public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        partial.append(data);
        if (last) {
          listener.accept(toMap(partial.toString()));
          partial.setLength(0);
        }
        webSocket.request(1);
        return null;
      }
    };
    try {
      socket = http.newWebSocketBuilder()
          .buildAsync(URI.create(ws + "/api/tm"), frames)
          .get(10, TimeUnit.SECONDS);
    } catch (Exception e) {
      throw new LinkException("WebSocket connect to /api/tm failed: " + e, e);
    }
  }

  @Override
  public void close() {
    if (socket != null) {
      socket.abort();
    }
  }
}
