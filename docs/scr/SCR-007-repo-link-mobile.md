# SCR-007 — Repository link and mobile usability package (new increment M1e)

- Status: Proposed (specification PR #71: SCR + SDP + SRS + SVS)
- Date: 2026-07-19
- Originator: project lead (C. Möllmann); drafted by AI assistant per SDP §6
- Affected configuration items: SATSIM-SDP, SATSIM-SRS, SATSIM-SVS

## 1. Change description

Two frontend-only changes, as new increment **M1e**:

1. **Repository link.** The console gains a permanent, clearly labelled
   link to the public source repository
   (`github.com/cmoellmann/satsim`), e.g. in the header — so visitors of
   the live demo can reach the baseline (code, ICD, document set) behind
   the running instance. New requirement SIM-REQ-UI-015, new manual case
   SIM-TC-039.
2. **Mobile usability.** The layout is desktop-oriented (SCR-006/SPR-005
   even widened it); on phone-class viewports the header wraps
   awkwardly, compose rows and the log table overflow the page. Change:
   the layout reflows down to narrow mobile viewports (order of 360 px
   CSS width) — header, compose form and log controls stack without
   overlap or clipping, page-level horizontal scrolling does not occur,
   and wide log content (raw hex, detail view) wraps or scrolls within
   its own container. Desktop presentation is unchanged; SIM-TC-038
   (single-line hex at ≥ 1440 px) remains valid. New requirement
   SIM-REQ-UI-016, new manual case SIM-TC-040.

## 2. Rationale

- The live demo (satsim.onrender.com) is public and actively promoted; a
  substantial share of visitors arrives on phones. Today they get a
  broken-looking console and no path from the running artifact back to
  its source and controlled documentation.
- The repository link also serves attribution: one shared instance,
  many anonymous visitors — the link is the only on-page provenance.
- Frontend-only, one bundled SCR with one spec PR + one implementation
  PR + one gate, mirroring the M1c (SCR-004) and M1d (SCR-006) pattern
  for frontend-only increments.

## 3. Impact analysis

| CI / area | Impact |
|---|---|
| SDP §4 | New milestone row **M1e** inserted between M1d and M2 (label scheme per SCR-001; own session and gate, mirroring M1c/M1d). |
| ICD | **None** — web API §8.1/§8.2 unchanged, space-link bytes untouched, no reference vectors affected. |
| SRS | New requirement proposals (CLAUDE.md rule 3), scope M1e: **UI-015** repository link, **UI-016** narrow-viewport usability. Existing UI requirements remain valid and unamended — SIM-TC-038's desktop wording is a viewport-specific check, not contradicted by reflow below it. |
| SVS | New manual cases, scope M1e: **SIM-TC-039** (repository link), **SIM-TC-040** (mobile usability checklist at 360 px). No amendments to existing cases. |
| SPR register | None — no baseline nonconformance; the baseline is silent on mobile viewports and attribution (evolution, not correction, per SDP §2.4 demarcation). |
| TraceabilityCheck | No change: M1e follows the `M<n><letter>` ordinal scheme. CI pin stays M1d until the M1e gate; UI-015/016 are M-verified, so both gates stay finding-free. |
| ADRs | None contradicted; ADR-0005 (own thin frontend) remains the enabler. Determinism unaffected — presentation only. |
| SDD | §3.4 amended at implementation: header link, responsive reflow notes. |
| README | M1e status row at the gate; console screenshot unaffected (desktop presentation unchanged). |
| Implementation code | None in this SCR (specification level). One frontend implementation PR follows approval. |

## 4. Disposition

- [ ] Approved — project lead (C. Möllmann), via review and merge of
      specification PR #71.
