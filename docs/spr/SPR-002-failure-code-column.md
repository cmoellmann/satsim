# SPR-002 — Failure code embedded in the type column impairs log readability

- Status: Rejected (as-designed; converted to SCR-006, M1d)
- Severity: minor (readability; no functional deviation)
- Reported: 2026-07-19, project lead (C. Möllmann), manual console use;
  analysis by AI assistant per SDP §6
- Affected CI / component: `simulator` web frontend (`app.js` packet log,
  SCR-004 inline failure code); observed on master @ `a61b1db` (post-M1c)

## 1. Problem description

**Observed:** TM(1,2)/TM(1,8) log rows carry the ICD §10.4 failure-code name
appended to the packet type in the type column (e.g.
`TM(1,8) · ILLEGAL_COLLECTION_INTERVAL`), per the SCR-004 design.

**Expected (reporter):** the failure code in a dedicated log column, so the
type column stays uniform and short and failure codes align vertically —
better readability when scanning the log.

## 2. Analysis (cause / classification)

The observed behavior is **conformant to the baseline**: SIM-REQ-UI-013
requires the failure-code name "directly in the log row, without requiring
the detail view" — it does not prescribe the column — and SIM-TC-036 was
verified against exactly this presentation at the M1c gate (verdict pass,
2026-07-19). The inline suffix was an explicit design decision of SCR-004
§1.3.

Per the SDP §2.4 demarcation this is therefore **not a nonconformance**: the
product deviates from the reporter's preference, not from the baseline. The
finding is valid as a usability improvement and is corrective for nothing —
it is evolutionary, i.e. SCR territory. Recorded here as reported; the SPR
instrument's rejection path exists precisely for findings that analysis
reclassifies.

Implementation note for the SCR: `addRow` (`app.js:227`) already receives
the type label and per-kind fields separately; the failure code is computed
in `handleFrame` (`app.js:642–646`) and merely concatenated into the label —
splitting it into an own cell is a local change. SIM-TC-036's checklist
wording ("no failure-code suffix") would be updated by the same SCR to match
the column presentation; the requirement text of SIM-REQ-UI-013 needs no
change.

## 3. Disposition (proposed)

**Reject as defect (as-designed)** and **convert to SCR**: fold the
dedicated failure-code column into the SCR already proposed by SPR-001 for
the log-ordering fix (one "log presentation" SCR covering both SRS/SVS
touches and both frontend changes), or raise it as its own SCR at the
project lead's discretion. This SPR closes as Rejected with a
cross-reference to the implementing SCR once raised.

## 4. Implementation and verification

- Disposition: reject as-designed, approved 2026-07-19 via review and merge
  of PR #56. Converted to SCR-006 (item 2, dedicated failure-code column,
  M1d; SIM-TC-036 amended). No fix under this SPR — closed as Rejected with
  this cross-reference.
