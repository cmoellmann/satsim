package org.satsim.mcp;

import java.util.Optional;

/**
 * The session TC budget per ICD §8.4 [SIM-REQ-MCP-005]: a coarse ceiling on
 * how many telecommands one gateway session may forward, decremented by every
 * forwarded injection ({@code preview_tc} exempt; rejected and held calls
 * consume nothing).
 *
 * <p>Content-based authorization is deliberately <em>not</em> here. From M1h it
 * belongs to the Command Authorization Gate (ADR-0007 C1, SCR-010): the gate is
 * the Category B configuration item and the gateway keeps transport concerns,
 * so the budget — a session control that needs no knowledge of what a
 * telecommand does — stays on this side of the seam and the gate stays small.
 */
final class Authority {

  private final GatewayConfig config;
  private int remaining;

  Authority(GatewayConfig config) {
    this.config = config;
    this.remaining = config.budget();
  }

  /**
   * Consumes one budget unit for an injection the gate has authorized.
   *
   * @return empty if consumed, otherwise the exhaustion reason
   */
  synchronized Optional<String> consumeBudget() {
    if (remaining <= 0) {
      return Optional.of("BUDGET_EXHAUSTED: the session TC budget of "
          + config.budget() + " injections is used up");
    }
    remaining--;
    return Optional.empty();
  }

  synchronized int remaining() {
    return remaining;
  }
}
