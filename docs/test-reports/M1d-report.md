# SatSim M1d Milestone Test Report

- Configuration item: SATSIM-M1D-REPORT, Issue 1
- Report date: 2026-07-19
- Baseline commit: 46eb330 (Merge pull request #64 from cmoellmann/feat/m1d-hmi-presentation)
- Generated from: Surefire test results + JaCoCo coverage; assembled by AI tooling, reviewed and approved via PR

## Environment

| Item | Value |
|---|---|
| Java | OpenJDK Runtime Environment (build 21.0.11+10-1-24.04.2-Ubuntu) |
| Maven | 3.9.11 (from .mvn/wrapper/maven-wrapper.properties) |
| OS | Linux 6.17.0-35-generic |

## Scope

M1d per SDP §4 comprises a single change request, implemented in one milestone
gate:

**SCR-006** — HMI presentation package (PR #62 spec, PR #64 implementation),
frontend-only, bundling the dispositions of the 2026-07-19 SPR campaign
(SPR-001…006): causal log ordering by sorted insertion (SPR-001, new
requirement SIM-REQ-UI-014), dedicated failure-code column (SPR-002), numeric
dropdown ordering (SPR-004), widened layout for single-line raw hex (SPR-005),
log heading alignment (SPR-006). The gate additionally records the full
SIM-TC-034 re-run that closes SPR-003 (compose field visibility, fixed
separately in PR #61 — see the verification-escape note below). No ICD
impact — the space-link contract and the §8 web API are unchanged.

## M1d Exit Criteria vs. Status

| Criterion | Status | Evidence |
|---|---|---|
| Causal log ordering verified (SIM-TC-037) | PENDING | Manual verdict to be recorded in §"Manual Test Verdicts" below. Supporting evidence: automated pre-merge check at the baseline — a scripted headless run (Chromium/CDP, PR #64) rendered TM(1,7) / TM(17,2) / TM(1,1) / TC(17,1) top-down at identical OBT, and a semantically invalid TC(3,1) showed its TM(1,8) above the TC row. |
| HMI presentation checklist verified (SIM-TC-038) | PENDING | Manual verdict to be recorded below. Supporting evidence: scripted check at 1512 px viewport — TM(3,25) default-SID raw hex on a single log line; heading at the card's top-left. |
| Amended SIM-TC-032 re-verified (numeric dropdown order) | PENDING | Manual verdict to be recorded below. Supporting evidence: scripted DOM check — type options `3 — ST[3] / 17 — ST[17] / custom…`, initial selection ST[17]/1. |
| Amended SIM-TC-036 re-verified (failure-code column) | PENDING | Manual verdict to be recorded below. Supporting evidence: scripted check — failure-code name rendered in the dedicated column on a TM(1,2) row, empty elsewhere. |
| SIM-TC-034 re-run recorded (SPR-003 closure) | PENDING | Full re-run required per SPR-003 §2 (the M1c verdict's two visibility steps could not have behaved as recorded); fresh verdict to be recorded below. |
| Existing automated suite green | PASS | `./mvnw verify` green at the baseline commit: 130 tests, 0 failures (tables below); CI green on merged PR #64. |
| SPR-001…006 closed in the register | PENDING | SPR-002/004/005/006 are terminal (Rejected/converted). SPR-001 and SPR-003 close with the SIM-TC-037 and SIM-TC-034 verdicts above; register update rides in the verdict-recording commit. |
| SRS M1d-scope requirements all traced+passed | PENDING | Traceability matrix below; TraceabilityCheck M1d gate: 0 findings → OK. SIM-REQ-UI-014 is M-verified — verdict pending. |

Per the M1b/M1c precedent, the gate record closes with the commit that records
the manual verdicts; the `M1d` tag is proposed on that merge commit.

## Test Results Summary

M1d changes no Java production or test code (frontend static resources and
documents only); the automated suite is unchanged from M1c and re-run green
at the M1d baseline.

### Per-Module Test Classes

#### pus-core

| Test Class | Tests | Failures | Errors | Skipped | Time (s) | Verdict |
|---|---|---|---|---|---|---|
| org.satsim.pus.crc.Crc16CcittTest | 8 | 0 | 0 | 0 | 0.007 | PASS |
| org.satsim.pus.ccsds.PrimaryHeaderTest | 6 | 0 | 0 | 0 | 0.059 | PASS |
| org.satsim.pus.time.CucTimeTest | 12 | 0 | 0 | 0 | 0.018 | PASS |
| org.satsim.pus.tc.TcSecondaryHeaderTest | 7 | 0 | 0 | 0 | 0.006 | PASS |
| org.satsim.pus.tc.TcPacketTest | 7 | 0 | 0 | 0 | 0.010 | PASS |
| org.satsim.pus.tm.TmSecondaryHeaderTest | 4 | 0 | 0 | 0 | 0.003 | PASS |
| org.satsim.pus.tm.TmPacketTest | 7 | 0 | 0 | 0 | 0.007 | PASS |
| org.satsim.pus.ReferenceVectorDecodeTest | 1 | 0 | 0 | 0 | 0.005 | PASS |
| org.satsim.pus.St1ReferenceVectorTest | 1 | 0 | 0 | 0 | 0.006 | PASS |
| org.satsim.pus.St3ReferenceVectorTest | 1 | 0 | 0 | 0 | 0.001 | PASS |
| org.satsim.pus.st3.HkCodecsTest | 24 | 0 | 0 | 0 | 0.048 | PASS |
| **pus-core subtotal** | **78** | **0** | **0** | **0** | **0.170** | **PASS** |

#### simulator

| Test Class | Tests | Failures | Errors | Skipped | Time (s) | Verdict |
|---|---|---|---|---|---|---|
| org.satsim.sim.obsw.LoopbackTargetTest | 14 | 0 | 0 | 0 | 0.008 | PASS |
| org.satsim.sim.time.ManualSimulationClockTest | 4 | 0 | 0 | 0 | 0.003 | PASS |
| org.satsim.sim.time.SimulationSchedulerTest | 4 | 0 | 0 | 0 | 0.006 | PASS |
| org.satsim.sim.PusChainTest | 9 | 0 | 0 | 0 | 0.007 | PASS |
| org.satsim.sim.DeterminismReplayTest | 1 | 0 | 0 | 0 | 0.056 | PASS |
| org.satsim.sim.web.HmiWebApiTest | 3 | 0 | 0 | 0 | 3.502 | PASS |
| org.satsim.sim.web.WebApiEndToEndTest | 3 | 0 | 0 | 0 | 0.165 | PASS |
| org.satsim.sim.St3HousekeepingTest | 7 | 0 | 0 | 0 | 0.007 | PASS |
| **simulator subtotal** | **45** | **0** | **0** | **0** | **3.754** | **PASS** |

#### sim-test-support

| Test Class | Tests | Failures | Errors | Skipped | Time (s) | Verdict |
|---|---|---|---|---|---|---|
| org.satsim.testsupport.trace.TraceabilityCheckTest | 7 | 0 | 0 | 0 | 0.061 | PASS |
| **sim-test-support subtotal** | **7** | **0** | **0** | **0** | **0.061** | **PASS** |

**Overall total: 130 tests, 0 failures, 0 errors, 0 skipped; elapsed 3.985 s (from `./mvnw verify` at the baseline commit).**

Data sources: `*/target/surefire-reports/` in the three modules.

## Code Coverage

### pus-core

Per SDP §2.1 tailoring, pus-core is the correctness-critical core (packet layer) and carries an indicative 80% line coverage target.

**Measured (from `pus-core/target/site/jacoco/jacoco.csv`):**

| Metric | Missed | Covered | Total | % |
|---|---|---|---|---|
| Line | 12 | 424 | 436 | 97.25% |
| Branch | 20 | 184 | 204 | 90.20% |

**Status:** PASS (both line and branch exceed the indicative 80% target; unchanged from M1c — M1d touches no pus-core code).

**Other modules (simulator, sim-test-support):** No formal coverage target per SDP §2.1 tailoring.

## Traceability Matrix

### SRS M1d Requirements and Verification

| Req ID | Title | Ver. | Scope | SVS Case(s) | Test Method(s) | Verdict |
|---|---|---|---|---|---|---|
| SIM-REQ-UI-014 | Causal log order regardless of web-API channel arrival order; equal-OBT TM rows in emission order | M | M1d | SIM-TC-037 | Manual checklist (frontend) | PENDING |

Amended SVS cases re-verifying prior requirements at M1d (SCR-006):
SIM-TC-032 (SIM-REQ-UI-005, SIM-REQ-UI-010 — dropdown ordering),
SIM-TC-036 (SIM-REQ-UI-013 — failure-code column), and SIM-TC-038
(SIM-REQ-UI-002, SIM-REQ-UI-010 — presentation checklist). SIM-TC-034
(SIM-REQ-UI-011) is re-run in full per SPR-003.

Preceding-milestone requirements remain in scope and passing; their gate
records are the [M0 report](M0-report.md), [M1 report](M1-report.md),
[M1a report](M1a-report.md), [M1b report](M1b-report.md), and
[M1c report](M1c-report.md). The M0/M1/M1a review verdicts (SIM-REQ-TIME-001,
SIM-REQ-TIME-003) carry per the cumulative verdict rule (ACT-004). The M1c
SIM-TC-034 verdict is superseded by the re-run recorded here (SPR-003).

**TraceabilityCheck output** (per SDP §5, CI consistency gate):
```
Traceability check M1d (gate): 0 finding(s) -> OK
```

Source: `java -cp sim-test-support/target/classes org.satsim.testsupport.trace.TraceabilityCheck --root . --milestone M1d --gate`

## Manual Test Verdicts

To be executed against the running simulator (http://localhost:8090) at the
baseline commit; verdict + date + name recorded here per SVS.

### SIM-TC-037 (causal log ordering at equal OBT) — PENDING

Checklist per SVS: fresh simulator; a ping with default ack flags yields,
top-down in the newest-first log, TM(1,7), TM(17,2), TM(1,1), TC(17,1) — the
TC row below all its responses despite identical OBT; repeated five times
without a single inversion; a structured TC(3,1) with interval 50 ms shows its
TM(1,8) above the TC row. Verifies: SIM-REQ-UI-014. Closes: SPR-001.

### SIM-TC-038 (HMI presentation checklist) — PENDING

Checklist per SVS, viewport ≥ 1440 px wide: the raw hex of a TM(3,25)
default-SID report renders on a single log line; the "Live packet log"
heading sits at the top-left of its card, aligned like "Compose TC".
Verifies: SIM-REQ-UI-002, SIM-REQ-UI-010.

### SIM-TC-032 re-verification (amended: dropdown ordering) — PENDING

Checklist per amended SVS: both compose dropdowns list their entries in
ascending numeric order with the custom… entry last; header orientation and
OBT clock unchanged. Verifies: SIM-REQ-UI-005, SIM-REQ-UI-010.

### SIM-TC-036 re-verification (amended: failure-code column) — PENDING

Checklist per amended SVS: the TM(1,8) row provoked in SIM-TC-034 (interval
50 ms) shows the failure-code name ILLEGAL_COLLECTION_INTERVAL in the
dedicated failure-code column; the column is empty for all other rows.
Verifies: SIM-REQ-UI-013.

### SIM-TC-034 full re-run (SPR-003 closure) — PENDING

Full checklist per SVS: fresh simulator, all ack flags cleared; selecting
TC(3,1) replaces the free application-data field with SID/interval/parameter
inputs; entering SID 2, interval 5000 ms, parameters HK-P001+HK-P003 yields
an encoded-packet preview byte-identical to V-TC-03; after sending, TC(3,5)
then TC(3,7) with SID list "2" preview byte-identical to V-TC-04 and V-TC-05
at the consumed sequence counts; selecting a custom type or subtype restores
free hex entry; a semantically invalid structured compose (interval 50 ms) is
sendable and yields TM(1,8) ILLEGAL_COLLECTION_INTERVAL. Verifies:
SIM-REQ-UI-011. Closes: SPR-003 (supersedes the impaired M1c verdict, see
SPR-003 §2).

## Review Verdicts

No requirement entered review-verification scope at M1d (SIM-REQ-UI-014 is
manually verified per the SRS). The M0/M1/M1a review verdicts carry forward
(recorded in the [M0 report](M0-report.md)); the ACT-004 verdict gate remains
active.

## Notes and Deviations

- **First SPR-driven increment:** M1d exists because the 2026-07-19 manual
  test campaign produced six SPRs; the SPR↔SCR demarcation routed one to a
  direct fix (SPR-003, PR #61), one to fix-plus-requirement (SPR-001) and
  four to Rejected-with-conversion into SCR-006 (SPR-002/004/005/006).
- **Verification escape recorded:** the M1c SIM-TC-034 pass verdict contains
  two compose-field-visibility steps that could not have behaved as written
  (SPR-003 §2); remedy is the full re-run at this gate. The substantive
  byte-identity steps were unaffected.
- **Register data loss repaired:** the PR #62 merge-conflict resolution
  dropped five SPR-LOG rows; detected by post-merge cross-check, restored in
  PR #63, recorded in SCR-006 §5.
- **Automated pre-merge verification:** PR #64 was verified end-to-end
  against the live simulator via a scripted headless browser (Chromium/CDP):
  causal row order at one OBT, failure-code column population, single-line
  TM(3,25) hex at 1512 px, heading alignment, dropdown order and
  field-visibility switching. These checks support but do not replace the
  manual M verdicts above.

## Open Items / Proposals

- ACT-001 (ECSS clause citations, before M2) remains open.
- ACT-002 (TEMU licensing, before M5) remains open.
- Next increment per SDP §4: M2 (TCP length-framed space-packet link, ICD §8).

## Milestone Checklist

| Item | Status | Evidence |
|---|---|---|
| Build + CI green | PASS | `./mvnw verify` green at baseline commit; CI green on merged PRs #62/#63/#64. |
| In-scope SVS cases implemented and passing | PENDING | SIM-TC-037/038 + amended 032/036 + 034 re-run await manual verdicts. No M2 scope pulled in. |
| Test report + traceability matrix generated | PASS | This document (M1d-report.md). Traceability matrix above. |
| CI gate milestone raised to M1d | PASS | `.github/workflows/ci.yml` runs TraceabilityCheck `--milestone M1d --gate` (this PR). |
| Milestone tag proposed | PENDING | Tag `M1d` to be proposed on the merge commit of the verdict record (the gate-closing commit), per M1b/M1c precedent. |

---

Generated by AI tooling (Claude Code); reviewed and approved via PR before merge to master.
