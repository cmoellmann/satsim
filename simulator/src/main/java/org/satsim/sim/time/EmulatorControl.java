package org.satsim.sim.time;

/**
 * Master-to-slave time control contract (ADR-0006). The Java scheduler is the
 * sole time master; every OBSW target — in-process loopback, native
 * OBSW process, TSIM, TEMU, QEMU — implements this interface via a thin
 * adapter. The loopback target implements it too, so the protocol is
 * exercised from the first increment (ADR-0006 C5, SIM-REQ-LINK-001).
 *
 * <p>Grant/consume semantics (SIM-REQ-TIME-004): the master grants a simulated
 * time budget; the slave executes and reports actual consumption and a stop
 * reason. Early return is explicitly allowed and expected.
 */
public interface EmulatorControl {

  /** Load/prepare the OBSW target (e.g. OBSW binary). Idempotent before start. */
  void initialize();

  /**
   * Execute up to {@code budgetNanos} of simulated time.
   *
   * @param budgetNanos granted budget in simulated nanoseconds, > 0
   * @return actual consumption and stop reason; consumption never exceeds the budget
   */
  Consumed grant(long budgetNanos);

  /** Halt the OBSW target and release resources. */
  void shutdown();
}
