package org.satsim.cag;

import java.util.Set;

/**
 * The channel through which a human records a confirmation for a held
 * telecommand [SIM-REQ-CAG-005].
 *
 * <p>The gate is specified against this interface and not against any
 * particular transport, so the requirement outlives the mechanism (SCR-010 §5
 * F-4). One property is not negotiable and is the entire point of the
 * increment: <strong>the channel must not be reachable by the party that
 * submits telecommands.</strong> A confirmation an AI operator client can
 * record is not a confirmation — it puts the safety barrier back where
 * ADR-0007 §4 found it, in the MCP client's permission prompt.
 *
 * <p>The gate never trusts a token it did not issue: a recorded token matching
 * no pending hold is discarded unhonoured (see
 * {@link CommandAuthorizationGate#decide}).
 */
public interface ConfirmationChannel {

  /** Tokens currently recorded as confirmed. */
  Set<String> recorded();

  /** Removes a recorded confirmation, whether honoured or discarded. */
  void discard(String token);

  /**
   * Drops every recorded confirmation. Called at gate construction: neither
   * pending holds nor recorded confirmations survive a restart (ICD §8.4), and
   * holds live in memory while confirmations may outlive the process.
   */
  void clear();
}
