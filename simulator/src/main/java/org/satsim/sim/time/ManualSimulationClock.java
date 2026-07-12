package org.satsim.sim.time;

/**
 * Manually stepped {@link SimulationClock} for the M0 walking skeleton: the
 * owner advances simulated time explicitly. The discrete-event scheduler that
 * drives this clock from the event queue arrives in M1.
 */
public final class ManualSimulationClock implements SimulationClock {

  private long nanos;

  @Override
  public long nanos() {
    return nanos;
  }

  /** Advances simulated time by {@code deltaNanos} >= 0. */
  public void advance(long deltaNanos) {
    if (deltaNanos < 0) {
      throw new IllegalArgumentException("deltaNanos must be >= 0: " + deltaNanos);
    }
    nanos += deltaNanos;
  }
}
