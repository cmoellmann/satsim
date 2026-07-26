# SatSim M1h Milestone Test Report

- Configuration item: SATSIM-M1H-REPORT, Issue 1
- Report date: 2026-07-26
- Baseline commit: d6408fb (Merge pull request #99 from cmoellmann/feat/m1h-command-authorization-gate)
- Generated from: Surefire test results + JaCoCo coverage + recorded review
  verdict; assembled by AI tooling, reviewed and approved via PR

## Environment

| Item | Value |
|---|---|
| Java | OpenJDK Runtime Environment (build 21.0.11+10-1-24.04.2-Ubuntu) |
| Maven | 3.9.11 (from .mvn/wrapper/maven-wrapper.properties) |
| OS | Linux 7.0.0-28-generic |

## Scope

M1h per SDP §4 comprises a single change request, implemented in two PRs
plus this gate record:

**SCR-010** — Command Authorization Gate foundation (PR #88 plan + ADR-0007 +
SCR, PR #89 disposition, PR #90 SRF erratum, PR #98 ICD Issue 8 + SDP + SRS +
SVS, PR #99 implementation + SDD). The new `ops-cag` module is a distinct
configuration item engineered to the ECSS **Category B technical bar**
(SDP §1.1) inside the otherwise Category D product. It decodes every
telecommand, classifies it by content into an authority tier, and decides
forward / reject / hold — fail-closed on anything it cannot classify. The
authorization decision moved out of the gateway's `Authority`, which retains
only the session TC budget. State-changing telecommands are held in-gate and
forwarded only against a confirmation recorded through a channel that is not
reachable from MCP. The ops log carries the decision, its reason and the tier.
**No space-link change (ICD §2–§7), no reference vector touched, no new
third-party dependency.** New requirements SIM-REQ-CAG-001…006, amended
SIM-REQ-MCP-005, new SVS cases SIM-TC-048…052 (all automated), amended
SIM-TC-044.

### What this increment fixes

ADR-0007 §4 recorded the defect that drove it: before M1h the only barrier
holding a state-changing telecommand for human confirmation was the *MCP
client's* permission prompt. That prompt belongs to third-party software. A
different client, or one configured to auto-allow, removed the barrier silently
and the gateway never noticed. From M1h the barrier is inside the trusted
perimeter and does not depend on which client is attached or how it is set up —
because no MCP tool records confirmations at all.

## M1h Exit Criteria vs. Status

| Criterion | Status | Evidence |
|---|---|---|
| SIM-TC-048…052 pass (scripted MCP client, no AI in the loop) | PASS | `org.satsim.sim.mcp.McpGatewaySvsTest` — five cases, each driving the gateway as a real child process over its production stdio transport; green in `./mvnw verify` (tables below). |
| SIM-TC-044 passes in its amended form | PASS | `McpGatewaySvsTest#rejectionVisibility`: V-NEG-01 injected over the ICD §8.1 REST interface, which the gate does not mediate; the rejection frame is recorded and served by `get_packet_log` as before. |
| SIM-TC-041…043, 045…047 pass unchanged | PASS | `McpGatewaySvsTest` 11/11 green. SIM-TC-045's specification is unchanged; its implementation follows the new budget semantics (SCR-010 §5 F-8). |
| SIM-REQ-CAG-001 review verdict recorded | PASS | §"Review Verdicts" below — reviewed-PASS, recorded via review and merge of this gate-record PR. |
| Existing automated suite green | PASS | `./mvnw verify` green at the baseline commit: **160 tests, 0 failures** (tables below); CI green on merged PR #98/#99. |
| SRS M1h-scope requirements all traced+passed | PASS | Traceability matrix below; TraceabilityCheck M1h gate: 0 findings → OK (with the SIM-REQ-CAG-001 review verdict recorded in this report). |

**Explicitly not a criterion here.** The Category B *verification* bar — hazard
analysis, software FMEA over the command path, derived safety requirements,
100 % statement and decision coverage, and the full robustness suite — is
**M1i** scope per [SCR-011](../scr/SCR-011-cag-assurance.md). M1h delivers the
gate; M1i discharges the bar. Nothing in this report should be read as claiming
that evidence exists yet.

Per the M1b–M1g precedent, the gate record closes with the commit that records
the verdicts; the `M1h` tag is proposed on the merge commit of this PR.

## Test Results Summary

### Per-Module Test Classes

#### pus-core

| Test Class | Tests | Failures | Errors | Skipped | Verdict |
|---|---|---|---|---|---|
| org.satsim.pus.ReferenceVectorDecodeTest | 1 | 0 | 0 | 0 | PASS |
| org.satsim.pus.St1ReferenceVectorTest | 1 | 0 | 0 | 0 | PASS |
| org.satsim.pus.St3ReferenceVectorTest | 1 | 0 | 0 | 0 | PASS |
| org.satsim.pus.ccsds.PrimaryHeaderTest | 6 | 0 | 0 | 0 | PASS |
| org.satsim.pus.crc.Crc16CcittTest | 8 | 0 | 0 | 0 | PASS |
| org.satsim.pus.st3.HkCodecsTest | 24 | 0 | 0 | 0 | PASS |
| org.satsim.pus.tc.TcPacketTest | 7 | 0 | 0 | 0 | PASS |
| org.satsim.pus.tc.TcSecondaryHeaderTest | 7 | 0 | 0 | 0 | PASS |
| org.satsim.pus.time.CucTimeTest | 12 | 0 | 0 | 0 | PASS |
| org.satsim.pus.tm.TmPacketTest | 7 | 0 | 0 | 0 | PASS |
| org.satsim.pus.tm.TmSecondaryHeaderTest | 4 | 0 | 0 | 0 | PASS |
| **Total** | **78** | **0** | **0** | **0** | **PASS** |

#### ops-cag (new in M1h)

| Test Class | Tests | Failures | Errors | Skipped | Verdict |
|---|---|---|---|---|---|
| org.satsim.cag.CommandAuthorizationGateTest | 12 | 0 | 0 | 0 | PASS |
| org.satsim.cag.FileConfirmationChannelTest | 7 | 0 | 0 | 0 | PASS |
| **Total** | **19** | **0** | **0** | **0** | **PASS** |

Untraced unit tests (SDP §5 two-level scheme): engineering hygiene on the gate
in isolation, against an in-memory `ConfirmationChannel`. The *traced*
validation of the same behaviour is SIM-TC-048…052 against the real gateway
process. These 19 are not the Category B coverage evidence — that is M1i.

#### simulator

| Test Class | Tests | Failures | Errors | Skipped | Verdict |
|---|---|---|---|---|---|
| org.satsim.sim.DeterminismReplayTest | 1 | 0 | 0 | 0 | PASS |
| org.satsim.sim.PusChainTest | 9 | 0 | 0 | 0 | PASS |
| org.satsim.sim.St3HousekeepingTest | 7 | 0 | 0 | 0 | PASS |
| org.satsim.sim.mcp.McpGatewaySvsTest | 11 | 0 | 0 | 0 | PASS |
| org.satsim.sim.obsw.LoopbackTargetTest | 14 | 0 | 0 | 0 | PASS |
| org.satsim.sim.time.ManualSimulationClockTest | 4 | 0 | 0 | 0 | PASS |
| org.satsim.sim.time.SimulationSchedulerTest | 4 | 0 | 0 | 0 | PASS |
| org.satsim.sim.web.HmiWebApiTest | 3 | 0 | 0 | 0 | PASS |
| org.satsim.sim.web.WebApiEndToEndTest | 3 | 0 | 0 | 0 | PASS |
| **Total** | **56** | **0** | **0** | **0** | **PASS** |

#### sim-test-support

| Test Class | Tests | Failures | Errors | Skipped | Verdict |
|---|---|---|---|---|---|
| org.satsim.testsupport.trace.TraceabilityCheckTest | 7 | 0 | 0 | 0 | PASS |
| **Total** | **7** | **0** | **0** | **0** | **PASS** |

`mcp-gateway` carries no test class of its own by design: its validation is
end-to-end against a running simulator and therefore lives in the `simulator`
test tree (SDD §3.5).

**Grand total: 160 tests, 0 failures, 0 errors, 0 skipped.**

### Coverage (pus-core, indicative target 80 % line — SDP §2.1)

| Counter | Covered | Missed | Ratio |
|---|---|---|---|
| Line | 424 | 12 | **97.2 %** |
| Instruction | 2756 | 54 | 98.1 % |
| Branch | 184 | 20 | 90.2 % |
| Method | 69 | 2 | 97.2 % |
| Class | 15 | 0 | 100 % |

Unchanged by M1h — the increment adds no `pus-core` code. Coverage is measured
on `pus-core` only per the SDP §2.1 tailoring; `ops-cag` gets a 100 %
statement-and-decision target in **M1i**, not here.

## Traceability Matrix

### SRS M1h Requirements and Verification

| Req ID | Title | Ver. | Scope | SVS Case(s) | Test Method(s) | Verdict |
|---|---|---|---|---|---|---|
| SIM-REQ-CAG-001 | CAG as a self-contained, dependency-free configuration item | R | M1h | — (design constraint) | Review (verdict below) | PASS |
| SIM-REQ-CAG-002 | Decision on decoded content alone, not on caller-supplied metadata | T | M1h | SIM-TC-048 | McpGatewaySvsTest.classificationFollowsDecodedContent | PASS |
| SIM-REQ-CAG-003 | Fail-closed: undecodable, table gap, outside allowlist → reject | T | M1h | SIM-TC-049 | McpGatewaySvsTest.failsClosedOnAnythingItCannotClassify | PASS |
| SIM-REQ-CAG-004 | Authority tiers; ST[17] benign write, ST[3] state-changing | T | M1h | SIM-TC-048 | McpGatewaySvsTest.classificationFollowsDecodedContent | PASS |
| SIM-REQ-CAG-005 | In-gate hold; no forward without a recorded out-of-band confirmation | T | M1h | SIM-TC-050, SIM-TC-051 | McpGatewaySvsTest.stateChangingCommandIsHeldAndNothingIsForwarded, McpGatewaySvsTest.recordedConfirmationForwardsExactlyOnce | PASS |
| SIM-REQ-CAG-006 | Gate decision, reason and tier logged per injection | T | M1h | SIM-TC-052 | McpGatewaySvsTest.gateDecisionsAreLoggedAsEvidence | PASS |

Amended in M1h scope: **SIM-REQ-MCP-005** (the gateway submits every injection
to the gate, retains the session TC budget, holds no authorization logic) —
re-verified by SIM-TC-045, green.

Preceding-milestone requirements remain in scope and passing; their gate
records are the [M0](M0-report.md) … [M1g](M1g-report.md) reports. The
M0/M1/M1a review verdicts carry per the cumulative verdict rule (ACT-004).

**TraceabilityCheck output** (per SDP §5, CI consistency gate):
```
Traceability check M1h (gate): 0 finding(s) -> OK
```

Source: `java -cp sim-test-support/target/classes org.satsim.testsupport.trace.TraceabilityCheck --root . --milestone M1h --gate`
(the CI pin in `.github/workflows/ci.yml` moves from M1g to M1h with this
gate record).

## Review Verdicts

### SIM-REQ-CAG-001 (Command Authorization Gate as a self-contained CI) — reviewed-PASS, 2026-07-26, C. Möllmann

The gate is a self-contained configuration item in its own module, depending
only on the JDK and `pus-core`, free of Spring types, MCP protocol types,
network access, filesystem access beyond its confirmation channel, and
dynamically loaded policy. The small, verifiable surface is not a stylistic
preference — it is part of the Category B argument (ADR-0007 C1), because the
claim being made is that this component can be verified where the operator
client cannot.

Evidence reviewed:

- `ops-cag/pom.xml` declares exactly one compile dependency, the internal
  `pus-core` module; `sim-test-support` and JUnit 5 are test-scoped. No
  third-party component is added, recorded in the SRF change log (PR #99).
- No Spring, MCP or Jackson type appears anywhere under
  `ops-cag/src/main/java`: the imports are `java.*` plus `org.satsim.pus.*`.
  The gateway depends on the gate, never the reverse.
- Filesystem access is confined to `FileConfirmationChannel` and `CagConfirm`;
  `CommandAuthorizationGate` itself is specified against the
  `ConfirmationChannel` interface and is exercised in unit test against an
  in-memory implementation, which is what keeps the requirement honest rather
  than nominal.
- No thread is created and no wall clock is read (Checkstyle's forbidden-API
  rules pass; the channel is polled on demand from `decide`).
- `ClassificationTable` is static and total as written — no configuration file,
  no default tier — so extending the authorized telecommand set requires a
  reviewed source change.
- Authorization has one entry point on the gateway side (`Gateway.authorize`),
  through which both send tools pass, so no route to the simulator bypasses the
  gate.

Verdict recorded via review and merge of this gate-record PR.

## Notes on the Increment

### Decisions taken during M1h, with their reasoning

Four deltas against the approved SCR-010 impact analysis were surfaced rather
than absorbed silently, and are recorded in full in
[SCR-010 §5](../scr/SCR-010-cag-foundation.md):

- **F-2** — the ICD *is* affected: the gate adds a third observable outcome to
  the §8.4 boundary that SIM-REQ-MCP-001 binds. Amended in ICD Issue 8,
  confined to §8.4.
- **F-3** — ADR-0007 C3 (fail-closed) contradicted the approved M1f baseline,
  which deliberately let undecodable raw octets through `send_raw_tc` so the
  spacecraft's own rejection path stayed reachable. Resolved by the project lead
  in favour of strict C3. The loss is narrower than it appears: the gate rejects
  on undecodability, not on spacecraft-level validity, so V-NEG-02 (unsupported
  PUS version) still decodes, classifies as the ping it is, forwards, and is
  refused by the spacecraft under ICD §10.2. SIM-TC-049 pins both halves of
  that contrast.
- **F-4** — the confirmation channel. Neither the ADR nor the SCR said how a
  confirmation reaches the gate, and the choice decides whether the increment
  fixes the §4 finding or reproduces it: a confirmation exposed as an MCP tool
  is one the operator client can call itself. Decided out-of-band for M1h;
  console-mediated confirmation deferred to the README roadmap on scope, not
  merit.
- **F-6/F-7/F-8** — confirmations bound to the decoded command identity rather
  than to exact octets (the sequence count would expire them for unrelated
  reasons); the project's first SpotBugs exclusion, scoped and justified; and
  SIM-TC-045's implementation following the new budget semantics with its
  specification unchanged.

### The property worth naming

SIM-TC-050 asserts the barrier **negatively**: `tools/list` must still return
exactly the five ICD §8.4 tools. The check is not that the operator client
declined to confirm its own command, but that the interface gives it no way to
express a confirmation at all. A rule an agent is asked to respect and a
capability an agent does not have are different engineering objects, and only
the second survives a client that does not cooperate.

### Limits of the claim (ADR-0007 C8, not restated more favourably)

The gate bounds one failure mode: an operator proposing a command type it has
no authority for. It does **not** bound a valid command issued at the wrong
moment, a harmful sequence of individually authorized commands, a correct
command derived from misread telemetry, an omission, or a confirmation obtained
on false pretenses. Nor does it bound an operator host with arbitrary shell
execution on the same machine, which can run `CagConfirm` as easily as a human
can — containment holds for the **tool path**. Independence of verification,
which Category B normally requires, is not attainable in a single-person project
and is recorded in SDP §2.1 as a deviation with **no compliance claim**.

---

Generated by AI tooling (Claude Code); reviewed and approved via PR before merge to master.
