package org.satsim.mcp;

import java.util.Optional;

/**
 * Authority bounds per ICD §8.4 [SIM-REQ-MCP-005]: (service, subtype)
 * allowlist on the decoded content of every injection and a session TC
 * budget on all injections. Enforced before anything reaches the web API.
 */
final class Authority {

  private final GatewayConfig config;
  private int remaining;

  Authority(GatewayConfig config) {
    this.config = config;
    this.remaining = config.budget();
  }

  /**
   * Vets one injection and, if permitted, consumes one budget unit.
   * {@code service}/{@code subtype} are {@code null} for undecodable raw
   * octets, which the allowlist deliberately permits (ICD §8.4 — they
   * exercise the §6.3 rejection path).
   *
   * @return empty if permitted, otherwise the denial reason
   */
  synchronized Optional<String> vetInjection(Integer service, Integer subtype) {
    if (service != null && subtype != null && !config.allows(service, subtype)) {
      return Optional.of("ALLOWLIST_DENIED: TC(" + service + "," + subtype
          + ") is not in the configured allowlist " + config.allowlist());
    }
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
