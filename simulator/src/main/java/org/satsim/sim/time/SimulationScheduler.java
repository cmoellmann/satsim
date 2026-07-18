package org.satsim.sim.time;

import java.util.function.Consumer;
import org.satsim.sim.obsw.ObswTarget;

/**
 * The simulation time master (ADR-0006): the sole component that advances
 * simulated time [SIM-REQ-TIME-003]. Owns the master {@link SimulationClock}
 * and drives the OBSW target with grant/consume cycles; the master clock only
 * advances by the target's reported consumption, so master and slave time
 * agree at every grant boundary.
 *
 * <p>TCs are injected at the current master time and delivered to the target
 * at the current grant boundary; TM emitted by the target is forwarded to the
 * registered consumer during {@link #advanceBy(long)}. Pacing (e.g.
 * interactive 1:1 wall-clock pacing) is a policy layered on top by callers;
 * this class itself never reads the wall clock [SIM-REQ-TIME-001].
 *
 * <p>Not thread-safe: callers serialize access (single simulation thread).
 */
public final class SimulationScheduler {

  private final ManualSimulationClock clock = new ManualSimulationClock();
  private final ObswTarget target;
  private boolean started;

  /**
   * @param target the OBSW target to drive; initialized on {@link #start()}
   */
  public SimulationScheduler(ObswTarget target) {
    if (target == null) {
      throw new NullPointerException("target must be non-null");
    }
    this.target = target;
  }

  /** The master simulation clock (read-only view). */
  public SimulationClock clock() {
    return clock;
  }

  /** Initializes the OBSW target. Must be called once before advancing time. */
  public void start() {
    target.initialize();
    started = true;
  }

  /** Halts the OBSW target. */
  public void stop() {
    target.shutdown();
    started = false;
  }

  /** Queues one encoded TC space packet for delivery at the current simulated time. */
  public void injectTc(byte[] tcPacket) {
    requireStarted();
    target.sendTc(tcPacket);
  }

  /** Registers the consumer for TM space packets emitted by the target. */
  public void onTm(Consumer<byte[]> tmConsumer) {
    target.onTm(tmConsumer);
  }

  /**
   * Advances simulated time by {@code deltaNanos}, granting the target
   * execution budgets and re-granting after early returns (event processing)
   * until the delta is fully consumed [SIM-REQ-TIME-004].
   *
   * @param deltaNanos simulated nanoseconds to advance, >= 0
   * @throws IllegalStateException if the target halts or violates the
   *     grant/consume contract (no progress on budget exhaustion)
   */
  public void advanceBy(long deltaNanos) {
    requireStarted();
    if (deltaNanos < 0) {
      throw new IllegalArgumentException("deltaNanos must be >= 0: " + deltaNanos);
    }
    long remaining = deltaNanos;
    while (remaining > 0) {
      Consumed consumed = target.grant(remaining);
      if (consumed.reason() == StopReason.HALTED) {
        throw new IllegalStateException("OBSW target halted during advance");
      }
      if (consumed.nanosConsumed() < 0 || consumed.nanosConsumed() > remaining) {
        throw new IllegalStateException(
            "grant/consume contract violated: consumed " + consumed.nanosConsumed()
                + " of budget " + remaining);
      }
      if (consumed.nanosConsumed() == 0 && consumed.reason() == StopReason.BUDGET_EXHAUSTED) {
        throw new IllegalStateException("no progress: zero consumption with BUDGET_EXHAUSTED");
      }
      clock.advance(consumed.nanosConsumed());
      remaining -= consumed.nanosConsumed();
    }
  }

  /**
   * Advances simulated time to the absolute simulated instant
   * {@code targetNanos} (no-op if already there).
   *
   * @param targetNanos absolute simulated time, >= current clock time
   */
  public void advanceTo(long targetNanos) {
    advanceBy(targetNanos - clock.nanos());
  }

  private void requireStarted() {
    if (!started) {
      throw new IllegalStateException("scheduler not started");
    }
  }
}
