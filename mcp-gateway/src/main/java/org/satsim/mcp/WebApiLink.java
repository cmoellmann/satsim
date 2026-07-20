package org.satsim.mcp;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Link-adapter seam between the gateway and the simulator
 * [SIM-REQ-MCP-002]. The only implementation today is {@link RestWsLink}
 * (ICD §8.1/§8.2); the M2 TCP link (ICD §8.3) would join as a second
 * adapter by a later SCR.
 *
 * <p>Requests and frames are passed as the parsed JSON structures of the
 * web API — the gateway relays packet octets (hex fields) unmodified.
 */
public interface WebApiLink extends AutoCloseable {

  /** Structured compose and injection per ICD §8.1. */
  Map<String, Object> submitStructured(int service, int subtype, Integer ackFlags,
      String appDataHex) throws LinkException;

  /** Raw injection per ICD §8.1, deliberately unvalidated. */
  Map<String, Object> submitRaw(String hex) throws LinkException;

  /** Preview per ICD §8.1: encoded octets only, nothing injected. */
  Map<String, Object> preview(int service, int subtype, Integer ackFlags,
      String appDataHex) throws LinkException;

  /** Connects the ICD §8.2 frame stream; each parsed frame goes to {@code listener}. */
  void start(Consumer<Map<String, Object>> listener) throws LinkException;

  @Override
  void close();

  /** A web-API interaction failed (HTTP error, transport failure). */
  final class LinkException extends Exception {
    public LinkException(String message) {
      super(message);
    }

    public LinkException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
