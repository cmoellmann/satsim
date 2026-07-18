# Agent definitions — tiered AI staffing

Routine work is delegated to cheaper models; the frontier model (main session)
keeps spec interpretation, traceability decisions, dependency approval, and
review of every delegated diff before it is committed. Delegation is
announce-first: the main session proposes each hand-off and waits for the
human's go. The agents below are the delegation targets; their guardrails
(no edits to ICD vectors, SVS expected results, ADRs, or poms; no commits)
are part of the reviewed baseline, like every other process control here.
