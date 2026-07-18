package org.satsim.sim.obsw;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;
import org.satsim.sim.time.Consumed;
import org.satsim.sim.time.StopReason;

/**
 * In-process loopback OBSW target (ADR-0006 C5): implements the same
 * {@link ObswTarget} contract as external targets, so the grant/consume
 * synchronization protocol is exercised from the first increment
 * [SIM-REQ-LINK-001, SIM-REQ-TIME-004].
 *
 * <p>Each received TC is "processed" for a fixed simulated delay and then
 * handed to the {@link SimulatedObsw} at its due time; the resulting
 * TM packets are emitted at that same simulated instant (the M0 placeholder
 * echo became a real {@code SimulatedObsw} in M1). TM due exactly at the
 * budget boundary is emitted (event takes precedence over budget exhaustion).
 *
 * <p>Slave-local time only advances inside {@link #grant(long)}; TCs sent
 * between grants arrive at the current grant boundary. Not thread-safe: the
 * target is owned and driven solely by the time master (ADR-0006).
 */
public final class LoopbackTarget implements ObswTarget {

  private enum State { CREATED, INITIALIZED, HALTED }

  private record PendingTc(long dueNanos, byte[] packet) {}

  private final long tcProcessingNanos;
  private final SimulatedObsw obsw;
  /** Constant processing delay keeps due times monotonic, so FIFO order holds. */
  private final Deque<PendingTc> pendingTcs = new ArrayDeque<>();
  private final Deque<byte[]> tmBuffer = new ArrayDeque<>();

  private State state = State.CREATED;
  private long localNanos;
  private Consumer<byte[]> tmConsumer;

  /**
   * @param tcProcessingNanos simulated delay between TC arrival and its
   *     processing by the on-board software, >= 0 (the ICD §6 vectors fix
   *     TM time = TC injection time, i.e. delay 0, for the M1 chain)
   * @param obsw the simulated on-board software processing due TCs
   */
  public LoopbackTarget(long tcProcessingNanos, SimulatedObsw obsw) {
    if (tcProcessingNanos < 0) {
      throw new IllegalArgumentException("tcProcessingNanos must be >= 0: " + tcProcessingNanos);
    }
    if (obsw == null) {
      throw new NullPointerException("obsw must be non-null");
    }
    this.tcProcessingNanos = tcProcessingNanos;
    this.obsw = obsw;
  }

  @Override
  public void initialize() {
    if (state == State.HALTED) {
      throw new IllegalStateException("target already shut down");
    }
    state = State.INITIALIZED;
  }

  @Override
  public Consumed grant(long budgetNanos) {
    if (state == State.CREATED) {
      throw new IllegalStateException("initialize() not called");
    }
    if (state == State.HALTED) {
      return new Consumed(0, StopReason.HALTED);
    }
    if (budgetNanos <= 0) {
      throw new IllegalArgumentException("budgetNanos must be > 0: " + budgetNanos);
    }
    PendingTc next = pendingTcs.peek();
    if (next != null && next.dueNanos() - localNanos <= budgetNanos) {
      long consumed = next.dueNanos() - localNanos;
      localNanos = next.dueNanos();
      pendingTcs.remove();
      for (byte[] tm : obsw.handleTc(next.packet(), localNanos)) {
        emitTm(tm);
      }
      return new Consumed(consumed, StopReason.EVENT_PENDING);
    }
    localNanos += budgetNanos;
    return new Consumed(budgetNanos, StopReason.BUDGET_EXHAUSTED);
  }

  @Override
  public void shutdown() {
    state = State.HALTED;
    pendingTcs.clear();
  }

  @Override
  public void sendTc(byte[] tcPacket) {
    if (state != State.INITIALIZED) {
      throw new IllegalStateException("target not running: " + state);
    }
    pendingTcs.add(new PendingTc(localNanos + tcProcessingNanos, tcPacket.clone()));
  }

  @Override
  public void onTm(Consumer<byte[]> tmConsumer) {
    this.tmConsumer = tmConsumer;
    while (!tmBuffer.isEmpty()) {
      tmConsumer.accept(tmBuffer.remove());
    }
  }

  private void emitTm(byte[] tmPacket) {
    if (tmConsumer != null) {
      tmConsumer.accept(tmPacket);
    } else {
      tmBuffer.add(tmPacket);
    }
  }
}
