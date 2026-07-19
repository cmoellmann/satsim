# SPR-005 — Narrow page layout wraps TM raw hex onto two lines

- Status: Rejected (baseline silent; converted to SCR-006, M1d)
- Severity: minor (presentation; no functional deviation)
- Reported: 2026-07-19, project lead (C. Möllmann), manual console use;
  analysis by AI assistant per SDP §6
- Affected CI / component: `simulator` web frontend (`style.css` page
  layout); present since the M1 frontend, observed on master @ `6ff69a3`

## 1. Problem description

**Observed:** The Compose TC and Live packet log cards are capped at a
narrow width regardless of viewport size; in the log, the raw hex of longer
packets — a TM(3,25) housekeeping report in particular — breaks onto two
lines, which hampers scanning and comparing hex dumps.

**Expected (reporter):** broader cards, so that on a typical desktop
viewport the raw hex of the tailored set's packets fits on one line.

## 2. Analysis (cause / classification)

Both cards live in `main`, which is capped at `max-width: 62rem` (≈ 930 px
at the 15 px root font size, `style.css:61–67`) — a value chosen for the
M1-era layout, before ST[3] housekeeping introduced the longest packets of
the tailored set. The log's hex column (`td.hex-cell`) declares
`word-break: break-all` (`style.css:156`), so hex exceeding the column's
share of the ~930 px wraps mid-dump. A TM(3,25) with the default SID-1
parameter set runs to ~130+ monospace characters of formatted hex — more
than the entire card width at that cap.

**Classification per SDP §2.4:** no baselined statement constrains the page
layout or hex-line presentation — SIM-REQ-UI-002 requires raw hex display,
not its line count; SCR-003/004 specify log content and detail views, not
card geometry. As with SPR-004, this is "baseline-silent": the cap is an
unconsidered M1-era artifact, not a documented decision. The product does
not deviate from its baseline — **not a nonconformance**; the remedy is
evolutionary (SCR).

## 3. Disposition (proposed)

**Reject as defect (baseline silent)** and **convert to SCR**: fold into the
pending HMI presentation SCR (with the SPR-001 log-ordering fix, SPR-002
failure-code column, SPR-004 dropdown sort): raise the layout cap so the
tailored set's longest packets render on one hex line on common desktop
viewports (e.g. `max-width` on the order of 100rem or a viewport-relative
cap, margins preserved on ultrawide screens), verified with the TM(3,25)
default-SID report as the sizing reference. This SPR closes as Rejected
with a cross-reference to the implementing SCR once raised.

## 4. Implementation and verification

- Disposition: reject (baseline silent), approved 2026-07-19 via review and
  merge of PR #59. Converted to SCR-006 (item 4, widened layout, M1d;
  SIM-TC-038 added). No fix under this SPR — closed as Rejected with this
  cross-reference.
