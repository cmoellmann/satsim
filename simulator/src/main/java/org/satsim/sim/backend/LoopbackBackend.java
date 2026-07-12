package org.satsim.sim.backend;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;
import org.satsim.sim.link.SpaceLink;
import org.satsim.sim.time.Consumed;
import org.satsim.sim.time.EmulatorControl;
import org.satsim.sim.time.StopReason;

/**
 * In-process loopback back-end (ADR-0006 C5): implements the same
 * {@link EmulatorControl} and {@link SpaceLink} contracts as external
 * back-ends, so the grant/consume synchronization protocol is exercised from
 * the first increment [SIM-REQ-LINK-001, SIM-REQ-TIME-004].
 *
 * <p>M0 behavior: each received TC is "processed" for a fixed simulated delay
 * and then echoed back verbatim as TM — a placeholder application; PUS ST[17]
 * request handling replaces the echo in M1. TM due exactly at the budget
 * boundary is emitted (event takes precedence over budget exhaustion).
 *
 * <p>Slave-local time only advances inside {@link #grant(long)}; TCs sent
 * between grants arrive at the current grant boundary. Not thread-safe: the
 * back-end is owned and driven solely by the time master (ADR-0006).
 */
public final class LoopbackBackend implements EmulatorControl, SpaceLink {

  private enum State { CREATED, INITIALIZED, HALTED }

  private record PendingTc(long dueNanos, byte[] packet) {}

  private final long tcProcessingNanos;
  /** Constant processing delay keeps due times monotonic, so FIFO order holds. */
  private final Deque<PendingTc> pendingTcs = new ArrayDeque<>();
  private final Deque<byte[]> tmBuffer = new ArrayDeque<>();

  private State state = State.CREATED;
  private long localNanos;
  private Consumer<byte[]> tmConsumer;

  /**
   * @param tcProcessingNanos simulated delay between TC arrival and TM
   *     emission, > 0
   */
  public LoopbackBackend(long tcProcessingNanos) {
    if (tcProcessingNanos <= 0) {
      throw new IllegalArgumentException("tcProcessingNanos must be > 0: " + tcProcessingNanos);
    }
    this.tcProcessingNanos = tcProcessingNanos;
  }

  @Override
  public void initialize() {
    if (state == State.HALTED) {
      throw new IllegalStateException("back-end already shut down");
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
      emitTm(next.packet());
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
      throw new IllegalStateException("back-end not running: " + state);
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
