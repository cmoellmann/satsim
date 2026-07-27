# SPR-007 — A forwarded telecommand can be recorded in the ops log without its gate decision

- Status: Open (analysis complete; disposition proposed)
- Severity: major (loss of authorization evidence on the Category B barrier;
  no incorrect authorization occurs — see §2.4)
- Reported: 2026-07-26, AI assistant per SDP §6, during the software FMEA of
  [SCR-011](../scr/SCR-011-cag-assurance.md) (M1i); recorded as finding F-1 of
  SCR-011 §5 and row **FM-21** of the
  [CAG safety analysis](../safety/cag-safety-analysis.md)
- Affected CI / component: `mcp-gateway` (`Gateway.tool`, `Gateway.authorize`,
  `OpsLog.record`) — Category D. **Not** `ops-cag`: the gate decides correctly
  in every case below; what is lost is the record of its decision.
- Observed on: master @ `068c199` (tag `M1h`)

## 1. Problem description

**Expected**, per **SIM-REQ-CAG-006**: the ops log carries, *for every injection
the gate decides*, the gate decision, its reason, the assigned authority tier and
the on-board time. Per the derived **SIM-REQ-CAG-SAFE-003** (M1i): no
authorization decision is taken without a corresponding ops-log record.

**Observed:** there are two paths on which a telecommand is injected into the
simulator and the resulting ops-log line carries **no** `gateDecision`,
`gateReason` or `authorityTier` field at all — or no line at all.

### 1.1 Path A — the tool body fails after a successful injection

`Gateway.tool` (`Gateway.java:322–341`) records the ops-log line only after
`body.apply(args)` returns normally:

```java
try {
  outcome = body.apply(args);
} catch (WebApiLink.LinkException | IllegalArgumentException e) {
  outcome = plain(error(e.getMessage()));      // <- decision discarded
}
...
opsLog.record(name, args, ..., outcome.decision());   // <- null on that path
```

`plain(...)` is defined as "a result from a tool that authorizes nothing, so
carries no gate decision" and sets `decision` to `null` (`Gateway.java:305–308`).
`OpsLog.record` then writes the `tool`/`params`/`outcome`/`obt` fields and skips
the whole gate block, which is guarded by `if (decision != null)`
(`OpsLog.java:53`).

The injection itself happens *inside* `body.apply`, in the `FORWARD` arm of
`Gateway.authorize` (`Gateway.java:167`):

```java
case FORWARD -> new ToolOutcome(ok(injection.inject()), decision);
```

So any exception raised by `injection.inject()` **after** the simulator has
accepted the telecommand destroys the decision that authorized it.

That window is real and reachable, not theoretical. `RestWsLink.post`
(`RestWsLink.java:65–84`) checks the status code first and parses the response
body afterwards:

```java
HttpResponse<String> response = http.send(request, ...);
if (response.statusCode() != 200) { throw new LinkException(...); }
return toMap(response.body());          // <- runs after the TC was accepted
```

`toMap` (`RestWsLink.java:87–93`) throws `UncheckedIOException` on an unparseable
body; `post`'s `catch (Exception e)` rewraps it as `LinkException`; `tool`
catches that and replaces the outcome with `plain(error(...))`. Net effect: **the
telecommand was injected, the spacecraft acted on it, and the ops log says only
that a tool call errored.**

### 1.2 Path B — the ops-log write itself fails after a successful injection

`OpsLog.record` throws `UncheckedIOException` when the write fails
(`OpsLog.java:74–76`). It is called after the injection and is not itself
guarded, so a write failure at that moment loses the record **entirely** — the
telecommand is in the spacecraft and nothing at all is written about it.

## 2. Analysis

### 2.1 Cause

One ordering decision: **the decision is recorded after the action it
authorizes, and only on the success path.** The ops-log call sits at the end of
the tool wrapper, where it has a convenient view of the finished result — but
by then the injection is long done, and the error handling between the two has
already thrown the decision away in favour of a uniform error result.

The `plain(...)` helper is correct for what it was written for (tools that
authorize nothing: `preview_tc`, the read-only tools). It is being reused on an
error path where a decision *does* exist, and its "no decision" semantics then
silently overwrite it.

### 2.2 Is this a nonconformance against the baseline?

**Yes.** SIM-REQ-CAG-006 requires the decision to be logged "for every injection
it decides". On path A the gate decided FORWARD, the injection occurred, and the
decision is absent from the log. The requirement is not met on that path, so
this is a defect against the M1h baseline and belongs in this register rather
than being evolutionary SCR territory (SDP §2.4 demarcation).

Note the neighbouring requirement is *not* violated: SIM-REQ-MCP-006 asks for
every tool invocation with parameters, outcome and OBT, and that line is still
written on path A. It is specifically the CAG-006 evidence fields that vanish.

### 2.3 Why the validation suite did not catch it

Every M1h validation case drives a healthy simulator over loopback, where the
POST succeeds and the response parses. Paths A and B require a failure
*between* a successful injection and the record being written — a state no
existing case constructs. The suite verifies that the right thing is logged
when everything works, which is exactly the blind spot an FMEA is for.

### 2.4 Consequence, stated precisely

No telecommand is forwarded that should not have been, and no hold is released
without a confirmation: the gate's authority logic is untouched by this defect,
and SIM-TC-048…052 remain valid. What is lost is **evidence**. For a Category D
component that would be a logging bug; for the audit trail of the one component
carrying the Category B claim it is the difference between "the barrier decided"
and "the barrier can be shown to have decided" (ADR-0007 C6). Hazard **H-3**,
loss of accountability, in the safety analysis.

## 3. Disposition (proposed)

**Fix**, in a follow-up increment — **not** in M1i, which SCR-011 §1 restricts to
verifying the M1h behaviour without changing it. Proposed change, for the
implementing SCR:

1. Record the gate decision **before** attempting the injection, so the evidence
   precedes the act it authorizes. A forward that is then not carried out is a
   harmless over-record; an injection that is not recorded is not harmless.
2. Carry the decision through the error paths instead of discarding it — give
   the wrapper an error variant that preserves `outcome.decision()` rather than
   reusing `plain(...)`, whose "authorizes nothing" meaning is wrong there.
3. Ensure an ops-log write failure cannot silently follow a successful
   injection (fail the tool call loudly, and treat the log as part of the
   authorization step rather than as reporting after it).
4. Add a validation case that injects a fault between a successful injection and
   the record — the case class §2.3 shows is currently unexercised.

Until then the residual risk is recorded in the safety analysis (FM-21, R-2
neighbourhood) and stated in SCR-011 §5 F-1, so it is carried openly rather than
discovered later.

## 4. Implementation and verification

*(to be completed at disposition — no fix under M1i by design)*
