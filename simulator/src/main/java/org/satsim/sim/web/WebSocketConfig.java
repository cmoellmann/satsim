package org.satsim.sim.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** Registers the TM WebSocket endpoint at {@code /api/tm} (ICD §8). */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

  private final TmWebSocketHandler tmHandler;

  public WebSocketConfig(TmWebSocketHandler tmHandler) {
    this.tmHandler = tmHandler;
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry.addHandler(tmHandler, "/api/tm");
  }
}
