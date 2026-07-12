package org.satsim.sim.time;

/**
 * Master-to-slave time control contract (ADR-0006). The Java scheduler is the
 * sole time master; every execution back-end — in-process loopback, native
 * OBSW process, TSIM, TEMU, QEMU — implements this interface via a thin
 * adapter. The loopback back-end implements it too, so the protocol is
 * exercised from the first increment (ADR-0006 C5, SIM-REQ-LINK-001).
 *
 * <p>Grant/consume semantics (SIM-REQ-TIME-004): the master grants a simulated
 * time budget; the slave executes and reports actual consumption and a stop
 * reason. Early return is explicitly allowed and expected.
 */
public interface EmulatorControl {

  /** Load/prepare the back-end (e.g. OBSW binary). Idempotent before start. */
  void initialize();

  /**
   * Execute up to {@code budgetNanos} of simulated time.
   *
   * @param budgetNanos granted budget in simulated nanoseconds, > 0
   * @return actual consumption and stop reason; consumption never exceeds the budget
   */
  Consumed grant(long budgetNanos);

  /** Halt the back-end and release resources. */
  void shutdown();
}
