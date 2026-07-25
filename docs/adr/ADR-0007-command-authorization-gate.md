# ADR-0007 — Command Authorization Gate: per-CI criticality classification and containment

- Status: **Proposed** (awaiting project-lead disposition; see SCR-010)
- Date: 2026-07-25
- Deciders: Project lead (with AI-assisted trade-off analysis)
- Configuration item: SATSIM-ADR-0007
- Related: ADR-0001 (process isolation, `SpaceLink`), ADR-0002 (strict PUS-C),
  ADR-0004 (`SimulationClock` / OBT), SCR-008 (MCP operator gateway, M1f),
  SCR-009 (shared-traffic console, M1g)
- Supersedes: —

## Context and Problem Statement

SCR-008 (M1f) added the MCP operator gateway: a Category D AI agent can now
submit telecommands to the spacecraft through the same web API a human operator
uses. SCR-008 §3 anticipated this decision — *"if review judges the
operator-boundary decisions architecture-shaping, an ADR-0007 may be proposed."*
It is, and this is that ADR.

The whole SatSim product is classified **Category D** per ECSS-Q-ST-80C (SDP §1):
a ground development tool whose outputs are always re-verified by downstream OBSW
V&V, so its failures carry negligible safety/mission consequence. That rationale
holds for the simulator and the human-operated console. It does **not** transfer
cleanly to a path where a **non-deterministic** component (an LLM agent) issues
commands: the agent's reasoning has no structural coverage, cannot be verified by
the project's normal means, and yet now sits on the command chain.

Two facts sharpen the problem:

1. **The agent is unverifiable by nature.** There is no structural-coverage story
   for model reasoning. Asserting any assurance level for the agent itself would
   be a false claim.
2. **Today's safety barrier is in the wrong place.** The only thing that currently
   holds a state-changing command for human confirmation is the *MCP client's*
   permission prompt (Claude Code). Verified: `mcp-gateway/src` contains no
   confirm/hold/approval logic. A different client, or one set to auto-allow,
   removes the barrier silently and the gateway never notices. The gateway's own
   controls are a service allowlist (`Authority`, default ST[3]/ST[17]) and a
   session TC budget — coarse, and keyed on operator-declared service, not on the
   actual command content.

The question this ADR settles: **how do you place a non-deterministic component
into a system that must not issue unauthorized commands, without either
(a) pretending the agent is verifiable, or (b) raising the entire Category D
product to an assurance level its nature does not warrant?**

## Decision Drivers

- DA1 — Authority-boundary behaviour (what may be commanded, and what must be held
  for confirmation) must be **deterministic and verifiable**, not a probabilistic
  property of the agent.
- DA2 — The command decision point carries the entire consequence of the agent's
  advice; assurance effort must land **there**, proportionate to that consequence.
- DA3 — The Category D justification for the rest of the product must remain
  intact; agentic commanding must not silently inflate the whole product's
  assurance burden.
- DA4 — The safety argument must not rest on MCP-client UX (the §4 finding above).
- DA5 — This is a **single-person project**: Category B *independence* (ISVV,
  organizational separation) is not attainable and must be stated honestly, not
  faked.
- DA6 — The agent host (Claude Code) has arbitrary shell execution; on the same
  machine it can bypass any tool-path gate (e.g. `curl POST /api/tc`). The scope
  of any containment claim must be stated honestly, not overclaimed.

## Considered Options

- **Option A — Per-configuration-item classification with a Command Authorization
  Gate (CAG).** Keep the agent Category D (advisory). Extract a small, deterministic,
  dependency-free **CAG** that decodes every TC, classifies it by content, decides
  (forward / reject / HOLD-for-confirmation), and logs — engineered to the
  Category B *technical* bar. One product, per-CI criticality.
- **Option B — Status quo (all Category D).** Keep the gateway's allowlist + budget
  and leave confirmation to the MCP client. Rejected: the safety argument rests on
  client UX (DA4); the trusted decision point is neither content-aware (DA1) nor
  verified (DA2); nothing changes about the §4 finding.
- **Option C — Split off a second Category B product** with its own SDP and document
  set. Rejected: SCR-008 already placed the gateway inside the SatSim baseline; a
  split forks the document set, is heavier, and misdescribes what exists — one
  product, one baseline. Per-CI classification (Option A) is the ECSS-correct,
  lighter instrument.

## Analysis

The governing principle: **you do not verify a non-deterministic component — you
bound it with one you can verify.** Criticality is *contained*, not asserted, by
concentrating the consequence in a component small enough to carry a Category B
technical bar (decode, classify, decide, log) and paying that price **only there**.

This is honest only if the limits of containment are stated. Containment reduces
criticality **only for the failure modes the container actually catches** — it is
not a blanket transfer (see Consequences C8).

Option A also fixes the §4 finding structurally: the confirmation barrier moves
**inside** the trusted component, so the safety argument no longer depends on which
MCP client is attached or how it is configured.

## Decision

**Option A is adopted.** SatSim remains one Category D product; within it, the
**Command Authorization Gate (CAG)** is classified and engineered to the **Category
B technical bar**, recorded as a per-configuration-item classification in the SDP.
The AI agent remains **Category D** (advisory, unverifiable by nature).

**Framing (binding wording for SDP and derived documents):** "Category B" here
names the **engineering rigor** applied to the CAG — hazard analysis, software
FMEA over the command path, derived safety requirements, 100 % statement **and**
decision coverage, and a robustness suite. The Category B **independence**
requirement is **not satisfiable** in a single-person project and is recorded as an
explicit **deviation with no compliance claim** (C7). No certification claim is
made.

## Consequences

### Required design provisions (binding on implementation)

- **C1 — The CAG is its own configuration item: a thin, dependency-free module**
  (proposed name `ops-cag`), JDK-only, no Spring (as `pus-core` is). The small,
  verifiable surface *is* part of the Category B argument. The gateway keeps
  transport/MCP concerns; the CAG keeps decode-classify-decide-log. No agent logic,
  no formatting, no retries, no dynamic policy loading in the CAG.
- **C2 — Content-based authorization.** Because the MCP tools are generic, the CAG
  MUST NOT key on tool names. It decodes the TC (reusing `pus-core`
  `TcPacket.decode` → `TcSecondaryHeader`) and classifies by service/subtype, and
  where needed by parameters. This mirrors mission-control practice: authorization
  by command criticality.
- **C3 — Fail closed.** Undecodable, unclassifiable, or table-gap TCs are
  **rejected**, never forwarded.
- **C4 — Authority tiers** (upgrading the current allowlist):
  - *read / observation* → forward, log;
  - *benign write* (ST[17] ping) → forward, log with attribution;
  - *state-changing write* (ST[3] interval / structure changes) → **HOLD**; return
    a structured "confirmation required" result; forward **only** after a recorded
    confirmation.
- **C5 — The confirmation barrier lives INSIDE the CAG.** The CAG holds
  state-changing commands and refuses to forward without a recorded confirmation of
  its own, with defined restart behaviour. The MCP client's prompt MAY remain as a
  convenience layer but MUST NOT be what the safety argument rests on. (Fixes the
  §4 finding.)
- **C6 — Every decision is logged** — attribution, classification, tier, outcome,
  reason — extending the existing OBT-stamped ops log (`OpsLog`). The log is
  evidence. Wall-clock reads remain prohibited in simulation logic (ADR-0004); the
  CAG is ground infrastructure and stamps OBT from §8.2 frames.
- **C7 — Category B verification bar.** Hazard analysis → software FMEA over the
  command path → derived safety requirements → 100 % statement **and** decision
  coverage → robustness suite (malformed, truncated, unknown subtype, table gap,
  oversized) exercising every error path. Traced as SRS requirements (proposed
  SIM-REQ-CAG-xxx) with `@TestCase`/`@Requirement` annotations.

### C8 — Limits of containment (binding; do not overclaim)

Contained by a content-based CAG: **the agent proposing a command type it has no
authority for.** Fully bounded, deterministically. For that failure class,
Category D for the agent is sound.

**Not contained:**

| Failure mode | Why the gate misses it |
|---|---|
| **Bypass via arbitrary execution** ⚠ | The current agent host (Claude Code) has shell access and can `curl POST /api/tc` directly. Structural containment is **not achievable** on the same machine against a general-purpose agent; it holds for the *tool path* only. It disappears for a network-exposed instance where the agent has no shell on the server — which makes that endpoint a **security** increment, not a convenience. |
| Valid command at the wrong moment | ST[3] reconfiguration is benign in cruise, possibly not during a maneuver. The gate sees a permitted subtype and forwards it, correctly by its own rules. |
| Harmful sequence of authorized commands | Each passes; the aggregate does not. (The TC budget is a crude partial mitigation.) |
| Correct command from misread telemetry | The agent misdiagnoses and proposes the textbook recovery for the wrong fault. Authorized, wrong. |
| Omission | A gate cannot catch what was never sent. |
| Confirmation on false pretenses | The human's picture of spacecraft state comes from the agent's narration; a misreporting agent can walk a human into confirming a damaging command. Mitigated — not closed — by the client showing the raw tool call, and further by an independent, trusted telemetry path. SCR-009's shared-traffic console (broadcast TM/TC independent of the agent) is arguably such a path. |

**What a flight deployment would additionally require:** context-aware
authorization (spacecraft mode, not type alone), sequence/rate limits, a trusted
telemetry path for confirmations, and an agent host without arbitrary execution.

### C9 — Independence deviation (honest, not a compliance claim)

Category B normally requires independent verification (ISVV / testing by someone
other than the developer / organizational separation). A one-person project cannot
satisfy this. Recorded in the SDP tailoring matrix as an explicit **deviation, not
a tailoring**: *"Independence requirements per [clause] are not satisfiable in a
single-person project. Recorded as a deviation. No compliance claim is made for
this requirement."* Separately, *AI-mediated independence* MAY be run as a
documented **experiment** (a verification agent with no access to the
implementation, working only from the requirements), stated plainly as **not
ISVV** — correlated failure modes (same model family), no organizational
separation.

### C10 — Evaluation is two regimes.

Authority-boundary behaviour is **not** a probabilistic property. Anything touching
C3/C4/C5 is enforced by the CAG (deterministic, 100 % required, ordinary SVS cases,
no tolerance). The agent's pass rate on operator scenarios is a **quality** metric,
not a **safety** argument. Safety comes from the gate; the agent's numbers describe
usability.

### Positive
- The safety argument no longer depends on MCP-client UX.
- Category B rigor is exercised on a component small enough to hold in one's head —
  rehearsal for the flight-software follow-on named in the roadmap.
- Criticality containment is *demonstrated* (coverage + FMEA), not asserted.

### Negative / risks
- R1 — Extracting the CAG is a refactor of the M1f gateway; regression risk on the
  existing SVS cases (SIM-TC-041..045). Mitigation: extract behind the current
  behaviour first, then tighten.
- R2 — The honest limitation statements (C8, C9) invite the reading that
  containment is weak. They are the opposite: they define exactly where the claim
  holds, which is what makes it credible.

## Notes

- **Human-factors caveat:** automation bias is well documented — operators confirm
  what the machine proposes, especially under time pressure after a run of correct
  suggestions. A confirmation step is a real barrier only with independent
  information *and* a realistic opportunity to dissent.
- **Cross-domain note:** EASA's assistive-AI concepts rest on the classical system
  and the human retaining authority; the scrutiny falls on whether that authority
  is *effective* or nominal.
- This ADR spans two increments: **M1h** (SCR-010) implements C1–C6; **M1i**
  (SCR-011) discharges the C7 verification bar plus the operator-eval harness, the
  bypass test (documenting C8), and the C9 independence experiment.
