# SCR-010 — Command Authorization Gate (Cat B CI): extract, content-based classification, in-gate confirmation (new increment M1h)

- Status: **Implemented** (proposal PR #88 merged 2026-07-25; disposition PR
  #89; specification PR #98 — ICD Issue 8 §8.4, SDP §1.1/§2.1/§4, SRS
  SIM-REQ-CAG-001…006, SVS SIM-TC-048…052; implementation PR #99 — `ops-cag`
  module, gateway refactor, SIM-TC-048…052, SDD §3.6. Deltas F-2…F-8 in §5.
  Gate record: [M1h test report](../test-reports/M1h-report.md))
- Date: 2026-07-25
- Originator: project lead (C. Möllmann); drafted by AI assistant per SDP §6
- Affected configuration items: SATSIM-SDP, SATSIM-ADR (ADR-0007 +
  DECISION-LOG), SATSIM-SRF (§1a, Erratum E1), SATSIM-SRS, SATSIM-SVS,
  SATSIM-SDD, CLAUDE.md
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
| ICD | *As dispositioned:* **None** — no wire-format change, no reference vectors touched; the "confirmation required" result was judged an MCP tool-result shape (gateway/CAG concern), documented in the SDD, not a §8 wire change. *At spec time this proved too narrow:* **ICD Issue 8** amends §8.4 only — see §5 **F-2** (the gate's three outcomes are observable at the §8.4 boundary, which SIM-REQ-MCP-001 binds) and **F-3** (the §8.4 carve-out permitting undecodable raw octets is withdrawn). Space link §2–§7, web API §8.1/§8.2 and all reference vectors remain untouched, and no §8.4 tool is added, renamed or removed. |
| SRS | New requirement group (rule-3 proposals, scope M1h): **SIM-REQ-CAG-001…006** as sketched in §1. Existing requirements unamended (SIM-REQ-MCP-* keep describing the gateway; the CAG requirements are new). |
| SVS | New automated cases **SIM-TC-048…052** (scope M1h) as sketched in §1. Existing SIM-TC-041..045 (gateway) retained; **SIM-TC-044 amended** at spec time (V-NEG-01 injected over the §8.1 REST path instead of `send_raw_tc`, coverage unchanged — see §5 F-3), SIM-TC-045 reviewed and left unchanged. Recorded in the SVS change log. |
| CLAUDE.md | Module map gains **`ops-cag`** (dependency-free CAG CI). Rule 5 ("no Spring outside `simulator`") holds — `ops-cag` is Spring-free. Controlled-document change via the implementing PR (rule 7). |
| SRF (reuse file) | **No new third-party dependency.** `ops-cag` reuses `pus-core` (internal). Recorded for completeness in the implementing PR; no license situation to present (rule 8 n/a). |
| SDD | New module section at implementation: `ops-cag` structure, the classification table, the HOLD/confirmation state machine and restart behaviour, the gateway↔CAG seam, extended ops-log record shape. |
| TraceabilityCheck | No tool change: M1h follows the `M<n><letter>` ordinal scheme; CI pin stays M1g until the M1h gate. New SIM-REQ-CAG-* / SIM-TC-* rows flow through the existing annotation-driven matrix. |
| SPR register | None — evolution and a design-improvement (moving the confirmation barrier), not a nonconformance against a baseline that ever specified in-gate confirmation (SDP §2.4 demarcation). |
| README | Status/Highlights updated at the M1h gate (the CAG, the confirmation-barrier fix). Roadmap "next steps" bullet moves from idea toward increment. |
| Implementation code | `ops-cag` module + gateway refactor to call it; extended `OpsLog`. Implementation PR(s) follow approval and the follow-up spec PR (specification-level SCR). |

## 4. Disposition

- [x] Approved — project lead (C. Möllmann), 2026-07-25, via review and merge
      of proposal PR #88; disposition recorded in PR #89.

## 5. Findings during implementation

- **F-1 (2026-07-25) — classification error in ADR-0007, corrected by erratum.**
  A delta review (from a parallel design session) caught that ADR-0007 as first
  drafted called the AI operator agent a "Category D" component of *this project*.
  That is a category error: the agent loop runs entirely inside third-party MCP
  client software (Claude Code today, any conforming client in principle) and the
  repository contains **no model call anywhere** (verified by source grep).
  Software this project did not write and cannot inspect is not a CI of this
  project — under ECSS-Q-ST-80C it is third-party software, recorded in the SRF,
  **untrusted by construction, no criticality claim**. Disposition: **erratum in
  place** on ADR-0007 (**Erratum E1**), permissible because the ADR is Accepted
  but not yet baselined at the M1h tag and no decision is reversed; new SRF §1a
  row + change-log entry added. The correction *strengthens* the containment
  argument (bounding a non-inspectable black box). A project-owned **reference
  operator client** (a classifiable Cat D CI that would make client-agnostic CAG
  enforcement verifiable — the operator-side twin of SIM-REQ-LINK-003, speaking
  MCP) was considered and **deferred as a named future extension** (README
  roadmap; would enter via its own SCR), not pulled into M1h/M1i scope.

- **F-2 (spec PR, 2026-07-26) — the ICD is affected after all: §8.4 amended
  (ICD Issue 8).** §3 of this SCR dispositioned "ICD: None", reasoning that the
  confirmation-required result is a tool-result shape belonging in the SDD.
  Writing the specification showed that reasoning does not survive contact with
  SIM-REQ-MCP-001, which binds the gateway to offer "exactly the tools and
  resources of ICD §8.4 **with the semantics defined there**". The gate changes
  those semantics observably: §8.4 today documents two outcomes for an injection
  (injected, or "a violating call returns an MCP tool error and injects
  nothing"), and the CAG introduces a third — held, nothing injected, a token
  returned. Leaving that out of §8.4 would either falsify SIM-REQ-MCP-001 or
  push the operator's contract into a descriptive document (the SDD), where an
  external MCP client implementer would never look. Disposition: **ICD Issue 8,
  confined to §8.4** — the three outcomes tabled, the gateway-state resource
  extended with pending holds, and the confirmation channel declared explicitly
  outside the contract and unreachable through MCP. Delta vs §3, no wire-format
  change: §2–§7, §8.1, §8.2 and every reference vector are untouched, and the
  §8.4 tool and resource *sets* are unchanged.

- **F-3 (spec PR, 2026-07-26) — ADR-0007 C3 contradicts the M1f baseline over
  undecodable raw octets; resolved in favour of C3.** ADR-0007 C3 requires that
  undecodable telecommands be "rejected, never forwarded". ICD §8.4 (Issue 6)
  and SIM-REQ-MCP-005 specify the opposite for `send_raw_tc`: raw injection is
  "deliberately without gateway-side validation of the octets (negative paths
  reachable)", and undecodable octets "are permitted, they exercise the §6.3
  rejection path". Verified in code: `Gateway.java` leaves `service`/`subtype`
  null when `TcPacket.decode` throws, and `Authority.vetInjection(null, null)`
  permits. The conflict is not abstract — **SIM-TC-044** is a passing M1f case
  built on the permissive behaviour. Both directions were put to the project
  lead on 2026-07-26 with their costs; **strict C3 was chosen**: the gate never
  forwards octets it could not classify. Consequences, all in this PR: ICD §8.4
  withdraws the carve-out (Issue 8); SIM-REQ-MCP-005 is amended; SIM-TC-044 is
  amended to inject V-NEG-01 over the §8.1 REST interface, which the gate does
  not mediate — the case keeps exactly the coverage it had (a rejection frame
  observed on the §8.2 stream is recorded and served by `get_packet_log`), only
  its injection path changes. The loss is narrower than it first appears:
  decodable-but-invalid telecommands still reach the spacecraft, so V-NEG-02
  (unsupported PUS version) classifies as TC(17,1), forwards, and yields its
  TM(1,2) exactly as before — `TcSecondaryHeader.decode` deliberately does not
  enforce `pusVersion == 2`. Only octets that fail to decode at all are now
  refused on the MCP path. SIM-TC-049 verifies both halves of that contrast.

- **F-4 (spec PR, 2026-07-26) — the confirmation channel: out-of-band channel
  chosen; console-mediated confirmation deferred.** Neither ADR-0007 C5 nor §1
  of this SCR states *how* a human confirmation reaches the gate, and the choice
  decides whether the increment fixes the §4 finding or reproduces it. A
  confirmation exposed as an MCP tool is one the agent can call itself, which
  would put the barrier back behind the MCP client's permission prompt — the
  precise arrangement ADR-0007 DA4 rejects. Three options were put to the
  project lead on 2026-07-26; the decision is: **for M1h, confirmations arrive
  through a channel that is not an MCP tool** — the gate holds the injection and
  returns a token, and a human records the confirmation out-of-band, so the
  client has no means of expressing a confirmation at all (asserted negatively
  by SIM-TC-050, which requires that `tools/list` still return exactly the five
  §8.4 tools). Specified in SIM-REQ-CAG-005 without naming a transport, so the
  requirement outlives the mechanism. **Deferred, recorded on the README
  roadmap:** confirmation in the M1g shared-traffic console, where the human
  approves while looking at telemetry that did not pass through the agent —
  directly addressing the "confirmation on false pretenses" row of ADR-0007 C8,
  which already names that console as arguably such a path. Excluded from M1h on
  scope, not on merit: it needs a REST endpoint, an §8.2 frame kind and frontend
  work, making the gate depend on the simulator and the increment exceed one
  session. It pairs naturally with the reference operator client deferred in
  F-1, and would enter via its own SCR.

- **F-5 (spec PR, 2026-07-26) — editorial corrections to the SDP.** The SDP was
  amended for this increment and carried stale content that the new §1.1 would
  have contradicted: §7 listed ISVV artifacts as consciously dropped without the
  Category B exception (corrected, with the deviation restated); §3's module
  list never gained `mcp-gateway` at M1f (added, together with `ops-cag`); §7's
  status column named ICD Issue 1, ADR-0001…0006 and SCR-001…005 (corrected to
  Issue 8, ADR-0001…0007 and SCR-001…011). Editorial only — no disposition is
  changed.

- **F-6 (implementation PR, 2026-07-26) — a confirmation is bound to the
  command's identity, not to its exact octets.** `SIM-REQ-CAG-005` requires that
  a recorded confirmation release the telecommand it was issued for and no
  other, which needs a definition of "the same command". Binding to the literal
  octets was tried first and does not work: the CCSDS packet sequence count
  differs between the octets the gateway previews when the gate classifies them
  and the octets finally injected, and it moves again whenever another operator
  commands in between — so a confirmation would expire for reasons unrelated to
  what was confirmed. The gate therefore binds to the decoded **identity** —
  service, subtype, acknowledgement flags, application data (`CommandSummary`) —
  and deliberately excludes the sequence count, which is transport bookkeeping.
  This matches what a human actually confirms ("disable housekeeping structure
  1", not one counter value) and keeps `SIM-TC-051`'s "identical command"
  well-defined. The narrowing that matters is preserved: a confirmation for
  TC(3,7) cannot release TC(3,5), which `CommandAuthorizationGateTest`
  asserts directly.

- **F-7 (implementation PR, 2026-07-26) — first entry in the SpotBugs exclude
  filter.** `config/spotbugs/exclude.xml` had been deliberately empty since M0
  ("no findings are excluded by default"). The gate's injected
  `ConfirmationChannel` raises `EI_EXPOSE_REP2`, whose premise — a stored
  mutable object is data the class should have copied — does not hold here: the
  channel is a collaborator and its mutation *is* its contract (the gate
  discards a confirmation as it honours it, so one confirmation releases one
  telecommand). Copying it would break that. Injecting rather than constructing
  the channel inside the gate is required by `SIM-REQ-CAG-001` — direct
  filesystem access stays in `FileConfirmationChannel` and the gate remains
  testable against an in-memory channel. The alternative, `@SuppressFBWarnings`,
  would add a third-party annotation dependency and needs rule-8 approval for a
  static-analysis nicety. Exclusion scoped to the single class and field, with
  the justification in the file, so the pattern stays active everywhere else.
  Flagged explicitly for review as a change to a shared quality gate.

- **F-8 (implementation PR, 2026-07-26) — `SIM-TC-045`'s implementation had to
  change, its specification did not.** The M1f test consumed the session TC
  budget by injecting undecodable octets three times, which the allowlist then
  exempted. The gate now rejects those, and rejected calls consume no budget, so
  the budget half is exercised with the forwarding telecommand the case already
  describes. The two halves also need two gateway configurations, since one
  allowlist cannot both exclude and permit ST[17] — previously masked by the
  allowlist-exempt path. The SVS text of `SIM-TC-045` is unchanged and was
  re-reviewed against the new behaviour; only test code moved.
