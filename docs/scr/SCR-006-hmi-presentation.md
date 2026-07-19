# SCR-006 — HMI presentation package from SPR dispositions (new increment M1d)

- Status: Implemented (spec PR #62, frontend implementation + SDD §3.4
  amendment PR #64; gate record: docs/test-reports/M1d-report.md)
- Date: 2026-07-19
- Originator: project lead (C. Möllmann) via SPR-001/002/004/005/006;
  drafted by AI assistant per SDP §6
- Affected configuration items: SATSIM-SDP, SATSIM-SRS, SATSIM-SVS, SPR
  register

## 1. Change description

Five frontend-only presentation changes, as new increment **M1d**, bundling
the dispositions of the first SPR campaign (2026-07-19):

1. **Causal log ordering** (SPR-001, disposition *fix*). Log rows are
   inserted by sort key instead of unconditional prepend: primary key the
   frame's `timeSeconds` at full JSON precision, tiebreak by kind (TC before
   rejection before TM at the same instant), stable within equal keys so TM
   arrival order — the wire order — is preserved. New requirement
   SIM-REQ-UI-014, new manual case SIM-TC-037.
2. **Dedicated failure-code column** (SPR-002, rejected as-designed →
   converted). The ICD §10.4 failure-code name of TM(1,2)/(1,8) rows moves
   from the type-column suffix into an own log column (empty for all other
   rows). SIM-REQ-UI-013 wording remains satisfied unchanged; SIM-TC-036
   checklist amended.
3. **Numeric dropdown ordering** (SPR-004, rejected baseline-silent →
   converted). Type and subtype dropdowns sort ascending by number
   independent of `TC_TYPES` declaration order; the custom… entry stays
   last. SIM-TC-032 checklist amended.
4. **Widened layout** (SPR-005, rejected baseline-silent → converted). The
   page cap (`main { max-width: 62rem }`) is raised so the tailored set's
   longest packet — the TM(3,25) default-SID report as sizing reference —
   renders its raw hex on one log line on common desktop viewports
   (target: order of 100rem / viewport-relative, margins preserved on
   ultrawide screens). New manual case SIM-TC-038.
5. **Log heading alignment** (SPR-006, rejected baseline-silent →
   converted). The "Live packet log" heading aligns to the top-left of its
   card like "Compose TC" (e.g. `align-items: flex-start` on `.log-head`),
   keeping the controls' internal layout. Covered by SIM-TC-038.

The **M1d gate additionally records the SIM-TC-034 re-run** that closes
SPR-003 (fix already implemented in its own PR; verification-escape note in
SPR-003 §2).

## 2. Rationale

- Outcome of the first systematic problem-reporting campaign under the
  SDP §2.4 instrument: one genuine nonconformance with a spec gap
  (SPR-001 — the baseline never made log ordering normative) and four
  presentation findings the baseline was silent on. Per the §2.4
  demarcation the evolutionary remedies belong in one SCR rather than five
  ad-hoc patches.
- Bundling keeps one spec PR + one implementation PR + one gate, mirroring
  the M1c pattern (SCR-004) for frontend-only increments.

## 3. Impact analysis

| CI / area | Impact |
|---|---|
| SDP §4 | New milestone row **M1d** inserted between M1c and M2 (label scheme per SCR-001; own session and gate, mirroring M1c). |
| ICD | **None** — web API §8.1/§8.2 unchanged; the ordering key uses the existing `timeSeconds` fields; no reference vectors touched. |
| SRS | New requirement proposal (CLAUDE.md rule 3), scope M1d: **UI-014** causal log ordering. UI-002/010/013 remain valid and unamended — column placement, dropdown order and layout width stay within their wording. |
| SVS | New manual cases, scope M1d: **SIM-TC-037** (causal ordering at equal OBT), **SIM-TC-038** (presentation checklist: single-line hex, heading alignment). Amendments: **SIM-TC-032** (dropdown numeric order from M1d), **SIM-TC-036** (failure-code column from M1d). Amendments follow the SIM-TC-007 precedent (SCR-002): the pre-M1d wording remains the record for past gates; no approved expected result is invalidated. |
| SPR register | SPR-002/004/005/006 close as **Rejected (converted to SCR-006)**; SPR-001 advances to Dispositioned/fix with SCR-006 as implementing SCR (closes at the M1d gate); SPR-003 closes at the M1d gate via the recorded SIM-TC-034 re-run. |
| TraceabilityCheck | No change: M1d follows the `M<n><letter>` ordinal scheme. CI pin stays M1c until the M1d gate; UI-014 and SIM-TC-037/038 are M-verified, so both gates stay finding-free. |
| ADRs | None contradicted; ADR-0005 (own thin frontend) remains the enabler. Determinism unaffected — space-link bytes untouched. |
| SDD | §3.4 amended at implementation: log rendering (sorted insertion, failure-code column) and layout notes. |
| README | M1d status row at the gate; screenshot refresh if the console image goes stale. |
| Implementation code | None in this SCR (specification level). One frontend implementation PR follows approval. |

## 4. Disposition

- [x] Approved — project lead (C. Möllmann), 2026-07-19, via review and
      merge of PR #62 (SCR + SDP + SRS + SVS + SPR register in one
      specification PR).

## 5. Findings during implementation

- While registering this SCR: the SCR-LOG row of SCR-005 still carried
  status Proposed although PR #54 had implemented it — corrected in the
  same PR (register bookkeeping only).
- Merging PR #62 required a manual conflict resolution in
  `docs/spr/SPR-LOG.md` (concurrent PR #61); the resolution dropped this
  SCR's five register-row updates while all other documents merged intact.
  Detected by post-merge cross-check, restored in the follow-up register
  PR. Register bookkeeping only; no specification content affected.
