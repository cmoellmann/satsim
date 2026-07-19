# SPR-001 — Frontend log can show response TM rows as preceding the TC that caused them

- Status: Dispositioned — fix (PR #55 review); implementing SCR: SCR-006
  (M1d), closure at the M1d gate via SIM-TC-037
- Severity: minor (display only; wire behavior and determinism unaffected)
- Reported: 2026-07-19, project lead (C. Möllmann), manual console use;
  analysis by AI assistant per SDP §6
- Affected CI / component: `simulator` web frontend (`app.js` packet log);
  latent since the M1a packet log (SCR-003), observed on master @ `fe2e85b`
  (post-M1c)

## 1. Problem description

**Observed:** After sending TC(17,1) with default acknowledgement flags from
the console, the packet log (newest entry on top) shows the TC row as the
newest entry, above TM(1,1), TM(1,7) and TM(17,2) — i.e. read
chronologically, the log claims all response TM preceded the TC that caused
them. All four rows carry the same displayed OBT, so nothing in the display
contradicts the wrong order.

**Expected:** The log presents the causal order: the TC row precedes its
response TM rows, which the ICD normatively orders on the wire as TM(1,1) →
TM(17,2) → TM(1,7) for an accepted TC (ICD §10, report ordering).

**Evidence:** reproducible interactively (ping button, default ack 0b1001);
cause fully identified by code analysis (§2), no instrumentation needed.

## 2. Analysis (cause)

The log order is purely DOM **insertion order** (`logBody.prepend`,
`app.js:264`); no ordering key exists. The TC row and the TM rows reach the
log over two independent paths with no ordering relationship:

- The **TC row** is inserted only when the `POST /api/tc` round trip
  completes: `sendCompose`/ping handler → `addTcRow` (`app.js:571–611`),
  after two awaited promises in `post()` (`app.js:177–188`).
- The **TM rows** are inserted on WebSocket push (`handleFrame`,
  `app.js:626`), as soon as the frames arrive.

Backend timing: `SimulationService.sendTc` only queues the TC
(`LoopbackTarget.sendTc`) and returns the HTTP response; the response TMs are
emitted by the next `InteractivePacer` tick (20 ms wall clock) driving
`grant()`, and are broadcast on the WebSocket directly from the simulation
thread — concurrently with, and independent of, the HTTP response
serialization still in flight on the servlet thread. Whenever the WebSocket
frames win that wall-clock race (observed consistently in the reported
environment), the TC row is prepended last and displays as the newest entry.

The OBT cannot disambiguate: the loopback target is configured with zero TC
processing delay (`SimulatorApplication:35`), so `grant()` delivers the TC
and emits its response TMs at **exactly the injection instant** — identical
OBT by construction, not merely within display resolution. The true order
exists only in the single-threaded backend's execution sequence and is
discarded by splitting delivery across the REST response and the WebSocket.

**Demarcation note (SDP §2.4):** no baselined SRS requirement specifies the
log's ordering — the SRS is silent, while the ICD specifies the wire order
the log misrepresents. The defect record is this SPR; making the causal
ordering normative (SRS requirement + SVS case for the fix's regression
evidence) is evolutionary and therefore spawns an SCR.

## 3. Disposition (proposed)

**Fix**, frontend-only, no ICD/web-API change:

- Insert log rows by **sort key** instead of unconditional prepend: primary
  key the frame's `timeSeconds` at full JSON precision, tiebreak by kind
  (TC before rejection before TM at the same instant), stable within equal
  keys so TM arrival order — the wire order — is preserved.
- Spawn **SCR-006** alongside the fix: SRS requirement making the causal log
  order normative, plus a manual SVS case (ping with default ack → row order
  TM(1,7), TM(17,2), TM(1,1), TC top-down) as regression evidence per
  SDP §2.4.

Alternative considered, not recommended now: echo accepted TCs as an
additional frame kind on the `/api/tm` WebSocket (single ordered channel ⇒
ordering by construction). Rejected for this SPR because it extends the
ICD §8.2 interface for a display concern; worth revisiting with the M2 TCP
link, where ground-side clients will need a TC echo anyway.

## 4. Implementation and verification

- Disposition: fix, approved 2026-07-19 via review and merge of PR #55.
- SCR-006 raised 2026-07-19 (SIM-REQ-UI-014, SIM-TC-037; item 1 of the M1d
  HMI presentation package).
- Fix implemented: sorted DOM insertion by full-precision `timeSeconds` with
  TC < rejection < TM tiebreak at equal time, stable within equal keys
  (`app.js`, PR #64). Verified headless (Chromium/CDP): ping renders
  TM(1,7)/TM(17,2)/TM(1,1)/TC(17,1) top-down at identical OBT.
- Pending: SIM-TC-037 verdict at the M1d gate, closure date.
