# Design note — MCP gateway: agentic command & control

- Status: **concept, non-normative** (pre-SCR discussion paper)
- Date: 2026-07-20
- Relates to: README "Ideas beyond the current plan", ADR-0001, ADR-0005,
  ICD §8, SDP §4 (M2)
- This note records a design discussion. Nothing in it is baselined; the
  feature would enter the baseline via an SCR with its own impact analysis
  (SDP §2.3), expected after M2.

## 1. The idea

Expose the simulator's TM/TC interface as an
[MCP](https://modelcontextprotocol.io) server so that an AI agent can
*operate* the spacecraft: compose and send telecommands, monitor live
telemetry, react to TM(1,8) failure reports and off-nominal housekeeping
values. The AI that built the simulator becomes its operator — with the ICD
as its manual and the validation suite as its safety net.

## 2. Architecture position: the AI sits in the operator's chair

The MCP server is a **ground-segment client** of the simulator's public
interface — a separate `mcp-gateway` module running as its own process,
exactly like the future Yamcs attachment (ADR-0005). It is *not* an
endpoint embedded in the Spring backend.

- The simulator remains untouched; the gateway is "just another external
  client" on the seam ADR-0005 built.
- The gateway reuses `pus-core` for byte-exact TC encoding and TM decoding
  (framework-free by design, CLAUDE.md rule 5).
- The AI gets no privileged access: it operates through the same interface
  any operator or MCS would use. If it wants to know spacecraft state, it
  must command housekeeping.

**Transport: adapter-based, REST/WS first.** MCP transport (AI ↔ gateway:
stdio or Streamable HTTP) and link transport (gateway ↔ simulator) are
independent choices. For the link, the gateway core is written against a
thin `PacketLink` adapter seam — everything that matters (ring buffer,
tools, authority bounds, ops log) deals in space packets via `pus-core`
and is transport-independent; the adapters differ only in framing.

- **v1 adapter: REST/WS (ICD §8.1/§8.2).** Available today; carries the
  ground diagnostic channels that exist only there (`rejection` reasons,
  `time` frames — on the raw link a malformed TC is silently discarded, as
  in reality); server-side sequence-count management and `preview`; and
  the hosted demo exposes HTTP(S) only, so a REST/WS gateway can operate
  the public instance from anywhere.
- **Post-M2 adapter: the TCP length-framed link (ICD §8.3).** The
  purity/realism step: the gateway then sits on exactly the seam a real
  MCS (Yamcs) uses — byte-authoritative, OBT known only from TM
  timestamps, no rejection oracle — and doubles as M2's "external client
  demo" exit criterion. Requires co-location with the simulator.

This also decouples the MCP SCR from M2 sequencing: the gateway can enter
whenever prioritized, and the TCP adapter joins when M2 lands.

## 3. Tool surface (v1): PUS level, with a raw escape hatch

The agent composes PUS itself — that competence is the point of the demo.
No ops-level convenience verbs (`enable_housekeeping(sid)` etc.) in v1;
they would hide exactly the ICD literacy on display.

| Tool | Sketch | Notes |
|---|---|---|
| `send_tc` | `(service, subtype, ackFlags?, appDataHex?)` → injected packet + OBT | Mirrors ICD §8.1 structured compose 1:1; gateway owns the ground sequence count |
| `preview_tc` | same, no injection | Mirrors `POST /api/tc/preview`; lets the agent check bytes against the ICD before sending |
| `send_raw_tc` | `(hex)` | Deliberately unvalidated, like §8.1 raw injection — exercises negative paths and full ICD literacy |
| `get_packet_log` | `(since_cursor, filter?)` → TM/rejection records | Paged pull over a gateway-side ring buffer; rejection diagnostics included (a human operator sees them too) |
| `await_tm` | `(filter, timeout)` → first matching TM or timeout | Blocking tool; makes "react to TM(1,8)" a one-step agent loop instead of poll-spinning |

MCP **resources**: the ICD itself (the manual, literally), current OBT,
gateway state (next sequence count, TC budget remaining).

## 4. Boundaries

1. **The space/ground boundary is the ICD, full stop.** The agent sees TM
   packets and the §8.2 rejection diagnostic channel, nothing else. No tool
   reads simulator internals.
2. **Operator vs. test conductor.** Time control, fault injection, and
   reset are test-bench controls, not operator tools. v1 exposes the
   operator persona only; a conductor namespace (or second server) would be
   a later, separate SCR — mirroring the real-world role split.
3. **Bounded authority, enforced by the gateway, not the prompt:** allowed
   service/subtype list, TC rate/budget per session, optional human
   confirmation for flagged commands (MCP elicitation). Every tool call is
   recorded in an **ops log** — the audit-trail principle applied to
   operations.
4. **Determinism boundary.** Agent sessions are interactive and wall-paced;
   they never participate in verification. The replay/determinism story
   (SIM-REQ-TIME-005) is unaffected.

## 5. Deployment pictures (increasing ambition)

1. **Local**: Claude Code / Claude Desktop → stdio gateway → local
   simulator. The README demo GIF.
2. **Public**: gateway with Streamable-HTTP transport next to the hosted
   demo instance — visitors' agents operate the shared spacecraft, and
   everyone watches the AI's TCs appear in the shared live log. Requires
   the authority bounds of §4.3 (rate limits, TC budget) as a precondition,
   not an afterthought.
3. **Operator evals**: a headless agent harness runs scripted scenarios —
   which is where this idea fuses with the fault-injection idea: inject a
   fault, require the agent to detect it in housekeeping and respond.
   Evaluation criteria written before shipping, for the AI operator.

## 6. Verification approach

- The **gateway is deterministic software** and is validated normally:
  SRS requirements (new SIM-REQ-MCP group) → SVS cases → tests driving it
  with a *scripted* MCP client (tool call in → exact TC bytes on the wire;
  TM in → exact tool result).
- The **AI's behavior is explicitly not the unit under test** in the ECSS
  baseline. Agent competence is assessed by the operator-eval scenarios of
  §5.3, outside the verification chain.

## 7. Process path (when this becomes real)

1. SCR with per-document impact analysis; schedulable independently of M2
   (the REST/WS adapter needs nothing from M2; the TCP adapter joins once
   M2 lands).
2. Dependency approval **before any code** (CLAUDE.md rule 8): official MCP
   Java SDK (MIT), recorded in the Software Reuse File.
3. New SRS group SIM-REQ-MCP-xxx; SVS cases per §6; ICD gains an MCP
   companion section or document (tool contract ↔ §8 mapping).
4. Module layout: `mcp-gateway` alongside `simulator`, depending on
   `pus-core` only (no Spring types outside `simulator` — the gateway
   would use the MCP SDK's own runtime).

## 8. Candidate demo scenarios

- **Ping chain**: agent sends TC(17,1) with default ack flags, awaits and
  explains TM(1,1) → TM(17,2) → TM(1,7).
- **TM(1,8) recovery**: agent enables a housekeeping SID that does not
  exist, receives TM(1,8), reads the failure code, consults the ICD
  resource, corrects the command, verifies TM(3,25) arrives.
- **Watchdog**: agent monitors housekeeping over N cycles and reports an
  out-of-family value (compelling once subsystem simulation or fault
  injection exists to make values move).

## 9. Open questions (for the eventual SCR)

- MCP transport for the public deployment: authentication story, session
  TC budget sizing, abuse handling on the shared instance.
- Whether the TCP adapter is worth building before the Yamcs attachment
  (M4) needs it, given the diagnostic channels lost relative to REST/WS.
- `await_tm` filter language: field-match only, or predicate expressions?
- Ops-log format: free-form JSONL vs. a committed report format like the
  milestone gate records.
- Whether the ICD resource is served verbatim from `docs/icd.md` or as a
  versioned copy pinned to the deployed simulator build.
