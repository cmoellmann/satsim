# SatSim M1c Milestone Test Report

- Configuration item: SATSIM-M1C-REPORT, Issue 1
- Report date: 2026-07-19
- Baseline commit: e090dbc47b5a5ea2f5e1944720adc46acc0eb5cd (Merge pull request #50 from cmoellmann/feat/m1c-hk-compose)
- Generated from: Surefire test results + JaCoCo coverage; assembled by AI tooling, reviewed and approved via PR

## Environment

| Item | Value |
|---|---|
| Java | OpenJDK Runtime Environment (build 21.0.11+10-1-24.04.2-Ubuntu) |
| Maven | 3.9.11 (from .mvn/wrapper/maven-wrapper.properties) |
| OS | Linux 6.17.0-35-generic |

## Scope

M1c per SDP §4 comprises a single change request, implemented in one milestone gate:

**SCR-004** — HK compose usability package (PRs #49 spec, #50 implementation),
frontend-only: structured compose fields for TC(3,1)/(3,5)/(3,7) generating
the application data per ICD §9.2/§9.3, interpreted ST[3] TC application data
in the log detail view, inline ICD §10.4 failure-code names on TM(1,2)/(1,8)
log rows. First SCR with no ICD impact — the space-link contract and the §8
web API are unchanged; encoding still round-trips `POST /api/tc/preview`.

## M1c Exit Criteria vs. Status

| Criterion | Status | Evidence |
|---|---|---|
| Structured compose of the V-TC-03/04/05 field values reproduces the vectors byte-identically in the preview (SIM-TC-034) | PENDING (manual) | Verdict to be recorded in §"Manual Test Verdicts" below. Supporting evidence: automated live check at the baseline — the generated application data for the SVS field values previews byte-identical to V-TC-03/04/05 at sequence counts 0/1/2 via `POST /api/tc/preview`. |
| Interpreted TC detail incl. layout-mismatch handling verified (SIM-TC-035) | PENDING (manual) | Verdict to be recorded below. |
| Inline failure code verified (SIM-TC-036) | PENDING (manual) | Verdict to be recorded below. |
| Existing frontend smoke cases unaffected | PASS | No SVS criterion amended; ping, raw injection, TM(3,25)/ST[1] detail and log-control paths unchanged (regression-checked live at the baseline); full automated suite green. |
| SRS M1c-scope requirements all traced+passed | PENDING (manual) | Traceability matrix below; TraceabilityCheck M1c gate: 0 findings → OK. UI-011..013 are M-verified — passed follows from the three manual verdicts. |

Per the M1b precedent, the gate record closes with the commit that records
the manual verdicts; the `M1c` tag is proposed on that merge commit.

## Test Results Summary

M1c changes no Java production or test code (frontend static resources and
documents only); the automated suite is unchanged from M1b and re-run green
at the M1c baseline.

### Per-Module Test Classes

#### pus-core

| Test Class | Tests | Failures | Errors | Skipped | Time (s) | Verdict |
|---|---|---|---|---|---|---|
| org.satsim.pus.crc.Crc16CcittTest | 8 | 0 | 0 | 0 | 0.024 | PASS |
| org.satsim.pus.ccsds.PrimaryHeaderTest | 6 | 0 | 0 | 0 | 0.152 | PASS |
| org.satsim.pus.time.CucTimeTest | 12 | 0 | 0 | 0 | 0.066 | PASS |
| org.satsim.pus.tc.TcSecondaryHeaderTest | 7 | 0 | 0 | 0 | 0.016 | PASS |
| org.satsim.pus.tc.TcPacketTest | 7 | 0 | 0 | 0 | 0.019 | PASS |
| org.satsim.pus.tm.TmSecondaryHeaderTest | 4 | 0 | 0 | 0 | 0.013 | PASS |
| org.satsim.pus.tm.TmPacketTest | 7 | 0 | 0 | 0 | 0.024 | PASS |
| org.satsim.pus.ReferenceVectorDecodeTest | 1 | 0 | 0 | 0 | 0.005 | PASS |
| org.satsim.pus.St1ReferenceVectorTest | 1 | 0 | 0 | 0 | 0.013 | PASS |
| org.satsim.pus.St3ReferenceVectorTest | 1 | 0 | 0 | 0 | 0.003 | PASS |
| org.satsim.pus.st3.HkCodecsTest | 24 | 0 | 0 | 0 | 0.085 | PASS |
| **pus-core subtotal** | **78** | **0** | **0** | **0** | **0.420** | **PASS** |

#### simulator

| Test Class | Tests | Failures | Errors | Skipped | Time (s) | Verdict |
|---|---|---|---|---|---|---|
| org.satsim.sim.obsw.LoopbackTargetTest | 14 | 0 | 0 | 0 | 0.016 | PASS |
| org.satsim.sim.time.ManualSimulationClockTest | 4 | 0 | 0 | 0 | 0.024 | PASS |
| org.satsim.sim.time.SimulationSchedulerTest | 4 | 0 | 0 | 0 | 0.031 | PASS |
| org.satsim.sim.PusChainTest | 9 | 0 | 0 | 0 | 0.010 | PASS |
| org.satsim.sim.DeterminismReplayTest | 1 | 0 | 0 | 0 | 0.092 | PASS |
| org.satsim.sim.web.HmiWebApiTest | 3 | 0 | 0 | 0 | 5.482 | PASS |
| org.satsim.sim.web.WebApiEndToEndTest | 3 | 0 | 0 | 0 | 0.269 | PASS |
| org.satsim.sim.St3HousekeepingTest | 7 | 0 | 0 | 0 | 0.010 | PASS |
| **simulator subtotal** | **45** | **0** | **0** | **0** | **5.934** | **PASS** |

#### sim-test-support

| Test Class | Tests | Failures | Errors | Skipped | Time (s) | Verdict |
|---|---|---|---|---|---|---|
| org.satsim.testsupport.trace.TraceabilityCheckTest | 7 | 0 | 0 | 0 | 0.086 | PASS |
| **sim-test-support subtotal** | **7** | **0** | **0** | **0** | **0.086** | **PASS** |

**Overall total: 130 tests, 0 failures, 0 errors, 0 skipped; elapsed 6.440 s (from `./mvnw verify` at the baseline commit).**

Data sources: `*/target/surefire-reports/` in the three modules.

## Code Coverage

### pus-core

Per SDP §2.1 tailoring, pus-core is the correctness-critical core (packet layer) and carries an indicative 80% line coverage target.

**Measured (from `pus-core/target/site/jacoco/jacoco.csv`):**

| Metric | Missed | Covered | Total | % |
|---|---|---|---|---|
| Line | 12 | 424 | 436 | 97.25% |
| Branch | 20 | 184 | 204 | 90.20% |

**Status:** PASS (both line and branch exceed the indicative 80% target; unchanged from M1b — M1c touches no pus-core code).

**Other modules (simulator, sim-test-support):** No formal coverage target per SDP §2.1 tailoring.

## Traceability Matrix

### SRS M1c Requirements and Verification

| Req ID | Title | Ver. | Scope | SVS Case(s) | Test Method(s) | Verdict |
|---|---|---|---|---|---|---|
| SIM-REQ-UI-011 | Structured HK compose fields generating ICD §9.2/§9.3 application data; free hex retained elsewhere | M | M1c | SIM-TC-034 | Manual checklist (frontend) | PENDING |
| SIM-REQ-UI-012 | Interpreted ST[3] TC application data in the packet-log detail view; layout mismatches marked | M | M1c | SIM-TC-035 | Manual checklist (frontend) | PENDING |
| SIM-REQ-UI-013 | Inline ICD §10.4 failure-code name on TM(1,2)/(1,8) log rows | M | M1c | SIM-TC-036 | Manual checklist (frontend) | PENDING |

Preceding-milestone requirements remain in scope and passing; their gate records
are the [M0 report](M0-report.md), [M1 report](M1-report.md),
[M1a report](M1a-report.md), and [M1b report](M1b-report.md). The M0/M1/M1a
review verdicts (SIM-REQ-TIME-001, SIM-REQ-TIME-003) carry per the cumulative
verdict rule (ACT-004).

**TraceabilityCheck output** (per SDP §5, CI consistency gate):
```
Traceability check M1c (gate): 0 finding(s) -> OK
```

Source: `java -cp sim-test-support/target/classes org.satsim.testsupport.trace.TraceabilityCheck --root . --milestone M1c --gate`

## Manual Test Verdicts

To be executed against the running simulator (http://localhost:8090) at the
baseline commit; verdicts recorded here by the project lead.

### SIM-TC-034 (structured HK compose) — verdict pending

Checklist per SVS: fresh simulator, all ack flags cleared; selecting TC(3,1)
replaces the free application-data field with SID/interval/parameter inputs;
entering SID 2, interval 5000 ms, parameters HK-P001+HK-P003 yields an
encoded-packet preview byte-identical to V-TC-03; after sending, TC(3,5) then
TC(3,7) with SID list "2" preview byte-identical to V-TC-04 and V-TC-05 at the
consumed sequence counts; selecting a custom type or subtype restores free hex
entry; a semantically invalid structured compose (interval 50 ms) is sendable
and yields TM(1,8) ILLEGAL_COLLECTION_INTERVAL. Verifies: SIM-REQ-UI-011.

### SIM-TC-035 (interpreted ST[3] TC detail) — verdict pending

Checklist per SVS: expanding the three TC rows sent in SIM-TC-034 shows named
application-data fields (TC(3,1): SID 2, interval 5000 ms, parameters
HK-P001/HK-P003 by name; TC(3,5)/(3,7): SID list 2); a TC(3,1) composed via
the custom free-entry path with truncated application data (e.g. `00 02`)
shows a layout-mismatch note with raw hex instead of misdecoded fields.
Verifies: SIM-REQ-UI-012.

### SIM-TC-036 (inline failure code) — verdict pending

Checklist per SVS: the TM(1,8) row provoked in SIM-TC-034 (interval 50 ms)
shows the failure-code name ILLEGAL_COLLECTION_INTERVAL in the log row itself,
without opening the detail view; success reports TM(1,1)/(1,7) carry no
failure-code suffix. Verifies: SIM-REQ-UI-013.

## Review Verdicts

No requirement entered review-verification scope at M1c (all M1c-scope
requirements are manually verified per the SRS). The M0/M1/M1a review verdicts
carry forward (recorded in the [M0 report](M0-report.md)); the ACT-004 verdict
gate remains active.

## Notes and Deviations

- **First ICD-impact-free SCR:** SCR-004 changes neither the space-link
  contract (§2–§7, §9–§10) nor the web API (§8); the impact analysis records
  why. No reference vectors added or modified.
- **SDD statement superseded:** §3.4's "no packet knowledge is duplicated in
  JavaScript" was already stale (TM interpretation) and is now replaced — the
  frontend deliberately knows the ICD §9.2/§9.3/§9.5 application-data layouts;
  header/CRC/sequence-count encoding remains backend-only.
- **Latent display defect found and fixed in passing:** the ST[1] detail view
  previously sliced the trailing 4 hex chars of TM(1,2)/(1,8) application data
  unconditionally, so truncated app data would have shown request-ID bytes as
  a failure code; the shared `st1FailureCode()` helper (PR #50) adds a
  minimum-length guard. Recorded in SCR-004 §5.
- **Delegation:** implementation by the Sonnet-tier agent, spec and gate
  documents by the Haiku-tier scribe, per the tiered-staffing policy; all
  delegated diffs line-reviewed in the main session before commit.
- **Live end-to-end check before the gate:** booted the simulator at the
  baseline; the structured-compose application data for the SVS field values
  previewed byte-identical to V-TC-03 (seq 0), V-TC-04 (seq 1, after send)
  and V-TC-05 (seq 2); new DOM/JS served correctly.

## Open Items / Proposals

- ACT-001 (ECSS clause citations, before M2) remains open.
- ACT-002 (TEMU licensing, before M5) remains open.
- Next increment per SDP §4: M2 (TCP length-framed space-packet link, ICD §8).

## Milestone Checklist

| Item | Status | Evidence |
|---|---|---|
| Build + CI green | PASS | `./mvnw verify` green at baseline commit; CI green on merged PRs #49/#50. |
| In-scope SVS cases implemented and passing | PENDING | SIM-TC-034/035/036 are manual; verdicts to be recorded above. No M2 scope pulled in. |
| Test report + traceability matrix generated | PASS | This document (M1c-report.md). Traceability matrix above. |
| CI gate milestone raised to M1c | PASS | `.github/workflows/ci.yml` runs TraceabilityCheck `--milestone M1c --gate` (this PR). |
| Milestone tag proposed | PENDING | Tag `M1c` to be proposed on the merge commit of the verdict record (the gate-closing commit), per M1b precedent. |

---

Generated by AI tooling (Claude Code); reviewed and approved via PR before merge to master.
