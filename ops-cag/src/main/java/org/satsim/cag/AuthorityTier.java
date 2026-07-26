package org.satsim.cag;

/**
 * Authority tier a telecommand classifies into, per ADR-0007 C4 and
 * [SIM-REQ-CAG-004]. The tier — not the invoking tool, not an
 * operator-declared service type — determines what the gate does with a
 * telecommand.
 */
public enum AuthorityTier {

  /**
   * Read / observation: forwarded and logged. The ICD's tailored telecommand
   * set has no member of this tier — every ST[3] and ST[17] telecommand it
   * defines either pings or writes. The tier is defined because the
   * classification is the authorization model, not a list of today's
   * telecommands; a table gap is rejected (ADR-0007 C3), never silently
   * treated as an observation.
   */
  OBSERVATION,

  /** Benign write (ST[17] connection test): forwarded and logged with attribution. */
  BENIGN_WRITE,

  /**
   * State-changing write (ST[3] structure and interval changes): held. Never
   * forwarded without a confirmation recorded through a channel outside the
   * MCP interface [SIM-REQ-CAG-005].
   */
  STATE_CHANGING_WRITE;

  /** Whether the gate may forward this tier without a recorded confirmation. */
  boolean forwardsWithoutConfirmation() {
    return this != STATE_CHANGING_WRITE;
  }
}
