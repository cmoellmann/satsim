# SatSim M1f Milestone Test Report

- Configuration item: SATSIM-M1F-REPORT, Issue 1
- Report date: 2026-07-20
- Baseline commit: 365d3ae (Merge pull request #82 from cmoellmann/docs/sdd-mcp-diagrams)
- Generated from: Surefire test results + JaCoCo coverage + recorded MCP
  operator demo; assembled by AI tooling, reviewed and approved via PR

## Environment

| Item | Value |
|---|---|
| Java | OpenJDK Runtime Environment (build 21.0.11+10-1-24.04.2-Ubuntu) |
| Maven | 3.9.11 (from .mvn/wrapper/maven-wrapper.properties) |
| OS | Linux 6.17.0-35-generic |

## Scope

M1f per SDP §4 comprises a single change request, implemented in one
milestone gate:

**SCR-008** — MCP operator gateway (PR #78 SCR + SDP, PR #80 ICD Issue 6
§8.4 + SRS + SVS, PR #81 implementation, PR #82 SDD architecture views).
New Spring-free `mcp-gateway` module: the TM/TC interface exposed as an
MCP server over stdio for AI operator clients — five PUS-level tools
(`send_tc`, `preview_tc`, `send_raw_tc`, `get_packet_log`, `await_tm`),
three resources (ICD text, OBT, gateway state), gateway-enforced authority
bounds (service/subtype allowlist, session TC budget) and an OBT-stamped
JSONL ops log. Pure ground-segment client of the ICD §8.1/§8.2 web API
behind the `WebApiLink` adapter seam; no wire-format change, no reference
vector touched. New requirements SIM-REQ-MCP-001…006, new automated SVS
cases SIM-TC-041…045, new dependency MCP Java SDK 2.0.0 (MIT, SRF
updated).

## M1f Exit Criteria vs. Status

| Criterion | Status | Evidence |
|---|---|---|
| SIM-TC-041..045 pass (scripted MCP client, incl. byte-identical V-TC-01 injection via `send_tc`) | PASS | Automated: `org.satsim.sim.mcp.McpGatewaySvsTest`, 5/5 green in `./mvnw verify` (tables below). The scripted `McpSyncClient` spawns the gateway as a real child process over its production stdio transport. |
| Existing automated suite green | PASS | `./mvnw verify` green at the baseline commit: 135 tests, 0 failures (tables below); CI green on merged PR #81/#82. |
| SRF updated with the MCP SDK dependency closure | PASS | [docs/reuse-file.md](../reuse-file.md): MCP Java SDK 2.0.0 (mcp-core + mcp-json-jackson2, MIT) with full transitive closure, rule-8 approval via merge of PR #81. |
| Manual demo recorded: AI agent performs the ping chain and the TM(1,8) recovery scenario via MCP | PASS | §"Recorded MCP Operator Demo" below: headless Claude (Sonnet) operator session against a live simulator, evidence = operator report + gateway ops log. |
| SRS M1f-scope requirements all traced+passed | PASS | Traceability matrix below; TraceabilityCheck M1f gate: 0 findings → OK (with the SIM-REQ-MCP-002 review verdict recorded in this report). |

Per the M1b–M1e precedent, the gate record closes with the commit that
records the verdicts; the `M1f` tag is proposed on that merge commit.

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
| org.satsim.sim.mcp.McpGatewaySvsTest | 5 | 0 | 0 | 0 | PASS |
| **simulator subtotal** | **50** | **0** | **0** | **0** | **PASS** |

#### sim-test-support

| Test Class | Tests | Failures | Errors | Skipped | Verdict |
|---|---|---|---|---|---|
| org.satsim.testsupport.trace.TraceabilityCheckTest | 7 | 0 | 0 | 0 | PASS |
| **sim-test-support subtotal** | **7** | **0** | **0** | **0** | **PASS** |

#### mcp-gateway

No own test tree: the module's validation tests (SIM-TC-041..045) run in
the `simulator` module (`McpGatewaySvsTest`), where the Spring test
context provides the deterministically driven simulator; the gateway under
test runs as a real child process (SDD §3.5).

**Overall total: 135 tests, 0 failures, 0 errors, 0 skipped (from `./mvnw verify` at the baseline commit).**

Data sources: `*/target/surefire-reports/` in the modules.

## Code Coverage

### pus-core

**Measured (from `pus-core/target/site/jacoco/jacoco.csv`):**

| Metric | Missed | Covered | Total | % |
|---|---|---|---|---|
| Line | 12 | 424 | 436 | 97.25% |
| Branch | 20 | 184 | 204 | 90.20% |

**Status:** PASS (exceeds the indicative 80% target; unchanged from M1e —
M1f touches no pus-core code).

**Other modules (simulator, sim-test-support, mcp-gateway):** No formal
coverage target per SDP §2.1 tailoring.

## Traceability Matrix

### SRS M1f Requirements and Verification

| Req ID | Title | Ver. | Scope | SVS Case(s) | Test Method(s) | Verdict |
|---|---|---|---|---|---|---|
| SIM-REQ-MCP-001 | MCP server: ICD §8.4 tools/resources, byte-identical structured send path | T | M1f | SIM-TC-041, -042, -044 | McpGatewaySvsTest.serverContract / byteExactSendPath / rejectionVisibility | PASS |
| SIM-REQ-MCP-002 | Web-API-only access behind link-adapter seam, Spring-free, no simulator internals | R | M1f | — (design constraint) | Review (verdict below) | PASS |
| SIM-REQ-MCP-003 | Ring buffer, cursor-paged get_packet_log, blocking await_tm | T | M1f | SIM-TC-043, -044 | McpGatewaySvsTest.blockingWaitAndLogPaging / rejectionVisibility | PASS |
| SIM-REQ-MCP-004 | ICD / OBT / gateway-state resources | T | M1f | SIM-TC-041 | McpGatewaySvsTest.serverContract | PASS |
| SIM-REQ-MCP-005 | Allowlist + session TC budget enforcement | T | M1f | SIM-TC-045 | McpGatewaySvsTest.authorityBoundsAndOpsLog | PASS |
| SIM-REQ-MCP-006 | JSONL ops log: every tool invocation incl. denied, with outcome and OBT | T | M1f | SIM-TC-045 | McpGatewaySvsTest.authorityBoundsAndOpsLog | PASS |

Preceding-milestone requirements remain in scope and passing; their gate
records are the [M0](M0-report.md) … [M1e](M1e-report.md) reports. The
M0/M1/M1a review verdicts carry per the cumulative verdict rule (ACT-004).

**TraceabilityCheck output** (per SDP §5, CI consistency gate):
```
Traceability check M1f (gate): 0 finding(s) -> OK
```

Source: `java -cp sim-test-support/target/classes org.satsim.testsupport.trace.TraceabilityCheck --root . --milestone M1f --gate`

## Review Verdicts

### SIM-REQ-MCP-002 (ground-client boundary) — reviewed-PASS, 2026-07-20, C. Möllmann

The mcp-gateway interacts with the simulator exclusively through the
public web API per ICD §8.1/§8.2, behind the `WebApiLink` adapter seam,
contains no Spring types and has no access to simulator internals.
Evidence reviewed:

- `mcp-gateway` compile/runtime dependency tree (PR #81) contains no
  Spring artifact: pus-core, mcp-core, mcp-json-jackson2 and their
  Jackson/Reactor/SLF4J transitives only.
- All simulator interaction is confined to `RestWsLink` (JDK
  `java.net.http` against `/api/tc`, `/api/tc/preview`, `/api/tm`); no
  other code path reaches the simulator (SDD §3.5 class diagram, PR #82).
- The SVS tests run the gateway as a separate OS process, demonstrating
  the process-level boundary end to end.

Verdict recorded via review and merge of this gate-record PR.

## Recorded MCP Operator Demo

Full record: [M1f-demo-transcript.md](M1f-demo-transcript.md) (operator's
report unedited, plus the complete gateway ops log as the audit trail).

Setup: headless Claude Code session, model `claude-sonnet-5` —
deliberately a *routine-tier* model, with the ICD MCP resource as its only
source of spacecraft knowledge; five satsim MCP tools + resource reads,
nothing else. Simulator with interactive 1:1 pacing; gateway spawned over
its production stdio transport (default allowlist `3,17`, budget 100).

| Pass phase | Result |
|---|---|
| Ping chain | Agent-composed TC(17,1) ack 0b1001 reproduced **V-TC-06 byte-for-byte** (the agent itself noted the match); full verification chain TM(1,1) → TM(17,2) → TM(1,7) received and quoted with request IDs, ordering per ICD §10.2 |
| Induced fault | TC(3,5) for uncreated SID 2 → **TM(1,8), failure code 0x0006 UNKNOWN_SID**, named per ICD §10.4; agent's diagnosis correctly cited the §9.3 atomicity rule (no housekeeping state changed) |
| Recovery | Agent derived the TC(3,1) application-data layout from the ICD (`0002 0000 07D0 0002 0001 0002`: SID 2, 2000 ms, HK-P001+HK-P002), **previewed before sending**, created and enabled SID 2, confirmed TM(3,25) at t0+interval and decoded SID and parameter values — noting the expected interleaving with the default SID 1 reports |

The ops log shows 14 tool invocations, all `ok`, each OBT-stamped —
including the preview-before-send discipline the tasking asked for. The
demo satisfies the M1f exit criterion "AI agent performs the ping chain
and the TM(1,8) recovery scenario via MCP".

## Notes and Deviations

- **The demo is evidence, not verification.** Per SCR-008, the AI agent is
  not the unit under test; requirement verdicts rest exclusively on the
  scripted-client SVS cases and the review verdict above. The recorded
  demo documents the intended use end to end.
- **Demo fault scenario vs. SVS:** the demo's TM(1,8) path (UNKNOWN_SID
  via TC(3,5)) complements SIM-TC-033, which covers the ST[3] semantic
  error classes with byte-exact vectors; no new verification claim is
  derived from the demo.
- **Implementation findings** (recorded in SCR-008 §5 and PR #81): stdout
  hygiene on the stdio transport (logging must go to stderr) and the
  Jackson 3 annotations clash that motivated the Jackson 2 binding choice.

## Open Items / Proposals

- ACT-001 (ECSS clause citations, before M2) remains open.
- ACT-002 (TEMU licensing, before M5) remains open.
- Next increment per SDP §4: M2 (TCP length-framed space-packet link, ICD §8).

## Milestone Checklist

| Item | Status | Evidence |
|---|---|---|
| Build + CI green | PASS | `./mvnw verify` green at baseline commit; CI green on merged PRs #78/#80/#81/#82. |
| In-scope SVS cases implemented and passing | PASS | SIM-TC-041..045 automated, green. No M2 scope pulled in (TCP link untouched; gateway on REST/WS per SCR-008). |
| Test report + traceability matrix generated | PASS | This document (M1f-report.md). Traceability matrix above. |
| CI gate milestone raised to M1f | PASS | `.github/workflows/ci.yml` runs TraceabilityCheck `--milestone M1f --gate` (this PR). |
| Milestone tag proposed | PASS | Tag `M1f` proposed on the merge commit of this gate record, per M1b–M1e precedent. |

---

Generated by AI tooling (Claude Code); reviewed and approved via PR before merge to master.
