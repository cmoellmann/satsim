# SatSim

Satellite simulator (Java 21, Maven) for developing and automatically testing
satellite on-board software, with an ECSS-E-ST-70-41C (PUS-C) TM/TC interface
and a thin web frontend. Developed under a tailored ECSS-E-ST-40C /
ECSS-Q-ST-80C process (Category D ground software).

Start here:
- `CLAUDE.md` — project context and hard rules (also for AI-assisted sessions)
- `docs/sdp.md` — development plan, milestones M0–M5, action register
- `docs/icd.md` — byte-level TM/TC contract with authoritative reference vectors
- `docs/srs.md`, `docs/svs.md` — requirements and validation test definitions
- `docs/adr/` — architecture decision records

Build: `mvn -q verify` (Java 21, Maven ≥ 3.9).

Status: bootstrap package — documents drafted, skeleton compiles, no production
logic yet. Next: milestone M0 (see SDP §4).
