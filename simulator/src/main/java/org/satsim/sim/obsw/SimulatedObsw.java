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
 *
 * <p>From M1b the OBSW also has autonomous, time-driven behavior (periodic
 * housekeeping, ICD §9.6, SCR-001): {@link #nextEventNanos()} exposes the
 * next simulated instant at which on-board work is due, and
 * {@link #handleTimeEvent(long)} performs all work due at that instant. The
 * hosting target polls the former and calls the latter exactly when
 * slave-local time reaches it, so time-triggered TM is emitted at exact
 * simulated instants [SIM-REQ-HK-002].
 */
@FunctionalInterface
public interface SimulatedObsw {

  /** Sentinel for {@link #nextEventNanos()}: no autonomous on-board work is scheduled. */
  long NO_EVENT = Long.MAX_VALUE;

  /**
   * Processes one TC space packet at simulated time {@code nowNanos} and
   * returns the TM space packets to emit, in emission order (possibly empty,
   * e.g. for rejected TCs).
   *
   * @param tcPacket the complete encoded TC space packet
   * @param nowNanos current slave-local simulated time, nanoseconds since epoch
   */
  List<byte[]> handleTc(byte[] tcPacket, long nowNanos);

  /**
   * The next simulated time (nanoseconds since epoch) at which this OBSW has
   * autonomous work due, or {@link #NO_EVENT} if none is scheduled. Never
   * earlier than the last {@code nowNanos} passed to
   * {@link #handleTimeEvent(long)}, which must advance it strictly beyond
   * that instant (the hosting target enforces this to guarantee progress).
   */
  default long nextEventNanos() {
    return NO_EVENT;
  }

  /**
   * Performs all autonomous work due at simulated time {@code nowNanos} —
   * called by the hosting target exactly when slave-local time reaches
   * {@link #nextEventNanos()} — and returns the TM space packets to emit,
   * in emission order.
   *
   * @param nowNanos current slave-local simulated time, nanoseconds since epoch
   */
  default List<byte[]> handleTimeEvent(long nowNanos) {
    return List.of();
  }
}
