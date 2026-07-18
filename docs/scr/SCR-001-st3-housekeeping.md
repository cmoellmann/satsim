# SCR-001 — Add ST[3] housekeeping subset as new increment M1b

- Status: Implemented (spec level; implementation code follows in the M1b session)
- Date: 2026-07-18
- Originator: project lead (C. Möllmann); drafted by AI assistant per SDP §6
- Affected configuration items: SATSIM-SDP, SATSIM-ICD, SATSIM-SRS, SATSIM-SVS

## 1. Change description

Add a minimal ECSS-E-ST-70-41C ST[3] housekeeping subset to the simulated
spacecraft, as a new milestone increment **M1b** between M1 and M2:

- **TC(3,1)** — create housekeeping parameter report structure
- **TC(3,5) / TC(3,7)** — enable / disable periodic generation
- **TM(3,25)** — housekeeping parameter report
- **Default configuration:** housekeeping structure SID 1 is predefined in
  the ICD and **enabled at startup** with a collection interval of 1.0 s
  simulated time, containing: TC-received counter, TM-sent counter, and one
  synthetic analog channel computed from simulated time by an integer-only
  formula fixed in the ICD.

## 2. Rationale

First-use experience: a user who starts the simulator and opens the frontend
shall immediately see periodic telemetry arriving without sending any
command. Beyond demo value, periodic HK is the first behavior that makes the
simulation-time architecture externally observable: reports are emitted by
the OBSW target at simulated-time boundaries, making time mastering
(ADR-0006) and determinism (SIM-REQ-TIME-005) visible on the wire.

## 3. Impact analysis

| CI / area | Impact |
|---|---|
| SDP §4 | New milestone row **M1b** inserted between M1 and M2. No renumbering: existing M2–M5 references in SRS/SVS/ICD/action register remain valid. |
| SDP §2.3 | New: change-control paragraph defining the SCR instrument (introduced together with this SCR). |
| ICD | New Issue 2 (draft): ST[3] TC/TM application-data layouts, parameter definitions incl. default SID 1 and the synthetic-channel formula, new reference vectors (V-TC-03…, V-TM-03…). Vectors are human-approved reference data per CLAUDE.md rule 1. |
| SRS | New functional requirement group SIM-REQ-HK-00x, scope M1b (proposals per CLAUDE.md rule 3; applicable after human approval). SIM-REQ-PUS-007 ("all ICD §6 vectors") automatically extends to the new vectors. |
| SVS | New validation cases SIM-TC-018…021, scope M1b, incl. extension of the manual frontend smoke test (periodic HK visible). |
| TraceabilityCheck | Milestone identifier "M1b" must be handled by scope filtering/ordering (verify; adjust in M1/M1b implementation if needed). |
| ADRs | **No contradiction.** ADR-0002's decision is strict PUS-C header layouts with tailoring at service level only; the ST[17]/ST[1] mention is sequencing, not a closed service set. Adding ST[3] is service-level tailoring. HK generation is bound by ADR-0006: emitted by the OBSW target within granted simulated-time windows, never by a wall-clock timer (SIM-REQ-TIME-001/003 unaffected). |
| Implementation code | None in this SCR (specification level only). Implementation follows in the M1b session after M1. |

## 4. Disposition

- [x] Approved — project lead (C. Möllmann), 2026-07-18, via review and merge
      of PR #14. Implementing specification PR: #15.
