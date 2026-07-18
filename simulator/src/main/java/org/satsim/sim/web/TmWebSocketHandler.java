package org.satsim.sim.web;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.logging.Logger;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * WebSocket TM distribution at {@code /api/tm} (ICD §8): every emitted TM is
 * broadcast as one JSON text frame ({@link TmFrame}) to all connected
 * sessions [SIM-REQ-UI-002, SIM-REQ-UI-003].
 */
@Component
public class TmWebSocketHandler extends TextWebSocketHandler {

  private static final Logger LOG = Logger.getLogger(TmWebSocketHandler.class.getName());

  private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();

  @Override
  public void afterConnectionEstablished(WebSocketSession session) {
    sessions.add(session);
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    sessions.remove(session);
  }

  /** Broadcasts one JSON payload to all open sessions; dead sessions are dropped. */
  public void broadcast(String payload) {
    TextMessage message = new TextMessage(payload);
    for (WebSocketSession session : sessions) {
      try {
        synchronized (session) {
          if (session.isOpen()) {
            session.sendMessage(message);
          }
        }
      } catch (IOException e) {
        LOG.warning(() -> "dropping WebSocket session after send failure: " + e.getMessage());
        sessions.remove(session);
      }
    }
  }
}
