# M1f Recorded MCP Operator Demo — Transcript and Audit Trail

- Recorded: 2026-07-20, against baseline commit 365d3ae (gate branch)
- Operator: headless Claude Code session, model `claude-sonnet-5` — deliberately a *routine-tier* model: the ICD as MCP resource is the only spacecraft knowledge source
- Setup: simulator with interactive 1:1 pacing; `mcp-gateway` spawned over stdio by the MCP client (production transport); default allowlist `3,17`, session budget 100; tool access restricted to the five satsim MCP tools + MCP resource reads
- Tasking (verbatim intent): perform the ping chain with full acknowledgement; induce a fault by enabling housekeeping for an uncreated SID; diagnose the failure report against the ICD; recover and confirm reporting

## Operator´s report (agent output, unedited)

All three phases complete and verified against the ICD. Here is the operator's report.

## Operator's Report — Pass Plan Execution

**1. PING — TC(17,1), ack=0b1001**

Composed/sent: `18 64 C0 00 00 06 29 11 01 00 00 52 FF` (seq 0) — matches ICD reference vector V-TC-06 byte-for-byte.

Full verification chain received, all bearing request ID `18 64 C0 00`:

| Packet | Hex |
|---|---|
| TM(1,1) acceptance | `08 64 C0 2D 00 12 20 01 01 00 00 00 00 00 00 00 2D A8 F6 18 64 C0 00 0E 99` |
| TM(17,2) connection-test response | `08 64 C0 2E 00 0E 20 11 02 00 00 00 00 00 00 00 2D A8 F6 44 F5` |
| TM(1,7) successful completion | `08 64 C0 2F 00 12 20 01 07 00 00 00 00 00 00 00 2D A8 F6 18 64 C0 00 B3 94` |

Wire ordering matches §10.2 (TM(1,1) → service TM → TM(1,7)). OBT 45.660 s for all three. Nominal.

**2. FAULT — TC(3,5), enable HK for SID 2 (uncreated)**

Sent `TC(3,5)` N=1, SID=2 (appData `0001 0002`), seq 1. Structurally valid → accepted (TM(1,1) issued), but execution failed:

`TM(1,8)`: `08 64 C0 A2 00 14 20 01 08 00 00 00 00 00 00 00 9E DC 29 18 64 C0 01 00 06 AB 3C`

- Report type: **TM(1,8) failed completion of execution verification**
- Failure code: **0x0006**
- Name per ICD §10.4: **UNKNOWN_SID** — "TC(3,5)/TC(3,7) referencing a SID that does not exist (§9.3, atomic)"

Diagnosis: per §9.3, enable/disable requires the SID to already exist; SID 2 had never been created via TC(3,1). No housekeeping state was changed (atomic failure).

**3. RECOVER — create SID 2, enable, confirm reporting**

- `TC(3,1)` create SID 2, collection interval 2000 ms, params HK-P001 (0x0001) + HK-P002 (0x0002), seq 2 → TM(1,1)+TM(1,7) both successful (request ID `18 64 C0 02`).
- `TC(3,5)` enable SID 2, seq 3, sent at OBT 201.06 s → TM(1,1)+TM(1,7) successful (request ID `18 64 C0 03`).
- First `TM(3,25)` for SID 2 arrived at OBT 203.06 s (= t0 + 1·interval, per §9.3 rule), then every 2000 ms thereafter as expected:

`08 64 C0 D4 00 18 20 03 19 00 CB 00 00 00 00 00 CB 0F 5C 00 02 00 00 00 04 00 00 00 D4 87 30`

Decoded application data: **SID = 0x0002 (2)**, HK-P001 (TC accepted count) = 4, HK-P002 (TM emitted count, sampled pre-emission) = 212. SID field confirms the report is for our newly created/enabled structure, interleaved on the link with the default SID 1's 1 Hz reports (as expected — both structures run concurrently).

**Summary:** Pass executed nominally end-to-end. One deliberate fault (UNKNOWN_SID, 0x0006) was induced and correctly diagnosed against ICD §9.1/§9.3/§10.4, then recovered by creating the missing structure before re-enabling. No anomalies outside the intended fault injection.

## Gateway ops log (complete, unedited — the audit trail per SIM-REQ-MCP-006)

```
{"tool":"send_tc","params":{"service":17,"subtype":1,"ackFlags":9},"outcome":"ok","obt":{"timeFine":39322,"timeCoarse":45,"timeSeconds":45.600006103515625}}
{"tool":"await_tm","params":{"service":1,"subtype":1,"timeoutMs":5000},"outcome":"ok","obt":{"timeFine":0,"timeCoarse":55,"timeSeconds":55.0}}
{"tool":"get_packet_log","params":{"kind":"tm"},"outcome":"ok","obt":{"timeFine":45875,"timeCoarse":57,"timeSeconds":57.69999694824219}}
{"tool":"preview_tc","params":{"service":3,"subtype":5,"ackFlags":9,"appDataHex":"00010002"},"outcome":"ok","obt":{"timeFine":13107,"timeCoarse":156,"timeSeconds":156.1999969482422}}
{"tool":"send_tc","params":{"service":3,"subtype":5,"ackFlags":9,"appDataHex":"00010002"},"outcome":"ok","obt":{"timeFine":52429,"timeCoarse":158,"timeSeconds":158.8000030517578}}
{"tool":"await_tm","params":{"service":1,"subtype":8,"timeoutMs":5000},"outcome":"ok","obt":{"timeFine":32768,"timeCoarse":167,"timeSeconds":167.5}}
{"tool":"get_packet_log","params":{"kind":"tm","service":1,"subtype":8},"outcome":"ok","obt":{"timeFine":0,"timeCoarse":171,"timeSeconds":171.0}}
{"tool":"preview_tc","params":{"service":3,"subtype":1,"ackFlags":9,"appDataHex":"0002000007D0000200010002"},"outcome":"ok","obt":{"timeFine":45875,"timeCoarse":187,"timeSeconds":187.6999969482422}}
{"tool":"send_tc","params":{"service":3,"subtype":1,"ackFlags":9,"appDataHex":"0002000007D0000200010002"},"outcome":"ok","obt":{"timeFine":45875,"timeCoarse":190,"timeSeconds":190.6999969482422}}
{"tool":"get_packet_log","params":{"kind":"tm","service":1},"outcome":"ok","obt":{"timeFine":0,"timeCoarse":193,"timeSeconds":193.0}}
{"tool":"preview_tc","params":{"service":3,"subtype":5,"ackFlags":9,"appDataHex":"00010002"},"outcome":"ok","obt":{"timeFine":19661,"timeCoarse":197,"timeSeconds":197.3000030517578}}
{"tool":"send_tc","params":{"service":3,"subtype":5,"ackFlags":9,"appDataHex":"00010002"},"outcome":"ok","obt":{"timeFine":0,"timeCoarse":201,"timeSeconds":201.0}}
{"tool":"await_tm","params":{"service":3,"subtype":25,"timeoutMs":7000},"outcome":"ok","obt":{"timeFine":0,"timeCoarse":210,"timeSeconds":210.0}}
{"tool":"get_packet_log","params":{"kind":"tm","service":3,"subtype":25,"afterCursor":169},"outcome":"ok","obt":{"timeFine":32768,"timeCoarse":235,"timeSeconds":235.5}}
```
