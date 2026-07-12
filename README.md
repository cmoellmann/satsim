# SatSim

Satellite simulator (Java 21, Maven) for developing and automatically testing
satellite on-board software, with an ECSS-E-ST-70-41C (PUS-C) TM/TC interface
and a thin web frontend. Developed under a tailored ECSS-E-ST-40C /
ECSS-Q-ST-80C process (Category D ground software).

## Why this project exists

SatSim is two experiments in one.

**The engineering experiment:** a satellite simulator (Java 21, PUS-C/ECSS-E-ST-70-41C TM/TC interface) for developing and automatically testing satellite on-board software — built with the same discipline I spent a decade applying to real onboard software: a tailored ECSS-E-ST-40C/Q-ST-80C process, byte-level ICD with authoritative reference vectors, spec-first validation, full requirement-to-test traceability, and deterministic replay (identical inputs ⇒ byte-identical TM streams).

**The methodology experiment:** the project is deliberately AI-assisted (Claude / Claude Code) — under explicit, documented controls. AI proposes; the human decides. Architecture trade-offs are AI-analyzed and human-decided (see ADR-0006). Reference vectors and expected test results are human-approved and **immutable to the AI**: if implementation and spec disagree, the AI must stop and report a finding — never adjust the spec to make a test pass. All AI-generated content enters the baseline only via reviewed PR. Several of the process safeguards — including the immutability rule itself — originated as AI proposals that were evaluated and approved by the project lead. That loop is the point.

**The question behind it:** can one engineer, with 15 years of satellite onboard-software and safety-critical systems experience, use AI-assisted development to produce flight-software-grade engineering at a fraction of the traditional effort — *without* sacrificing the integrity that makes it flight-grade? This repository is the running answer.

*Start with [ADR-0006](docs/adr/ADR-0006-simulation-time-ownership.md) for a sample of the decision process, and [docs/sdp.md §6](docs/sdp.md) for the AI-governance controls.*

## Getting started

Start here:
- `CLAUDE.md` — project context and hard rules (also for AI-assisted sessions)
- `docs/sdp.md` — development plan, milestones M0–M5, action register
- `docs/icd.md` — byte-level TM/TC contract with authoritative reference vectors
- `docs/srs.md`, `docs/svs.md` — requirements and validation test definitions
- `docs/adr/` — architecture decision records

Build: `./mvnw -q verify` (Java 21; Maven 3.9.11 via committed wrapper).

Status: bootstrap package — documents drafted, skeleton compiles, no production
logic yet. Next: milestone M0 (see SDP §4).
