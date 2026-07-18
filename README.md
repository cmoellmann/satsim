# SatSim

Satellite simulator (Java 21, Maven) for developing and automatically testing
satellite on-board software, with an ECSS-E-ST-70-41C (PUS-C) TM/TC interface
and a thin web frontend. Developed under a tailored ECSS-E-ST-40C /
ECSS-Q-ST-80C process (Category D ground software).

## Why this project exists

SatSim is two experiments in one.

**The engineering experiment:** a satellite simulator (Java 21, PUS-C/ECSS-E-ST-70-41C TM/TC interface) for developing and automatically testing satellite on-board software — built with the same discipline I spent a decade applying to real onboard software: a tailored ECSS-E-ST-40C/Q-ST-80C process, byte-level ICD with authoritative reference vectors, spec-first validation, full requirement-to-test traceability, and deterministic replay (identical inputs ⇒ byte-identical TM streams).

**The methodology experiment:** the project is deliberately AI-assisted (Claude / Claude Code) — under explicit, documented controls. AI proposes; the human decides. Architecture trade-offs are AI-analyzed and human-decided (see [ADR-0006](docs/adr/ADR-0006-simulation-time-ownership.md)). Reference vectors and expected test results are human-approved and **immutable to the AI**: if implementation and spec disagree, the AI must stop and report a finding — never adjust the spec to make a test pass. All AI-generated content enters the baseline only via reviewed PR. Several of the process safeguards — including the immutability rule itself — originated as AI proposals that were evaluated and approved by the project lead. That loop is the point.

**The question behind it:** can one engineer, with 15 years of satellite onboard-software and safety-critical systems experience, use AI-assisted development to produce flight-software-grade engineering at a fraction of the traditional effort — *without* sacrificing the integrity that makes it flight-grade? This repository is the running answer.

## Status

**Milestone M0 (walking skeleton) closed 2026-07-18** — tag
[`M0`](https://github.com/cmoellmann/satsim/releases/tag/M0), twelve reviewed PRs over two working days.
The gate record is a committed, auditable artifact, not a checkbox:

- **30/30 tests green** at the baseline commit, reproduced by a clean
  `./mvnw clean verify` — see the
  [M0 milestone test report](docs/test-reports/M0-report.md).
- **98.55 % line coverage** on the packet library `pus-core`
  (indicative target: 80 %).
- **All 8 M0 requirements traced** requirement → SVS case → annotated test
  method, verified by the CI traceability gate (0 findings).
- **Human review verdicts** — named, dated, with the exact evidence
  inspected — recorded in the report for the two requirements no test can
  prove.

In place after M0: CI on every PR, CRC-16 verified against ICD anchors, CCSDS
primary header codec, a time-mastered loopback OBSW target, the traceability
gate, and a build-enforced wall-clock ban (Checkstyle + SpotBugs).

**Next: M1** — the full TC(17,1) → TM(17,2) chain through the web frontend,
PUS-C TC/TM codecs validated against the ICD §6 reference vectors, REST/WS
API, and a determinism replay test (see [SDP §4](docs/sdp.md)).

## Highlights

- **The ECSS standards are the input, not the afterthought.** The controlled
  document set — [SDP](docs/sdp.md), [SRS](docs/srs.md), [SVS](docs/svs.md),
  [ICD](docs/icd.md), [ADR log](docs/adr/DECISION-LOG.md),
  [Software Reuse File](docs/reuse-file.md), milestone
  [test reports](docs/test-reports/) — exists for real and is *live*: parsed
  by CI, gated at milestones, or both. None of it is shelf-ware. The process
  gives the AI hard rails; the AI makes the process affordable at PoC scale.
- **Traceability is a CI gate — and it found a real spec gap on its first
  run.** A JDK-only checker cross-checks SRS/SVS tables against
  `@TestCase`/`@Requirement` annotations in every PR. Its first dry-run
  revealed a requirement no validation case covered; the AI reported it as a
  finding (it is forbidden from editing the spec), the human approved the fix.
- **The milestone gate is auditable.** The
  [M0 report](docs/test-reports/M0-report.md) records per-case verdicts,
  coverage, a full traceability matrix, and — for review-verified
  requirements — human verdicts with the evidence down to reproducible grep
  commands. A registered process action (ACT-004) will make future gates
  *fail* if a review verdict is ever missing.
- **Design rules are build-enforced.** The simulation-time rule (no wall-clock
  access — time comes only from `SimulationClock`, [ADR-0006](docs/adr/ADR-0006-simulation-time-ownership.md))
  is a Checkstyle forbidden-API gate with exactly one sanctioned, documented
  suppression. SpotBugs ships with an intentionally empty exclude list — and
  its very first run caught real defects in our own tooling, which were fixed,
  not excluded.
- **Dependency governance, ECSS-style.** No third-party component (including
  build plugins) enters without the license being presented and approved
  first; everything is recorded with version, scope, and SPDX license in the
  [Software Reuse File](docs/reuse-file.md). Copyleft components are confined
  to build scope.
- **Tiered AI staffing — and the org chart is a reviewed artifact.** Routine
  implementation and report assembly are delegated to cheaper models under
  [committed agent definitions](.claude/agents/README.md) with bounded
  authority (no spec edits, no dependency changes, no commits); the senior
  model reviews every delegated diff, the human merges. Delegation is
  announce-first and the policy itself entered the repo via PR.

## Document set

| Document | File | What it is |
|---|---|---|
| Software Development Plan (SDP) | [docs/sdp.md](docs/sdp.md) | Process, ECSS tailoring matrix, milestones M0–M5 with exit criteria, action register, AI-governance controls (§6) |
| Software Requirements Specification (SRS) | [docs/srs.md](docs/srs.md) | Numbered requirements in a strict table format, machine-parsed by the CI traceability gate |
| Software Validation Specification (SVS) | [docs/svs.md](docs/svs.md) | Spec-first validation test case definitions with human-approved expected results |
| Interface Control Document (ICD) | [docs/icd.md](docs/icd.md) | Byte-level TM/TC contract with authoritative reference vectors (§6) and CRC anchors (§7) |
| Architecture decisions (ADR) | [docs/adr/DECISION-LOG.md](docs/adr/DECISION-LOG.md) | Immutable decision log; [ADR-0006](docs/adr/ADR-0006-simulation-time-ownership.md) (simulation time ownership) as the full-form sample |
| Software Reuse File (SRF) | [docs/reuse-file.md](docs/reuse-file.md) | Dependency/license register (Q-ST-80C style): version, scope, SPDX license, approval record |
| M0 milestone test report | [docs/test-reports/M0-report.md](docs/test-reports/M0-report.md) | Gate record: test results, coverage, traceability matrix, human review verdicts |
| AI working rules | [CLAUDE.md](CLAUDE.md) | Controlled document: project context and the hard rules every AI session runs under |
| AI agent definitions | [.claude/agents/README.md](.claude/agents/README.md) | Tiered delegation setup: implementer + scribe agents with bounded authority |

## Getting started

Build: `./mvnw -q verify` (Java 21; Maven 3.9.11 via committed wrapper — no
network resources required at test time).

For a quick tour of the methodology, read
[ADR-0006](docs/adr/ADR-0006-simulation-time-ownership.md) for a sample of the
decision process, [SDP §6](docs/sdp.md) for the AI-governance controls, and
the [M0 report](docs/test-reports/M0-report.md) for what a milestone gate
produces.
