# SatSim M1e Milestone Test Report

- Configuration item: SATSIM-M1E-REPORT, Issue 1
- Report date: 2026-07-19
- Baseline commit: acb6748 (Merge pull request #72 from cmoellmann/feat/m1e-repo-link-mobile)
- Generated from: Surefire test results + JaCoCo coverage; assembled by AI tooling, reviewed and approved via PR

## Environment

| Item | Value |
|---|---|
| Java | OpenJDK Runtime Environment (build 21.0.11+10-1-24.04.2-Ubuntu) |
| Maven | 3.9.11 (from .mvn/wrapper/maven-wrapper.properties) |
| OS | Linux 6.17.0-35-generic |

## Scope

M1e per SDP §4 comprises a single change request, implemented in one milestone
gate:

**SCR-007** — Repository link and mobile usability package (PR #71 spec,
PR #72 implementation), frontend-only: a permanent, clearly labelled link
from the console header to the public source repository (new requirement
SIM-REQ-UI-015), and layout reflow down to narrow mobile viewports —
stacked header, compose form and log controls, no page-level horizontal
scrolling, wide log content confined to its own container (new requirement
SIM-REQ-UI-016). Desktop presentation unchanged (SIM-TC-038 remains
valid). No ICD impact — the space-link contract and the §8 web API are
unchanged.

## M1e Exit Criteria vs. Status

| Criterion | Status | Evidence |
|---|---|---|
| Repository link verified (SIM-TC-039) | PASS | Verdict recorded in §"Manual Test Verdicts" below: pass, 2026-07-19, C. Möllmann. Supporting evidence: pre-merge headless-browser screenshots at 360 px and 1440 px viewports showing the labelled link in both presentations (PR #72). |
| Mobile usability checklist verified (SIM-TC-040) | PASS | Verdict recorded below: pass, 2026-07-19, C. Möllmann. Supporting evidence: pre-merge headless-browser screenshots at 360 px with a populated log — stacked header/compose/controls, log table scrolling inside its card, compact rows (PR #72). |
| SIM-TC-038 unaffected (desktop presentation unchanged) | PASS | No desktop rule touched (all M1e CSS inside the ≤ 640 px media query plus the header link); pre-merge 1440 px screenshot unchanged against the M1d presentation. |
| Existing automated suite green | PASS | `./mvnw verify` green at the baseline commit: 130 tests, 0 failures (tables below); CI green on merged PR #72. |
| SRS M1e-scope requirements all traced+passed | PASS | Traceability matrix below; TraceabilityCheck M1e gate: 0 findings → OK. SIM-REQ-UI-015/016 are M-verified; verdicts pass. |

Per the M1b–M1d precedent, the gate record closes with the commit that
records the manual verdicts; the `M1e` tag is proposed on that merge commit.

## Test Results Summary

M1e changes no Java production or test code (frontend static resources and
documents only); the automated suite is unchanged from M1d and re-run green
at the M1e baseline.

### Per-Module Test Classes

#### pus-core

| Test Class | Tests | Failures | Errors | Skipped | Verdict |
|---|---|---|---|---|---|
| org.satsim.pus.crc.Crc16CcittTest | 8 | 0 | 0 | 0 | PASS |
| org.satsim.pus.ccsds.PrimaryHeaderTest | 6 | 0 | 0 | 0 | PASS |
| org.satsim.pus.time.CucTimeTest | 12 | 0 | 0 | 0 | PASS |
| org.satsim.pus.tc.TcSecondaryHeaderTest | 7 | 0 | 0 | 0 | PASS |
| org.satsim.pus.tc.TcPacketTest | 7 | 0 | 0 | 0 | PASS |
| org.satsim.pus.tm.TmSecondaryHeaderTest | 4 | 0 | 0 | 0 | PASS |
| org.satsim.pus.tm.TmPacketTest | 7 | 0 | 0 | 0 | PASS |
| org.satsim.pus.ReferenceVectorDecodeTest | 1 | 0 | 0 | 0 | PASS |
| org.satsim.pus.St1ReferenceVectorTest | 1 | 0 | 0 | 0 | PASS |
| org.satsim.pus.St3ReferenceVectorTest | 1 | 0 | 0 | 0 | PASS |
| org.satsim.pus.st3.HkCodecsTest | 24 | 0 | 0 | 0 | PASS |
| **pus-core subtotal** | **78** | **0** | **0** | **0** | **PASS** |

#### simulator

| Test Class | Tests | Failures | Errors | Skipped | Verdict |
|---|---|---|---|---|---|
| org.satsim.sim.obsw.LoopbackTargetTest | 14 | 0 | 0 | 0 | PASS |
| org.satsim.sim.time.ManualSimulationClockTest | 4 | 0 | 0 | 0 | PASS |
| org.satsim.sim.time.SimulationSchedulerTest | 4 | 0 | 0 | 0 | PASS |
| org.satsim.sim.PusChainTest | 9 | 0 | 0 | 0 | PASS |
| org.satsim.sim.DeterminismReplayTest | 1 | 0 | 0 | 0 | PASS |
| org.satsim.sim.web.HmiWebApiTest | 3 | 0 | 0 | 0 | PASS |
| org.satsim.sim.web.WebApiEndToEndTest | 3 | 0 | 0 | 0 | PASS |
| org.satsim.sim.St3HousekeepingTest | 7 | 0 | 0 | 0 | PASS |
| **simulator subtotal** | **45** | **0** | **0** | **0** | **PASS** |

#### sim-test-support

| Test Class | Tests | Failures | Errors | Skipped | Verdict |
|---|---|---|---|---|---|
| org.satsim.testsupport.trace.TraceabilityCheckTest | 7 | 0 | 0 | 0 | PASS |
| **sim-test-support subtotal** | **7** | **0** | **0** | **0** | **PASS** |

**Overall total: 130 tests, 0 failures, 0 errors, 0 skipped; elapsed 4.243 s (from `./mvnw verify` at the baseline commit).**

Data sources: `*/target/surefire-reports/` in the three modules.

## Code Coverage

### pus-core

Per SDP §2.1 tailoring, pus-core is the correctness-critical core (packet layer) and carries an indicative 80% line coverage target.

**Measured (from `pus-core/target/site/jacoco/jacoco.csv`):**

| Metric | Missed | Covered | Total | % |
|---|---|---|---|---|
| Line | 12 | 424 | 436 | 97.25% |
| Branch | 20 | 184 | 204 | 90.20% |

**Status:** PASS (both line and branch exceed the indicative 80% target; unchanged from M1d — M1e touches no pus-core code).

**Other modules (simulator, sim-test-support):** No formal coverage target per SDP §2.1 tailoring.

## Traceability Matrix

### SRS M1e Requirements and Verification

| Req ID | Title | Ver. | Scope | SVS Case(s) | Test Method(s) | Verdict |
|---|---|---|---|---|---|---|
| SIM-REQ-UI-015 | Clearly labelled link to the public source repository | M | M1e | SIM-TC-039 | Manual checklist (frontend) | PASS |
| SIM-REQ-UI-016 | Fully operable on narrow viewports down to 360 px CSS width | M | M1e | SIM-TC-040 | Manual checklist (frontend) | PASS |

Preceding-milestone requirements remain in scope and passing; their gate
records are the [M0 report](M0-report.md), [M1 report](M1-report.md),
[M1a report](M1a-report.md), [M1b report](M1b-report.md),
[M1c report](M1c-report.md), and [M1d report](M1d-report.md). The M0/M1/M1a
review verdicts (SIM-REQ-TIME-001, SIM-REQ-TIME-003) carry per the
cumulative verdict rule (ACT-004).

**TraceabilityCheck output** (per SDP §5, CI consistency gate):
```
Traceability check M1e (gate): 0 finding(s) -> OK
```

Source: `java -cp sim-test-support/target/classes org.satsim.testsupport.trace.TraceabilityCheck --root . --milestone M1e --gate`

## Manual Test Verdicts

Executed by the project lead against the M1e baseline frontend.

### SIM-TC-039 (repository link) — pass, 2026-07-19, C. Möllmann

Checklist per SVS: the console shows a clearly labelled link to the public
source repository, present on both a desktop viewport and a 360 px mobile
viewport; activating it leads to the repository start page
(github.com/cmoellmann/satsim). Verifies: SIM-REQ-UI-015.

### SIM-TC-040 (mobile usability checklist) — pass, 2026-07-19, C. Möllmann

Checklist per SVS at 360×740 CSS px: initial view and log-filled view show
no page-level horizontal scrolling; header (title, OBT clock, link status)
reflows without overlap or clipping; a ping with default ack flags can be
composed and sent, its four causally ordered rows appear; the raw hex of a
TM(3,25) default-SID report stays within the log card (scrolling within
it); an expanded detail view is readable without page-level horizontal
scrolling. Verifies: SIM-REQ-UI-016.

## Review Verdicts

No requirement entered review-verification scope at M1e (SIM-REQ-UI-015/016
are manually verified per the SRS). The M0/M1/M1a review verdicts carry
forward (recorded in the [M0 report](M0-report.md)); the ACT-004 verdict
gate remains active.

## Notes and Deviations

- **Delegated implementation, review-found spec defects:** the frontend
  chunk was implemented by the routine implementer agent (Sonnet) per the
  announce-first delegation protocol and matched its hand-off specification
  exactly. Review of the running artifact (headless-browser screenshots at
  360 px with a populated log) found two defects in the hand-off
  specification itself — hex wrapping inflating log-row heights, then
  min-content width propagation dragging the page beyond the viewport —
  fixed in review with two additional media-query rules and recorded in
  SCR-007 §5.
- **Pre-merge visual evidence:** PR #72 documents headless-Chromium
  screenshots at 360 px (populated log) and 1440 px (desktop regression)
  supporting, not replacing, the manual M verdicts above.

## Open Items / Proposals

- ACT-001 (ECSS clause citations, before M2) remains open.
- ACT-002 (TEMU licensing, before M5) remains open.
- Next increment per SDP §4: M2 (TCP length-framed space-packet link, ICD §8).

## Milestone Checklist

| Item | Status | Evidence |
|---|---|---|
| Build + CI green | PASS | `./mvnw verify` green at baseline commit; CI green on merged PRs #71/#72. |
| In-scope SVS cases implemented and passing | PASS | SIM-TC-039/040 manual pass (2026-07-19, C. Möllmann). No M2 scope pulled in. |
| Test report + traceability matrix generated | PASS | This document (M1e-report.md). Traceability matrix above. |
| CI gate milestone raised to M1e | PASS | `.github/workflows/ci.yml` runs TraceabilityCheck `--milestone M1e --gate` (this PR). |
| Milestone tag proposed | PASS | Tag `M1e` proposed on the merge commit of this gate record, per M1b–M1d precedent; the pushed tag is its own evidence. |

---

Generated by AI tooling (Claude Code); reviewed and approved via PR before merge to master.
