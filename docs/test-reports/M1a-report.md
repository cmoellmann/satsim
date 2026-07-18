# SatSim M1a Milestone Test Report

- Configuration item: SATSIM-M1A-REPORT, Issue 1
- Report date: 2026-07-18
- Baseline commit: 0ef90ad57c8f25380a05476de06fffa47e02d66b (Merge pull request #34 from cmoellmann/feat/m1a-st1-verification)
- Generated from: Surefire test results + JaCoCo coverage; assembled by AI tooling, reviewed and approved via PR

## Environment

| Item | Value |
|---|---|
| Java | OpenJDK Runtime Environment (build 21.0.11+10-1-24.04.2-Ubuntu) |
| Maven | 3.9.11 (from .mvn/wrapper/maven-wrapper.properties) |
| OS | Linux 6.17.0-35-generic |

## Scope

M1a per SDP §4 comprises two dispositioned change requests, implemented in
this order within one milestone gate:

1. **SCR-003** — HMI improvement package (PRs #29–#33): kind-discriminated
   WebSocket frames (tm/time/rejection), live OBT clock, enriched TC
   submission response, log controls, packet detail view, dropdown compose.
2. **SCR-002** — ST[1] request verification tailored subset (PR #34):
   TM(1,1)/(1,2)/(1,7)/(1,8) per ICD §10; success reports gated by TC
   acknowledgement flags, failure reports always; frontend default ack 0b1001.

## M1a Exit Criteria vs. Status

| Criterion | Status | Evidence |
|---|---|---|
| All ST[1] ICD vectors pass (encode+decode) | PASS | V-TC-06, clarified V-NEG-02, V-TM-05/06/07/08 encode byte-identically and decode to ICD field values (SIM-TC-022). |
| Default ping yields TM(1,1)→TM(17,2)→TM(1,7) | PASS | SIM-TC-023: V-TC-06 (ack=0b1001) yields exactly V-TM-05, V-TM-06, V-TM-07 in order; ack=0b0000 yields only TM(17,2). |
| V-NEG-02 yields exactly one TM(1,2) | PASS | SIM-TC-024: byte-identical to V-TM-08 (failure code 0x0001 ILLEGAL_PUS_VERSION), no TM(17,2). SIM-TC-007 re-verified under its SCR-002 amendment. |
| Determinism replay green incl. verification stream | PASS | SIM-TC-011: scripted scenario now emits 119 octets (TM(17,2) + TM(1,1)/(1,7) for the ack-1111 ping + TM(1,2) for V-NEG-02); two runs SHA-256-identical. |
| Frontend smoke test shows verification reports | PASS | SIM-TC-025 verdict recorded in §"Manual Test Verdicts" below: pass, 2026-07-18, C. Möllmann. |
| SCR-003 HMI cases SIM-TC-027..032 pass (automated + manual) | PASS | SIM-TC-027/028/029 automated (HmiWebApiTest); SIM-TC-030/031/032 manual verdicts recorded below: pass, 2026-07-18, C. Möllmann. |
| SRS M1a-scope requirements all traced+passed | PASS | Traceability matrix below; TraceabilityCheck M1a gate: 0 findings → OK. |

## Test Results Summary

### Per-Module Test Classes

#### pus-core

| Test Class | Tests | Failures | Errors | Skipped | Time (s) | Verdict |
|---|---|---|---|---|---|---|
| org.satsim.pus.crc.Crc16CcittTest | 8 | 0 | 0 | 0 | 0.016 | PASS |
| org.satsim.pus.ccsds.PrimaryHeaderTest | 6 | 0 | 0 | 0 | 0.086 | PASS |
| org.satsim.pus.time.CucTimeTest | 12 | 0 | 0 | 0 | 0.026 | PASS |
| org.satsim.pus.tc.TcSecondaryHeaderTest | 7 | 0 | 0 | 0 | 0.009 | PASS |
| org.satsim.pus.tc.TcPacketTest | 7 | 0 | 0 | 0 | 0.013 | PASS |
| org.satsim.pus.tm.TmSecondaryHeaderTest | 4 | 0 | 0 | 0 | 0.010 | PASS |
| org.satsim.pus.tm.TmPacketTest | 7 | 0 | 0 | 0 | 0.019 | PASS |
| org.satsim.pus.ReferenceVectorDecodeTest | 1 | 0 | 0 | 0 | 0.003 | PASS |
| org.satsim.pus.St1ReferenceVectorTest | 1 | 0 | 0 | 0 | 0.005 | PASS |
| **pus-core subtotal** | **53** | **0** | **0** | **0** | **0.187** | **PASS** |

#### simulator

| Test Class | Tests | Failures | Errors | Skipped | Time (s) | Verdict |
|---|---|---|---|---|---|---|
| org.satsim.sim.obsw.LoopbackTargetTest | 9 | 0 | 0 | 0 | 0.012 | PASS |
| org.satsim.sim.time.ManualSimulationClockTest | 4 | 0 | 0 | 0 | 0.003 | PASS |
| org.satsim.sim.time.SimulationSchedulerTest | 4 | 0 | 0 | 0 | 0.008 | PASS |
| org.satsim.sim.PusChainTest | 9 | 0 | 0 | 0 | 0.009 | PASS |
| org.satsim.sim.DeterminismReplayTest | 1 | 0 | 0 | 0 | 0.052 | PASS |
| org.satsim.sim.web.HmiWebApiTest | 3 | 0 | 0 | 0 | 3.371 | PASS |
| org.satsim.sim.web.WebApiEndToEndTest | 3 | 0 | 0 | 0 | 0.270 | PASS |
| **simulator subtotal** | **33** | **0** | **0** | **0** | **3.725** | **PASS** |

#### sim-test-support

| Test Class | Tests | Failures | Errors | Skipped | Time (s) | Verdict |
|---|---|---|---|---|---|---|
| org.satsim.testsupport.trace.TraceabilityCheckTest | 7 | 0 | 0 | 0 | 0.051 | PASS |
| **sim-test-support subtotal** | **7** | **0** | **0** | **0** | **0.051** | **PASS** |

**Overall total: 93 tests, 0 failures, 0 errors, 0 skipped; elapsed 3.963 s (from `./mvnw verify` at the baseline commit).**

Data sources: `*/target/surefire-reports/` in the three modules.

## Code Coverage

### pus-core

Per SDP §2.1 tailoring, pus-core is the correctness-critical core (packet layer) and carries an indicative 80% line coverage target.

**Measured (from `pus-core/target/site/jacoco/jacoco.csv`):**

| Metric | Missed | Covered | Total | % |
|---|---|---|---|---|
| Line | 12 | 270 | 282 | 95.74% |
| Branch | 20 | 116 | 136 | 85.29% |

**Status:** PASS (both line and branch exceed the indicative 80% target).
No production code was added to pus-core in M1a (the ST[1] report layouts are
plain application-data octets built in the simulator); the new vector test
exercises the existing codecs.

**Other modules (simulator, sim-test-support):** No formal coverage target per SDP §2.1 tailoring.

## Traceability Matrix

### SRS M1a Requirements and Verification

| Req ID | Title | Ver. | Scope | SVS Case(s) | Test Method(s) | Verdict |
|---|---|---|---|---|---|---|
| SIM-REQ-VER-001 | TM(1,1) on acceptance iff acceptance ack flag set | T | M1a | SIM-TC-022, SIM-TC-023 | St1ReferenceVectorTest#encodesAndDecodesReferenceVectorsToIcdSpecifiedFields, PusChainTest#ackFlagsDriveVerificationReportSequence | PASS |
| SIM-REQ-VER-002 | TM(1,7) on completion iff completion ack flag set | T | M1a | SIM-TC-023 | PusChainTest#ackFlagsDriveVerificationReportSequence | PASS |
| SIM-REQ-VER-003 | TM(1,2)/TM(1,8) failure reports regardless of ack flags; PUS-version rejection → one TM(1,2) ILLEGAL_PUS_VERSION | T | M1a | SIM-TC-024 | PusChainTest#acceptanceFailureYieldsFailureReport | PASS |
| SIM-REQ-VER-004 | Frontend default ack flags 0b1001, user-overridable | M | M1a | SIM-TC-025 | (manual, see verdict below) | PASS (manual) |
| SIM-REQ-UI-005 | WebSocket time frames (100 ms simulated quantum) + OBT clock | T+M | M1a | SIM-TC-027, SIM-TC-032 | HmiWebApiTest#timeFramesFollowSimulatedTime + manual | PASS |
| SIM-REQ-UI-006 | Enriched POST /api/tc response (OBT, seq count, decoded fields) | T+M | M1a | SIM-TC-028, SIM-TC-030 | HmiWebApiTest#tcResponsesCarryObtSequenceCountAndDecodedFields + manual | PASS |
| SIM-REQ-UI-007 | Rejection frames published and displayed | T+M | M1a | SIM-TC-029, SIM-TC-030 | HmiWebApiTest#rejectionsArePublishedAsRejectionFrames + manual | PASS |
| SIM-REQ-UI-008 | Log filtering, clearing, pause-autoscroll | M | M1a | SIM-TC-030 | (manual, see verdict below) | PASS (manual) |
| SIM-REQ-UI-009 | Field-level packet detail view; undecodable marked with first failed check | M | M1a | SIM-TC-031 | (manual, see verdict below) | PASS (manual) |
| SIM-REQ-UI-010 | Type/Subtype dropdowns from tailored set + free entry | M | M1a | SIM-TC-032 | (manual, see verdict below) | PASS (manual) |

Amended case: SIM-TC-007 (SIM-REQ-PUS-006, M1 scope) was re-verified under
its SCR-002 amendment — the PUS-version rejection now yields exactly one
TM(1,2) and no service TM (`PusChainTest#pusVersionRejectionYieldsNoServiceTm`).

M0/M1-scope requirements remain in scope and passing; their gate records are
the [M0 report](M0-report.md) and [M1 report](M1-report.md). The M0 review
verdicts (SIM-REQ-TIME-001, SIM-REQ-TIME-003, both reviewed-PASS 2026-07-18)
carry per the cumulative verdict rule (ACT-004).

**TraceabilityCheck output** (per SDP §5, CI consistency gate):
```
Traceability check M1a (gate): 0 finding(s) -> OK
```

Source: `java -cp sim-test-support/target/classes org.satsim.testsupport.trace.TraceabilityCheck --root . --milestone M1a --gate`

## Manual Test Verdicts

All four executed against the running simulator (`java -jar
simulator/target/simulator-0.1.0-SNAPSHOT.jar`, http://localhost:8090) at the
baseline commit.

### SIM-TC-025 (verification reports visible) — pass, 2026-07-18, C. Möllmann

Checklist per SVS: compose dialog defaults to ack 0b1001 (acceptance +
completion checked); sending a ping shows TM(1,1), TM(17,2), TM(1,7) in the
live log with decoded fields. Verifies: SIM-REQ-VER-004.

### SIM-TC-030 (log rows and controls) — pass, 2026-07-18, C. Möllmann

Checklist per SVS: TC rows show injection OBT and sequence count; a rejected
TC appears as a visually distinct rejection row with reason; direction and
service filters, clear, and pause-autoscroll work as labelled. Verifies:
SIM-REQ-UI-006, SIM-REQ-UI-007, SIM-REQ-UI-008 (manual halves).

### SIM-TC-031 (packet detail view) — pass, 2026-07-18, C. Möllmann

Checklist per SVS: selecting a TM row and a composed TC row shows the
field-level breakdown (primary header, secondary header, application data,
CRC); selecting an undecodable raw injection shows the failing check instead.
Verifies: SIM-REQ-UI-009.

### SIM-TC-032 (compose and orientation) — pass, 2026-07-18, C. Möllmann

Checklist per SVS: header is prominent with orientation sentence and running
OBT clock; Type/Subtype dropdowns pre-populated from the tailored TC set with
working custom free-entry; labels read "Type"/"Subtype". Verifies:
SIM-REQ-UI-005, SIM-REQ-UI-010 (manual halves).

## Review Verdicts

No requirement entered review-verification scope at M1a (all M1a-scope
requirements are test- or manually-verified). The M0 verdicts carry forward
(see the traceability matrix above); the ACT-004 verdict gate remains active.

## Notes and Deviations

- **Implementation order within M1a**: SCR-003 (HMI) first per Christian's
  sequencing decision, then SCR-002 (ST[1]); one gate covers both.
- **Gate catch on PR #34**: the traceability gate failed the first CI run
  with DUP-TEST (two methods annotated SIM-TC-022). Fixed by merging the
  encode/decode halves into one implementing test method — the rule (exactly
  one implementing test per SVS case, SIM-REQ-QA-001) is not checked by a
  local `./mvnw verify`, only by the CI gate step.
- **Untraced counter tests** re-inject V-TC-01 instead of V-TC-02: V-TC-02's
  ack=0b1111 now interleaves a TM(1,1) that would shift the APID-wide
  sequence count away from the value V-TM-02 pins. V-TC-02/V-TM-02 decode
  coverage is unaffected (SIM-TC-005); the ICD defines both vectors
  standalone.
- **Delegation**: ST[1] implementation chunk executed by the Sonnet-tier
  agent per the tiered-staffing policy; diff reviewed against ICD §6.6/§10
  in the main session before commit. All six new reference vectors
  reproduced byte-identically on the first test run.
- **TM(1,8) path**: present in the ICD tailoring but not yet exercisable —
  no implemented service has semantic execution errors. It activates at M1b
  when the ST[3] error cases map per ICD OP-3 (SIM-TC-024 note).

## Open Items / Proposals

- SRF-OPEN-1 (LICENSE file choice) still open, see docs/reuse-file.md §3.
- ACT-001 (ECSS clause citations, before M2) and ACT-002 (TEMU licensing, before M5) remain open.
- ICD OP-3 (ST[3] semantic errors → TM(1,8)/TM(1,2) failure codes) resolves at M1b.
- Next increment per SDP §4: M1b (ST[3] housekeeping subset, SCR-001); needs the time-triggered SimulatedObsw extension parked in SDD §7.

## Milestone Checklist

| Item | Status | Evidence |
|---|---|---|
| Build + CI green | PASS | `./mvnw verify` green at baseline commit; CI green on all merged PRs (#29–#34). |
| In-scope SVS cases implemented and passing | PASS | SIM-TC-022/023/024 and SIM-TC-027/028/029 automated PASS; SIM-TC-025/030/031/032 manual verdicts recorded; SIM-TC-007 amendment re-verified. No M1b scope pulled in. |
| Test report + traceability matrix generated | PASS | This document (M1a-report.md). Traceability matrix above. |
| CI gate milestone raised to M1a | PASS | `.github/workflows/ci.yml` runs TraceabilityCheck `--milestone M1a --gate` (this PR). |
| Milestone tag proposed | PENDING | Tag `M1a` to be created on the merge commit of this report's PR upon approval. |

---

Generated by AI tooling (Claude Code); reviewed and approved via PR before merge to master.
