# SCR-004 — Structured HK compose and interpreted TC display (new increment M1c)

- Status: Implemented (spec level; implementation code follows in the M1c session)
- Date: 2026-07-19
- Originator: project lead (C. Möllmann); drafted by AI assistant per SDP §6
- Affected configuration items: SATSIM-SDP, SATSIM-SRS, SATSIM-SVS

## 1. Change description

Three frontend-only usability items, as new increment **M1c**, so that correct
and meaningful ST[3] housekeeping TCs can be assembled and read without
hand-crafted hex:

1. **Structured HK compose fields.** When TC(3,1), TC(3,5) or TC(3,7) is
   selected from the compose dropdowns, the free application-data hex field is
   replaced by structured inputs per ICD §9.2/§9.3 — TC(3,1): SID, collection
   interval in milliseconds, parameter selection per §9.5 in report order;
   TC(3,5)/(3,7): SID list — and the frontend generates the application-data
   octets from them. Free hex entry remains for every other type/subtype
   selection, including the custom… entries, which double as the escape for
   deliberately malformed ST[3] application data. **No ground-side semantic
   validation**: semantically invalid values (SID 0, interval < 100 ms, more
   than 16 entries, …) stay composable so the TM(1,8) failure paths
   (ICD §9.1/§10.4) remain exercisable from the HMI; only structurally
   unencodable input (non-numeric, outside the field's bit range) is rejected
   ground-side.
2. **Interpreted TC detail.** The packet-log detail row decodes
   TC(3,1)/(3,5)/(3,7) application data into named fields (SID, collection
   interval, parameter names per §9.5, SID list); application data not
   matching the ICD layout is marked as such and left as raw hex.
3. **Inline failure code.** TM(1,2)/TM(1,8) log rows show the ICD §10.4
   failure-code name directly in the row (e.g.
   `TM(1,8) · ILLEGAL_COLLECTION_INTERVAL`), so the reason for a TC rejection
   is visible without opening the detail view; unknown codes are shown as hex.

Architecture: frontend-only (`app.js`), symmetric to the existing JS-side
interpretation of TM application data. Headers, sequence counts and CRC remain
backend-encoded — the compose preview still round-trips through
`POST /api/tc/preview` (ICD §8.1), which is unchanged.

## 2. Rationale

- TC(3,1) application data is a five-field variable-length layout; today a
  user must hand-type e.g. `00 02 00 00 13 88 00 02 00 01 00 03` —
  error-prone, and errors become visible only as TM(1,8) reports or
  rejections after sending.
- Asymmetry: TM(3,25) and ST[1] application data is already interpreted in
  the log, while TC application data is raw hex both on entry and on display;
  likewise the failure code exists decoded in the detail view but not in the
  row.
- Teaching value: named fields against ICD §9.2/§9.3 continue the byte-level
  PUS-learning-tool line of SCR-003.

## 3. Impact analysis

| CI / area | Impact |
|---|---|
| SDP §4 | New milestone row **M1c** inserted between M1b and M2 (the label scheme `M<n><letter>` per SCR-001 has a free slot; the package lands in its own session, warranting its own gate). |
| ICD | **None** — first SCR without ICD impact: the §9 application-data layouts are the (unchanged) basis; web API §8.1/§8.2 untouched (`appDataHex` semantics identical, preview endpoint unchanged); no reference vectors added or modified. |
| SRS | New requirement proposals (CLAUDE.md rule 3), scope M1c: **UI-011** structured HK compose, **UI-012** interpreted TC detail, **UI-013** inline failure code. UI-001 ("optional application data (hex)") remains valid — free hex entry is retained for all non-ST[3] selections and for raw injection. |
| SVS | New manual cases, scope M1c: **SIM-TC-034** (M: structured compose reproduces V-TC-03/04/05 byte-identically in the preview), **SIM-TC-035** (M: interpreted TC detail incl. layout-mismatch case), **SIM-TC-036** (M: inline failure code on TM(1,2)/(1,8) rows). No amendment of any approved expected result. |
| TraceabilityCheck | No change: M1c is covered by the `M<n><letter>` ordinal scheme (ordinal 13). The CI pin stays M1b until the M1c gate; the new items are M-verified, so both gates stay finding-free. |
| ADRs | None contradicted; ADR-0005 (own thin frontend) is the enabler. Determinism (SIM-REQ-TIME-005) unaffected — space-link bytes are still produced solely by the unchanged backend encoder. |
| SDD | §3.4 amended at implementation: the "no packet knowledge is duplicated in JavaScript" statement is superseded — the frontend deliberately knows the ICD §9.2/§9.3/§9.5 application-data layouts (compose + interpretation); header/CRC/sequence-count encoding stays backend-only. |
| README | Feature mention in the tour at implementation; M1c status row at the gate. |
| Implementation code | None in this SCR (specification level only). Implementation follows after approval, in the M1c session. |

## 4. Disposition

- [x] Approved — project lead (C. Möllmann), 2026-07-19, via review and merge
      of PR #49 (SCR + SDP + SRS + SVS in one specification PR).

## 5. Findings during implementation

- Extracting the shared `st1FailureCode()` helper (implementation PR #50)
  surfaced a latent display defect: the ST[1] detail view previously sliced
  the trailing 4 hex chars of TM(1,2)/(1,8) application data unconditionally,
  so truncated application data would have shown request-ID bytes as a
  failure code. Fixed with a minimum-length guard ("(missing)" is shown
  instead). Display-only; no ICD/SRS impact.
- None otherwise; the implementation matched the specification without
  further spec feedback.
