# SatSim Software Requirements Specification (SRS)

- Configuration item: SATSIM-SRS, Issue 1 (draft)
- Collapsed RB+TS per SDP §2.1. Verification methods: T = automated test (traced
  via SVS), M = manual test (recorded in test report), R = review, A = analysis.
- Column format is strict and identical in both tables (CI scripts parse them
  uniformly). Scope column = first milestone at which the requirement must be
  satisfied.
- Requirements are classified as functional (§1: externally observable behavior
  of the software) and non-functional (§2: quality attributes and constraints).
  The Category column uses the following vocabulary:
  - **Functional** — externally observable behavior (all §1 entries).
  - **Design constraint** — restricts how the software is built, without being
    externally observable behavior.
  - **Architectural constraint** — fixes a structural property of the system
    (typically decided by an ADR).
  - **Reproducibility** — determinism/repeatability quality attribute.
  - **Configurability** — required adjustability without code change.
  - **Portability** — invariance across OBSW targets/environments.
  - **Verifiability** — property enabling or enforcing verification (process
    constraint).

## 1. Functional requirements

| ID | Requirement | Category | Ver. | Scope | Source |
|---|---|---|---|---|---|
| SIM-REQ-PUS-001 | The simulator shall encode and decode CCSDS Space Packets with the primary header layout per ICD §2. | Functional | T | M0 | ICD §2 [ADR-0002] |
| SIM-REQ-PUS-002 | The simulator shall compute and verify the packet error control as CRC-16 per ICD §7, reproducing all three sanity anchors. | Functional | T | M0 | ICD §7 |
| SIM-REQ-PUS-003 | The simulator shall encode and decode PUS-C TC secondary headers per ICD §3. | Functional | T | M1 | ICD §3 [ADR-0002] |
| SIM-REQ-PUS-004 | The simulator shall encode and decode PUS-C TM secondary headers per ICD §4. | Functional | T | M1 | ICD §4 [ADR-0002] |
| SIM-REQ-PUS-005 | The simulator shall reject packets with invalid CRC and shall not process their content. | Functional | T | M1 | ICD §6.3 |
| SIM-REQ-PUS-006 | The simulator shall reject TC packets whose PUS version number is not 2. | Functional | T | M1 | ICD §6.3 |
| SIM-REQ-PUS-007 | The simulator shall byte-exactly reproduce all reference vectors of ICD §6 (encode direction) and accept them (decode direction). | Functional | T | M1 | ICD §6 |
| SIM-REQ-PUS-008 | The simulated spacecraft shall respond to a valid TC(17,1) with exactly one TM(17,2) on the same APID. | Functional | T | M1 | E-ST-70-41C ST[17] |
| SIM-REQ-PUS-009 | TM packet sequence counts shall increment per APID and wrap at 16383; message type counters shall increment per (service, subtype) and wrap at 65535. | Functional | T | M1 | ICD §2, §4 |
| SIM-REQ-PUS-010 | The simulator shall use APID 100 for all TM/TC of the simulated spacecraft. | Functional | T | M1 | ICD §2 [ADR-0003] |
| SIM-REQ-TIME-002 | TM time fields shall be encoded as CUC 4+2 per ICD §5 with epoch 2026-01-01T00:00:00 UTC. | Functional | T | M1 | ICD §5 [ADR-0004] |
| SIM-REQ-TIME-004 | EmulatorControl shall implement grant/consume semantics: a grant of simulated-time budget returns actual consumption and a stop reason; early return shall be supported. | Functional | T | M0 | [ADR-0006 C2] |
| SIM-REQ-LINK-002 | The simulator shall expose a TCP link carrying length-framed CCSDS space packets per ICD §8. | Functional | T | M2 | ICD §8 [ADR-0005] |
| SIM-REQ-UI-001 | The web frontend shall allow composing and sending a TC by service, subtype, and optional application data (hex). | Functional | M | M1 | project goal |
| SIM-REQ-UI-002 | The web frontend shall display received TM live, showing raw hex and decoded header fields. | Functional | M | M1 | project goal |
| SIM-REQ-UI-003 | The backend shall provide REST TC submission and WebSocket TM distribution per ICD §8 such that the end-to-end path is automatable without a browser. | Functional | T | M1 | ICD §8 |
| SIM-REQ-UI-004 | The frontend shall display the raw encoded bytes of each sent TC (hex) before/after sending. | Functional | M | M1 | debugging goal |
| SIM-REQ-HK-001 | The simulated spacecraft shall create a housekeeping report structure upon a valid TC(3,1) per ICD §9.2; created structures shall start with periodic generation disabled. | Functional | T | M1b | ICD §9 [SCR-001] |
| SIM-REQ-HK-002 | For each enabled housekeeping structure, the simulated spacecraft shall emit one TM(3,25) per ICD §9.4 at every collection interval of simulated time per ICD §9.3. | Functional | T | M1b | ICD §9 [SCR-001] |
| SIM-REQ-HK-003 | The default housekeeping structure SID 1 per ICD §9.6 shall exist with periodic generation enabled at simulation start, without ground commanding. | Functional | T | M1b | ICD §9 [SCR-001] |
| SIM-REQ-HK-004 | The simulated spacecraft shall enable and disable periodic generation of housekeeping structures upon valid TC(3,5) and TC(3,7) per ICD §9.3. | Functional | T | M1b | ICD §9 [SCR-001] |

## 2. Non-functional requirements

| ID | Requirement | Category | Ver. | Scope | Source |
|---|---|---|---|---|---|
| SIM-REQ-TIME-001 | All simulation components shall obtain time exclusively from the SimulationClock abstraction; direct wall-clock access in simulation logic is prohibited. | Design constraint | R+A | M0 | [ADR-0004][ADR-0006 C1] |
| SIM-REQ-TIME-003 | The Java scheduler shall be the sole simulation time master; OBSW targets shall be controlled via EmulatorControl. | Architectural constraint | R | M0 | [ADR-0006] |
| SIM-REQ-TIME-005 | Given identical initial state and identical TC input sequence with identical simulated injection times, the simulator shall produce byte-identical TM streams including timestamps. | Reproducibility | T | M1 | [ADR-0006 C6] |
| SIM-REQ-TIME-006 | The synchronization quantum shall be configurable per OBSW-target link at runtime. | Configurability | T | M5 | [ADR-0006 C3] |
| SIM-REQ-LINK-001 | The loopback OBSW target shall implement the same EmulatorControl and SpaceLink contracts as external OBSW targets. | Design constraint | T | M0 | [ADR-0006 C5] |
| SIM-REQ-LINK-003 | The validation test suite shall pass unchanged against any conforming OBSW target (loopback, native process, emulator). | Portability | T | M3 | [ADR-0001] |
| SIM-REQ-QA-001 | Every validation test shall carry @TestCase and @Requirement annotations resolvable against SVS and SRS. | Verifiability | A | M0 | SDP §5 |
| SIM-REQ-QA-002 | CI shall fail a milestone gate if any SRS requirement in scope lacks a passing validation test (or recorded manual verdict). | Verifiability | A | M0 | SDP §5 |

## Change log

| Issue | Date | Change |
|---|---|---|
| 1 (draft) | 2026-07-12 | Initial set for M0–M3 scope (+M5 sync item). Lowercase "shall"; functional / non-functional split with Category column and defined category vocabulary. |
| 1 (draft) | 2026-07-18 | SIM-REQ-HK-001…004 added (ST[3] housekeeping subset, scope M1b) per SCR-001. |
