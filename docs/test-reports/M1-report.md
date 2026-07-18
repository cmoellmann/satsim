# SatSim M1 Milestone Test Report

- Configuration item: SATSIM-M1-REPORT, Issue 1
- Report date: 2026-07-18
- Baseline commit: e51710ce51f73c0f3b8c329508a51527770034ac (Merge pull request #24 from cmoellmann/feat/m1-st17-chain)
- Generated from: Surefire test results + JaCoCo coverage; assembled by AI tooling, reviewed and approved via PR

## Environment

| Item | Value |
|---|---|
| Java | OpenJDK Runtime Environment (build 21.0.11+10-1-24.04.2-Ubuntu) |
| Maven | 3.9.11 (from .mvn/wrapper/maven-wrapper.properties) |
| OS | Linux 6.17.0-35-generic |

## M1 Exit Criteria vs. Status

| Criterion | Status | Evidence |
|---|---|---|
| All ICD §6 vectors pass incl. negatives | PASS | M1-scope vectors (ICD Issue 1 §6 set, i.e. §6.1–§6.3): V-TC-01/02 and V-TM-01/02 encode byte-identically (SIM-TC-003/004) and decode to ICD field values (SIM-TC-005); V-NEG-01 rejected without TM (SIM-TC-006); V-NEG-02 rejected without TM (SIM-TC-007; TM(1,2) response is M1a scope per SCR-002). §6.4–§6.6 vectors belong to increments M1b/M1a (SCR-001/002), verified there (SIM-TC-018/022). |
| Manual frontend smoke test recorded | PASS | SIM-TC-013 verdict recorded in §"Manual Test Verdicts" below: pass, 2026-07-18, C. Möllmann. |
| SRS M1-scope requirements all traced+passed | PASS | Traceability matrix below; TraceabilityCheck M1 gate: 0 findings → OK (incl. the new review-verdict check, ACT-004). |

Additional M1 goal evidence (SDP §4 content column):

- TC(17,1)→TM(17,2) chain via web frontend: SIM-TC-012 (REST→WebSocket, no browser) + SIM-TC-013 (browser).
- Determinism replay (ADR-0006 C6): SIM-TC-011 — two scripted runs produce SHA-256-identical TM streams incl. timestamps.
- End-to-end vector fidelity beyond the letter of the criteria: a two-ping scenario reproduces V-TM-02 at T=1.5 s byte-identically (untraced test `PusChainTest#secondPingAtOnePointFiveSecondsReproducesVectorTm02`).

## Test Results Summary

### Per-Module Test Classes

#### pus-core

| Test Class | Tests | Failures | Errors | Skipped | Time (s) | Verdict |
|---|---|---|---|---|---|---|
| org.satsim.pus.crc.Crc16CcittTest | 8 | 0 | 0 | 0 | 0.009 | PASS |
| org.satsim.pus.ccsds.PrimaryHeaderTest | 6 | 0 | 0 | 0 | 0.088 | PASS |
| org.satsim.pus.time.CucTimeTest | 12 | 0 | 0 | 0 | 0.036 | PASS |
| org.satsim.pus.tc.TcSecondaryHeaderTest | 7 | 0 | 0 | 0 | 0.006 | PASS |
| org.satsim.pus.tc.TcPacketTest | 7 | 0 | 0 | 0 | 0.018 | PASS |
| org.satsim.pus.tm.TmSecondaryHeaderTest | 4 | 0 | 0 | 0 | 0.006 | PASS |
| org.satsim.pus.tm.TmPacketTest | 7 | 0 | 0 | 0 | 0.013 | PASS |
| org.satsim.pus.ReferenceVectorDecodeTest | 1 | 0 | 0 | 0 | 0.002 | PASS |
| **pus-core subtotal** | **52** | **0** | **0** | **0** | **0.178** | **PASS** |

#### simulator

| Test Class | Tests | Failures | Errors | Skipped | Time (s) | Verdict |
|---|---|---|---|---|---|---|
| org.satsim.sim.obsw.LoopbackTargetTest | 9 | 0 | 0 | 0 | 0.008 | PASS |
| org.satsim.sim.time.ManualSimulationClockTest | 4 | 0 | 0 | 0 | 0.007 | PASS |
| org.satsim.sim.time.SimulationSchedulerTest | 4 | 0 | 0 | 0 | 0.011 | PASS |
| org.satsim.sim.PusChainTest | 7 | 0 | 0 | 0 | 0.006 | PASS |
| org.satsim.sim.DeterminismReplayTest | 1 | 0 | 0 | 0 | 0.075 | PASS |
| org.satsim.sim.web.WebApiEndToEndTest | 3 | 0 | 0 | 0 | 1.963 | PASS |
| **simulator subtotal** | **28** | **0** | **0** | **0** | **2.070** | **PASS** |

#### sim-test-support

| Test Class | Tests | Failures | Errors | Skipped | Time (s) | Verdict |
|---|---|---|---|---|---|---|
| org.satsim.testsupport.trace.TraceabilityCheckTest | 7 | 0 | 0 | 0 | 0.052 | PASS |
| **sim-test-support subtotal** | **7** | **0** | **0** | **0** | **0.052** | **PASS** |

**Overall total: 87 tests, 0 failures, 0 errors, 0 skipped; elapsed 2.300 s (from `./mvnw verify` at the baseline commit).**

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

**Other modules (simulator, sim-test-support):** No formal coverage target per SDP §2.1 tailoring.

## Traceability Matrix

M1 scope per SDP §4: PUS-C TC/TM codecs against ICD §6 vectors, TC(17,1)→TM(17,2) chain, REST/WS API + web frontend, determinism replay, review-verdict gate (ACT-004).

### SRS M1 Requirements and Verification

| Req ID | Title | Ver. | Scope | SVS Case(s) | Test Method(s) | Verdict |
|---|---|---|---|---|---|---|
| SIM-REQ-PUS-003 | TC secondary header codec per ICD §3 | T | M1 | SIM-TC-003 | TcPacketTest#encodeMatchesReferenceVectors | PASS |
| SIM-REQ-PUS-004 | TM secondary header codec per ICD §4 | T | M1 | SIM-TC-004 | TmPacketTest#encodeMatchesReferenceVectors | PASS |
| SIM-REQ-PUS-005 | Invalid-CRC packets rejected, content not processed | T | M1 | SIM-TC-006 | PusChainTest#crcFailureIsRejectedWithoutTm | PASS |
| SIM-REQ-PUS-006 | TC with PUS version ≠ 2 rejected | T | M1 | SIM-TC-007 | PusChainTest#pusVersionRejectionYieldsNoTm | PASS |
| SIM-REQ-PUS-007 | ICD §6 reference vectors byte-exact (encode) and accepted (decode) | T | M1 | SIM-TC-003, SIM-TC-004, SIM-TC-005 | (see above) + ReferenceVectorDecodeTest#decodesReferenceVectorsToIcdSpecifiedFields | PASS |
| SIM-REQ-PUS-008 | Valid TC(17,1) → exactly one TM(17,2), same APID | T | M1 | SIM-TC-008 | PusChainTest#validPingYieldsExactlyOneConnectionTestReport | PASS |
| SIM-REQ-PUS-009 | Sequence counts per APID (wrap 16383); type counters per (service,subtype) (wrap 65535) | T | M1 | SIM-TC-009 | PusChainTest#countersIncrementAndWrap | PASS |
| SIM-REQ-PUS-010 | APID 100 for all TM/TC | T | M1 | SIM-TC-008 | PusChainTest#validPingYieldsExactlyOneConnectionTestReport | PASS |
| SIM-REQ-TIME-002 | TM time as CUC 4+2, epoch 2026-01-01, from SimulationClock | T | M1 | SIM-TC-004 | TmPacketTest#encodeMatchesReferenceVectors | PASS |
| SIM-REQ-TIME-005 | Byte-identical TM streams on identical scripted runs | T | M1 | SIM-TC-011 | DeterminismReplayTest#scriptedReplayProducesByteIdenticalTmStreams | PASS |
| SIM-REQ-UI-001 | Frontend composes/sends TC by service, subtype, app data | M | M1 | SIM-TC-013 | (manual, see verdict below) | PASS (manual) |
| SIM-REQ-UI-002 | Frontend displays TM live: raw hex + decoded fields | M | M1 | SIM-TC-013 | (manual, see verdict below) | PASS (manual) |
| SIM-REQ-UI-003 | REST TC submission + WS TM distribution, browser-free automatable | T | M1 | SIM-TC-012 | WebApiEndToEndTest#restTcYieldsWebSocketTmFrame | PASS |
| SIM-REQ-UI-004 | Frontend shows raw encoded TC bytes before/after sending | M | M1 | SIM-TC-013 | (manual, see verdict below) | PASS (manual) |
| SIM-REQ-QA-003 | Gate fails on missing/failed review verdicts | A | M1 | SIM-TC-026 | TraceabilityCheckTest#gateFailsOnMissingOrFailedReviewVerdicts | PASS |

M0-scope requirements (SIM-REQ-PUS-001/002, TIME-001/003/004, LINK-001, QA-001/002) remain in scope and passing; their gate record is the [M0 report](M0-report.md). The M0 review verdicts (SIM-REQ-TIME-001, SIM-REQ-TIME-003, both reviewed-PASS 2026-07-18) are carried per the cumulative verdict rule (ACT-004 implementation, TraceabilityCheck).

**TraceabilityCheck output** (per SDP §5, CI consistency gate — now including the ACT-004 review-verdict check):
```
Traceability check M1 (gate): 0 finding(s) -> OK
```

Source: `java -cp sim-test-support/target/classes org.satsim.testsupport.trace.TraceabilityCheck --root . --milestone M1 --gate`

## Manual Test Verdicts

### SIM-TC-013 (frontend smoke test) — pass, 2026-07-18, C. Möllmann

Checklist per SVS: page loads (TM link online); compose TC(17,1); raw hex preview shown and equal to the expected vector V-TC-01 (`18 64 C0 00 00 06 20 11 01 00 00 FA 83`); send; TM(17,2) appears in the live log with decoded fields. Executed against the runnable jar (`java -jar simulator/target/simulator-0.1.0-SNAPSHOT.jar`, http://localhost:8090).

Verifies: SIM-REQ-UI-001, SIM-REQ-UI-002, SIM-REQ-UI-004.

## Review Verdicts

No requirement entered review-verification scope at M1; the verdicts recorded
at M0 for the two review-verified time requirements carry forward (see the
traceability matrix above). The ACT-004 verdict gate is active from this
milestone: a missing verdict fails the gate, a recorded failing verdict fails
any run.

(Format note: this section deliberately avoids pairing a requirement ID with
a verdict token on one line — the TraceabilityCheck parser would read that as
a recorded verdict. Its first gate run did exactly that on a draft of this
report and correctly failed the build.)

## Notes and Deviations

- **Stacked-PR merge order**: the M1 implementation stack (#20→#21→#22) was merged top-of-stack-first, leaving #21/#22 content off master; repaired by PR #24 (no content change; both PRs were individually reviewed and CI-green). Process note for future stacks: merge bottom-up.
- **Spring Boot 3.5.3** added to `simulator` per rule-8 approval 2026-07-18 (PR #22), recorded in the Software Reuse File incl. the Logback EPL-1.0 license election and the spring-boot-maven-plugin (build). `pus-core` remains JDK-only.
- Default HTTP port is 8090 (developer-machine port conflict on 8080).

## Open Items / Proposals

- SRF-OPEN-1 (LICENSE file choice) still open, see docs/reuse-file.md §3.
- ACT-001 (ECSS clause citations, before M2) and ACT-002 (TEMU licensing, before M5) remain open.
- Next increments per SDP §4: M1a (ST[1] request verification subset, SCR-002), then M1b (ST[3] housekeeping subset, SCR-001).

## Milestone Checklist

| Item | Status | Evidence |
|---|---|---|
| Build + CI green | PASS | `./mvnw verify` green at baseline commit; CI green on all merged PRs (#20–#24). |
| In-scope SVS cases implemented and passing | PASS | SIM-TC-003…012 automated PASS; SIM-TC-013 manual verdict recorded; SIM-TC-026 PASS. No M1a/M1b scope pulled in. |
| Test report + traceability matrix generated | PASS | This document (M1-report.md). Traceability matrix above. |
| CI gate milestone raised to M1 | PASS | `.github/workflows/ci.yml` runs TraceabilityCheck `--milestone M1 --gate` (this PR). |
| Milestone tag proposed | PENDING | Tag `M1` to be created on the merge commit of this report's PR upon approval. |

---

Generated by AI tooling (Claude Code); reviewed and approved via PR before merge to master.
