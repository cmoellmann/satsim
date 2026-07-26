package org.satsim.cag;

import java.util.List;

/**
 * The gate's verdict on one injection — one of the three outcomes of ICD §8.4,
 * with the evidence the ops log records [SIM-REQ-CAG-006].
 *
 * @param outcome what happens to the injection
 * @param reason why, machine-readable prefix first; never blank
 * @param tier the assigned authority tier, or {@code null} where classification
 *     did not complete (undecodable octets, table gap)
 * @param token the confirmation token, non-{@code null} exactly when the
 *     outcome is {@link Outcome#CONFIRMATION_REQUIRED}
 * @param command the decoded command, or {@code null} if the octets did not decode
 * @param ignoredConfirmations tokens found in the confirmation channel that
 *     matched no pending hold; discarded unhonoured, reported so they are logged
 */
public record GateDecision(
    Outcome outcome,
    String reason,
    AuthorityTier tier,
    String token,
    CommandSummary command,
    List<String> ignoredConfirmations) {

  /** The three outcomes of an authorization decision (ICD §8.4). */
  public enum Outcome {
    /** Authorized: the octets may be injected. */
    FORWARD,
    /** Refused: nothing is injected. */
    REJECT,
    /** Held: nothing is injected until a confirmation is recorded. */
    CONFIRMATION_REQUIRED
  }

  public GateDecision {
    if (outcome == null || reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("outcome and a non-blank reason are required");
    }
    if ((outcome == Outcome.CONFIRMATION_REQUIRED) != (token != null)) {
      throw new IllegalArgumentException(
          "a confirmation token is present exactly for CONFIRMATION_REQUIRED: " + outcome);
    }
    ignoredConfirmations = List.copyOf(ignoredConfirmations);
  }

  /** Whether the caller may inject the octets. */
  public boolean forwards() {
    return outcome == Outcome.FORWARD;
  }

  /** Lowercase outcome name as written to the ops log and the tool result. */
  public String outcomeName() {
    return switch (outcome) {
      case FORWARD -> "forward";
      case REJECT -> "reject";
      case CONFIRMATION_REQUIRED -> "confirmation-required";
    };
  }

  /**
   * The same decision with a different reject reason — used where a control the
   * gate does not own (the gateway's session TC budget) refuses an injection the
   * gate had classified. Keeps the classified tier in the record as evidence.
   */
  public GateDecision rejectedInstead(String rejectReason) {
    return new GateDecision(
        Outcome.REJECT, rejectReason, tier, null, command, ignoredConfirmations);
  }
}
