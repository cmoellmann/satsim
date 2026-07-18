# CLAUDE.md — SatSim

SatSim is a satellite simulator (Java 21, Maven multi-module) for developing and
automatically testing satellite on-board software (OBSW), with an ECSS-E-ST-70-41C
(PUS-C) TM/TC interface and a thin web frontend. Developed under a tailored
ECSS-E-ST-40C / Q-ST-80C process (Category D ground software) — see docs/sdp.md.

## Module map

- `pus-core` — CCSDS/PUS-C packet library. Framework-free, no Spring, no I/O.
- `simulator` — Spring Boot backend: scheduler (time master), OBSW targets
  (execution back-ends), REST/WebSocket API; serves the static frontend from
  `src/main/resources/static`.
- `sim-test-support` — `@Requirement` / `@TestCase` annotations for traceability.

## Authoritative documents (read before implementing)

- `docs/icd.md` — byte-level TM/TC contract **with reference vectors (§6) and CRC anchors (§7)**.
- `docs/srs.md` — numbered requirements (strict table format, parsed by CI).
- `docs/svs.md` — validation test case definitions (spec-first).
- `docs/sdp.md` — process, milestones M0–M5 with exit criteria, action register.
- `docs/sdd.md` — as-built architecture to class level: who does what, threads,
  key flows. Descriptive, not normative; update it in the same PR as
  architecture-shaping changes.
- `docs/adr/` — decision log (`DECISION-LOG.md`, ADR-0001…0006 summaries) plus
  full ADR files where warranted (currently only ADR-0006). **Immutable once
  accepted.**

## Architecture decisions in one breath

Process-isolated OBSW targets ('back-ends' in ADR wording) behind `SpaceLink` (ADR-0001); strict PUS-C
header layouts, services tailored to ST[17] then ST[1] (ADR-0002); single APID
100 (ADR-0003); CUC 4+2 time, epoch 2026-01-01T00:00:00 UTC, sourced only from
`SimulationClock` (ADR-0004); own thin frontend now, Yamcs-ready TCP
space-packet link later (ADR-0005); **the Java scheduler is the sole simulation
time master**, OBSW targets are stepped slaves via `EmulatorControl` with
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
8. **Dependency control:** never add, remove, or upgrade a third-party
   dependency (incl. build plugins) without prior human approval, presenting
   the license situation first. Every dependency is recorded with version,
   scope, and license in `docs/reuse-file.md` (Software Reuse File) in the
   same PR that introduces it.

## Working agreement

- One milestone = one session scope. Current target: **M0** (see docs/sdp.md §4
  for exit criteria). Do not pull M1+ scope into M0 beyond compilable stubs.
- Definition of done per milestone: build + CI green, all in-scope SVS cases
  implemented and passing, test report + traceability matrix generated,
  milestone tag proposed.
- Commit style: conventional commits; reference requirement/test IDs where
  applicable (e.g. `feat(pus-core): primary header codec [SIM-REQ-PUS-001]`).
- Coverage: indicative 80% line coverage on `pus-core` only; no formal target
  elsewhere (SDP §2.1 tailoring).

## Build & test

- `./mvnw -q verify` — full build with tests.
- `./mvnw -q -pl pus-core -am test` — build & test one module (and its deps).
- `./mvnw -q -pl pus-core test -Dtest=PrimaryHeaderCodecTest` — single test
  class; append `#methodName` for a single method.
- Java 21; Maven is pinned to 3.9.11 via the committed wrapper — always use
  `./mvnw`, never a locally installed `mvn`. No network resources required at
  test time.
- Static analysis (Checkstyle/SpotBugs) to be wired in M0; the wall-clock ban
  (rule 2) gets an automated check there.

## Current state

Bootstrap package: documents complete (drafts), Maven skeleton with interface
stubs (`SimulationClock`, `EmulatorControl`, `SpaceLink`) and traceability
annotations. Spring Boot dependencies and the CI workflow do not exist yet —
both are M0 scope; the module map above describes the target architecture.
No production logic yet — M0 starts here.
