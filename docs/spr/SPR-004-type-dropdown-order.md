# SPR-004 — Type dropdown lists ST[17] before ST[3] instead of numeric order

- Status: Rejected (baseline silent; converted to SCR-006, M1d)
- Severity: minor (presentation; no functional deviation)
- Reported: 2026-07-19, project lead (C. Möllmann), manual console use;
  analysis by AI assistant per SDP §6
- Affected CI / component: `simulator` web frontend (`app.js` compose
  dropdowns); present since M1b added ST[3], observed on master @ `8c88904`

## 1. Problem description

**Observed:** The compose Type dropdown lists `17 — ST[17] Test` before
`3 — ST[3] Housekeeping`.

**Expected (reporter):** numeric ascending order — ST[3] before ST[17] — as
conventional for enumerated ID lists.

## 2. Analysis (cause / classification)

The dropdown renders `TC_TYPES` (`app.js:14–29`) in declaration order, and
that order is an artifact of milestone accretion: ST[17] was the sole entry
at M1a (SCR-003 dropdown compose), ST[3] was appended by M1b (SCR-001) —
the code comment says exactly that ("grows with the milestones"). No sorting
is applied (`fillTypeSelect`, `app.js:34–46`).

**Classification per SDP §2.4:** the baseline is silent on dropdown
ordering — SIM-REQ-UI-010 ("shall offer the TC message types of the
tailored service set (ICD) as type and subtype dropdowns"), SIM-TC-032
("dropdowns pre-populated from the tailored TC set") and SCR-003 §1 all
specify content, not order. The product therefore does not deviate from its
baseline, and this is **not a nonconformance**. Nuance versus SPR-002: this
is not "as-designed" (no documented decision chose this order) but
"baseline-silent" — an unconsidered artifact. Either way the remedy is
evolutionary: making numeric order normative is SCR territory.

## 3. Disposition (proposed)

**Reject as defect (baseline silent)** and **convert to SCR**: fold into the
pending HMI presentation SCR (with the SPR-001 log-ordering fix and the
SPR-002 failure-code column): type and subtype dropdowns sorted numerically
ascending independent of `TC_TYPES` declaration order (robust as the
tailored set grows; the custom… free-entry option stays last), plus the
matching SIM-TC-032 checklist touch-up. This SPR closes as Rejected with a
cross-reference to the implementing SCR once raised.

## 4. Implementation and verification

- Disposition: reject (baseline silent), approved 2026-07-19 via review and
  merge of PR #58. Converted to SCR-006 (item 3, numeric dropdown ordering,
  M1d; SIM-TC-032 amended). No fix under this SPR — closed as Rejected with
  this cross-reference.
