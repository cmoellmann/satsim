# SatSim M1b Milestone Test Report

- Configuration item: SATSIM-M1B-REPORT, Issue 1
- Report date: 2026-07-19
- Baseline commit: aa8ef8de4213bf43e6f8b33b23abce3d2b34477c (Merge pull request #46 from cmoellmann/feat/m1b-st3-service)
- Generated from: Surefire test results + JaCoCo coverage; assembled by AI tooling, reviewed and approved via PR

## Environment

| Item | Value |
|---|---|
| Java | OpenJDK Runtime Environment (build 21.0.11+10-1-24.04.2-Ubuntu) |
| Maven | 3.9.11 (from .mvn/wrapper/maven-wrapper.properties) |
| OS | Linux 6.17.0-35-generic |

## Scope

M1b per SDP §4 comprises a single change request, implemented in one milestone gate:

**SCR-001** — ST[3] housekeeping subset (PRs #42–#46): TC(3,1)/(3,5)/(3,7) 
lifecycle, TM(3,25) periodic reports, default SID 1 enabled at startup with 
1.0 s simulated-time collection interval. Per ICD Issue 5 (OP-3 resolution), 
ST[3] semantic errors yield TM(1,8) completion failure reports.

## M1b Exit Criteria vs. Status

| Criterion | Status | Evidence |
|---|---|---|
| All ST[3] ICD vectors pass (encode+decode) | PASS | V-TC-03/04/05 and V-TM-03/04 encode byte-identically and decode to ICD field values (SIM-TC-018). |
| Periodic emission at correct simulated times verified | PASS | SIM-TC-019: fresh start, no TC traffic — reports at T=1.0/2.0 s matching V-TM-03/04 byte-identically. |
| HK structure lifecycle (create/enable/disable) verified | PASS | SIM-TC-020: V-TC-03 creates SID 2 disabled, V-TC-04 enables with 5.0 s interval, V-TC-05 disables; SID 1 unaffected throughout. |
| Determinism replay green incl. HK stream | PASS | SIM-TC-011: scripted scenario with 12 SID 1 reports (T=1..12 s) + 2 SID 2 reports (T=7,12 s) yields 573-octet stream; two runs SHA-256-identical. |
| Frontend smoke test shows periodic HK | PASS | SIM-TC-021 verdict recorded in §"Manual Test Verdicts" below: pass, 2026-07-19, C. Möllmann. |
| SRS M1b-scope requirements all traced+passed | PASS | Traceability matrix below; TraceabilityCheck M1b gate: 0 findings → OK. |

## Test Results Summary

### Per-Module Test Classes

#### pus-core

| Test Class | Tests | Failures | Errors | Skipped | Time (s) | Verdict |
|---|---|---|---|---|---|---|
| org.satsim.pus.crc.Crc16CcittTest | 8 | 0 | 0 | 0 | 0.010 | PASS |
| org.satsim.pus.ccsds.PrimaryHeaderTest | 6 | 0 | 0 | 0 | 0.079 | PASS |
| org.satsim.pus.time.CucTimeTest | 12 | 0 | 0 | 0 | 0.046 | PASS |
| org.satsim.pus.tc.TcSecondaryHeaderTest | 7 | 0 | 0 | 0 | 0.010 | PASS |
| org.satsim.pus.tc.TcPacketTest | 7 | 0 | 0 | 0 | 0.014 | PASS |
| org.satsim.pus.tm.TmSecondaryHeaderTest | 4 | 0 | 0 | 0 | 0.007 | PASS |
| org.satsim.pus.tm.TmPacketTest | 7 | 0 | 0 | 0 | 0.014 | PASS |
| org.satsim.pus.ReferenceVectorDecodeTest | 1 | 0 | 0 | 0 | 0.002 | PASS |
| org.satsim.pus.St1ReferenceVectorTest | 1 | 0 | 0 | 0 | 0.006 | PASS |
| org.satsim.pus.St3ReferenceVectorTest | 1 | 0 | 0 | 0 | 0.004 | PASS |
| org.satsim.pus.st3.HkCodecsTest | 24 | 0 | 0 | 0 | 0.062 | PASS |
| **pus-core subtotal** | **78** | **0** | **0** | **0** | **0.254** | **PASS** |

#### simulator

| Test Class | Tests | Failures | Errors | Skipped | Time (s) | Verdict |
|---|---|---|---|---|---|---|
| org.satsim.sim.obsw.LoopbackTargetTest | 14 | 0 | 0 | 0 | 0.018 | PASS |
| org.satsim.sim.time.ManualSimulationClockTest | 4 | 0 | 0 | 0 | 0.003 | PASS |
| org.satsim.sim.time.SimulationSchedulerTest | 4 | 0 | 0 | 0 | 0.008 | PASS |
| org.satsim.sim.PusChainTest | 9 | 0 | 0 | 0 | 0.016 | PASS |
| org.satsim.sim.DeterminismReplayTest | 1 | 0 | 0 | 0 | 0.058 | PASS |
| org.satsim.sim.web.HmiWebApiTest | 3 | 0 | 0 | 0 | 3.969 | PASS |
| org.satsim.sim.web.WebApiEndToEndTest | 3 | 0 | 0 | 0 | 0.282 | PASS |
| org.satsim.sim.St3HousekeepingTest | 7 | 0 | 0 | 0 | 0.015 | PASS |
| **simulator subtotal** | **45** | **0** | **0** | **0** | **4.369** | **PASS** |

#### sim-test-support

| Test Class | Tests | Failures | Errors | Skipped | Time (s) | Verdict |
|---|---|---|---|---|---|---|
| org.satsim.testsupport.trace.TraceabilityCheckTest | 7 | 0 | 0 | 0 | 0.060 | PASS |
| **sim-test-support subtotal** | **7** | **0** | **0** | **0** | **0.060** | **PASS** |

**Overall total: 130 tests, 0 failures, 0 errors, 0 skipped; elapsed 4.683 s (from `./mvnw verify` at the baseline commit).**

Data sources: `*/target/surefire-reports/` in the three modules.

## Code Coverage

### pus-core

Per SDP §2.1 tailoring, pus-core is the correctness-critical core (packet layer) and carries an indicative 80% line coverage target.

**Measured (from `pus-core/target/site/jacoco/jacoco.csv`):**

| Metric | Missed | Covered | Total | % |
|---|---|---|---|---|
| Line | 12 | 424 | 436 | 97.25% |
| Branch | 20 | 184 | 204 | 90.20% |

**Status:** PASS (both line and branch exceed the indicative 80% target).

**M1b addition:** `org.satsim.pus.st3` package (ST[3] housekeeping codecs: 
HkCreateRequest, HkSidList, HkReport, HkParameter):

| Metric | Missed | Covered | Total | % |
|---|---|---|---|---|
| Line | 0 | 153 | 153 | 100.00% |
| Branch | 0 | 68 | 68 | 100.00% |

All ST[3] packet-layer codecs are exercised fully by SIM-TC-018 (vector 
encode/decode round-trip).

**Other modules (simulator, sim-test-support):** No formal coverage target per SDP §2.1 tailoring.

## Traceability Matrix

### SRS M1b Requirements and Verification

| Req ID | Title | Ver. | Scope | SVS Case(s) | Test Method(s) | Verdict |
|---|---|---|---|---|---|---|
| SIM-REQ-PUS-007 | All ICD §6 vectors pass (encode+decode) — extended to §6.4/§6.5 ST[3] vectors per SCR-001 | T | M1 (ext. M1b) | SIM-TC-018 | St3ReferenceVectorTest#encodesAndDecodesReferenceVectorsToIcdSpecifiedFields | PASS |
| SIM-REQ-HK-001 | Create HK structure on TC(3,1), start disabled | T | M1b | SIM-TC-018, SIM-TC-020 | St3ReferenceVectorTest#encodesAndDecodesReferenceVectorsToIcdSpecifiedFields, St3HousekeepingTest#structureLifecycleCreateEnableDisable | PASS |
| SIM-REQ-HK-002 | Emit TM(3,25) per collection interval for enabled structures | T | M1b | SIM-TC-018, SIM-TC-019 | St3ReferenceVectorTest#encodesAndDecodesReferenceVectorsToIcdSpecifiedFields, St3HousekeepingTest#defaultSidReportsPeriodicallyFromStartMatchingVectors | PASS |
| SIM-REQ-HK-003 | Default SID 1 enabled at startup with 1.0 s interval | T | M1b | SIM-TC-019 | St3HousekeepingTest#defaultSidReportsPeriodicallyFromStartMatchingVectors | PASS |
| SIM-REQ-HK-004 | Enable/disable periodic generation via TC(3,5)/TC(3,7) | T | M1b | SIM-TC-020 | St3HousekeepingTest#structureLifecycleCreateEnableDisable | PASS |
| SIM-REQ-VER-003 | TM(1,2)/TM(1,8) failure reports regardless of ack flags; semantic errors yield TM(1,8) per ICD §10.4 — extended at M1b to cover ST[3] errors per OP-3 resolution | T | M1a (ext. M1b) | SIM-TC-033 | St3HousekeepingTest#semanticErrorsYieldCompletionFailureReports | PASS |

**Amendment note:** SIM-TC-033 additionally re-verifies SIM-REQ-VER-003 at M1b 
scope (ST[3] semantic error → TM(1,8) path activated per ICD OP-3). The M1a 
verification (PUS-version rejection → TM(1,2)) carries forward and remains 
exercised; SIM-TC-033 extends the requirement's scope to the new error class.

Preceding-milestone requirements remain in scope and passing; their gate records 
are the [M0 report](M0-report.md), [M1 report](M1-report.md), and 
[M1a report](M1a-report.md). The M0/M1/M1a review verdicts (SIM-REQ-TIME-001, 
SIM-REQ-TIME-003) carry per the cumulative verdict rule (ACT-004).

**TraceabilityCheck output** (per SDP §5, CI consistency gate):
```
Traceability check M1b (gate): 0 finding(s) -> OK
```

Source: `java -cp sim-test-support/target/classes org.satsim.testsupport.trace.TraceabilityCheck --root . --milestone M1b --gate`

## Manual Test Verdicts

Executed against the running simulator (http://localhost:8090) at the
baseline commit.

### SIM-TC-021 (periodic HK visible) — pass, 2026-07-19, C. Möllmann

Checklist per SVS: open frontend on a freshly started simulator; without sending 
any TC, TM(3,25) entries appear about once per second (interactive 1:1 pacing) 
with decoded SID and parameter values; HK-P003 value changes between reports. 
Verifies: SIM-REQ-HK-003 (manual half).

## Review Verdicts

No requirement entered review-verification scope at M1b (all M1b-scope
requirements are test-verified per the SRS). The M0/M1/M1a review verdicts carry 
forward (recorded in the [M0 report](M0-report.md)); the ACT-004 verdict gate remains 
active.

## Notes and Deviations

- **OP-3 resolved as a dedicated spec PR (#42)** before implementation: ICD 
  Issue 5 adds §10.4 failure codes 0x0004–0x0008 for ST[3] semantic errors and 
  new reference vectors V-NEG-03/V-TM-09 (§6.7); SIM-TC-033 added to the SVS.
- **First time-triggered TM:** SimulatedObsw gained a pull-based time-event pair 
  (nextEventNanos/handleTimeEvent) to support periodic HK reports; the loopback 
  target ends grant windows at event due times (ADR-0006), decision recorded in 
  SDD §3.2.3/§7.
- **Existing M1/M1a test scenarios adapted** to the default periodic HK without 
  touching SVS criteria: TC-response tests advance < 1 s to avoid spurious HK 
  reports; SIM-TC-027 filters to time frames; the V-TM-02 reproduction disables 
  SID 1 via TC(3,7) first; the unimplemented-service probe moved from TC(3,1) 
  to TC(2,1).
- **Delegation:** pus-core ST[3] codecs and frontend HK display chunks 
  implemented by the Sonnet-tier agent, report/matrix assembled by the 
  Haiku-tier scribe, per the tiered-staffing policy; all delegated diffs reviewed 
  in the main session; review added one fix (HkReport.encode value-width check).
- **Live end-to-end check before the gate:** booted simulator, observed 
  TM(3,25) on the WebSocket once per wall-clock second (interactive 1:1 pacing) 
  at exact whole-second OBTs; SID 1 SID field and parameter values decoded correctly.
- **CI catch after the gate PR:** SIM-TC-012 (`WebApiEndToEndTest`, runs under 
  real 1:1 pacing) took the *first* TM frame off the WebSocket; on a slow CI 
  runner the SID 1 heartbeat crossed the 1.0 s boundary ahead of the ping 
  response (`expected 17 but was 3`). Fixed in the verdict-record PR by 
  selecting the first ST[17] TM frame — the SIM-TC-012 criterion constrains 
  the TM(17,2) frame, not the surrounding stream.

## Open Items / Proposals

- ACT-001 (ECSS clause citations, before M2) remains open.
- ACT-002 (TEMU licensing, before M5) remains open.
- Next increment per SDP §4: M2 (TCP length-framed space-packet link, ICD §8).

## Milestone Checklist

| Item | Status | Evidence |
|---|---|---|
| Build + CI green | PASS | `./mvnw verify` green at baseline commit; CI green on all merged PRs (#42–#46). |
| In-scope SVS cases implemented and passing | PASS | SIM-TC-018/019/020 automated PASS; SIM-TC-033 automated PASS (OP-3 semantic errors); SIM-TC-021 manual pass (2026-07-19, C. Möllmann). No M2 scope pulled in. |
| Test report + traceability matrix generated | PASS | This document (M1b-report.md). Traceability matrix above. |
| CI gate milestone raised to M1b | PASS | `.github/workflows/ci.yml` runs TraceabilityCheck `--milestone M1b --gate` (this PR). |
| Milestone tag proposed | PASS | Tag `M1b` proposed on the merge commit of this verdict record (the gate-closing commit); the pushed tag is its own evidence. |

---

Generated by AI tooling (Claude Code); reviewed and approved via PR before merge to master.
