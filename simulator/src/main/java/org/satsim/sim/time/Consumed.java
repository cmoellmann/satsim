package org.satsim.sim.time;

/**
 * Result of a {@link EmulatorControl#grant(long)} call (ADR-0006 C2).
 *
 * @param nanosConsumed simulated time actually consumed; 0 <= nanosConsumed <= granted budget
 * @param reason why the slave stopped
 */
public record Consumed(long nanosConsumed, StopReason reason) {
  public Consumed {
    if (nanosConsumed < 0) {
      throw new IllegalArgumentException("nanosConsumed must be >= 0");
    }
  }
}
