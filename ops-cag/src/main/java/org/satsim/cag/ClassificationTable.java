package org.satsim.cag;

import java.util.Map;
import java.util.Optional;

/**
 * Maps a decoded (service type, message subtype) pair to its
 * {@link AuthorityTier} per [SIM-REQ-CAG-004].
 *
 * <p>The table is <strong>static and complete as written</strong>: there is no
 * dynamic loading, no configuration file and no default tier (ADR-0007 C1/C3).
 * A pair with no entry is a table gap, and a table gap is a rejection — see
 * {@link CommandAuthorizationGate}. Adding a telecommand to the tailored ICD
 * subset therefore requires adding it here, deliberately, in a reviewed change.
 */
public final class ClassificationTable {

  private static final Map<Integer, AuthorityTier> TIERS = Map.of(
      key(17, 1), AuthorityTier.BENIGN_WRITE,
      key(3, 1), AuthorityTier.STATE_CHANGING_WRITE,
      key(3, 5), AuthorityTier.STATE_CHANGING_WRITE,
      key(3, 7), AuthorityTier.STATE_CHANGING_WRITE);

  private ClassificationTable() {
  }

  /**
   * The tier of one telecommand, or empty for a table gap.
   *
   * @param service PUS service type of the decoded telecommand
   * @param subtype PUS message subtype of the decoded telecommand
   */
  public static Optional<AuthorityTier> tierOf(int service, int subtype) {
    return Optional.ofNullable(TIERS.get(key(service, subtype)));
  }

  /** The tabled pairs, as {@code "service/subtype"}, for the state resource. */
  public static java.util.List<String> entries() {
    return TIERS.keySet().stream()
        .sorted()
        .map(k -> (k >> 8) + "/" + (k & 0xFF))
        .toList();
  }

  private static int key(int service, int subtype) {
    return (service << 8) | subtype;
  }
}
