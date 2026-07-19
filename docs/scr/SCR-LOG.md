# Software Change Request Log — SatSim

Configuration item: SATSIM-SCR-LOG
Purpose: register of Software Change Requests (SCRs) against baselined
controlled documents (SDP, SRS, SVS, ICD). Instrument defined in SDP §2.3.
Status lifecycle: Proposed → Approved (disposition by project lead via PR
review) → Implemented (all affected documents updated) | Rejected.

| ID | Title | Status | Date | Affected CIs | Implementing PRs |
|----|-------|--------|------|--------------|------------------|
| SCR-001 | Add ST[3] housekeeping subset as new increment M1b | Implemented (spec level) | 2026-07-18 | SDP, ICD, SRS, SVS | #14 (SCR + SDP), #15 (ICD Issue 2, SRS, SVS) |
| SCR-002 | Pull ST[1] request verification forward as new increment M1a | Implemented (spec level) | 2026-07-18 | SDP, ICD, SRS, SVS | #17 (SCR + SDP), #18 (ICD Issue 3, SRS, SVS) |
| SCR-003 | HMI improvement package (extends increment M1a) | Implemented (spec level) | 2026-07-18 | SDP, ICD, SRS, SVS | #29 (SCR + SDP), #30 (ICD Issue 4, SRS, SVS) |
| SCR-004 | Structured HK compose, interpreted TC detail, inline failure codes as new increment M1c | Implemented (spec level) | 2026-07-19 | SDP, SRS, SVS | #49 (SCR + SDP + SRS + SVS) |
| SCR-005 | Introduce Software Problem Reports (SPR) as the problem-reporting instrument | Implemented | 2026-07-19 | SDP, CLAUDE.md, README | #54 |
| SCR-006 | HMI presentation package from SPR dispositions as new increment M1d | Implemented | 2026-07-19 | SDP, SRS, SVS, SPR register | #62 (SCR + SDP + SRS + SVS + SPR register), #64 (frontend implementation + SDD) |
| SCR-007 | Repository link and mobile usability package as new increment M1e | Implemented | 2026-07-19 | SDP, SRS, SVS | #71 (SCR + SDP + SRS + SVS), #72 (frontend implementation + SDD) |
