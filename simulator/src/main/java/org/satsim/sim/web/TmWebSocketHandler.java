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
 * WebSocket TM/event distribution at {@code /api/tm} (ICD §8.2): every
 * emitted frame ({@code kind} tm/time/rejection) is broadcast as one JSON
 * text frame to all connected sessions [SIM-REQ-UI-002, SIM-REQ-UI-003,
 * SIM-REQ-UI-005, SIM-REQ-UI-007].
 */
@Component
public class TmWebSocketHandler extends TextWebSocketHandler {

  private static final Logger LOG = Logger.getLogger(TmWebSocketHandler.class.getName());

  private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();
  private java.util.function.Consumer<WebSocketSession> connectHook;

  /**
   * Registers the single hook invoked for each newly connected session
   * (ICD §8.2: time frame on connect, [SIM-REQ-UI-005]).
   */
  public void onSessionConnect(java.util.function.Consumer<WebSocketSession> hook) {
    this.connectHook = hook;
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) {
    sessions.add(session);
    if (connectHook != null) {
      connectHook.accept(session);
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    sessions.remove(session);
  }

  /** Broadcasts one JSON payload to all open sessions; dead sessions are dropped. */
  public void broadcast(String payload) {
    for (WebSocketSession session : sessions) {
      sendTo(session, payload);
    }
  }

  /** Sends one JSON payload to a single session; the session is dropped on failure. */
  public void sendTo(WebSocketSession session, String payload) {
    try {
      synchronized (session) {
        if (session.isOpen()) {
          session.sendMessage(new TextMessage(payload));
        }
      }
    } catch (IOException e) {
      LOG.warning(() -> "dropping WebSocket session after send failure: " + e.getMessage());
      sessions.remove(session);
    }
  }
}
