# CLAUDE.md — SatSim

SatSim is a satellite simulator (Java 21, Maven multi-module) for developing and
automatically testing satellite on-board software (OBSW), with an ECSS-E-ST-70-41C
(PUS-C) TM/TC interface and a thin web frontend. Developed under a tailored
ECSS-E-ST-40C / Q-ST-80C process (Category D ground software) — see docs/sdp.md.

## Module map

- `pus-core` — CCSDS/PUS-C packet library. Framework-free, no Spring, no I/O.
- `simulator` — Spring Boot backend: scheduler (time master), execution
  back-ends, REST/WebSocket API; serves the static frontend from
  `src/main/resources/static`.
- `sim-test-support` — `@Requirement` / `@TestCase` annotations for traceability.

## Authoritative documents (read before implementing)

- `docs/icd.md` — byte-level TM/TC contract **with reference vectors (§6) and CRC anchors (§7)**.
- `docs/srs.md` — numbered requirements (strict table format, parsed by CI).
- `docs/svs.md` — validation test case definitions (spec-first).
- `docs/sdp.md` — process, milestones M0–M5 with exit criteria, action register.
- `docs/adr/` — architecture decisions ADR-0001…0006. **Immutable once accepted.**

## Architecture decisions in one breath

Process-isolated OBSW back-ends behind `SpaceLink` (ADR-0001); strict PUS-C
header layouts, services tailored to ST[17] then ST[1] (ADR-0002); single APID
100 (ADR-0003); CUC 4+2 time, epoch 2026-01-01T00:00:00 UTC, sourced only from
`SimulationClock` (ADR-0004); own thin frontend now, Yamcs-ready TCP
space-packet link later (ADR-0005); **the Java scheduler is the sole simulation
time master**, back-ends are stepped slaves via `EmulatorControl` with
`grant(budget) → consumed(time, stopReason)` semantics (ADR-0006).

## Hard rules

1. **Never modify** ICD reference vectors/anchors or SVS expected results to
   make a test pass. If implementation and ICD disagree, stop and report the
   discrepancy as a finding.
2. **Never read wall-clock time in simulation logic** (`System.currentTimeMillis`,
   `Instant.now`, etc.). Use `SimulationClock`. Wall clock is allowed only in
   the interactive pacing policy and infrastructure/logging.
3. Every new behavior needs a validation test: SRS requirement → SVS entry →
   JUnit test annotated `@TestCase` + `@Requirement`. You may **propose** new
   SRS/SVS entries, but mark them clearly as proposals; they apply only after
   human approval.
4. ADRs are immutable. To change a decision, draft a new superseding ADR.
5. `pus-core` stays dependency-free (JDK only). No Spring types outside `simulator`.
6. Strict PUS-C: no PUS-A fallbacks, no optional-field creativity beyond the ICD.
7. This file is a controlled document — propose changes via PR, don't rewrite ad hoc.

## Working agreement

- One milestone = one session scope. Current target: **M0** (see docs/sdp.md §4
  for exit criteria). Do not pull M1+ scope into M0 beyond compilable stubs.
- Definition of done per milestone: build + CI green, all in-scope SVS cases
  implemented and passing, test report + traceability matrix generated,
  milestone tag proposed.
- Commit style: conventional commits; reference requirement/test IDs where
  applicable (e.g. `feat(pus-core): primary header codec [SIM-REQ-PUS-001]`).

## Build & test

- `mvn -q verify` — full build with tests.
- Java 21, Maven ≥ 3.9. No network resources required at test time.
- Static analysis (Checkstyle/SpotBugs) to be wired in M0; the wall-clock ban
  (rule 2) gets an automated check there.

## Current state

Bootstrap package: documents complete (drafts), Maven skeleton with interface
stubs (`SimulationClock`, `EmulatorControl`, `SpaceLink`) and traceability
annotations. No production logic yet — M0 starts here.
