# SatSim M1g Milestone Test Report

- Configuration item: SATSIM-M1G-REPORT, Issue 1
- Report date: 2026-07-25
- Baseline commit: a7f1466 (Merge pull request #95 from cmoellmann/feat/m1g-tc-broadcast)
- Generated from: Surefire test results + JaCoCo coverage + recorded manual
  checklist; assembled by AI tooling, reviewed and approved via PR

## Environment

| Item | Value |
|---|---|
| Java | OpenJDK Runtime Environment (build 21.0.11+10-1-24.04.2-Ubuntu) |
| Maven | 3.9.11 (from .mvn/wrapper/maven-wrapper.properties) |
| OS | Linux 7.0.0-28-generic |

## Scope

M1g per SDP §4 comprises a single change request, implemented in two PRs
plus this gate record:

**SCR-009** — Shared-traffic console (PR #86 SCR + SDP, PR #94 ICD Issue 7
+ SRS + SVS, PR #95 implementation + SDD). Every ICD §8.1 injection —
structured or raw, decodable or not — is broadcast to all WebSocket
sessions as a new §8.2 frame kind `tc`, carrying the field contents of the
§8.1 response and correlated with it by the new `injectionId`. The web
console renders TC rows from that broadcast only, so each injection appears
exactly once and injections submitted elsewhere (another console, an MCP
operator gateway, a plain `curl`) are marked as remote. The gateway's ring
buffer admits the new kind and `get_packet_log` serves it, so an AI
operator sees other operators' commanding. **No space-link change (ICD
§2–§7), no reference vector touched, no new dependency.** New requirements
SIM-REQ-UI-017/018, amended SIM-REQ-MCP-003, new SVS cases SIM-TC-046
(automated) and SIM-TC-047 (manual).

## M1g Exit Criteria vs. Status

| Criterion | Status | Evidence |
|---|---|---|
| SIM-TC-046 passes (automated: passive WS session receives the `tc` frame for another client's injection, fields per §8.1; gateway `get_packet_log` returns the `tc` record) | PASS | `org.satsim.sim.mcp.McpGatewaySvsTest#injectionsAreBroadcastAsTcFramesAndServedByPacketLog`: two passive WS observers plus the gateway as a third, non-submitting observer; green in `./mvnw verify` (tables below). |
| SIM-TC-047 passes (manual: remote TC rows marked, no own-row duplication) | PENDING | §"Manual Checklist" below — awaiting execution and verdict by the project lead. |
| Amended SIM-TC-027..029 re-verified | PASS | `HmiWebApiTest` 3/3 green: SIM-TC-028 additionally asserts the `injectionId` contract of the §8.1 response, SIM-TC-029 the one `tc` frame per stimulus in the expected traffic. SIM-TC-027 unchanged by design — it injects nothing, so its expected traffic gains no `tc` frame (recorded in the SVS change log). |
| Existing automated suite green | PASS | `./mvnw verify` green at the baseline commit: 136 tests, 0 failures (tables below); CI green on merged PR #94/#95. |
| SRS M1g-scope requirements all traced+passed | PASS | Traceability matrix below; TraceabilityCheck M1g gate: 0 findings → OK. |

Per the M1b–M1f precedent, the gate record closes with the commit that
records the verdicts; the `M1g` tag is proposed on that merge commit.

## Test Results Summary

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
| org.satsim.sim.mcp.McpGatewaySvsTest | 6 | 0 | 0 | 0 | PASS |
| **simulator subtotal** | **51** | **0** | **0** | **0** | **PASS** |

#### sim-test-support

| Test Class | Tests | Failures | Errors | Skipped | Verdict |
|---|---|---|---|---|---|
| org.satsim.testsupport.trace.TraceabilityCheckTest | 7 | 0 | 0 | 0 | PASS |
| **sim-test-support subtotal** | **7** | **0** | **0** | **0** | **PASS** |

#### mcp-gateway

No own test tree: the module's validation tests (SIM-TC-041..046) run in
the `simulator` module (`McpGatewaySvsTest`), where the Spring test
context provides the deterministically driven simulator; the gateway under
test runs as a real child process (SDD §3.5).

**Overall total: 136 tests, 0 failures, 0 errors, 0 skipped (from `./mvnw verify` at the baseline commit).**

Data sources: `*/target/surefire-reports/` in the modules.

## Code Coverage

### pus-core

**Measured (from `pus-core/target/site/jacoco/jacoco.csv`):**

| Metric | Missed | Covered | Total | % |
|---|---|---|---|---|
| Line | 12 | 424 | 436 | 97.25% |
| Branch | 20 | 184 | 204 | 90.20% |

**Status:** PASS (exceeds the indicative 80% target; unchanged from M1f —
M1g touches no pus-core code).

**Other modules (simulator, sim-test-support, mcp-gateway):** No formal
coverage target per SDP §2.1 tailoring.

## Traceability Matrix

### SRS M1g Requirements and Verification

| Req ID | Title | Ver. | Scope | SVS Case(s) | Test Method(s) | Verdict |
|---|---|---|---|---|---|---|
| SIM-REQ-UI-017 | injectionId assignment, §8.2 `tc` broadcast, ordering before caused frames | T | M1g | SIM-TC-046 | McpGatewaySvsTest.injectionsAreBroadcastAsTcFramesAndServedByPacketLog | PASS |
| SIM-REQ-UI-018 | Console: one row per injection, remote marking, causal ordering | M | M1g | SIM-TC-047 | Manual checklist (below) | PENDING |

Amended in M1g scope: **SIM-REQ-MCP-003** (ring buffer serves `tm`,
`rejection` and `tc`) — re-verified by SIM-TC-043/044/046, all green.

Preceding-milestone requirements remain in scope and passing; their gate
records are the [M0](M0-report.md) … [M1f](M1f-report.md) reports. The
M0/M1/M1a review verdicts carry per the cumulative verdict rule (ACT-004).

**TraceabilityCheck output** (per SDP §5, CI consistency gate):
```
Traceability check M1g (gate): 0 finding(s) -> OK
```

Source: `java -cp sim-test-support/target/classes org.satsim.testsupport.trace.TraceabilityCheck --root . --milestone M1g --gate`
(the CI pin in `.github/workflows/ci.yml` moves from M1f to M1g with this
gate record).

## Manual Checklist

### SIM-TC-047 (remote TC rows without own-row duplication) — PENDING

Setup: simulator at `http://localhost:8090` (interactive 1:1 pacing), two
console windows W1 and W2 on it, plus a third-party injection from outside
any console.

| Step | Expected | Observed |
|---|---|---|
| Ping composed and sent in W1 | W1: exactly one TC row, unmarked; no second, remote-marked row for the same injection | *(to be recorded)* |
| Same injection observed in W2 | W2: exactly one TC row, visibly marked `remote` | *(to be recorded)* |
| Ping sent in W2 | mirrored result: unmarked in W2, `remote` in W1 | *(to be recorded)* |
| Injection from a client without a console (`curl` against `POST /api/tc`, or MCP `send_tc`) | marked `remote` in **both** windows | *(to be recorded)* |
| Causal ordering of the remote row (SIM-REQ-UI-014) | its TM(1,1)/TM(17,2)/TM(1,7) responses stand above it in the newest-first log | *(to be recorded)* |
| Detail view of a remote row | same decoded field breakdown as an own row | *(to be recorded)* |

Verdict, date and name are recorded here once the project lead has
executed the checklist; the gate record is merged only with the verdict in
place.

## Notes and Deviations

- **`injectionId` is a delta against the approved SCR.** SCR-009 §3
  foresaw an ICD change confined to §8.2; specifying the case showed that
  "own TCs appear exactly once" is not verifiable without a correlation
  handle between the §8.1 response and the broadcast frame. The
  alternative (client-side match on hex + OBT) was rejected as ambiguous
  when two clients inject byte-identical octets at the same simulated
  instant. Decision and rationale are recorded as SCR-009 §5 D-1; the ICD
  change is additive (no existing field changes meaning, preview
  unaffected).
- **SIM-TC-044 test adjustment.** The case polled the *unfiltered* packet
  log expecting exactly one record; from M1g the buffer additionally holds
  the `tc` record of the same injection. Its SVS pass criterion is
  kind-scoped ("exactly one rejection record"), so the test now asserts it
  with an explicit `kind=rejection` filter — criterion unchanged, now
  verified precisely. No SVS amendment required; recorded here for the
  audit trail.
- **Race resolved in the console, not in the ICD.** The simulator
  broadcasts before it responds, so a console can receive the frame for
  its own injection while its own POST is still in flight. The console
  holds frames of undecided origin back until its outstanding submissions
  have returned their ids, rather than mislabelling an own TC as remote
  (SDD §3.4).
- **Determinism unaffected.** `tc` frames are web-API artifacts like
  `time` and `rejection`; the byte-authoritative TM stream
  (SIM-REQ-TIME-005) is untouched — `DeterminismReplayTest` green.

## Open Items / Proposals

- **M1g tag proposed** on the merge commit of this gate record.
- Next increments, both approved and not yet built: **M1h** Command
  Authorization Gate foundation ([SCR-010](../scr/SCR-010-cag-foundation.md),
  [ADR-0007](../adr/DECISION-LOG.md)) and **M1i** its Category B assurance
  ([SCR-011](../scr/SCR-011-cag-assurance.md)). Their SDP §4 milestone rows
  are still outstanding and are due with the first M1h specification PR.
