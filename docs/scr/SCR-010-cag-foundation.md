# SCR-010 — Command Authorization Gate (Cat B CI): extract, content-based classification, in-gate confirmation (new increment M1h)

- Status: Proposed (drafted 2026-07-25; awaiting project-lead disposition)
- Date: 2026-07-25
- Originator: project lead (C. Möllmann); drafted by AI assistant per SDP §6
- Affected configuration items: SATSIM-SDP, SATSIM-ADR (ADR-0007 +
  DECISION-LOG), SATSIM-SRS, SATSIM-SVS, SATSIM-SDD, CLAUDE.md
- Design basis: [ADR-0007](../adr/ADR-0007-command-authorization-gate.md)
  (Proposed with this SCR); increment plan `satsim_agent_increment_plan.md`
  Rev 3 (non-normative handover; the decisions here and in ADR-0007 govern
  where they differ)

## 1. Change description

New increment **M1h**: turn the existing MCP gateway's coarse authority controls
(service allowlist + session budget, `Authority`) into a verifiable **Command
Authorization Gate (CAG)** — a small, deterministic, dependency-free configuration
item classified and engineered to the ECSS **Category B technical bar** (ADR-0007),
inside the otherwise Category D product. This SCR covers the **foundation**; the
Category B verification bar, the operator-eval harness, and the bypass test follow
in **SCR-011 (M1i)**.

Scope of M1h:

1. **ADR-0007 (per-CI classification & containment).** Record the decision that
   the agent stays Category D and the CAG is engineered to the Category B technical
   bar; the containment-limits table and the independence deviation are stated
   verbatim in the ADR. Written before any CAG code.
2. **SDP: per-CI classification section + verification bar.** A subsection stating
   the CAG's Category B technical bar and review shape inside the Category D
   product, and recording the independence **deviation** in the §2.1 tailoring
   matrix (no compliance claim). §1 gains a per-CI classification note; the
   "ISVV: Not applicable — Cat D" row gains the CAG exception + deviation.
3. **Extract the CAG as its own module** (proposed `ops-cag`): thin, **JDK-only,
   Spring-free** (as `pus-core`). Authorization decision logic moves out of the
   gateway's `Authority` into the CAG; the gateway keeps transport/MCP concerns and
   calls the CAG. Behaviour is preserved first, then tightened (per ADR-0007 R1).
4. **Content-based classification table** (ADR-0007 C2/C3): decode every TC
   (`pus-core` `TcPacket.decode` → `TcSecondaryHeader`) and classify by
   service/subtype (and parameters where needed) into authority tiers.
   Fail-closed default: undecodable / unclassifiable / table-gap → reject.
5. **In-gate confirmation / HOLD** (ADR-0007 C4/C5): state-changing writes (e.g.
   ST[3] interval/structure changes) are held; the CAG returns a structured
   "confirmation required" result and forwards only after a recorded confirmation,
   with defined restart behaviour. **This is the core fix** — the confirmation
   barrier moves inside the trusted perimeter, off MCP-client UX.
6. **Extended ops log** (ADR-0007 C6): each entry gains the gate decision, its
   reason, and the tier; OBT stamping retained.

Planned specification entries (rule-3 proposals, tabled in the follow-up spec PR):
SRS group **SIM-REQ-CAG-001…006** — CAG as a distinct Cat B CI; content-based
decode-and-classify; fail-closed rejection; authority tiers; in-gate HOLD /
recorded-confirmation (no forward without it); gate-decision logging.
SVS cases **SIM-TC-048…052** (automated, driven by a *scripted* MCP client, no AI
in the loop): classification correctness per service/subtype; reject-on-unknown /
fail-closed on malformed/truncated input; state-changing TC is held (no forward)
until a recorded confirmation, then forwarded once; ops-log entry carries decision
+ reason + tier. The Category B coverage/FMEA/robustness evidence is **SCR-011
(M1i)** scope, not M1h.

Explicitly **not** in this SCR: the AI agent is not the unit under test (per
SCR-008); the Cat B verification artifacts (hazard analysis, software FMEA, 100 %
statement+decision coverage, full robustness suite), the operator-eval harness, the
direct-REST bypass test, and the AI-mediated independence experiment — all
**SCR-011 (M1i)**.

## 2. Rationale

- **Fixes a real defect (ADR-0007 §4 finding).** Today the only barrier holding a
  state-changing command for human confirmation is the MCP *client's* permission
  prompt — verified: no confirm/hold/approval logic exists in `mcp-gateway/src`. A
  different client, or one set to auto-allow, removes it silently. For any safety
  argument that is the wrong location; the barrier must live in the gateway.
- **Honest criticality containment.** Rather than raising the whole Category D
  product, per-CI classification concentrates the Category B rigor on the small,
  deterministic component that carries the command-chain consequence — the correct,
  lighter ECSS instrument (ADR-0007 Option A vs C).
- **SCR-008 anticipated it.** SCR-008 §3 recorded that operator-boundary decisions,
  if architecture-shaping, would be captured as ADR-0007. Review judges them so.
- **Session-sized.** M1h is the foundation (extract + classify + confirm + log);
  the assurance bar (M1i) is a separate increment, keeping each to one session.
- **Reuse, no new dependency.** The CAG reuses `pus-core` decoding; JDK-only,
  Spring-free. No third-party dependency is added (SRF unaffected).

## 3. Impact analysis

| CI / area | Impact |
|---|---|
| SDP §1 / §2.1 | New per-CI classification note in §1 (product Cat D; CAG engineered to the Cat B technical bar). §2.1 tailoring matrix: the "ISVV — Not applicable (Cat D)" row gains the CAG exception and the **independence deviation** (no compliance claim, ADR-0007 C9). |
| SDP §4 | New milestone row **M1h** inserted between M1g and M2 (label scheme per SCR-001). M2…M5 unchanged, shifted one increment later. (M1i is added by SCR-011.) |
| ADR | **ADR-0007 proposed** (per-CI classification & containment; §5 limits + independence deviation verbatim). New DECISION-LOG row. On disposition it becomes Accepted and immutable (rule 4). |
| ICD | **None.** No wire-format change, no reference vectors touched. The CAG operates on the existing §8.1/§8.4 contract; the "confirmation required" result is an MCP tool-result shape (gateway/CAG concern), documented in the SDD, not a §8 wire change. |
| SRS | New requirement group (rule-3 proposals, scope M1h): **SIM-REQ-CAG-001…006** as sketched in §1. Existing requirements unamended (SIM-REQ-MCP-* keep describing the gateway; the CAG requirements are new). |
| SVS | New automated cases **SIM-TC-048…052** (scope M1h) as sketched in §1. Existing SIM-TC-041..045 (gateway) retained; adjusted only if the extraction changes an observable tool result, recorded in the SVS change log. |
| CLAUDE.md | Module map gains **`ops-cag`** (dependency-free CAG CI). Rule 5 ("no Spring outside `simulator`") holds — `ops-cag` is Spring-free. Controlled-document change via the implementing PR (rule 7). |
| SRF (reuse file) | **No new third-party dependency.** `ops-cag` reuses `pus-core` (internal). Recorded for completeness in the implementing PR; no license situation to present (rule 8 n/a). |
| SDD | New module section at implementation: `ops-cag` structure, the classification table, the HOLD/confirmation state machine and restart behaviour, the gateway↔CAG seam, extended ops-log record shape. |
| TraceabilityCheck | No tool change: M1h follows the `M<n><letter>` ordinal scheme; CI pin stays M1g until the M1h gate. New SIM-REQ-CAG-* / SIM-TC-* rows flow through the existing annotation-driven matrix. |
| SPR register | None — evolution and a design-improvement (moving the confirmation barrier), not a nonconformance against a baseline that ever specified in-gate confirmation (SDP §2.4 demarcation). |
| README | Status/Highlights updated at the M1h gate (the CAG, the confirmation-barrier fix). Roadmap "next steps" bullet moves from idea toward increment. |
| Implementation code | `ops-cag` module + gateway refactor to call it; extended `OpsLog`. Implementation PR(s) follow approval and the follow-up spec PR (specification-level SCR). |

## 4. Disposition

- [ ] Approved — project lead (C. Möllmann), _pending_.

## 5. Findings during implementation

*(to be completed)*
