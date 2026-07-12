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
| SIM-TC-007 | SIM-REQ-PUS-006 | PUS version rejection | A | V-NEG-02 rejected; no TM emitted. |
| SIM-TC-008 | SIM-REQ-PUS-008, SIM-REQ-PUS-010 | ST[17] round-trip via loopback | A | Injecting V-TC-01 yields exactly one TM(17,2), APID 100; byte-identical to V-TM-01 when injected at T=0 with fresh counters. |
| SIM-TC-009 | SIM-REQ-PUS-009 | Counter behavior | A | Two consecutive pings yield TM seq counts n, n+1 and msg type counters m, m+1; wrap tested at forced counter presets 16383 and 65535. |
| SIM-TC-010 | SIM-REQ-TIME-004, SIM-REQ-LINK-001 | EmulatorControl grant/consume contract (loopback) | A | grant(b) returns consumed ≤ b with stop reason; early return on pending TM event reports correct consumed time. |
| SIM-TC-011 | SIM-REQ-TIME-005 | Determinism replay | A | Two runs, identical scripted TC injection times ⇒ byte-identical TM streams incl. timestamps (full-stream hash equality). |
| SIM-TC-012 | SIM-REQ-UI-003 | REST/WS end-to-end (no browser) | A | POST of TC(17,1) via REST yields TM(17,2) frame on WebSocket containing raw hex and decoded fields matching ICD values. |
| SIM-TC-013 | SIM-REQ-UI-001, SIM-REQ-UI-002, SIM-REQ-UI-004 | Frontend smoke test | M | Checklist: page loads; compose TC(17,1); raw hex shown and equals expected vector; send; TM(17,2) appears in live log with decoded fields. Verdict + date + name recorded in milestone report. |
| SIM-TC-014 | SIM-REQ-QA-001, SIM-REQ-QA-002 | Traceability toolchain self-check | A | CI script detects: SVS case without implementing test, test with unknown IDs, in-scope requirement without SVS coverage. Verified with deliberate fixtures. |
| SIM-TC-015 | SIM-REQ-LINK-002 | TCP length-framed link conformance | A | External test client sends V-TC-01 over TCP framing per ICD §8, receives correctly framed TM(17,2). (Scope: M2) |
| SIM-TC-016 | SIM-REQ-LINK-003 | OBSW target interchangeability | A | SIM-TC-003…012 suite passes unchanged against native-process OBSW target. (Scope: M3) |

## Change log

| Issue | Date | Change |
|---|---|---|
| 1 (draft) | 2026-07-12 | Initial cases for M0–M3 scope. |
