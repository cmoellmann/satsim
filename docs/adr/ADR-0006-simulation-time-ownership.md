# ADR-0006 — Simulation Time Ownership: Java Scheduler as Time Master

- Status: **Accepted**
- Date: 2026-07-12
- Deciders: Project lead (with AI-assisted trade-off analysis)
- Configuration item: SATSIM-ADR-0006
- Related: ADR-0001 (process isolation, `SpaceLink`), ADR-0004 (`SimulationClock`)
- Supersedes: —

## Context and Problem Statement

The simulator will host on-board software (OBSW) in several execution configurations:

1. In-process Java loopback (PoC, ST[17] echo)
2. Native OBSW process (C/C++/Rust) connected via TCP space-packet link
3. Instruction-level emulators running real cross-compiled OBSW binaries:
   - Gaisler TSIM (LEON/SPARC)
   - Terma TEMU (LEON/SPARC, PowerPC e500 — incl. the official Beyond Gravity cOBC simulator, ARM Cortex-R5, RISC-V NOEL-V)
   - QEMU (`ppce500`, LEON) as a license-free functional stand-in

In a coupled simulation, two clocks must remain consistent: the emulator's cycle
counter and the environment simulator's event time (TM timing, later equipment
and orbit models). Exactly one component must be the authority on simulated
"now" and grant execution budgets to the others. The choice determines the
synchronization architecture, the determinism properties of the automated test
API, and the integration burden per emulator.

## Decision Drivers

- D1: A unified, deterministic automated OBSW test API is a primary project goal
  ("run to T+x, inject TC, assert TM" must be reproducible bit-for-bit).
- D2: All execution configurations (1)–(3) must behave identically from the
  test API's perspective.
- D3: Environment models (clock, later equipment/orbit/power) are implemented
  in Java, in the simulator process.
- D4: Growth path may include multiple processors (constellation context,
  OBC + payload processor), which requires a single external time authority.
- D5: Emulator raw performance (TEMU/TSIM dynamic translation, incl.
  faster-than-real-time execution) should be exploitable where possible.
- D6: Vendor specifics (TSIM API, TEMU API, QEMU) must not leak through the
  simulator's abstractions.

## Considered Options

- **Option A — Java simulator scheduler owns time.** The Java discrete-event
  scheduler is the single time master. Emulators and native OBSW processes are
  stepped slaves: the master grants a simulated-time budget; the slave executes
  and reports consumption.
- **Option B — Emulator owns time.** TEMU (which can act as simulation kernel)
  or TSIM free-runs and schedules attached models; the Java side reacts as an
  event-driven client via a socket-bridge device model.
- **Hybrid refinements (of A):** adaptive quantum sizing; free-running
  "interactive" grant mode paced against wall clock.

## Analysis

### Option A — Java scheduler as master

Pros:
- Single scheduling regime across all configurations (D2): only the Java side
  exists in every configuration; any other master splits the project into two
  regimes (loopback/native must be Java-mastered regardless).
- Deterministic test API (D1): the timeline is advanced in controlled steps;
  assertions bind to exact simulated instants, eliminating wall-clock timeouts
  and flaky waits.
- Pause, single-step, slower/faster-than-real-time, and snapshot orchestration
  are uniform master-side features, not per-vendor features (D6).
- Environment models remain Java-native and time-consistent by construction (D3).
- Generalizes to multiple emulated nodes (D4).

Cons / accepted costs:
- Synchronization overhead at each quantum boundary (process/socket crossing);
  raw emulator throughput is reduced versus free-running (partially conflicts
  with D5). Mitigation: adaptive quanta, see Consequences.
- Externally injected events are delivered at quantum granularity unless the
  slave supports early return. Mitigation: event-capable protocol, see
  Consequences. (Note: cycle-exact timing is not attainable in the TEMU e500
  model at present regardless — no static timing model — so the practical
  accuracy loss is bounded and acceptable for functional V&V.)
- The grant/consume synchronization protocol must be designed, implemented and
  tested by this project.

### Option B — Emulator as master

Pros:
- Maximum emulator throughput (uninterrupted translated-code bursts) (D5).
- Least initial integration code; matches the vendors' standalone usage mode.
- Device-level I/O timing at the emulator's native event-queue precision.

Cons (structural, decisive):
- Violates D2: loopback/native configurations still need a Java time master,
  yielding two divergent scheduling regimes and a test API whose semantics
  differ per configuration.
- Violates D1: without owning time, the test API degenerates to
  "send-and-wait-with-timeout", reintroducing nondeterminism; deterministic
  scripting would live in vendor-specific emulator scripts instead of the
  unified Java API (violating D6).
- Violates D3: environment models must either move behind the emulator's C API
  or run time-skewed.
- Violates D4: does not generalize to more than one emulator.

## Decision

**Option A is adopted: the Java simulator's discrete-event scheduler is the
sole simulation time master.** All OBSW execution back-ends (in-process
loopback, native process, TSIM, TEMU, QEMU) are time slaves controlled through
the `EmulatorControl` abstraction.

Rationale summary: Option A is chosen for architectural reasons (D1–D4, D6) at
the cost of a known, bounded and tunable synchronization overhead (D5).
Option B's disadvantages are structural and grow with the project; Option A's
disadvantage is a performance tax that can be engineered down.

## Consequences

### Required design provisions (binding on implementation)

- C1: `SimulationClock` is the only time source for all simulator components,
  including PUS TM time stamping (reaffirms ADR-0004). Direct wall-clock reads
  in simulation logic are prohibited (enforced by static analysis rule).
- C2: `EmulatorControl` SHALL use event-capable budget semantics —
  `grant(budget) → consumed(timeConsumed, stopReason)` — not a naive
  `step(quantum)`. Slaves MAY return early (I/O event, breakpoint, halt),
  reporting actual consumption. This enables adaptive quanta and precise event
  delivery without future API breakage.
- C3: Quantum size is a per-link, runtime-tunable parameter (target envelope
  0.1–10 ms simulated time); default profiles: "accuracy" (small quanta) for
  automated tests, "throughput" (large/adaptive quanta) for batch runs.
- C4: An interactive mode SHALL be provided in which the master paces grants
  against wall clock (soft real time) for manual frontend use; this is a
  pacing policy of the master, not a transfer of time ownership.
- C5: The in-process loopback back-end implements the same `EmulatorControl`
  contract, so the synchronization protocol is exercised (and regression-tested)
  from the very first PoC increment, before any real emulator is integrated.
- C6: Time synchronization conformance is verified by a dedicated test suite
  (budget accounting, early return, determinism replay: identical inputs ⇒
  identical TM byte streams and timestamps), traced as requirements
  SIM-REQ-TIME-xxx [ADR-0006].

### Positive
- One test API, deterministic and configuration-independent.
- Vendor emulator APIs are confined to thin adapter classes.
- Ready for multi-node simulation without redesign.

### Negative / risks
- R1: Sync overhead may dominate for very small quanta. Mitigation: C2/C3;
  measure early with the loopback + native-process back-ends.
- R2: Protocol defects (deadlock, budget drift) are subtle. Mitigation: C5
  (protocol exercised from PoC day one), C6 (determinism replay tests).
- R3: TEMU-as-kernel usage (vendor's native mode) is intentionally not used;
  if a future OBSW-only unit-test workflow wants it, it shall be introduced as
  a separate lightweight tool, not by inverting this decision.

## Notes

- TEMU e500 model limitations at decision time: no static timing model
  (1 instruction = 1 cycle), MMU model not hardware-validated, no cache control
  interfaces. Functional V&V is unaffected; timing-accurate verification claims
  must not be made on this back-end.
- The official cOBC simulator (Terma, based on TEMU, with Beyond Gravity board
  models) is commercial; early licensing contact recommended if a cOBC-based
  mission becomes the target.
