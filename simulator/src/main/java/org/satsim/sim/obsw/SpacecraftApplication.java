package org.satsim.sim.obsw;

import java.util.List;

/**
 * The (simulated) spacecraft's application software behind an OBSW target's
 * transport mechanics: receives complete TC space packets and produces the TM
 * space packets they cause. Implementations are pure with respect to time —
 * the current simulated time is passed in by the hosting target (slave-local
 * time within granted execution windows, ADR-0006); wall-clock access is
 * prohibited [SIM-REQ-TIME-001].
 */
@FunctionalInterface
public interface SpacecraftApplication {

  /**
   * Processes one TC space packet at simulated time {@code nowNanos} and
   * returns the TM space packets to emit, in emission order (possibly empty,
   * e.g. for rejected TCs).
   *
   * @param tcPacket the complete encoded TC space packet
   * @param nowNanos current slave-local simulated time, nanoseconds since epoch
   */
  List<byte[]> handleTc(byte[] tcPacket, long nowNanos);
}
