# SatSim

[![CI](https://github.com/cmoellmann/satsim/actions/workflows/ci.yml/badge.svg)](https://github.com/cmoellmann/satsim/actions/workflows/ci.yml)

A satellite simulator with a byte-exact ECSS PUS-C TM/TC interface and a live
web console, for developing and automatically testing satellite on-board
software (OBSW). Created **from scratch with AI** (Claude / Claude Code)
under — and enforcing — the ECSS software development standards
(ECSS-E-ST-40C, ECSS-Q-ST-80C, tailored): a proof of concept of how
AI-assisted development and space-grade process discipline work together.

**▶ [Try it live: satsim.onrender.com](https://satsim.onrender.com)** — one
shared spacecraft for all visitors: everyone sees the same live log,
including each other's telecommands. Hosted on a free tier that sleeps when
idle, so the first visit may take up to a minute to wake the simulator
(fresh boot, on-board time from zero). The instance always runs the latest
master that passed the full CI gate.

![SatSim console — live OBT clock, TC(17,1) ping answered by TM(1,1) acceptance, TM(17,2) report, and TM(1,7) completion](docs/assets/console.png)

*The console after one ping with default acknowledgement flags: acceptance
report, service report, completion report — each row expandable to a
field-level breakdown down to the CRC.*

## What SatSim is

SatSim is two experiments in one repository:

- **The engineering experiment** — a simulator that speaks strict PUS-C
  (ECSS-E-ST-70-41C) over CCSDS space packets, developed as **Category D
  ground software** under a tailored ECSS process: a byte-level ICD with
  authoritative reference vectors, spec-first validation, full
  requirement-to-test traceability, and deterministic replay.
- **The methodology experiment** — the development is deliberately
  AI-assisted, under explicit controls: AI proposes, the human decides;
  reference vectors and expected test results are human-approved and
  *immutable to the AI*; everything enters the baseline via reviewed pull
  request.
  Several of the safeguards, including the immutability rule itself,
  originated as AI proposals that were evaluated and approved by the human.

What the PoC covers today:

- **ECSS compliance as a working practice, not paperwork**: the full
  controlled document set (SDP, SRS, SVS, ICD, SDD, ADRs, SCRs, SRF,
  milestone gate reports) exists and is *live* — machine-parsed by CI,
  gated at milestones, or both.
- **A documented AI development approach**: tiered AI staffing under
  committed agent definitions — cheaper models implement well-specified
  chunks with bounded authority, the senior model reviews every delegated
  diff, the human merges.
- **PUS-C over CCSDS space packets**, strictly tailored: ST[17] connection
  test, the ST[1] request verification subset, and the ST[3] housekeeping
  subset live — the simulator emits periodic telemetry from the moment it
  boots. Single APID, CUC 4+2 on-board time.
- **A thin web console**: running OBT clock, live packet log with rejection
  rows and field-level detail view, compose form whose hex preview *is* the
  ICD reference vector.
- **Process-isolated OBSW targets** behind two narrow contracts
  (`SpaceLink`, `EmulatorControl`): the entire validation suite must pass
  unchanged against any conforming target — a conformance kit for
  progressively more real spacecraft software.

The as-built architecture — modules, threads, and key flows down to class
level — is described in the [Software Design Document](docs/sdd.md).

## Highlights

- **The ECSS standards are the input, not the afterthought.** The controlled
  document set below exists for real and is *live*: parsed by CI, gated at
  milestones, or both. The process gives the AI hard rails; the AI makes the
  process affordable at PoC scale.
- **Specs are immutable to the AI.** If implementation and spec disagree, the
  AI must stop and report a finding — never adjust the spec to make a test
  pass. This rule has caught real defects: a negative test vector whose stale
  CRC masked the check it existed to verify, found while generating vectors.
- **Traceability and review obligations are CI gates.** Requirement → SVS
  case → annotated test is machine-checked on every pull request (its first
  dry-run found a spec gap); missing human review verdicts fail the build
  (ACT-004). The gate even failed one of its own pull requests — and was
  right.
- **Determinism is build-enforced.** The wall-clock ban is a Checkstyle
  forbidden-API gate with one sanctioned suppression; the replay test proves
  two identical runs yield SHA-256-identical TM streams.
- **The on-board software is a plug, not a partner.** Today an in-process
  loopback, next a native C/Rust demo process, then real OBSW binaries under
  instruction-level emulators (QEMU, TSIM, Terma TEMU) — same validation
  suite, unchanged (SIM-REQ-LINK-003).
- **Change and staffing go through process, not chat.** Scope changes are
  SCRs with per-document impact analyses, defects are SPRs with analysis,
  disposition, and verified closure — both dispositioned by pull-request
  review; routine
  implementation is delegated to cheaper models under committed agent
  definitions with bounded authority, every delegated diff reviewed before
  commit.

## Status

| Milestone | Date | Scope | Gate record |
|---|---|---|---|
| [`M0`](https://github.com/cmoellmann/satsim/releases/tag/M0) | 2026-07-18 | Walking skeleton: build, CI, interface trio, loopback target, CRC + primary header codecs | [M0 report](docs/test-reports/M0-report.md) |
| [`M1`](https://github.com/cmoellmann/satsim/releases/tag/M1) | 2026-07-18 | TC(17,1)→TM(17,2) chain: PUS-C codecs, time-mastered scheduler, REST/WS API, web console, determinism replay | [M1 report](docs/test-reports/M1-report.md) |
| [`M1a`](https://github.com/cmoellmann/satsim/releases/tag/M1a) | 2026-07-18 | HMI package ([SCR-003](docs/scr/SCR-003-hmi-improvements.md)): OBT clock, rejection rows, detail view. ST[1] request verification ([SCR-002](docs/scr/SCR-002-st1-verification.md)): TM(1,1)/(1,2)/(1,7) | [M1a report](docs/test-reports/M1a-report.md) |
| [`M1b`](https://github.com/cmoellmann/satsim/releases/tag/M1b) | 2026-07-19 | ST[3] housekeeping ([SCR-001](docs/scr/SCR-001-st3-housekeeping.md)): periodic TM(3,25) from boot, structure lifecycle, ST[1] TM(1,8) semantic-error reports (ICD OP-3 resolved) | [M1b report](docs/test-reports/M1b-report.md) |
| [`M1c`](https://github.com/cmoellmann/satsim/releases/tag/M1c) | 2026-07-19 | HK compose usability ([SCR-004](docs/scr/SCR-004-hk-compose-usability.md)): structured TC(3,1)/(3,5)/(3,7) compose, interpreted ST[3] TC detail, inline TM(1,2)/(1,8) failure codes | [M1c report](docs/test-reports/M1c-report.md) |

Currently: **130/130 tests green**, pus-core line coverage **97 %**
(indicative target 80 %), traceability gate at 0 findings.
**Next: M2** — TCP length-framed space-packet link (ICD §8), the door for
external clients and Yamcs.

## Document set (ECSS compliant)

| Document | File | What it is |
|---|---|---|
| Software Development Plan (SDP) | [docs/sdp.md](docs/sdp.md) | Process, ECSS tailoring matrix, milestones M0–M5 with exit criteria, action register, AI-governance controls (§6) |
| Software Requirements Specification (SRS) | [docs/srs.md](docs/srs.md) | Numbered requirements in a strict table format, machine-parsed by the CI traceability gate |
| Software Validation Specification (SVS) | [docs/svs.md](docs/svs.md) | Spec-first validation test case definitions with human-approved expected results |
| Interface Control Document (ICD) | [docs/icd.md](docs/icd.md) | Byte-level TM/TC contract with authoritative reference vectors (§6) and CRC anchors (§7) |
| Software Design Document (SDD) | [docs/sdd.md](docs/sdd.md) | As-built architecture to class level: modules, threads, key flows |
| Architecture decisions (ADR) | [docs/adr/DECISION-LOG.md](docs/adr/DECISION-LOG.md) | Immutable decision log; [ADR-0006](docs/adr/ADR-0006-simulation-time-ownership.md) (simulation time ownership) as the full-form sample |
| Software Change Requests (SCR) | [docs/scr/SCR-LOG.md](docs/scr/SCR-LOG.md) | Change-control register; each SCR carries a per-document impact analysis and a recorded disposition |
| Software Problem Reports (SPR) | [docs/spr/SPR-LOG.md](docs/spr/SPR-LOG.md) | Problem/nonconformance register (SCR-005): observed vs expected behavior, cause analysis, disposition, verified closure |
| Software Reuse File (SRF) | [docs/reuse-file.md](docs/reuse-file.md) | Dependency/license register (Q-ST-80C style): version, scope, SPDX license, approval record |
| Milestone test reports | [docs/test-reports/](docs/test-reports/) | Gate records ([M0](docs/test-reports/M0-report.md), [M1](docs/test-reports/M1-report.md), [M1a](docs/test-reports/M1a-report.md), [M1b](docs/test-reports/M1b-report.md)): test results, coverage, traceability matrix, human review verdicts |
| AI working rules | [CLAUDE.md](CLAUDE.md) | Controlled document: project context and the hard rules every AI session runs under |
| AI agent definitions | [.claude/agents/README.md](.claude/agents/README.md) | Tiered delegation setup: implementer + scribe agents with bounded authority |

## Current limitations

- **In-process loopback target only.** No real OBSW binary runs yet — the
  target seam exists precisely for that, but native processes arrive at M3
  and emulated OBSW binaries at M5.
- **Tailored service subset.** ST[17], the ST[1] acceptance/completion
  subset, and the ST[3] housekeeping subset are live; everything else is
  rejected per the ICD (and says so on the wire).
- **Single APID (100), single ground source, strict PUS-C only** — by
  design (ADR-0002/0003), not by accident.
- **The web console is a PoC HMI, not a mission control system.** It paces
  simulated time 1:1 against wall clock for interactive use; the scheduler
  underneath can jump arbitrarily (that's what the tests do), but the UI
  exposes no fast-forward yet.
- **No external transport.** The TCP length-framed space-packet link (M2)
  and Yamcs attachment (M4) are not built yet; today the only way in is
  REST/WS.
- **Coverage target on pus-core only** (SDP §2.1 tailoring); other modules
  are covered by validation tests without a numeric bar.
- **Lightweight milestone model, not the ECSS review life cycle.** ECSS
  projects run formal reviews (PDR, CDR, QR, AR, …); this PoC replaces them
  with lightweight M0–M5 gates — exit criteria plus a committed, auditable
  gate record per milestone (SDP §4 tailoring).

## Roadmap & future extensions

Planned increments per [SDP §4](docs/sdp.md), each behind a milestone gate:

- **M2** — TCP length-framed space-packet link: external client demo over
  TCP, conformance-tested framing.
- **M3** — native OBSW demo process (small C or Rust ST[17] responder):
  the same validation suite green against a second, out-of-process target.
- **M4** — Yamcs attachment trial: TC/TM round-trip from a real mission
  control client over the M2 link.
- **M5** — first emulator adapter (QEMU): validation suite green against an
  OBSW binary under instruction-level emulation, time-sync conformance
  proven.

Ideas beyond the current plan — each would enter via SCR, not by quiet scope
growth:

- **Further PUS services**: ST[5] event reporting, ST[11] time-tagged
  commanding, ST[12] on-board monitoring.
- **Fault injection** on the space link (drops, corruption, delays).
- **Commercial instruction-level emulators**: TSIM, Terma TEMU/cOBC.
- **Multi-APID / multi-spacecraft scenarios.**
- **A follow-on flight-software project**: applying the same AI-assisted
  ECSS approach to actual on-board software, raised to **Category B** rigor
  — the step this ground-software PoC prepares for.

## Getting started

Build: `./mvnw -q verify` (Java 21; Maven 3.9.11 via committed wrapper — no
network resources required at test time).

Run the simulator:

```
./mvnw -q package
java -jar simulator/target/simulator-0.1.0-SNAPSHOT.jar
```

then open http://localhost:8090 — compose a TC(17,1) ping (the hex preview
shows the exact ICD vector), send it, and watch TM(1,1), TM(17,2), TM(1,7)
arrive in the live log. Selecting TC(3,1) swaps the free hex field for
structured SID/interval/parameter inputs, whose defaults reproduce the ICD
reference vector V-TC-03 byte-for-byte in the preview. REST/WebSocket API
per [ICD §8](docs/icd.md): `POST /api/tc`, WS `/api/tm`.

For a quick tour of the methodology, read
[ADR-0006](docs/adr/ADR-0006-simulation-time-ownership.md) for a sample of the
decision process, [SDP §6](docs/sdp.md) for the AI-governance controls, and
the [M0 report](docs/test-reports/M0-report.md) for what a milestone gate
produces.

## License

[Apache License 2.0](LICENSE) — chosen and recorded in the
[Software Reuse File](docs/reuse-file.md) (closes SRF-OPEN-1); all reused
components are compatible (copyleft components are confined to build/test
scope).

---

All AI-generated content in this repository — code, documents, this README —
enters the baseline only via human-reviewed pull request.
