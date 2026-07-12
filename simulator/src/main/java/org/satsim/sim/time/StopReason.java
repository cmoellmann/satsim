package org.satsim.sim.time;

/** Why a time slave returned from a grant (ADR-0006 C2). */
public enum StopReason {
  /** Full granted budget consumed. */
  BUDGET_EXHAUSTED,
  /** Returned early: an event for the master is pending (e.g. TM emitted, I/O). */
  EVENT_PENDING,
  /** Returned early: OBSW target halted (OBSW exit/crash/breakpoint). */
  HALTED
}
