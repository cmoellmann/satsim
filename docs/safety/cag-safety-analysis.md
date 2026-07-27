# SatSim Command Authorization Gate — Software Safety Analysis (CAG-SSA)

- Configuration item: SATSIM-CAG-SSA, Issue 1 (draft)
- Applies to: `ops-cag` (Command Authorization Gate), the one configuration item
  engineered to the ECSS Category B **technical** bar inside an otherwise
  Category D product (SDP §1.1, [ADR-0007](../adr/ADR-0007-command-authorization-gate.md)).
- Introduced by: [SCR-011](../scr/SCR-011-cag-assurance.md) (M1i), discharging ADR-0007 C7.
- Analysed baseline: `ops-cag` and `mcp-gateway` as delivered at tag `M1h`.
- Related: [SRS](../srs.md) SIM-REQ-CAG-001…006 and SIM-REQ-CAG-SAFE-001…005,
  [SVS](../svs.md) SIM-TC-048…060, [ICD](../icd.md) §8.4.

## 1. Purpose, scope and the claims this document does not make

This document does three things and nothing else:

1. a **hazard analysis** of the command path the gate sits in (§3);
2. a **software FMEA** over the gate's decode → classify → decide → log chain,
   one row per credible failure mode of each step (§4);
3. the **derived safety requirements** that fall out of the FMEA, each traced to
   the hazards it bounds and the validation case that verifies it (§5, §7).

**What is analysed.** The path from an operator client's request to inject a
telecommand, through the gate, to the telecommand reaching the spacecraft. That
path spans two configuration items: `ops-cag` (Category B technical bar) makes
the decision; `mcp-gateway` (Category D) presents the octets to the gate and
honours the verdict. The analysis deliberately crosses that boundary, because
several of the sharpest failure modes live in the Category D component that
*carries* the Category B decision (FM-01, FM-19, FM-21). Saying "out of scope,
different CI" about those would defeat the purpose of the analysis.

**What is not analysed.** Everything ADR-0007 C8 already excludes: the on-board
software's own behaviour, the correctness of a command's *content* as opposed to
its *class*, the simulator's Category D internals, and the operator host's
operating-system security. Those are stated again as scope limits in §6.

**Severity convention.** Severity is assessed against the **represented**
system — a spacecraft command path — not against today's simulated one. In the
current product the spacecraft is a Java process and the real-world severity of
every hazard below is *negligible*. The analysis is nonetheless conducted at
flight severity, because that is the basis on which ADR-0007 claims the Category
B technical bar, and an analysis conducted at PoC severity would justify no bar
at all. This is a deliberate, stated conservatism, not a claim that SatSim
commands a real spacecraft.

Severity classes: **Catastrophic** (loss of mission/vehicle), **Critical**
(loss of a mission-essential function), **Major** (degraded operations,
recoverable), **Minor** (operational nuisance), **Negligible**.

**No compliance claim is made for independence.** This analysis was produced by
the same single developer (AI-assisted) who produced the design. ECSS
independence for Category B verification is **not satisfied**; it is recorded as
a deviation in SDP §2.1, unchanged by this document. See also
`docs/eval/ai-independence-experiment.md`, which is an experiment and explicitly
**not ISVV**.

## 2. The command path under analysis

The chain, as built at `M1h`. Steps S1 and S8–S9 are `mcp-gateway`; S2–S7 are
`ops-cag`.

| Step | Where | What happens |
|---|---|---|
| S1 | `Gateway.sendTc` / `sendRawTc` / `authorize` | The octets to be authorized are obtained: verbatim from the client (`send_raw_tc`), or by asking the simulator to **preview** the structured compose (`send_tc`). Hex is parsed to octets. |
| S2 | `CommandAuthorizationGate.discardUnknownConfirmations` | The confirmation channel is polled; every recorded token matching no pending hold is discarded unhonoured and reported for logging. |
| S3 | `CommandAuthorizationGate.decide` → `TcPacket.decode` | The octets are decoded per ICD §3. Any failure is a rejection. |
| S4 | `CommandSummary.of` | The decoded packet is reduced to (service, subtype, ackFlags, appDataHex) and an `identity()` string. The CCSDS sequence count is deliberately excluded. |
| S5 | `ClassificationTable.tierOf` | (service, subtype) is mapped to exactly one authority tier. No entry ⇒ no tier ⇒ rejection. |
| S6 | `Allowlist.allows` | The session's configured (service, subtype) restriction is applied. |
| S7 | `decideStateChanging` | Observation / benign write ⇒ FORWARD. State-changing write ⇒ released only by a recorded confirmation for an outstanding hold of the **same identity**, consuming both; otherwise held under a fresh token. |
| S8 | `Gateway.authorize` | The gateway injects **if and only if** `decision.forwards()`; the session TC budget may still downgrade a forward to a rejection. |
| S9 | `OpsLog.record` | One JSONL record per tool invocation, carrying decision, reason, tier, token and ignored confirmations. |

Two structural properties matter throughout and are relied on by several rows
below:

- **One decoder.** The gate decodes with `pus-core` `TcPacket.decode`; the
  simulator ingests with the *same* method (`SimulationService`,
  `PusSimulatedObsw`). Gate/spacecraft interpretation divergence is excluded by
  shared code, not by agreement between two implementations.
- **One injection call site.** `Gateway.authorize` is the only route from an MCP
  tool to an injection, and it is guarded by `decision.forwards()`.

## 3. Hazard analysis

| ID | Hazard | Cause class | Severity | Bounded by |
|---|---|---|---|---|
| H-1 | **Unauthorized state change.** A telecommand that changes spacecraft state reaches the spacecraft without a human having confirmed it. | Misclassification, hold bypass, confirmation replay/misbinding, decision not honoured | Catastrophic | SIM-REQ-CAG-SAFE-001, -002, -004 |
| H-2 | **Unclassified command forwarded.** Octets the gate could not decode or could not classify reach the spacecraft, so the authority model does not apply to them at all. | Decode error treated as pass, table gap defaulted to a tier, exception escaping the gate | Critical | SIM-REQ-CAG-SAFE-002 |
| H-3 | **Loss of accountability.** A command reaches the spacecraft with no ops-log record of the decision behind it, so an incident cannot be reconstructed and the barrier cannot be audited. | Log write failure, decision discarded on an error path, record written after the injection | Major | SIM-REQ-CAG-SAFE-003 |
| H-4 | **Confirmation misbinding or replay.** A human confirmation authorizes a command other than the one confirmed, or authorizes more than one execution of it. | Token transplanted between commands, confirmation surviving its hold, token reuse across restart | Catastrophic | SIM-REQ-CAG-SAFE-001, -004 |
| H-5 | **Loss of command authority.** The gate rejects or holds commands it should forward, to the point that legitimate operations cannot be conducted. | Channel unreadable, table gap for a legitimate command, hold store exhausted | Major | Fail-closed by design; §6 R-3 |
| H-6 | **Containment misrepresented.** The documented containment claim is broader than what the gate actually enforces, so operators rely on a barrier that is not there. | Prose claim not matching the enforced path; the direct web-API route | Critical | SIM-REQ-CAG-SAFE-005, SIM-TC-058 |

H-5 deserves a word. A safety barrier that fails to the point of unusability
does not stay installed — operators remove it or route around it, and the
resulting hazard is H-1 by a slower path. Availability is therefore treated as a
safety concern here, not merely a quality one, which is why the fail-closed
behaviour of FM-04 and FM-13 is analysed rather than simply approved.

## 4. Software FMEA

Detection column: **A** = detected automatically at run time (rejection +
logged reason), **T** = detected by the validation suite, **R** = detected by
review only. Residual is the risk remaining after the mitigation in place at
`M1h`.

### S1 — Intake (gateway, Category D)

| ID | Failure mode | Cause | Local effect | Command-path effect | Hazard | Det. | Mitigation in place | Residual |
|---|---|---|---|---|---|---|---|---|
| FM-01 | The octets classified are not the octets injected (`send_tc`): the gate authorizes the **previewed** compose while `submitStructured` re-encodes server-side. | Divergence between the simulator's preview and submit encoders; a compromised or faulty simulator | A state-changing command is classified from octets that decode as a benign one | Unconfirmed state change | H-1 | T | Both endpoints derive from the same ICD §8.1 compose of the same arguments in the same simulator process; SIM-TC-048 asserts tier assignment follows decoded octets for both tools | **Accepted, stated:** a Category B decision depends on a Category D endpoint's honesty for `send_tc`. See §6 R-1. |
| FM-02 | Malformed hex from the client. | Client error | `parseHex` throws, caught | Rejected, nothing injected | — | T | `MALFORMED_INPUT` before the gate is reached | None |
| FM-03 | Client supplies octets the gate decodes differently from the spacecraft. | Two decoder implementations | Misclassification | Unconfirmed state change | H-1 | R | **Excluded by construction:** one `pus-core` decoder, shared by gate and simulator (§2) | None |

### S2 — Confirmation sweep (`ops-cag`)

| ID | Failure mode | Cause | Local effect | Command-path effect | Hazard | Det. | Mitigation in place | Residual |
|---|---|---|---|---|---|---|---|---|
| FM-04 | Confirmation channel unreadable. | I/O error, directory removed, permissions | `recorded()` returns the empty set | Held commands stay held; nothing is released | H-5 | A | Deliberate catch returning `Set.of()` — an unreadable channel confirms nothing. Fail-closed in the safe direction | Availability only; see §6 R-3 |
| FM-05 | A token the gate never issued is present in the channel. | Invented by an attacker, left by a prior session, guessed | Would match a hold if honoured | Release of a held command without a human decision | H-1, H-4 | T | `discardUnknownConfirmations` discards every token with no pending hold, before any decision; token must *also* belong to a hold of the same identity at S7 | None |
| FM-06 | A confirmation file survives a gateway restart while its in-memory hold does not. | Holds are in memory, the channel is on disk | After restart, `issuedTokens` resets to 0, so the next hold for the **same command** is issued the **identical** token `H1-<digest>` — the stale file would release it silently | Unconfirmed state change after a restart | H-1, H-4 | T | `channel.clear()` in the gate constructor drops every recorded confirmation at startup. This mitigation is **load-bearing**: without it the deterministic (non-random) token scheme would make the collision systematic rather than unlikely | None, given the constructor contract |
| FM-07 | Confirmation recorded between classification and hold matching. | External `CagConfirm` process races `decide` | Confirmation seen on the next call, not this one | Command held one extra round trip | — | R | `decide` is `synchronized`; the channel is read once per decision inside it | Nuisance only |

### S3 — Decode (`ops-cag`)

| ID | Failure mode | Cause | Local effect | Command-path effect | Hazard | Det. | Mitigation in place | Residual |
|---|---|---|---|---|---|---|---|---|
| FM-08 | Octets do not decode per ICD §3 (bad CRC, wrong PUS version, too short, length mismatch). | Corruption, client error, deliberate probe | `PacketDecodeException` | Rejected with `UNDECODABLE:` + reason; nothing injected | H-2 | T | Explicit catch ⇒ reject. ICD Issue 8 withdrew the M1f carve-out that let undecodable raw octets through | None |
| FM-09 | Decode throws an **unchecked** exception the decoder's own guards did not catch. | `IllegalArgumentException`, `ArrayIndexOutOfBoundsException` on a structurally impossible packet | Caught | Rejected as undecodable | H-2 | T | Second catch clause, present precisely for this | None |
| FM-10 | Decode throws an unchecked exception of **another** type (e.g. `NullPointerException`), or an `Error`. | Defect in `pus-core`, resource exhaustion | Propagates out of `decide` | The gateway's tool wrapper catches it, returns an error result, and **injects nothing** — the injection is inside the `FORWARD` branch that is never reached | H-2 | T | Fail-closed by control flow: an exception cannot produce a forward | Accountability only — the decision is lost, see FM-21 |
| FM-11 | Octets decode but are semantically invalid for the spacecraft (e.g. V-NEG-02). | Legitimate negative test, operator error | Decodes as TC(17,1), classifies benign, forwarded | The spacecraft's own rejection path runs and emits TM(1,2) | — | T | **Intended.** The gate authorizes by class, not by spacecraft-level validity; SIM-TC-049 asserts this contrast explicitly | None |

### S4–S5 — Summarize and classify (`ops-cag`)

| ID | Failure mode | Cause | Local effect | Command-path effect | Hazard | Det. | Mitigation in place | Residual |
|---|---|---|---|---|---|---|---|---|
| FM-12 | Two materially different commands share one `identity()`. | Identity omits a field that changes what the command does | A confirmation for one releases the other | Unconfirmed state change | H-4 | T | Identity covers service, subtype, ackFlags and the **complete** application data — every field that determines what the command does. The CCSDS sequence count is excluded deliberately and with reasons stated in `CommandSummary` | None for the ICD's tailored command set |
| FM-13 | (service, subtype) has no classification-table entry. | A telecommand added to the ICD without being tabled | `tierOf` returns empty | Rejected `UNCLASSIFIED`; nothing injected | H-2 → H-5 | T | No default tier exists; a gap is a rejection, never a silent observation. Static table, reviewed change | Availability: adding an ICD command without tabling it blocks it. Deliberate |
| FM-14 | A tabled entry carries the **wrong** tier — a state-changing command tabled as benign. | Editing error in the table | Forwarded without confirmation | Unconfirmed state change | H-1 | T | Four entries, static, no dynamic loading. SIM-TC-048 asserts the tier of every tabled pair against SIM-REQ-CAG-004 | Review-dependent; SIM-TC-053…057 enumerate the table so a new entry cannot be added untested |
| FM-15 | Key collision in the table: `(service << 8) \| subtype`. | Service or subtype exceeding one octet | Two pairs map to one entry | Misclassification | H-1 | R | Both fields are single octets in the decoded packet (ICD §3), so the packing is injective over the decoded domain | None |

### S6–S7 — Allowlist and decision (`ops-cag`)

| ID | Failure mode | Cause | Local effect | Command-path effect | Hazard | Det. | Mitigation in place | Residual |
|---|---|---|---|---|---|---|---|---|
| FM-16 | Allowlist consulted before classification, letting an untabled pair through. | Ordering defect | Untabled command forwarded | Unclassified command reaches the spacecraft | H-2 | T | Order is fixed: classify (S5) **then** allowlist (S6). A gap rejects before the allowlist is asked | None |
| FM-17 | A confirmation is transplanted onto a different command. | Human confirms token X while command Y is submitted | — | Unconfirmed state change | H-4 | T | Release requires **both** `hold.identity().equals(identity)` **and** the token recorded; the token alone releases nothing | None |
| FM-18 | A confirmation releases more than one execution. | Confirmation or hold not consumed | Repeated state change on one human decision | H-1, H-4 | H-4 | T | Both the hold and the recorded confirmation are removed on release; the next identical submission is held under a fresh token. SIM-TC-051 asserts exactly this | None |
| FM-19 | Unbounded growth of the hold store. | Each re-submission of an unconfirmed state-changing command adds a `LinkedHashMap` entry; nothing evicts | Memory pressure in the gateway | Gateway degradation, ultimately no commanding at all | H-5 | R | The gateway's session TC budget bounds injection attempts per session, bounding holds indirectly. The gate itself imposes no bound | **Open finding F-2** (§6). No behaviour change in M1i per SCR-011 |
| FM-20 | Token collision within a session. | `issuedTokens` overflow after 2³¹ holds | Two holds share a token | Wrong command released | H-4 | R | Strictly increasing counter; overflow is unreachable within any real session and is bounded by FM-19's budget in any case | Negligible |

### S8–S9 — Consume the verdict and record it (gateway, Category D)

| ID | Failure mode | Cause | Local effect | Command-path effect | Hazard | Det. | Mitigation in place | Residual |
|---|---|---|---|---|---|---|---|---|
| FM-21 | A gate decision is **not recorded** although the injection happened. | `OpsLog.record` runs only after `body.apply` returns normally. If the injection succeeds but the tool body then throws, the wrapper replaces the outcome with `plain(error(...))`, whose decision is `null` — so the record carries no `gateDecision`. A log write failing after a successful injection loses the record entirely | Injection performed, decision absent from the evidence trail | Command at the spacecraft with no recorded authorization | H-3 | T | Records are flushed per line; the gate's decision is otherwise recorded for every invocation, including rejected and held ones | **Open finding F-1** (§6) |
| FM-22 | The gateway injects despite a non-forward verdict. | Defect at the single call site | Barrier bypassed entirely | Unconfirmed or unclassified command reaches the spacecraft | H-1, H-2 | T | Exactly one injection call site, inside the `FORWARD` arm of a `switch` over the outcome; SIM-TC-049/050 assert nothing is injected on reject and hold | None |
| FM-23 | An injection route exists that does not pass the gate. | The simulator's own web API `POST /api/tc` is reachable directly | The gate is not consulted at all | Any command reaches the spacecraft | H-6 | T | **Not mitigated, and not claimed to be.** Containment holds for the MCP tool path only; SIM-TC-058 demonstrates the bypass succeeding, as executable documentation | **By design**, stated in SIM-REQ-CAG-SAFE-005 and §6 R-2 |
| FM-24 | The budget downgrade discards the classification evidence. | `rejectedInstead` rebuilds the decision | Tier lost from the record | Weaker evidence trail | H-3 | T | `rejectedInstead` deliberately preserves `tier` and `command` | None |

## 5. Derived safety requirements

The FMEA yields five derived safety requirements, tabled in the SRS as
**SIM-REQ-CAG-SAFE-001…005** (scope M1i, per SCR-011 §1), plus the coverage
requirement **SIM-REQ-QA-004**.

| Derived requirement | Bounds | From FMEA rows |
|---|---|---|
| SIM-REQ-CAG-SAFE-001 — every state-changing telecommand is held pending a recorded confirmation and is never auto-forwarded | H-1, H-4 | FM-14, FM-17, FM-18, FM-22 |
| SIM-REQ-CAG-SAFE-002 — fail-closed on every decode and classification error path | H-2 | FM-08, FM-09, FM-10, FM-13, FM-16 |
| SIM-REQ-CAG-SAFE-003 — no authorization decision without an ops-log record carrying decision, reason and tier | H-3 | FM-21, FM-24 |
| SIM-REQ-CAG-SAFE-004 — confirmation state is not lost across a restart in any way that results in an unconfirmed forward | H-1, H-4 | FM-05, FM-06, FM-20 |
| SIM-REQ-CAG-SAFE-005 — scope statement: containment is claimed for the gateway tool path only, and not against direct web-API access | H-6 | FM-23 |
| SIM-REQ-QA-004 — 100 % statement and decision coverage on `ops-cag`, enforced at the gate | all | the Category B bar itself (ADR-0007 C7) |

### Proposed additional derived requirements (not yet approved)

Per CLAUDE.md rule 3 these are **proposals** and apply only after human approval;
they are outside the SCR-011 scope as approved and are recorded here so the FMEA
is not silently truncated to the requirements already agreed.

- **P-1 (from FM-01):** the octets the gate classifies and the octets finally
  injected shall be shown to agree on service type, message subtype,
  acknowledgement flags and application data. Today this holds by construction
  and is asserted end-to-end by SIM-TC-048; making it a requirement would give
  the `send_tc` path an explicit obligation rather than an inherited one.
- **P-2 (from FM-19):** the gate shall bound the number of simultaneously
  pending holds and reject further state-changing submissions once that bound is
  reached. This is a **behaviour change** and therefore explicitly outside M1i
  (SCR-011 §1); it would need its own SCR. *Project lead 2026-07-27: an SCR is to
  be raised later; the risk is accepted for M1i (F-2).*

*P-1 status: deferred by the project lead on 2026-07-27 — no requirement is added
in M1i, and the property continues to hold by construction and to be asserted
end-to-end by SIM-TC-048.*

## 6. Residual risks, scope limits and open findings

**Residual risks accepted at this baseline.**

- **R-1 (FM-01).** For `send_tc`, the Category B classification input is
  obtained from a Category D endpoint (the simulator's ICD §8.1 preview). The
  gate's decision is therefore only as trustworthy as that endpoint. The
  `send_raw_tc` path has no such dependency. Accepted: the simulator is the
  system being protected, not an adversary in this threat model, and a
  compromised simulator makes the gate moot by other routes anyway.
- **R-2 (FM-23).** Structural containment is limited to the MCP tool path. Any
  party that can reach the simulator's HTTP API, or run `CagConfirm` on the
  operator host, is outside the barrier. This is ADR-0007 C8 restated, and
  SIM-TC-058 makes it executable rather than merely written down.
- **R-3 (FM-04, FM-13).** Every failure of the gate's inputs degrades
  availability, not authority. This is the intended direction, but it means a
  misconfigured confirmation channel silently blocks all state-changing
  commanding until a human notices the held tokens.

**Open findings for disposition.** Both concern the Category D gateway, not the
`ops-cag` module, and both would require a behaviour change that SCR-011 §1
places outside M1i.

| ID | Finding | From | Proposed disposition |
|---|---|---|---|
| F-1 | The gate decision is recorded only when the tool body completes normally, and the record is written **after** the injection. An injection that succeeds and then fails downstream is logged without its `gateDecision`; a log-write failure after a successful injection loses the record entirely. SIM-REQ-CAG-SAFE-003 as derived above is satisfied on every path exercised today, but not by construction. | FM-21 | **Dispositioned 2026-07-27 (project lead): raised as [SPR-007](../spr/SPR-007-ops-log-decision-ordering.md)**, Open, severity major, against the `mcp-gateway` baseline (SDP §2.4). Fix in a follow-up increment by recording the decision **before** the injection is attempted, plus a record on the exception path. Not fixed in M1i. |
| F-2 | The gate imposes no bound on pending holds; only the gateway's session TC budget bounds them indirectly. | FM-19 | **Dispositioned 2026-07-27 (project lead): accepted risk R-3 for M1i**, with an SCR to be raised later for the bound itself (proposal P-2), since a bound changes gate behaviour. |

## 7. Verification map

Each derived requirement's validation case, per SVS. SIM-TC-048…052 (M1h)
already carry much of the behavioural evidence and are re-used rather than
duplicated.

| Requirement | Verified by | Method |
|---|---|---|
| SIM-REQ-CAG-SAFE-001 | SIM-TC-050, SIM-TC-051, SIM-TC-059 | A |
| SIM-REQ-CAG-SAFE-002 | SIM-TC-053, 054, 055, 056, 057 | A |
| SIM-REQ-CAG-SAFE-003 | SIM-TC-052, SIM-TC-053…057 (logged decision per error path) | A |
| SIM-REQ-CAG-SAFE-004 | SIM-TC-051 (restart clause), SIM-TC-056 | A |
| SIM-REQ-CAG-SAFE-005 | SIM-TC-058 | A |
| SIM-REQ-QA-004 | SIM-TC-060 | A |

Hazard coverage: H-1 (SAFE-001/-004), H-2 (SAFE-002), H-3 (SAFE-003), H-4
(SAFE-001/-004), H-5 (fail-closed by design; accepted risk R-3, no requirement),
H-6 (SAFE-005).

## Change log

| Issue | Date | Change |
|---|---|---|
| 1 (draft) | 2026-07-26 | Initial issue: hazard analysis H-1…H-6, software FMEA FM-01…FM-24 over the M1h baseline, derived safety requirements SIM-REQ-CAG-SAFE-001…005, proposals P-1/P-2, open findings F-1/F-2. Per SCR-011 (M1i), discharging ADR-0007 C7. |
