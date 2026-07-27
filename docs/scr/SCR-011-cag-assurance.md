# SCR-011 — Command Authorization Gate (Cat B CI): Category B verification, operator-eval harness, bypass demonstration (new increment M1i)

- Status: Approved (proposal PR #91 merged 2026-07-25; disposition PR #92.
  SDP/SRS/SVS + the new CAG safety-analysis doc and implementation follow in
  later PRs per the SCR-008/009/010 pattern, and only after M1g and M1h)
- Date: 2026-07-25
- Originator: project lead (C. Möllmann); drafted by AI assistant per SDP §6
- Affected configuration items: SATSIM-SDP, SATSIM-SRS, SATSIM-SVS,
  **SATSIM-CAG-SSA** (new — CAG software safety analysis), SATSIM-SDD
- Design basis: [ADR-0007](../adr/ADR-0007-command-authorization-gate.md)
  (Accepted) — this SCR discharges its Category B verification bar (C7), the
  containment-limits demonstration (C8), the independence deviation + experiment
  (C9), and the two-regime evaluation policy (C10); increment plan
  `satsim_agent_increment_plan.md` Rev 4 §6 M1i steps 8–11.
- Depends on: **SCR-010 (M1h)** — the `ops-cag` module, content-based
  classification, in-gate confirmation and extended ops log must exist and be
  baselined before this increment's verification can bind.

## 1. Change description

New increment **M1i**: discharge the **Category B technical bar** on the
`ops-cag` configuration item delivered in M1h (SCR-010), formalize the
**operator-eval harness**, and demonstrate the honest **limits of containment**.
Per ADR-0007 C10 this is deliberately **two regimes**: everything touching the
authority boundary (ADR-0007 C3/C4/C5) is verified **deterministically at 100 %**;
the AI agent's scenario performance is reported as a **quality** metric, never as
a safety argument.

Scope of M1i:

1. **CAG software safety analysis** (new CI, `docs/safety/cag-safety-analysis.md`,
   SATSIM-CAG-SSA): a hazard analysis of the command path, a **software FMEA** over
   the CAG's decode→classify→decide→log chain (failure mode per step → local
   effect → command-path effect → detection/mitigation), and the **derived safety
   requirements** that fall out of it, traced to hazards. ADR-0007 C7.
2. **Category B verification of `ops-cag`**: **100 % statement and decision
   coverage** on the CAG module (JaCoCo branch coverage as the decision-coverage
   proxy; enforced as a build-gate threshold — configuration only, no new
   dependency), plus a **robustness suite** exercising every error path (malformed
   / undecodable, truncated header, unknown subtype, classification-table gap,
   oversized app data), each asserting fail-closed rejection + a logged decision.
   ADR-0007 C3/C7.
3. **Operator-eval harness** (`docs/eval/operator-eval.md` spec + runnable
   harness in the test tree): the eight scenarios of plan §8, formalizing the M1f
   demo transcript into a repeatable suite. Each scenario = fixed initial state →
   prompt → expected behaviour → **binary verdict per run**, reported as a **pass
   rate over N runs**. The **deterministic gate-enforcement assertions inside the
   scenarios** (scenario 3: CAG holds a state-changing TC, no forward without
   recorded confirmation; scenario 8: CAG refuses an escalation attempt) are
   ordinary 100 %-required SVS cases; the **agent pass rate** is recorded in an
   eval report, **not** a milestone gate criterion. ADR-0007 C10. Because the
   project-owned reference client is deferred (SCR-010 §5 F-1), the agent-in-the-
   loop runs use an external MCP client (headless, as in the M1f demo) and stay
   **out of the CI gate** (cost / network / non-determinism); the deterministic
   gate checks run scripted, no AI in the loop, in CI.
4. **Bypass demonstration** (ADR-0007 C8): an automated test that submits a TC
   **directly to the web API** (`POST /api/tc`), bypassing the gateway/CAG, and
   demonstrates it **succeeds** — documenting, rather than concealing, that
   structural containment holds only for the MCP tool path on a shared-host
   deployment. This is the honest scope statement made executable.
5. **AI-mediated independence experiment** (ADR-0007 C9): a verification agent
   with **no access to the implementation**, working only from the SRS/SVS/ICD,
   writing its own tests against the specification; recorded in
   `docs/eval/ai-independence-experiment.md`, stated plainly as **not ISVV**
   (correlated failure modes — same model family; no organizational separation).
   This is an **experiment**, not a compliance artifact, and makes **no
   independence claim**; the deviation stands (recorded in the SDP tailoring
   matrix at M1h).

Planned specification entries (rule-3 proposals, tabled in the follow-up spec PR):
SRS group **SIM-REQ-CAG-SAFE-001…005** — derived safety requirements from the
FMEA: (1) every state-changing TC is held pending a recorded confirmation and is
never auto-forwarded; (2) fail-closed on every decode/classify error path; (3)
no authorization decision is taken without an ops-log record (decision + reason +
tier); (4) confirmation state is not lost across a gateway restart in any way that
results in an unconfirmed forward; (5) **scope statement** — the containment
guarantee applies only to the gateway tool path; no containment is claimed against
direct web-API access (verified by the bypass demonstration). Plus **SIM-REQ-QA-004**
— the `ops-cag` CI shall achieve 100 % statement and decision coverage, enforced
at the milestone gate. SVS cases **SIM-TC-053…060** (all automated unless noted):
053 malformed/undecodable → reject+log; 054 truncated secondary header → reject;
055 unknown subtype → reject; 056 classification-table gap → reject; 057 oversized
app data → reject; 058 **bypass demonstration** (direct `POST /api/tc` succeeds);
059 operator-eval harness runs the suite and the deterministic gate assertions
(scenario 3 HOLD, scenario 8 escalation-refusal) pass; 060 coverage gate — ops-cag
100 % statement+decision (traces SIM-REQ-QA-004).

Explicitly **not** in this SCR: any change to the CAG's behaviour (that is M1h /
SCR-010 — M1i only verifies it); the project-owned reference operator client and
the client-agnostic conformance requirement (deferred future extension, SCR-010
§5 F-1); a network-exposed MCP endpoint (separate security increment).

## 2. Rationale

- **Discharges the Category B claim instead of asserting it.** M1h makes the gate
  real; M1i is where "engineered to the Category B technical bar" is *earned* — an
  FMEA behind the design, coverage reports in front of it, every error path tested.
  Without M1i the classification in ADR-0007 would be a promise, not evidence.
- **The honest artifacts are the point.** The bypass demonstration (C8) and the
  independence experiment framed as *not ISVV* (C9) are, per the plan §9, the most
  senior artifacts in the repository: they state exactly where the containment
  claim holds and where it stops. Making the bypass an executable test is stronger
  than a prose caveat.
- **Two regimes keep safety and usability separate.** Deterministic 100 % on the
  authority boundary; pass-rate reporting for the agent. This is the concrete
  answer to "how do you evaluate a system with a non-deterministic component" — you
  do not average away the safety property.
- **Session-sized and additive.** M1i adds documents and tests over a frozen M1h
  behaviour; no wire-format change, no CAG-behaviour change, no new dependency
  (JaCoCo already registered; the eval agent is external, recorded like the MCP
  client in SRF §1a).

## 3. Impact analysis

| CI / area | Impact |
|---|---|
| SDP §2.1 | Coverage-target row amended: add the per-CI target **100 % statement and decision coverage on `ops-cag`** (Cat B technical bar), alongside the existing indicative 80 % line on `pus-core`. The independence **deviation** recorded at M1h is referenced, unchanged. |
| SDP §4 | New milestone row **M1i** inserted after M1h (label scheme per SCR-001). M2…M5 unchanged, shifted one increment later. |
| SDP §5 | Note the **two-regime** evaluation (deterministic SVS at 100 % for the authority boundary; agent pass-rate as a reported quality metric, not a gate criterion) — ADR-0007 C10. |
| SRS | New **SIM-REQ-CAG-SAFE-001…005** (derived safety, scope M1i) + **SIM-REQ-QA-004** (coverage gate, scope M1i). SIM-REQ-CAG-001…006 (M1h) unamended. |
| SVS | New **SIM-TC-053…060** (scope M1i) as sketched in §1. |
| SATSIM-CAG-SSA (new) | New controlled document `docs/safety/cag-safety-analysis.md`: hazard analysis + software FMEA + derived-safety-requirement traceability. Registered in the document set (README / SDP §7). |
| ICD | **None.** No wire-format change, no reference vectors touched. |
| ADR | **None changed.** M1i discharges ADR-0007 C7/C8/C9/C10; ADR-0007 already names this increment. |
| SRF | **No new dependency.** JaCoCo (decision-coverage proxy) already registered. The external verification/eval agent is third-party operator-side software of the same nature as SRF §1a — recorded there, no criticality claim. |
| SDD | Light: note the eval-harness location and the `ops-cag` verification approach; no architecture change. |
| TraceabilityCheck | No tool change; new SIM-REQ-CAG-SAFE-* / SIM-REQ-QA-004 / SIM-TC-053..060 flow through the annotation-driven matrix. CI pin stays M1h until the M1i gate. |
| SPR register | None — planned verification of a baselined design (SDP §2.4 demarcation). |
| README | Status/Highlights updated at the M1i gate: Category B evidence (FMEA + coverage), the bypass demonstration, the AI-mediated-independence experiment; document set gains the safety analysis. |
| Implementation code | Robustness + gate tests, the operator-eval harness, the bypass test, and JaCoCo threshold config. No change to `ops-cag` behaviour. Implementation PR(s) follow approval and the follow-up spec PR. |

## 4. Disposition

- [x] Approved — project lead (C. Möllmann), 2026-07-25, via review and merge
      of proposal PR #91; disposition recorded in PR #92.

## 5. Findings during implementation

- **F-1 (from the FMEA, row FM-21) — ops-log record is not written before the
  injection.** `Gateway.tool(...)` records the ops-log line only after the tool
  body returns normally, and the injection happens inside that body. Two
  consequences: an injection that succeeds and then fails downstream is logged
  with no `gateDecision` at all (the wrapper replaces the outcome with a plain
  error result, whose decision is `null`), and a log-write failure after a
  successful injection loses the record entirely. SIM-REQ-CAG-SAFE-003 is
  satisfied on every path exercised by the suite, but by sequence rather than by
  construction. The affected code is `mcp-gateway` (Category D), not `ops-cag`.
  Fixing it means recording the decision *before* attempting the injection —
  a **behaviour change**, which SCR-011 §1 places outside M1i. **Disposition:
  raise as an SPR** against the M1h baseline (SDP §2.4) and fix in a follow-up
  increment.
- **F-2 (from the FMEA, row FM-19) — pending holds are unbounded.** Each
  re-submission of an unconfirmed state-changing telecommand adds a hold entry;
  nothing evicts them. The gateway's session TC budget bounds them indirectly,
  the gate itself imposes no bound. Availability concern (hazard H-5), not an
  authority one — the failure direction is "everything held", not "something
  forwarded". **Disposition: accepted risk R-3 for M1i.** A bound would change
  gate behaviour and needs its own SCR (recorded as proposal P-2 in the safety
  analysis).
- **F-3 — the impact analysis was wrong about "no tool change".** §3 states that
  the new IDs "flow through the annotation-driven matrix" with no
  `TraceabilityCheck` change. They do not. The parser's requirement-ID grammar
  was `SIM-REQ-[A-Z]+-\d+` — a **single** uppercase prefix segment — so the
  two-segment `SIM-REQ-CAG-SAFE-001…005` mandated by §1 did not match. The
  failure was silent in the worst way: the SRS rows were not recognized as
  requirement rows at all, so the SVS cases verifying them inherited the default
  scope M0 and were reported in scope of the **M1h** gate, which failed with
  seven spurious findings. **Disposition: fixed in the spec PR** by widening the
  grammar to `SIM-REQ-[A-Z]+(?:-[A-Z]+)*-\d+` in both the SRS-row and the
  free-text requirement-reference patterns. The requirement IDs are kept exactly
  as approved in §1; the parser was the thing that was wrong. Regression cover:
  the M1h gate returns to 0 findings and the M1i scope now resolves the eight new
  cases correctly.
- **P-1 (proposal, from FMEA row FM-01).** For `send_tc` the gate classifies the
  octets returned by the simulator's ICD §8.1 *preview* endpoint, while the
  injection re-encodes server-side — so a Category B decision takes its input
  from a Category D endpoint. It holds by construction today (same compose, same
  arguments, same process) and is asserted end-to-end by SIM-TC-048. Proposed as
  an explicit requirement rather than an inherited property; **not approved**,
  recorded per CLAUDE.md rule 3.
