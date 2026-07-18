# SatSim M0 Milestone Test Report

- Configuration item: SATSIM-M0-REPORT, Issue 1
- Report date: 2026-07-18
- Baseline commit: 90059eb9732e1e6b105570272e7097cf8eedfc83 (Merge pull request #11 from cmoellmann/feat/m0-static-analysis)
- Generated from: Surefire XML test results + JaCoCo coverage; assembled by AI tooling, reviewed and approved via PR

## Environment

| Item | Value |
|---|---|
| Java | OpenJDK Runtime Environment (build 21.0.11+10-1-24.04.2-Ubuntu) |
| Maven | 3.9.11 (from .mvn/wrapper/maven-wrapper.properties) |
| OS | Linux 6.17.0-35-generic |

## M0 Exit Criteria vs. Status

| Criterion | Status | Evidence |
|---|---|---|
| Build + CI green | PASS | All three modules compile; Surefire reports all tests pass (0 failures, 0 errors). CI workflows (.github/workflows) verified via PR #11 merge. |
| V-anchors of ICD §7 pass (SIM-TC-001) | PASS | SIM-TC-001 (Crc16CcittTest#reproducesAllIcdSanityAnchors) PASS. All three ICD §7 sanity anchors verified: 0x29B1, 0x1D0F, 0x04A2. Traced to SIM-REQ-PUS-002. |
| ≥1 traced test | PASS | SIM-TC-001, SIM-TC-002, SIM-TC-010 all annotated with @TestCase and @Requirement; SIM-TC-014 self-check on annotations. TraceabilityCheck M0 gate: 0 findings -> OK. |

## Test Results Summary

### Per-Module Test Classes

#### pus-core

| Test Class | Tests | Failures | Errors | Skipped | Time (s) | Verdict |
|---|---|---|---|---|---|---|
| org.satsim.pus.crc.Crc16CcittTest | 8 | 0 | 0 | 0 | 0.033 | PASS |
| org.satsim.pus.ccsds.PrimaryHeaderTest | 6 | 0 | 0 | 0 | 0.156 | PASS |
| **pus-core subtotal** | **14** | **0** | **0** | **0** | **0.189** | **PASS** |

#### simulator

| Test Class | Tests | Failures | Errors | Skipped | Time (s) | Verdict |
|---|---|---|---|---|---|---|
| org.satsim.sim.obsw.LoopbackTargetTest | 8 | 0 | 0 | 0 | 0.011 | PASS |
| org.satsim.sim.time.ManualSimulationClockTest | 4 | 0 | 0 | 0 | 0.031 | PASS |
| **simulator subtotal** | **12** | **0** | **0** | **0** | **0.042** | **PASS** |

#### sim-test-support

| Test Class | Tests | Failures | Errors | Skipped | Time (s) | Verdict |
|---|---|---|---|---|---|---|
| org.satsim.testsupport.trace.TraceabilityCheckTest | 4 | 0 | 0 | 0 | 0.049 | PASS |
| **sim-test-support subtotal** | **4** | **0** | **0** | **0** | **0.049** | **PASS** |

**Overall total: 30 tests, 0 failures, 0 errors, 0 skipped; elapsed 0.280 s (regenerated from a clean `./mvnw clean verify` at the baseline commit).**

Data sources:
- pus-core: `/home/christian/repos/satsim/pus-core/target/surefire-reports/TEST-*.xml`
- simulator: `/home/christian/repos/satsim/simulator/target/surefire-reports/TEST-*.xml`
- sim-test-support: `/home/christian/repos/satsim/sim-test-support/target/surefire-reports/TEST-*.xml`

## Code Coverage

### pus-core

Per SDP §2.1 tailoring, pus-core is the correctness-critical core (packet layer) and carries an indicative 80% line coverage target.

**Measured (from `/home/christian/repos/satsim/pus-core/target/site/jacoco/jacoco.xml`):**

| Metric | Missed | Covered | Total | % |
|---|---|---|---|---|
| Line | 1 | 68 | 69 | 98.55% |
| Branch | 2 | 36 | 38 | 94.74% |

**Status:** PASS (both line and branch exceed the indicative 80% target).

**Other modules (simulator, sim-test-support):** No formal coverage target per SDP §2.1 tailoring (Category D; packet-layer correctness is the focus).

## Traceability Matrix

M0 scope per SDP §4: interface trio (SimulationClock, EmulatorControl, SpaceLink), loopback OBSW target, CRC + primary header codec.

### SRS M0 Requirements and Verification

| Req ID | Title | Ver. | Status | SVS Case(s) | Test Method(s) | Verdict |
|---|---|---|---|---|---|---|
| SIM-REQ-PUS-001 | Primary header encode/decode per ICD §2 | T | M0 | SIM-TC-002 | PrimaryHeaderTest#encodeDecodeRoundTripIsIdentity | PASS |
| SIM-REQ-PUS-002 | CRC-16 computation per ICD §7, three sanity anchors | T | M0 | SIM-TC-001 | Crc16CcittTest#reproducesAllIcdSanityAnchors | PASS |
| SIM-REQ-TIME-001 | All simulation components use SimulationClock; no wall-clock in logic | R+A | M0 | (review + analysis) | (see §5.1 review verdicts below) | REVIEWED-PASS |
| SIM-REQ-TIME-003 | Java scheduler sole time master; targets via EmulatorControl | R | M0 | (review) | (see §5.1 review verdicts below) | REVIEWED-PASS |
| SIM-REQ-TIME-004 | EmulatorControl grant/consume semantics | T | M0 | SIM-TC-010 | LoopbackTargetTest#grantConsumeContractOnLoopback | PASS |
| SIM-REQ-LINK-001 | Loopback target implements EmulatorControl and SpaceLink | T | M0 | SIM-TC-010 | LoopbackTargetTest#grantConsumeContractOnLoopback | PASS |
| SIM-REQ-QA-001 | Validation tests carry @TestCase + @Requirement annotations | A | M0 | SIM-TC-014 | TraceabilityCheckTest#detectsPlantedDefectsAndAcceptsCleanSet | PASS |
| SIM-REQ-QA-002 | CI gates on in-scope requirement coverage | A | M0 | SIM-TC-014 | TraceabilityCheckTest#detectsPlantedDefectsAndAcceptsCleanSet | PASS |

**TraceabilityCheck output** (per SDP §5, CI consistency gate):
```
Traceability check M0 (gate): 0 finding(s) -> OK
```

Source: `java -cp sim-test-support/target/classes org.satsim.testsupport.trace.TraceabilityCheck --root . --milestone M0 --gate`

## Review Verdicts

The following requirements carry an R (review) verification component per the SRS; the review verdicts below were given by the project lead against the recorded evidence (SDP §5).

### SIM-REQ-TIME-001 (R part) — reviewed-PASS, 2026-07-18, C. Möllmann

Requirement: "All simulation components shall obtain time exclusively from the SimulationClock abstraction; direct wall-clock access in simulation logic is prohibited." (R+A; the A part is the Checkstyle wall-clock gate, see below.)

Evidence reviewed:
- Zero wall-clock API usages in main sources (grep for System.currentTimeMillis/nanoTime, Instant.now/java.time now(), Clock.system*, new Date over simulator/src/main/java and pus-core/src/main/java: no matches).
- Build-enforced by Checkstyle forbidden-API checks (config/checkstyle/checkstyle.xml, ids WallClockBan*), bound to the verify phase in the root pom; negative-tested in PR #11 (planted violation fails the build).
- ManualSimulationClock advances only via caller-invoked advance(); LoopbackTarget receives time exclusively through grant(budget) arguments and contains no clock reads.
- Sanctioned exemptions: test sources and the future org.satsim.sim.pacing package (sole wall-clock user per ADR-0006), suppressible only via reviewed PR (config/checkstyle/suppressions.xml).

### SIM-REQ-TIME-003 — reviewed-PASS, 2026-07-18, C. Möllmann

Requirement: "The Java scheduler shall be the sole simulation time master; OBSW targets shall be controlled via EmulatorControl." (R.)

Evidence reviewed:
- ObswTarget extends EmulatorControl (simulator/src/main/java/org/satsim/sim/obsw/ObswTarget.java); all target interaction goes through that contract.
- LoopbackTarget advances simulated time only inside grant(budget) → Consumed(...) calls; no threads, timers, or executors exist in main sources.
- Grant/consume contract exercised by SIM-TC-010 (LoopbackTargetTest), passing.
- Note: the scheduler itself is M1 scope; at M0 the constraint holds by construction. Per ADR-0006, M5 sync-conformance tests (incl. SIM-TC-017) will convert much of this review scope to test verification.

## Open Items / Proposals

PROPOSAL (per CLAUDE.md rule 3, for M1): extend TraceabilityCheck so a milestone gate fails when an in-scope R-verified requirement has no recorded review verdict in the milestone report. Approved for proposal by C. Möllmann 2026-07-18; SRS/SVS entries to be drafted in M1.

SRF-OPEN-1 (LICENSE file choice) still open, see docs/reuse-file.md §3.

## Milestone Checklist

| Item | Status | Evidence |
|---|---|---|
| Build + CI green | PASS | Maven verify: all modules compile; Surefire: all tests pass. CI: latest PR #11 merged to master. |
| In-scope SVS cases implemented and passing | PASS | SIM-TC-001, SIM-TC-002, SIM-TC-010, SIM-TC-014 all PASS. No M1+ cases were pulled into M0. |
| Test report + traceability matrix generated | PASS | This document (M0-report.md). Traceability matrix in §4. |
| Milestone tag proposed | PENDING | To be created upon human approval of this report via PR review. |

---

Generated by AI tooling (Claude Code); reviewed and approved via PR before merge to master.
