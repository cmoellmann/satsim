package org.satsim.sim.time;

/**
 * The sole source of time for all simulation logic (ADR-0004, ADR-0006 C1).
 *
 * <p>Simulated time is a linear count from the simulation epoch
 * (2026-01-01T00:00:00 UTC, ICD §5). Wall-clock access in simulation logic is
 * prohibited (SIM-REQ-TIME-001); only the master's interactive pacing policy
 * may consult wall time.
 */
public interface SimulationClock {

  /** Current simulated time in nanoseconds since the simulation epoch. */
  long nanos();

  /** Convenience: current simulated time as CUC 4+2 octets per ICD §5. */
  default byte[] toCuc() {
    long n = nanos();
    long coarse = n / 1_000_000_000L;
    long fine = ((n % 1_000_000_000L) * 65_536L + 500_000_000L) / 1_000_000_000L;
    if (fine == 65_536L) { coarse++; fine = 0; }
    return new byte[] {
      (byte) (coarse >>> 24), (byte) (coarse >>> 16), (byte) (coarse >>> 8), (byte) coarse,
      (byte) (fine >>> 8), (byte) fine
    };
  }
}
