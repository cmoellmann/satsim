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
| SPR-001 | Frontend log can show response TM rows as preceding the TC that caused them | Analyzed | minor | 2026-07-19 | simulator frontend (`app.js` packet log) | proposed: fix (sorted insertion + SCR-006 for SRS/SVS) |
| SPR-002 | Failure code embedded in the type column impairs log readability | Analyzed | minor | 2026-07-19 | simulator frontend (`app.js` packet log) | proposed: reject as-designed → convert to SCR (dedicated column) |
| SPR-003 | Compose form shows all structured HK fields for every type/subtype selection | Analyzed | minor | 2026-07-19 | simulator frontend (`style.css` compose form) | proposed: fix (`[hidden]` CSS guard); SIM-TC-034 re-run at closure (verification-escape note) |
