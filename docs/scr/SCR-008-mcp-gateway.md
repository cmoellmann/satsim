# SCR-008 — MCP operator gateway: agentic command & control (new increment M1f)

- Status: Implemented (spec level) — spec PR #78 (SCR + SDP), second spec
  PR (ICD Issue 6 §8.4 + SRS + SVS); gateway implementation outstanding
- Date: 2026-07-20
- Originator: project lead (C. Möllmann); drafted by AI assistant per SDP §6
- Affected configuration items: SATSIM-SDP, SATSIM-ICD, SATSIM-SRS,
  SATSIM-SVS, SATSIM-SRF, CLAUDE.md
- Design basis: [docs/notes/mcp-gateway-concept.md](../notes/mcp-gateway-concept.md)
  (non-normative concept note; the decisions below supersede it where they
  differ)

## 1. Change description

New increment **M1f**: a new Maven module `mcp-gateway` — a standalone,
Spring-free process that exposes the simulator's TM/TC interface as an
[MCP](https://modelcontextprotocol.io) server, so that an AI agent can
operate the spacecraft through the same interface any operator uses.

Scope of v1:

1. **Ground-segment client.** The gateway connects to the simulator
   exclusively via the public web API (ICD §8.1 REST TC submission,
   §8.2 WebSocket TM/event stream) using the JDK `java.net.http` client —
   no privileged access to simulator internals. The link sits behind a
   thin adapter seam so the M2 TCP link (ICD §8.3) can be added as a
   second adapter by a later SCR.
2. **MCP server, stdio transport only.** Local operation (an MCP client
   such as Claude Code/Desktop spawns the gateway); no network-exposed
   MCP endpoint in v1 — the public-deployment questions (auth, abuse,
   budgets per session) stay out of scope, per the concept note §9.
3. **Operator persona, PUS-level tool surface** (no ops-level convenience
   verbs, no test-bench controls):
   `send_tc`, `preview_tc` (structured compose per ICD §8.1),
   `send_raw_tc` (deliberately unvalidated raw injection),
   `get_packet_log` (cursor-paged TM/rejection history from a
   gateway-side ring buffer), `await_tm` (blocking wait for a matching TM
   with timeout). MCP resources: the ICD text, current OBT, gateway state
   (sequence count, remaining TC budget).
4. **Authority bounds, enforced by the gateway:** configurable service/
   subtype allowlist and session TC budget; every tool call and result
   appended to an ops log (JSONL).
5. **New third-party dependency** (CLAUDE.md rule 8): official MCP Java
   SDK `io.modelcontextprotocol.sdk:mcp` — MIT license, maintained with
   Spring AI; JSON layer Jackson (Apache-2.0, already in the dependency
   tree via Spring Boot). Exact version and full transitive set to be
   recorded in the Software Reuse File in the implementing PR.

Planned specification entries (proposals per CLAUDE.md rule 3, tabled in
the follow-up spec PR): SRS group **SIM-REQ-MCP-001…006** (server + tool
contract, ground-client boundary, TM access, ICD resource, authority
bounds, ops log); SVS cases **SIM-TC-041…045** (automated, driven by a
*scripted* MCP client); ICD Issue 5 with a new section mapping the MCP
tool contract onto §8.1/§8.2.

Explicitly **not** the unit under test: the AI agent itself. The gateway
is deterministic software validated per the ECSS process; agent competence
is demo/eval material (concept note §5.3, §6), outside the verification
chain.

## 2. Rationale

- Closes the loop the repository's thesis points at: the AI that *builds*
  the simulator becomes its *operator* — with the ICD as its manual and
  the validation suite as its safety net. This is the strongest available
  demonstration of the PoC's claim that bounded AI authority and process
  discipline compose.
- The idea entered the README's future-extensions list via PR #76 with the
  commitment "each would enter via SCR, not by quiet scope growth"; the
  concept note (PR #77) worked out the design position. This SCR is that
  entry.
- v1 needs nothing from M2: the REST/WS interface carries richer ground
  diagnostics (`rejection`, `time` frames) than the raw link and is the
  only interface reachable on the hosted demo. Scheduling before M2
  follows the established M1x insertion pattern (SCR-001/-002/-007).
- stdio-only and operator-only keep the increment session-sized and defer
  every security-sensitive question (public endpoint, test-conductor
  tools) to later SCRs with their own impact analyses.

## 3. Impact analysis

| CI / area | Impact |
|---|---|
| SDP §4 | New milestone row **M1f** inserted between M1e and M2 (label scheme per SCR-001). M2…M5 content unchanged, shifted one increment later. |
| ICD | **No wire-format change, no reference vectors touched.** Follow-up spec PR raises ICD to Issue 5 with a new section defining the MCP tool contract as a transport mapping onto §8.1/§8.2 (tool ↔ endpoint ↔ packet correspondence, error semantics, budget exhaustion). |
| SRS | New requirement group (rule-3 proposals, scope M1f): **SIM-REQ-MCP-001…006** as sketched in §1. Existing requirements unamended. |
| SVS | New automated cases **SIM-TC-041…045**: MCP handshake/tools contract; structured `send_tc` of the V-TC-01 field values injects the vector byte-identically; `await_tm` returns the TM(17,2) response with correct decode; raw negative injection surfaces the §8.2 rejection in `get_packet_log`; allowlist/budget violations produce tool errors and ops-log entries. Driven by a scripted MCP client over stdio — no AI in the loop. |
| SRF (reuse file) | New direct dependency `io.modelcontextprotocol.sdk:mcp` (MIT) + its transitive closure, recorded with version/scope/license in the implementing PR. This SCR presents the license situation per rule 8; Jackson (Apache-2.0) is already registered. |
| CLAUDE.md | Module map gains `mcp-gateway` (controlled-document change via the implementing PR, rule 7). Rule 5 wording ("no Spring types outside `simulator`") holds — the gateway is Spring-free. |
| TraceabilityCheck | No tool change: M1f follows the `M<n><letter>` ordinal scheme; CI pin stays M1e until the M1f gate. |
| ADRs | None contradicted: ADR-0001/0005 seams are the enablers; ADR-0004 untouched (gateway learns OBT from §8.2 frames, reads no wall clock into simulation logic — the gateway is ground infrastructure). If review judges the operator-boundary decisions architecture-shaping, an ADR-0007 may be proposed in the follow-up spec PR. |
| SDD | New module section at implementation (gateway structure, link adapter, ring buffer, threads). |
| SPR register | None — evolution, not correction (SDP §2.4 demarcation). |
| README | M1f status row at the gate; future-extensions bullet updated from idea to increment; demo material (agent transcript/GIF) once the gate is passed. |
| Implementation code | None in this SCR (specification level). Implementation PR(s) follow approval and the follow-up spec PR. |

## 4. Disposition

- [x] Approved — project lead (C. Möllmann), 2026-07-20, via review and
      merge of specification PR #78.

## 5. Findings during implementation

- **ICD issue numbering (spec phase):** the ICD was already at Issue 5 —
  the Issue 5 changes (OP-3 resolution, M1b) had been applied without
  bumping the configuration-item line, which still read "Issue 4". The
  §3 impact analysis therefore names the wrong target issue: the MCP
  section lands as **Issue 6**, §8.4. The stale line was corrected
  editorially in the same spec PR.
- **SVS table split (spec phase):** a stray blank line between
  SIM-TC-038 and SIM-TC-039 (introduced by the SCR-007 spec PR) split
  the SVS table in rendered markdown; removed editorially in the same
  spec PR, recorded in the SVS change log.
