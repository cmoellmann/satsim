# SCR-005 — Introduce Software Problem Reports (SPR) as the problem-reporting instrument

- Status: Proposed
- Date: 2026-07-19
- Originator: project lead (C. Möllmann); drafted by AI assistant per SDP §6
- Affected configuration items: SATSIM-SDP, CLAUDE.md, README

## 1. Change description

Introduce **Software Problem Reports (SPRs)** as the project's problem-reporting
instrument, replacing the repository issue tracker named in the SDP §2.1
tailoring row "NCR/problem reporting":

1. **Instrument.** An SPR file per problem in `docs/spr/`, registered in
   `docs/spr/SPR-LOG.md` — same repository-native mechanics as the SCR
   instrument (SDP §2.3). An SPR records a **nonconformance of the software or
   its documentation against its own baseline** (observed vs expected
   behavior, evidence), its analysis (cause), its disposition, and its
   verified closure.
2. **Lifecycle.** Open → Analyzed → Dispositioned (fix / no fix, by project
   lead via PR review) → Closed (fix implemented **and verified**) | Rejected
   (not a problem / duplicate / as-designed).
3. **Demarcation from SCR.** An SPR is corrective: the product deviates from
   its baseline. An SCR is evolutionary: the baseline itself changes. If SPR
   analysis concludes the baselined specification is wrong, the SPR does not
   change it — it spawns an SCR, and both cross-reference each other. This
   preserves CLAUDE.md hard rule 1 (never adjust reference vectors/expected
   results to make tests pass): such discrepancies are now **recorded as
   SPRs**.
4. **Regression evidence.** Where the fix changes software behavior, closure
   requires a regression test per CLAUDE.md rule 3 (SRS/SVS entry if the
   defect revealed a specification gap, otherwise at least an untraced unit
   test); documentation-only fixes are closed by the correcting PR.

## 2. Rationale

- ECSS-Q-ST-80C requires problem/nonconformance reporting; the current
  tailoring ("repository issue tracker") is unused in practice — findings so
  far live informally in SCR "findings" sections (e.g. SCR-004 §5) or commit
  messages, without status tracking or closure evidence.
- The SCR instrument has proven that file-plus-register in Git works well for
  this project (dispositions via PR review, full history, links from
  implementing PRs); SPRs reuse the identical mechanics rather than adding an
  external tool.
- A visible defect register with analysis and verified closure is itself a
  showcase artefact of the ECSS-style process.

## 3. Impact analysis

| CI / area | Impact |
|---|---|
| SDP §2.1 | Tailoring row "NCR/problem reporting" changes from "Repository issue tracker" to the SPR register (`docs/spr/`), justification updated. |
| SDP §2.4 | New section **Problem reporting** defining the SPR instrument, lifecycle, required content, and the SPR↔SCR demarcation. §2.3 (change control) gains one sentence delegating problem reporting to §2.4. |
| SDP §7 | Documentation plan gains an SPR log row. |
| CLAUDE.md | Hard rule 1 amended: discrepancy findings are recorded as SPRs in `docs/spr/`; authoritative-documents list gains the SPR register. Controlled-document change, in this PR per rule 7. |
| README | Documentation table gains an SPR register row. |
| ICD / SRS / SVS | **None** — no interface, requirement, or test-case change; the instrument is process-level. |
| TraceabilityCheck / CI | None — SPRs are not parsed by the traceability gate. A future gate extension (open SPRs block a milestone gate) would be its own SCR. |
| ADRs | None contradicted; no architecture impact. |
| Implementation code | None. |

## 4. Disposition

- [ ] Approved — project lead, via review and merge of the PR carrying this
      SCR, the SDP update, and the SPR register scaffold.

## 5. Findings during implementation

- None yet.
