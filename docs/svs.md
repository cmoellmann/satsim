# SatSim Software Validation Specification (SVS)

- Configuration item: SATSIM-SVS, Issue 1 (draft)
- Per SDP §5: validation tests only (unit tests are untraced). Spec-first: entries
  here are human-approved before implementation. Expected values reference the
  ICD and are never adjusted to make tests pass (SDP §6.2).
- Method: A = automated (JUnit, annotated), M = manual (checklist, verdict
  recorded in milestone test report).

| ID | Verifies | Objective | Method | Pass criteria |
|---|---|---|---|---|
| SIM-TC-001 | SIM-REQ-PUS-002 | CRC-16 implementation correctness | A | All three ICD §7 sanity anchors reproduced exactly. |
| SIM-TC-002 | SIM-REQ-PUS-001 | Primary header encode/decode round-trip | A | Encode→decode is identity for representative field combinations incl. boundary values (APID 0/2047, seq 0/16383, min/max length). |
| SIM-TC-003 | SIM-REQ-PUS-007, SIM-REQ-PUS-003 | TC encoding against reference vectors | A | Encoder output byte-identical to V-TC-01 and V-TC-02. |
| SIM-TC-004 | SIM-REQ-PUS-007, SIM-REQ-PUS-004, SIM-REQ-TIME-002 | TM encoding against reference vectors | A | Encoder output byte-identical to V-TM-01 and V-TM-02 (incl. CUC time field). |
| SIM-TC-005 | SIM-REQ-PUS-007 | Decoding of reference vectors | A | V-TC-01/02 and V-TM-01/02 decode without error; all decoded fields equal ICD-specified values. |
| SIM-TC-006 | SIM-REQ-PUS-005 | CRC failure rejection | A | V-NEG-01 rejected; no TM emitted; rejection observable (log/queue). |
| SIM-TC-007 | SIM-REQ-PUS-006 | PUS version rejection | A | V-NEG-02 rejected; no TM(17,2) emitted. (Amended per SCR-002: the former "no TM emitted" criterion is superseded — from M1a, rejection additionally yields exactly one TM(1,2), verified by SIM-TC-024; before M1a no TM of any kind exists.) |
| SIM-TC-008 | SIM-REQ-PUS-008, SIM-REQ-PUS-010 | ST[17] round-trip via loopback | A | Injecting V-TC-01 yields exactly one TM(17,2), APID 100; byte-identical to V-TM-01 when injected at T=0 with fresh counters. |
| SIM-TC-009 | SIM-REQ-PUS-009 | Counter behavior | A | Two consecutive pings yield TM seq counts n, n+1 and msg type counters m, m+1; wrap tested at forced counter presets 16383 and 65535. |
| SIM-TC-010 | SIM-REQ-TIME-004, SIM-REQ-LINK-001 | EmulatorControl grant/consume contract (loopback) | A | grant(b) returns consumed ≤ b with stop reason; early return on pending TM event reports correct consumed time. |
| SIM-TC-011 | SIM-REQ-TIME-005 | Determinism replay | A | Two runs, identical scripted TC injection times ⇒ byte-identical TM streams incl. timestamps (full-stream hash equality). |
| SIM-TC-012 | SIM-REQ-UI-003 | REST/WS end-to-end (no browser) | A | POST of TC(17,1) via REST yields TM(17,2) frame on WebSocket containing raw hex and decoded fields matching ICD values. |
| SIM-TC-013 | SIM-REQ-UI-001, SIM-REQ-UI-002, SIM-REQ-UI-004 | Frontend smoke test | M | Checklist: page loads; compose TC(17,1); raw hex shown and equals expected vector; send; TM(17,2) appears in live log with decoded fields. Verdict + date + name recorded in milestone report. |
| SIM-TC-014 | SIM-REQ-QA-001, SIM-REQ-QA-002 | Traceability toolchain self-check | A | CI script detects: SVS case without implementing test, test with unknown IDs, in-scope requirement without SVS coverage. Verified with deliberate fixtures. |
| SIM-TC-015 | SIM-REQ-LINK-002 | TCP length-framed link conformance | A | External test client sends V-TC-01 over TCP framing per ICD §8, receives correctly framed TM(17,2). (Scope: M2) |
| SIM-TC-016 | SIM-REQ-LINK-003 | OBSW target interchangeability | A | SIM-TC-003…012 suite passes unchanged against native-process OBSW target. (Scope: M3) |
| SIM-TC-017 | SIM-REQ-TIME-006 | Quantum reconfiguration conformance | A | Changing the per-link synchronization quantum at runtime takes effect from the next grant; grant/consume contract (SIM-TC-010 invariants) holds before and after the change; determinism replay (SIM-TC-011 method) unaffected. (Scope: M5) |
| SIM-TC-018 | SIM-REQ-PUS-007, SIM-REQ-HK-002 | ST[3] reference vector encode/decode | A | V-TC-03/04/05 and V-TM-03/04 encode byte-identically and decode without error to the ICD-specified field values. (Scope: M1b — completes SIM-REQ-PUS-007 coverage for the ICD §6.4/§6.5 vectors added by SCR-001.) |
| SIM-TC-019 | SIM-REQ-HK-003, SIM-REQ-HK-002 | Default-SID periodic reporting | A | Fresh start, no TC traffic: TM(3,25) for SID 1 emitted at simulated T=1.0 s and T=2.0 s; the first two reports are byte-identical to V-TM-03 and V-TM-04; no report before T=1.0 s. (Scope: M1b) |
| SIM-TC-020 | SIM-REQ-HK-001, SIM-REQ-HK-004 | HK structure lifecycle (create/enable/disable) | A | V-TC-03 creates SID 2 and no SID 2 report is emitted while disabled; after V-TC-04, SID 2 reports at 5.0 s simulated intervals with parameters {P001, P003}; after V-TC-05, no further SID 2 reports; SID 1 reporting unaffected throughout. (Scope: M1b) |
| SIM-TC-021 | SIM-REQ-HK-003 | Frontend smoke test: periodic HK visible | M | Checklist: open frontend on a freshly started simulator; without sending any TC, TM(3,25) entries appear about once per second (interactive 1:1 pacing) with decoded SID and parameter values; HK-P003 value changes between reports. Verdict + date + name recorded in milestone report. (Scope: M1b) |
| SIM-TC-022 | SIM-REQ-PUS-007, SIM-REQ-VER-001 | ST[1] reference vector encode/decode | A | V-TC-06, V-NEG-02 (clarified bytes) and V-TM-05/06/07/08 encode byte-identically and decode without error to the ICD-specified field values. (Scope: M1a — covers the ICD §6.6 vectors and the clarified V-NEG-02 added by SCR-002.) |
| SIM-TC-023 | SIM-REQ-VER-001, SIM-REQ-VER-002 | Ack-flag-driven verification sequence | A | Fresh start: injecting V-TC-06 (ack=0b1001) at T=0 yields exactly TM(1,1), TM(17,2), TM(1,7) in this order, byte-identical to V-TM-05/06/07; separately, fresh start: injecting V-TC-01 (ack=0b0000) yields exactly one TM(17,2) and no ST[1] report. (Scope: M1a) |
| SIM-TC-024 | SIM-REQ-VER-003 | Acceptance failure report | A | Fresh start: injecting V-NEG-02 at T=0 yields exactly one TM(1,2), byte-identical to V-TM-08, and no TM(17,2). (Scope: M1a. The TM(1,8) completion-failure path is exercised from M1b, when ST[3] semantic errors map to ST[1] per ICD OP-3.) |
| SIM-TC-025 | SIM-REQ-VER-004 | Frontend smoke test: verification reports visible | M | Checklist: compose dialog defaults to ack 0b1001; sending a ping shows TM(1,1), TM(17,2), TM(1,7) in the live log with decoded fields. Verdict + date + name recorded in milestone report. (Scope: M1a) |

## Change log

| Issue | Date | Change |
|---|---|---|
| 1 (draft) | 2026-07-12 | Initial cases for M0–M3 scope. |
| 1 (draft) | 2026-07-12 | SIM-TC-017 added: quantum reconfiguration (M5), closes coverage gap for SIM-REQ-TIME-006 found by traceability check (PR #8). |
| 1 (draft) | 2026-07-18 | SIM-TC-018…021 added (ST[3] housekeeping subset, scope M1b) per SCR-001. |
| 1 (draft) | 2026-07-18 | SIM-TC-022…025 added (ST[1] request verification subset, scope M1a); SIM-TC-007 pass criterion amended (TM(1,2) on PUS-version rejection from M1a). Per SCR-002. |
