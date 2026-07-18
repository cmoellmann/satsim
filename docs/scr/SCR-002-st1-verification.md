# SCR-002 — Pull ST[1] request verification forward as new increment M1a

- Status: Proposed (approval = disposition via review and merge of the PR introducing this file)
- Date: 2026-07-18
- Originator: project lead (C. Möllmann); drafted by AI assistant per SDP §6
- Affected configuration items: SATSIM-SDP, SATSIM-ICD, SATSIM-SRS, SATSIM-SVS

## 1. Change description

Pull the ECSS-E-ST-70-41C ST[1] request verification service forward from M2
into a new milestone increment **M1a** between M1 and M1b, and define its
tailored subset:

- **TM(1,1) / TM(1,2)** — successful / failed acceptance verification
- **TM(1,7) / TM(1,8)** — successful / failed completion of execution
- Start/progress subtypes TM(1,3)…(1,6) are excluded (no long-running TCs in
  the tailored service set).
- **Acknowledgement-flag semantics** per PUS-C: success reports are generated
  only when the corresponding ack flag of the TC secondary header (ICD §3) is
  set; failure reports are generated regardless of ack flags.
- **Frontend default ack flags change from 0b0000 to 0b1001**
  (acceptance + completion, user-overridable), so that from M1a a default
  ping visibly yields TM(1,1) → TM(17,2) → TM(1,7).
- **PUS-version rejection produces TM(1,2):** a readable TC whose PUS version
  is not 2 is rejected with a failed-acceptance report (change to the current
  "no TM emitted" expectation, see SVS impact). CRC-failed packets remain
  silently discarded: a packet failing CRC cannot be trusted, so no
  attributable request exists to verify.

## 2. Rationale

Request verification is the baseline observability an operator expects from
any PUS spacecraft: every command visibly acknowledged, every failure
visibly reported. Deferring it to M2 means M1 (ping chain) and M1b
(housekeeping) both ship command paths whose failures are log-only.
Pulling ST[1] forward to M1a means:

- the verification pipeline is built immediately after the first TC handling
  path exists (M1), instead of being retrofitted later;
- M1b can report ST[3] semantic errors (unknown SID, invalid application
  data) as TM(1,8)/TM(1,2) from its first implementation, resolving ICD OP-3
  one milestone earlier than planned;
- together with SCR-001's periodic housekeeping, a first-time user sees the
  two behaviors that make a spacecraft feel real: unsolicited telemetry and
  command acknowledgement.

## 3. Impact analysis

| CI / area | Impact |
|---|---|
| SDP §4 | New milestone row **M1a** inserted between M1 and M1b (ST[1] subset + frontend default-ack change). **M2 row reworded**: ST[1] content removed; the stale "ICD Issue 2" reference (already consumed by SCR-001) replaced. No renumbering; M2–M5 references elsewhere remain valid. |
| SDP §2.2 | **ACT-003** ("Decide ST[1] subservice subset and update ICD (OP-1)", target M2) is resolved by this SCR: subset decided here, ICD update in the implementing spec PR. Row set to Closed with reference to SCR-002. |
| ICD | New **Issue 3 (draft)**: new section for ST[1] (subservice table; report layouts — request ID = packet ID + packet sequence control of the verified TC, 4 octets; failure notice = failure code, enumeration fixed in the ICD); §3 ack-flag row note updated (frontend default 0b1001 from M1a); new §6 reference vectors: ping with ack=0b1001 plus its TM(1,1)/TM(1,7), and TM(1,2) for the V-NEG-02 stimulus. **OP-1 closed; OP-3 re-targeted from M2 to M1b** (ST[3] semantic errors → ST[1] failure reports, possible because M1a precedes M1b). Vectors are human-approved reference data per CLAUDE.md rule 1. |
| Existing reference vectors | **Unaffected.** All approved TC vectors (V-TC-01…05) carry ack=0b0000, so ST[1] produces no success reports for them; all are valid TCs, so no failure reports either. V-TM-01…04 byte-exact expectations remain valid, including the SCR-001 determinism cases SIM-TC-019/020. |
| SRS | New functional requirement group **SIM-REQ-VER-00x**, scope M1a (proposals per CLAUDE.md rule 3): acceptance/completion reporting per ack flags, failure reporting regardless of ack flags, TM(1,2) on PUS-version rejection, frontend default ack 0b1001. SIM-REQ-PUS-006 ("reject, not process") remains valid; the reporting behavior is added by the VER group, not by rewording PUS-006. SIM-REQ-PUS-007 automatically extends to the new §6 vectors. |
| SVS | New validation cases (ST[1] vector encode/decode; ping-with-ack end-to-end report sequence; failure-report cases), scope M1a. **Amendment of an approved expected result:** SIM-TC-007 pass criterion changes from "V-NEG-02 rejected; no TM emitted" to "V-NEG-02 rejected; exactly one TM(1,2); no TM(17,2)" — a human-dispositioned spec change under this SCR, explicitly not an AI adjustment to make a test pass (SDP §6.2). SIM-TC-006 (CRC failure, silent) unchanged. |
| TraceabilityCheck | "M1a" already parses under the milestone-label scheme introduced by SCR-001 (`M<n><letter>` → ordinal 11, between M1=10 and M1b=12). Verify by dry-run; no code change expected. |
| ADRs | **No contradiction.** ADR-0002 explicitly names ST[1] as the planned next service after ST[17]; this SCR changes only its milestone placement and fixes the tailored subset. Verification reports are TM like any other: emitted by the OBSW target within granted simulated-time windows (ADR-0006); SIM-REQ-TIME-001/003 unaffected. |
| README | Showcase landing page updated in the implementing spec PR (status/roadmap: M1 → M1a → M1b). |
| Implementation code | None in this SCR (specification level only). Implementation follows in the M1a session between M1 and M1b. |

## 4. Disposition

- [ ] Approved — project lead, via review and merge of the PR introducing
      this SCR.
