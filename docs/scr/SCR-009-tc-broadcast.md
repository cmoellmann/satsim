# SCR-009 — Shared-traffic console: broadcast injected TCs as §8.2 frames (new increment M1g)

- Status: Proposed (approval = review and merge of this specification PR;
  ICD/SRS/SVS spec updates follow in a second PR per the SCR-001…003
  pattern)
- Date: 2026-07-20
- Originator: project lead (C. Möllmann), from an observation during the
  M1f recorded demo; drafted by AI assistant per SDP §6
- Affected configuration items: SATSIM-SDP, SATSIM-ICD, SATSIM-SRS,
  SATSIM-SVS

## 1. Change description

New increment **M1g**: make command traffic visible to all observers of
the shared spacecraft.

1. **Web API (ICD §8.2, Issue 7):** every §8.1 injection — structured or
   raw, decodable or not — is additionally broadcast to all WebSocket
   sessions as a new frame kind **`tc`**, carrying the same information as
   the §8.1 response: packet hex, injection OBT, consumed ground sequence
   count (when applicable), decoded fields or `decodeError`. Existing
   frame kinds and the space-link contract are unchanged; no reference
   vector is touched.
2. **Web console:** TC rows are rendered from the broadcast stream for
   *all* injections, with rows originating from other sessions visually
   marked (e.g. a "remote" tag); the session's own TCs appear exactly once
   (no duplication between the §8.1 response path and the broadcast).
   Causal ordering per SIM-REQ-UI-014 extends to remote TC rows.
3. **MCP gateway:** the ring buffer accepts the new kind, and
   `get_packet_log` serves it (filter `kind` gains `tc`); `await_tm`
   remains TM-only. An AI operator thereby sees other operators'
   commanding — shared-console operations, as on a real control-room
   floor.

Planned specification entries (rule-3 proposals, tabled in the follow-up
spec PR): **SIM-REQ-UI-017** (backend `tc` broadcast, T),
**SIM-REQ-UI-018** (console remote-TC presentation without own-row
duplication, M); amendment of **SIM-REQ-MCP-003** (buffer serves tm,
rejection and tc). SVS: **SIM-TC-046** (automated: a second, passive WS
session receives the `tc` frame for another client's injection with
fields matching the §8.1 response; the gateway's `get_packet_log` returns
the corresponding `tc` record), **SIM-TC-047** (manual: observer console
shows a remote operator's TC row marked as remote; own rows not
duplicated). Amended cases as needed (SIM-TC-027..029 assert frame
counts on the WS stream and gain the additional `tc` frames in their
expected traffic).

## 2. Rationale

- Found empirically during the M1f recorded demo: an observer watching
  the web console while an AI operator commanded via MCP saw only the
  telemetry responses — the §8.2 stream has no TC frame kind, and TC rows
  are session-local. The README's shared-console claim ("everyone sees
  each other's telecommands") was corrected to match reality in PR #85;
  this SCR makes the claim true instead.
- The shared public instance and the agentic command & control story both
  want a full-traffic view: watching *the AI's commands* appear live is
  the demo; today observers only hear the downlink half of the
  conversation.
- Kept deliberately small and pre-M2: frontend + web layer + a pass-through
  in the gateway; no space-link change, no new dependency.

## 3. Impact analysis

| CI / area | Impact |
|---|---|
| SDP §4 | New milestone row **M1g** inserted between M1f and M2 (label scheme per SCR-001). M2…M5 unchanged, shifted one increment later. |
| ICD | Issue 7: §8.2 frame table gains the `tc` kind with its field set; §8.4 `get_packet_log` filter enum gains `tc`. **No space-link change (§2–§7), no reference vectors touched.** |
| SRS | New SIM-REQ-UI-017 (T) / SIM-REQ-UI-018 (M), scope M1g; SIM-REQ-MCP-003 amended (kind set). |
| SVS | New SIM-TC-046 (A) / SIM-TC-047 (M), scope M1g; SIM-TC-027..029 pass criteria amended where they enumerate expected WS frames. |
| TraceabilityCheck | No tool change; CI pin stays M1f until the M1g gate. |
| ADRs | None contradicted (ADR-0005 web API evolution). Determinism unaffected: `tc` frames are web-API artifacts like `time`/`rejection`; the byte-authoritative TM stream (SIM-REQ-TIME-005) is untouched. |
| SDD | §3.2 web classes and §3.5 gateway kind set amended at implementation; §5.5 sequence unchanged in substance. |
| mcp-gateway | `TmLog.accept` admits kind `tc`; `get_packet_log` filter extended. No new dependency (SRF unaffected). |
| README | Shared-console wording (PR #85) can be strengthened again once implemented; M1g status row at the gate. |
| SPR register | None — the baseline never specified TC broadcast; the inaccurate claim lived in the README (not a controlled CI) and was corrected editorially. Evolution per SDP §2.4 demarcation. |
| Implementation code | None in this SCR (specification level). |

## 4. Disposition

- [ ] Approved — project lead, via review and merge of this specification
      PR.

## 5. Findings during implementation

*(to be completed)*
