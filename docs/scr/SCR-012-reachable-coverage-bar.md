# SCR-012 — Category B coverage bar restated as 100 % of reachable code with itemized unreachability justifications

- Status: Proposed (disposition recorded by review and merge of this PR)
- Date: 2026-07-27
- Originator: AI assistant per SDP §6, during M1i implementation; decision taken
  by the project lead (C. Möllmann) in session on 2026-07-27
- Affected configuration items: SATSIM-SRS (SIM-REQ-QA-004), SATSIM-SVS
  (SIM-TC-060), SATSIM-SDP (§2.1), SATSIM-CAG-SSA (new §8)
- Design basis: [ADR-0007](../adr/ADR-0007-command-authorization-gate.md) C7;
  amends the coverage entries introduced by
  [SCR-011](../scr/SCR-011-cag-assurance.md)

## 1. Change description

`SIM-REQ-QA-004` and `SIM-TC-060`, baselined on 2026-07-26, require **100 %**
statement and decision coverage on `ops-cag` with the build failing below either
threshold. Implementation established that this is **not attainable**, and not
because of missing tests.

After the M1i robustness suite (SIM-TC-053…057) and the defensive-path unit
tests, `ops-cag` stands at **96.83 % instruction / 98.25 % branch**. The residue
is **23 instructions and 1 branch in four regions, all of which are unreachable
by construction**:

| Region | Instr / branch | Why it cannot be reached |
|---|---|---|
| `CommandAuthorizationGate:100–103` | 10 instr | The `IllegalArgumentException`/`ArrayIndexOutOfBoundsException` catch around `TcPacket.decode`. `decode` rejects anything shorter than 13 octets before touching the secondary header, so its offset check (`6 + 5 ≤ 13`) can never fail, and every field it builds is a single octet already inside its declared range. No input reaches the catch. |
| `CommandAuthorizationGate:204–205` | 7 instr | The `NoSuchAlgorithmException` catch around `MessageDigest.getInstance("SHA-256")`. SHA-256 is mandatory on every conforming JDK. |
| `CagConfirm:32–33` | 6 instr | `main`, whose body is `System.exit(run(...))`. Calling it in-process terminates the test JVM. The logic it delegates to — `run(args, out, err)` — is fully covered, which is why it was extracted in the first place (SCR-010). |
| `FileConfirmationChannel:53` | 1 branch | The `name != null` guard. `Path.getFileName()` returns `null` only for a root path, and a `DirectoryStream` never yields one. |

The change is therefore to restate the bar as what it can honestly be:

**SIM-REQ-QA-004 (amended).** 100 % statement and decision coverage of the
**reachable** code of `ops-cag`, with **every** uncovered region itemized and
justified as unreachable in the CAG safety analysis, and the build failing if
coverage drops below the recorded baseline.

**SIM-TC-060 (amended).** Verifies the amended requirement: the itemized list is
complete (no uncovered region lacks a justification), and the threshold is
enforced in the build rather than merely reported.

## 2. Rationale

- **The alternative was to make the number true by damaging the software.** The
  only way to reach a literal 100 % is to delete the unreachable catch clauses
  and restructure `main`. Those catches are defence in depth against a change in
  `pus-core`, which is a *separate configuration item* that can and will change;
  removing them so a metric reads well is precisely the failure mode a Category B
  bar exists to prevent. The gate's own FMEA (FM-09, FM-10) counts that
  defensiveness as a mitigation.
- **JaCoCo cannot exclude a line range.** Its exclusions are class-granular, so
  "measure 100 % and exclude the unreachable parts" would mean excluding
  `CommandAuthorizationGate` — gutting the measurement of the very component the
  bar exists to measure. There is no configuration that produces an honest 100 %.
- **Justifying uncovered code is the standard practice, not a concession.**
  Category B / DO-178-style verification asks for a rationale for every
  uncovered region, not for the absence of uncovered regions. With a residue of
  23 instructions the itemization is exhaustive rather than representative, so
  the justification is checkable line by line.
- **The itemized list is itself an honest artifact.** In the same spirit as the
  bypass demonstration (SIM-TC-058), it states exactly where the claim holds and
  where it stops, instead of letting a round number imply more than was done.
- **The ratchet keeps the bar real.** Coverage cannot silently decay: the build
  fails below the recorded baseline, so new uncovered code fails CI unless it is
  either tested or added to the justified list under review.

## 3. Impact analysis

| CI / area | Impact |
|---|---|
| SRS | `SIM-REQ-QA-004` reworded (reachable code, itemized justifications, ratchet). Scope stays M1i. No other requirement touched. |
| SVS | `SIM-TC-060` pass criteria reworded to match, adding the completeness check on the itemization. |
| SDP §2.1 | Coverage-target row for the CAG restated in the same terms. |
| SATSIM-CAG-SSA | New **§8 Coverage and unreachability justification**: the itemized table above, as the controlled record the requirement points at. |
| ICD / ADR | **None.** No wire-format change; ADR-0007 C7 asks for the verification bar, not for a particular metric wording. |
| Implementation code | **None.** No change to `ops-cag` behaviour — that is the point of the SCR. Build configuration only (`ops-cag/pom.xml` JaCoCo `check` thresholds). |
| SRF | **No new dependency.** JaCoCo already registered. |
| SPR register | None. The specification was over-tight, not violated — this is change control, not a nonconformance (SDP §2.3 / §2.4 demarcation). |

## 4. Disposition

- [x] Approved — project lead (C. Möllmann), 2026-07-27, decision taken in
      session and recorded by review and merge of this PR.

## 5. Findings during implementation

*(none)*
