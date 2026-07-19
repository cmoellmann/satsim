# Software Problem Report Log — SatSim

Configuration item: SATSIM-SPR-LOG
Purpose: register of Software Problem Reports (SPRs) — nonconformances of the
software or its documentation against its own baseline. Instrument defined in
SDP §2.4 (introduced by SCR-005).
Status lifecycle: Open → Analyzed → Dispositioned (fix / no fix, by project
lead via PR review) → Closed (fix implemented and verified) | Rejected
(not a problem / duplicate / as-designed).

Each SPR file (`SPR-<nnn>-<slug>.md`) records: affected CI/component and the
version (commit/tag) where observed; problem description (observed vs expected
behavior, evidence); analysis (cause); disposition; implementation and
verification (fixing PRs, regression evidence). If the analysis concludes the
baselined specification is wrong, the SPR spawns an SCR (SDP §2.3) and both
cross-reference each other.

| ID | Title | Status | Severity | Reported | Affected CI / component | Disposition / closing PRs |
|----|-------|--------|----------|----------|-------------------------|---------------------------|
| SPR-001 | Frontend log can show response TM rows as preceding the TC that caused them | Dispositioned: fix (PR #55) → SCR-006 (M1d); implemented (PR #64), closure via SIM-TC-037 at the gate | minor | 2026-07-19 | simulator frontend (`app.js` packet log) | SCR-006; fix PR #64 |
| SPR-002 | Failure code embedded in the type column impairs log readability | Rejected (as-designed) | minor | 2026-07-19 | simulator frontend (`app.js` packet log) | converted to SCR-006 (PR #56 disposition) |
| SPR-003 | Compose form shows all structured HK fields for every type/subtype selection | Dispositioned: fix (PR #57); implemented (PR #61), closure pending SIM-TC-034 re-run | minor | 2026-07-19 | simulator frontend (`style.css` compose form) | fix PR #61: `[hidden]` CSS guard; re-run at M1d gate |
| SPR-004 | Type dropdown lists ST[17] before ST[3] instead of numeric order | Rejected (baseline silent) | minor | 2026-07-19 | simulator frontend (`app.js` compose dropdowns) | converted to SCR-006 (PR #58 disposition) |
| SPR-005 | Narrow page layout wraps TM raw hex onto two lines | Rejected (baseline silent) | minor | 2026-07-19 | simulator frontend (`style.css` page layout) | converted to SCR-006 (PR #59 disposition) |
| SPR-006 | "Live packet log" heading not top-aligned in its card | Rejected (baseline silent) | minor | 2026-07-19 | simulator frontend (`style.css` log card header) | converted to SCR-006 (PR #60 disposition) |
