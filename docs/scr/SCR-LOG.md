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
| SCR-008 | MCP operator gateway (agentic command & control) as new increment M1f | Implemented | 2026-07-20 | SDP, ICD, SRS, SVS, SRF, CLAUDE.md | #78 (SCR + SDP), #80 (ICD Issue 6 §8.4 + SRS + SVS), #81 (mcp-gateway + SVS tests + SRF + SDD) |
| SCR-009 | Shared-traffic console: broadcast injected TCs as §8.2 frames, as new increment M1g | Approved | 2026-07-20 | SDP, ICD, SRS, SVS | #86 (SCR + SDP), #94 (ICD Issue 7 + SRS + SVS; delta D-1) |
| SCR-010 | Command Authorization Gate (Cat B CI): extract, content-based classification, in-gate confirmation, as new increment M1h | Approved | 2026-07-25 | SDP, ADR (ADR-0007), SRF (§1a), SRS, SVS, SDD, CLAUDE.md | #88 (plan + ADR-0007 + SCR-010), #89 (disposition), #90 (delta erratum: SRF §1a) |
| SCR-011 | Command Authorization Gate (Cat B CI): Category B verification, operator-eval harness, bypass demonstration, as new increment M1i | Approved | 2026-07-25 | SDP, SRS, SVS, CAG-SSA (new safety analysis), SDD | #91 (SCR), #92 (disposition) |
