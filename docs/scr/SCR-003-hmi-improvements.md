# SCR-003 — HMI improvement package (extends increment M1a)

- Status: Proposed
- Date: 2026-07-18
- Originator: project lead (C. Möllmann), from the post-M1 HMI review;
  drafted by AI assistant per SDP §6
- Affected configuration items: SATSIM-SDP, SATSIM-ICD, SATSIM-SRS, SATSIM-SVS

## 1. Change description

Improve the web HMI (frontend + its backend API) as agreed in the post-M1
HMI review. The package extends the scope of increment **M1a**; within M1a
the HMI increment is implemented first, then the ST[1] service (SCR-002), so
verification reports land on an HMI that can show them.

1. **Live OBT display.** The backend publishes the current simulated time
   over the WS `/api/tm` channel as dedicated time frames; the header shows
   a running OBT clock. Emission cadence is defined in *simulated* time
   (nominal: every 100 ms simulated) so no simulation logic touches the wall
   clock (SIM-REQ-TIME-001); with 1:1 pacing this appears as ~10 Hz.
2. **Enriched TC feedback.** The `POST /api/tc` response gains the injection
   OBT, the consumed ground sequence count, and the decoded packet fields;
   TC log rows show OBT and sequence count like TM rows do.
3. **Rejection visibility.** Spacecraft-side TC rejections are published as
   rejection frames (reject reason, offending packet hex, OBT) and shown as
   REJ rows in the log. This is a *simulator diagnostic channel*, not
   spacecraft telemetry: it deliberately also covers CRC-failed packets,
   which by ICD §6.3 never produce TM — and from the ST[1] part of M1a it
   complements, not replaces, TM(1,2)/TM(1,8).
4. **Log controls.** Filter by direction (TC/TM/REJ) and service type,
   clear button, pause autoscroll. Frontend only. Prepares for M1b's
   1 Hz periodic TM(3,25).
5. **Packet detail view.** Clicking a log row expands a field-level
   breakdown (primary header, secondary header, application data, CRC) —
   for TM from the already-decoded frame, for TC from the enriched send
   response; packets that cannot be decoded (raw negative vectors) are
   marked as such with the failing check.
6. **Compose and orientation polish.**
   - Compose labels **"Service" → "Type"**, keeping "Subtype" (PUS message
     type terminology: a message type is the pair (type, subtype)).
   - Type and Subtype become **dropdowns** pre-populated with the TC message
     types of the ICD's tailored service set (currently TC(17,1); grows with
     M1b), each with a **"custom…"** free-entry option so arbitrary values —
     including deliberately invalid ones — remain composable.
   - **Header made prominent**: larger title plus one orientation sentence
     stating what the console is (PUS-C TM/TC console for the simulated
     spacecraft, APID 100), so a first-time user is oriented immediately.
   - Quality items: click-to-copy on hex cells, a one-click **Ping** button,
     TC/TM/REJ row badges distinguishable by shape/text (not color only),
     `aria-live` on the link status.

Wire-format note: the WS payload gains a frame-type discriminator
(`"kind": "tm" | "time" | "rejection"`); the current TM-only frame format is
amended accordingly (breaking change to ICD §8, new ICD issue).

## 2. Rationale

- **Time is the simulator's core state** (ADR-0006: scheduler as sole time
  master) and is currently invisible until a TM happens to arrive. An
  operator cannot see whether — or how fast — simulated time is running.
- **Silent failures undermine trust:** today a rejected TC simply vanishes
  from the operator's view. ST[1] (SCR-002) answers this at protocol level,
  but CRC-failures stay silent by design; the diagnostic channel closes the
  gap and also aids debugging of the negative reference vectors.
- **The log will not survive M1b unaided:** SCR-001's TM(3,25) arrives every
  simulated second and would bury all other traffic in the capped log.
- **Teaching value:** the field-level detail view turns the console into a
  byte-level PUS learning tool, consistent with the project's showcase goal.
- **Timing:** doing this before the ST[1] implementation avoids reworking
  the HMI twice within one increment.

## 3. Impact analysis

| CI / area | Impact |
|---|---|
| SDP §4 | **M1a row amended**: scope gains "HMI improvement package (SCR-003), implemented before the ST[1] part"; exit criteria gain the new SVS cases and the extended frontend smoke test (OBT clock, REJ rows, log controls, detail view, dropdown compose). No new milestone label — the label scheme (`M<n><letter>`, SCR-001) has no slot between M1 and M1a, and a separate gate is not warranted for an HMI package that ships in the same session as ST[1]. |
| ICD | New **issue (draft)**: §8 web API amended — WS frame discriminator (`kind`), time frame format, rejection frame format (reason enumeration = the simulator reject reasons, incl. their relation to §10.4 failure codes), extended `POST /api/tc` response fields. **Space-link packet definitions (§2–§7) and all reference vectors untouched** — this SCR changes only the ground-side web API. |
| SRS | New requirement proposals (CLAUDE.md rule 3), scope M1a, group SIM-REQ-UI: **UI-005** OBT publication + display; **UI-006** enriched TC response + display; **UI-007** rejection publication + display; **UI-008** log filter/clear/pause; **UI-009** packet detail view; **UI-010** dropdown compose from the tailored TC set with free-entry escape. Existing UI-001..004 remain valid and unamended (relabeling, header, polish stay within their wording). |
| SVS | New cases, scope M1a: **SIM-TC-027** (A: time frames published, OBT monotonic, cadence in simulated time), **SIM-TC-028** (A: `POST /api/tc` response carries OBT/sequence count/decoded fields), **SIM-TC-029** (A: rejection frame emitted for a rejected TC, incl. CRC-silent case), **SIM-TC-030** (M: log controls), **SIM-TC-031** (M: detail view incl. undecodable raw injection), **SIM-TC-032** (M: dropdown compose, relabeled fields, prominent header with orientation sentence). No amendment of any approved expected result; SIM-TC-012 (existing smoke) unchanged. |
| TraceabilityCheck | No change: all new requirements/cases carry the existing label M1a (ordinal 11). |
| ADRs | None contradicted. ADR-0005 (own thin frontend) is the enabler; ADR-0006/SIM-REQ-TIME-001 respected by defining the time-frame cadence in simulated time and publishing from the existing sim-thread TM path. |
| Determinism (SIM-REQ-TIME-005) | Unaffected on the space link: TM streams stay byte-identical. Time and rejection frames are web-API-only artifacts; SIM-TC-011/025 replay comparisons operate on TM packets and remain valid. |
| README | Gains updated screenshots/feature list at the M1a gate (full README rewrite is separately planned post-HMI). |
| Implementation code | None in this SCR (specification level only). Implementation follows after approval: HMI package first, then ST[1] (SCR-002), one M1a gate for both. |

## 4. Disposition

- [ ] Approved — project lead, via review and merge of the SCR PR.

## 5. Findings during implementation

(to be filled during implementation)
