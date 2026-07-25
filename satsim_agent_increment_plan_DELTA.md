> **STATUS: FOLDED IN (2026-07-25).** D-1 applied as ADR-0007 Erratum E1 + SRF §1a +
> SCR-010 §5 finding F-1 (agent = untrusted third-party, no criticality claim). D-2
> **deferred** as a named future extension (README roadmap; reference client would speak
> MCP; own SCR later) — not in M1h/M1i scope. See `satsim_agent_increment_plan.md` Rev 4
> §0-bis. This file is kept for provenance only; the controlled docs are authoritative.

# DELTA to the Agent-Operations Increment Plan

*Addendum to `satsim_agent_increment_plan.md` (Revision 2). The plan is already in the
Claude Code session and an SCR exists — fold this in as an SCR amendment or a follow-on SCR,
whichever the change-control convention prefers. Nothing below contradicts the plan; it
corrects one classification error and adds one configuration item.*

---

## D-1 — Correction: "the agent" in the plan is not our software

The plan classifies *"Agent client — Category D"* as if it were a configuration item of this
project. It is not. Today the agent is **Claude Code** (or any other MCP client): the agent loop
— compose messages + tool definitions, receive `tool_use`, execute, append result, iterate,
decide termination — runs entirely inside third-party software.

**Evidence: the repository contains no model call anywhere.** Our code is exclusively the *tool
side* — MCP server, ICD-as-resource, allowlist, TC budget, ops log.

### Consequence for classification

Software we did not write and cannot inspect **cannot be classified as a CI of this project**.
Under ECSS-Q-ST-80C it is **third-party / reused software** and belongs in the
**[Software Reuse File](docs/reuse-file.md)**, not in the CI list.

**Action:** add an SRF row for the MCP client (Claude Code) — version/scope, license/terms,
approval record, and a note that it is *not* verified by this project and carries no
criticality claim.

**This strengthens the containment argument rather than weakening it.** The untrusted component
is not merely unverified — it is not ours, is not inspectable, and never will be. Bounding a
third-party black box with a component verified to Category B is a more compelling demonstration
than bounding an agent we wrote ourselves.

### Consequence for the ADR

In the classification ADR, replace *"agent client (Cat D)"* with a two-part statement:

1. **The operator client is third-party software**, recorded in the SRF, unclassifiable by this
   project, treated as **untrusted by construction**.
2. **Optionally, a project-owned reference operator client exists (Cat D)** — see D-2 — which
   *can* be classified, and which exists partly to prove the gate does not depend on any
   particular client.

---

## D-2 — Add a project-owned reference operator client (Category D)

### Rationale

Three distinct reasons, none of which the current design satisfies:

1. **Client-agnostic conformance.** The project already holds the mirror-image principle for the
   OBSW side: the validation suite must pass unchanged against any conforming target
   (`SIM-REQ-LINK-003`) — *the target is a plug, not a partner*. The same property must hold on
   the operator side: **the CAG shall enforce identically against any conforming operator
   client.** With exactly one client in existence, that property is currently unverifiable.
   A second, independent client makes it demonstrable.
2. **A classifiable CI.** Gives the Category D side of the containment architecture an actual
   configuration item with requirements, tests and a classification rationale — instead of a
   classification asserted over software we don't own.
3. **Engineering competence (the original learning objective).** Owning the loop — tool
   definitions, `tool_use` handling, result marshalling, iteration guards, termination, failure
   modes — is what "multi-step tool orchestration" actually means. The MCP server half does not
   demonstrate it.

### Scope

Minimal by design. Java, in a thin module (`ops-client-ref` or similar):

- Direct Anthropic API call via `java.net.http.HttpClient` + Jackson — **no new dependencies**
- Tool definitions mirroring the CAG-facing operations; the ICD supplied as context
- The loop: call → `tool_use` → execute via CAG → append `tool_result` → repeat
- Guards: max iterations, per-run timeout, structured per-turn trace
- Remote model (API key via env var). **No local model, no GPU.** Cost for development-scale
  use is single-digit €; set a spend cap and ignore it.

**Explicitly out of scope:** framework adoption (LangChain/LangGraph). If a framework is ever
wanted for keyword value, port *after* the hand-written loop exists — the abstraction is learned
in an afternoon once the mechanism is understood.

### Protocol choice — deliberate simplification

The reference client **need not speak MCP.** Talking directly to the CAG's API is simpler and
teaches the same lesson.

- **Simple path (recommended start):** reference client → CAG API directly.
- **Stronger path (optional):** reference client speaks MCP, which demonstrates the
  client-agnostic property more convincingly (two independent MCP clients, identical enforcement).

Start simple; add MCP only if the conformance property is worth the extra work.

### New requirement to add

> **The CAG shall enforce identical authorization decisions and logging irrespective of the
> operator client used.** Verified by executing the authority-boundary cases (R4/R5/R7) against
> both the third-party MCP client and the project-owned reference client, with identical verdicts.

That is the operator-side twin of `SIM-REQ-LINK-003`, and it is the requirement the reference
client exists to make verifiable.

---

## D-3 — Effect on the plan's build order

Insert after the confirmation-inside-the-CAG step (plan §6 step 5), i.e. once the gate's
behaviour is final and worth conforming against:

| # | Step | Notes |
|---|---|---|
| 5a | **SRF entry for the third-party MCP client** | Paperwork, minutes. Unblocks the corrected ADR wording (D-1). |
| 5b | **Reference operator client (Cat D)** | Hand-written loop, direct CAG API, guards + per-turn trace. Own SRS/SVS entries as a Cat D CI. |
| 5c | **Client-agnostic conformance test** | Authority-boundary cases run against *both* clients; identical verdicts required. Closes the new requirement. |

The eval harness (plan §8) then gains a second execution path for free: agent scenarios can be
run through the reference client, which is scriptable and reproducible in CI — something a
human-driven MCP client session is not. **That is a material side benefit: it makes the
scenario suite automatable.**

---

## D-4 — Summary of what changes

| Plan says | Corrected to |
|---|---|
| Agent client is a Cat D CI of this project | Third-party MCP client → **SRF entry**, untrusted by construction, no criticality claim |
| One operator client | Two: third-party (untrusted) + **project-owned reference client (Cat D)** |
| Containment demonstrated against our own agent | Containment demonstrated against a **third-party black box** — stronger claim |
| Gate correctness verified with one client | **Client-agnostic enforcement** as an explicit, verified requirement |
| Agent scenarios exercised manually | Scenarios **automatable** via the scriptable reference client |
