package org.satsim.sim.obsw;

import java.util.List;

/**
 * The on-board <em>software</em> a Java-hosted {@link ObswTarget} runs — the
 * behavioral stand-in for real OBSW: receives complete TC space packets and
 * produces the TM space packets they cause. The hosting target is the
 * on-board <em>computer</em> (transport and time mechanics); this interface
 * is the software running on it. It exists only for Java-hosted targets:
 * from M3 on, external targets carry the real OBSW binary instead, and no
 * {@code SimulatedObsw} is involved.
 *
 * <p>Implementations are pure with respect to time — the current simulated
 * time is passed in by the hosting target (slave-local time within granted
 * execution windows, ADR-0006); wall-clock access is prohibited
 * [SIM-REQ-TIME-001].
 */
@FunctionalInterface
public interface SimulatedObsw {

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
