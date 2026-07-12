# Architecture Decision Log — Satellite Simulator (SatSim)

Configuration item: SATSIM-ADR-LOG
Applicable process: ECSS-E-ST-40C / ECSS-Q-ST-80C, tailored for ground support software (criticality D).
Each decision is recorded as an ADR under `docs/adr/`. Status lifecycle: Proposed → Accepted → (Superseded by ADR-xxxx | Deprecated).
Baseline rule: ADRs are immutable once Accepted and baselined (Git tag); changes require a new superseding ADR.

| ID | Title | Status | Date | Summary |
|----|-------|--------|------|---------|
| ADR-0001 | Modular architecture with process-isolated OBSW | Accepted | 2026-07-12 | Three modules (pus-core, simulator, web-frontend). OBSW runs as separate process/emulator connected via space-packet link (`SpaceLink` seam); no JNI/FFI embedding. |
| ADR-0002 | Strict PUS-C secondary header layouts | Accepted | 2026-07-12 | TC/TM secondary headers per ECSS-E-ST-70-41C without field-layout tailoring (16-bit source ID, message type counter, destination ID). Tailoring occurs at service level only (initially ST[17], then ST[1]). |
| ADR-0003 | Single APID for the simulated spacecraft | Accepted | 2026-07-12 | One APID for all TM/TC in the PoC. Packet layer keeps APID as an explicit parameter to allow later per-subsystem APIDs without rework. |
| ADR-0004 | On-board time: CUC 4+2, agency epoch, implicit P-field | Accepted | 2026-07-12 | CCSDS CUC with 4 coarse + 2 fine octets, agency-defined epoch documented in the ICD, P-field not transmitted. Time is sourced exclusively from the `SimulationClock` abstraction, never from wall clock directly. |
| ADR-0005 | Thin own web frontend now; Yamcs-ready TCP packet link | Accepted | 2026-07-12 | Plain HTML/JS + WebSocket frontend served by Spring Boot for the PoC. Simulator additionally exposes a TCP link carrying CCSDS space packets as the stable external interface for later Yamcs (or other MCS) attachment. |
| ADR-0006 | Java simulator scheduler is the simulation time master | Accepted | 2026-07-12 | The Java discrete-event scheduler owns simulation time; emulators (TSIM, TEMU, QEMU) and native OBSW processes are stepped slaves via `EmulatorControl` with grant/consumed semantics. See ADR-0006 for full analysis. |

## Traceability

- ADRs are referenced from the Software Design Document (SDD) architecture section.
- Requirements derived from ADRs carry the marker `[ADR-xxxx]` in the requirements table (SRS).
- Verification: design-level decisions are verified by review (R); derived functional requirements by test (T) with `@Requirement` annotations in the test code.
