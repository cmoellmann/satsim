package org.satsim.cag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.satsim.cag.GateDecision.Outcome;
import org.satsim.pus.ccsds.PrimaryHeader;
import org.satsim.pus.tc.TcPacket;
import org.satsim.pus.tc.TcSecondaryHeader;
import org.satsim.testsupport.Requirement;
import org.satsim.testsupport.TestCase;

/**
 * Category B robustness suite over the gate's error paths [SIM-TC-053…057].
 *
 * <p>These cases drive the gate <strong>directly</strong>. That is deliberate
 * and complements rather than duplicates SIM-TC-049, which exercises the same
 * fail-closed property end-to-end through a scripted MCP client: the end-to-end
 * case proves the property is wired up, these prove it holds over the whole
 * input space of each error class rather than for one representative each.
 *
 * <p>Every case asserts the same two things, because together they are what
 * "fail closed" means [SIM-REQ-CAG-SAFE-002]: the outcome is a rejection with a
 * reason a human can act on, and {@link GateDecision#forwards()} is false so the
 * caller injects nothing. The reason string is asserted by prefix, since the
 * prefix is the machine-readable part the ops log carries
 * [SIM-REQ-CAG-SAFE-003].
 */
class CagRobustnessTest {

  /** ICD §6 reference vectors. Never modified — see CLAUDE.md rule 1. */
  private static final byte[] V_TC_01 = hex("18 64 C0 00 00 06 20 11 01 00 00 FA 83");
  private static final byte[] V_NEG_01 = hex("18 64 C0 00 00 06 20 11 01 00 00 FA 84");

  private final ConfirmationChannel channel = new MemoryChannel();
  private final CommandAuthorizationGate gate =
      new CommandAuthorizationGate((service, subtype) -> true, channel);

  /** An in-memory channel: the gate is specified against the interface, not a transport. */
  private static final class MemoryChannel implements ConfirmationChannel {
    private final Set<String> tokens = new LinkedHashSet<>();

    @Override
    public Set<String> recorded() {
      return Set.copyOf(tokens);
    }

    @Override
    public void discard(String token) {
      tokens.remove(token);
    }

    @Override
    public void clear() {
      tokens.clear();
    }
  }

  private static byte[] hex(String spaced) {
    return HexFormat.of().parseHex(spaced.replace(" ", ""));
  }

  /** A well-formed, correctly CRC'd telecommand — the encoder the simulator uses. */
  private static byte[] tc(int service, int subtype, byte[] appData) {
    TcSecondaryHeader secondary = new TcSecondaryHeader(
        TcSecondaryHeader.PUS_C_VERSION, 0b1001, service, subtype, 0);
    return TcPacket.of(100, 0, secondary, appData).encode();
  }

  private static byte[] tc(int service, int subtype) {
    return tc(service, subtype, new byte[0]);
  }

  /** Asserts the shape every fail-closed rejection must have. */
  private static void assertRejectedWith(String reasonPrefix, GateDecision decision) {
    assertEquals(Outcome.REJECT, decision.outcome(), "outcome");
    assertFalse(decision.forwards(), "nothing may be injected on a rejection");
    assertNull(decision.token(), "a rejection carries no confirmation token");
    assertTrue(decision.reason().startsWith(reasonPrefix),
        () -> "reason should start with " + reasonPrefix + " but was: " + decision.reason());
  }

  /**
   * SIM-TC-053 — undecodable octets are rejected, with no tier, on every decode
   * error class the packet library distinguishes.
   *
   * <p>Note on the fourth input, recorded rather than smoothed over: V-TC-01
   * with its PUS version nibble set to 1 rejects as a <em>CRC</em> error, not as
   * a version error. The gate does not check the PUS version at all — that is
   * the spacecraft's own acceptance check, and SIM-TC-049 asserts precisely that
   * contrast by having V-NEG-02 (version 1, CRC corrected) decode and forward.
   * The input therefore exercises the same path as V-NEG-01. Asserting the
   * concrete reason here keeps that visible in the code instead of leaving the
   * SVS wording to imply a distinct path; proposed as an SVS clarification at
   * the gate.
   */
  @Test
  @TestCase("SIM-TC-053")
  @Requirement({"SIM-REQ-CAG-SAFE-002", "SIM-REQ-CAG-SAFE-003"})
  void undecodableOctetsAreRejectedWithoutATier() {
    byte[] versionNibbleFlipped = V_TC_01.clone();
    versionNibbleFlipped[6] = (byte) ((versionNibbleFlipped[6] & 0x0F) | 0x10);

    byte[] lengthInconsistent = V_TC_01.clone();
    lengthInconsistent[5] = (byte) 0x20;

    record Input(String what, byte[] octets, String reason) {}
    List<Input> inputs = List.of(
        new Input("empty octet array", new byte[0], "TOO_SHORT"),
        new Input("single octet", new byte[] {0x18}, "TOO_SHORT"),
        new Input("V-NEG-01, CRC error", V_NEG_01, "CRC_ERROR"),
        new Input("V-TC-01 with PUS version 1", versionNibbleFlipped, "CRC_ERROR"),
        new Input("inconsistent length field", lengthInconsistent, "LENGTH_MISMATCH"));

    for (Input input : inputs) {
      GateDecision decision = gate.decide(input.octets());
      assertRejectedWith("UNDECODABLE:", decision);
      assertTrue(decision.reason().contains(input.reason()),
          () -> input.what() + " should reject as " + input.reason()
              + " but reason was: " + decision.reason());
      // No tier exists for octets that never decoded, and the ops log is
      // specified to omit it rather than invent one [SIM-REQ-CAG-SAFE-003].
      assertNull(decision.tier(), () -> "no tier for " + input.what());
      assertNull(decision.command(), () -> "no decoded command for " + input.what());
      assertFalse(decision.reason().isBlank(), "the ops log needs a non-blank reason");
    }
  }

  /**
   * SIM-TC-054 — every truncation of a valid telecommand is rejected. The whole
   * prefix space, not a representative: a length-boundary defect that admitted
   * exactly one truncation would still be a forward of octets the gate never
   * classified.
   */
  @Test
  @TestCase("SIM-TC-054")
  @Requirement({"SIM-REQ-CAG-SAFE-002", "SIM-REQ-CAG-SAFE-003"})
  void everyTruncationIsRejected() {
    for (int length = 0; length < V_TC_01.length; length++) {
      byte[] truncated = Arrays.copyOf(V_TC_01, length);
      GateDecision decision = gate.decide(truncated);
      int at = length;
      assertRejectedWith("UNDECODABLE:", decision);
      assertNull(decision.tier(), () -> "truncation to " + at + " octets must yield no tier");
      assertFalse(decision.reason().isBlank(), () -> "reason at length " + at);
    }
    // Control: the untruncated vector is forwarded, so the loop above is
    // rejecting truncation rather than rejecting everything.
    assertTrue(gate.decide(V_TC_01).forwards());
  }

  /**
   * SIM-TC-055 — telecommands outside the ICD's tailored set decode cleanly and
   * are still rejected. This is the fail-closed property that matters most: the
   * gate refuses what it cannot classify instead of passing it through as
   * harmless.
   */
  @Test
  @TestCase("SIM-TC-055")
  @Requirement({"SIM-REQ-CAG-SAFE-002", "SIM-REQ-CAG-SAFE-003"})
  void serviceAndSubtypeOutsideTheTailoredSetAreRejected() {
    int[][] pairs = {{17, 99}, {99, 1}, {0, 0}, {255, 255}};
    for (int[] pair : pairs) {
      byte[] octets = tc(pair[0], pair[1]);
      GateDecision decision = gate.decide(octets);
      assertRejectedWith("UNCLASSIFIED:", decision);
      // Decoding completed, so the decoded command is evidence in the log —
      // only the tier is absent, because no tier was assignable.
      assertNotNull(decision.command(),
          () -> "TC(" + pair[0] + "," + pair[1] + ") decodes, so the command is known");
      assertEquals(pair[0], decision.command().service());
      assertEquals(pair[1], decision.command().subtype());
      assertNull(decision.tier(),
          () -> "TC(" + pair[0] + "," + pair[1] + ") has no tier");
    }
  }

  /**
   * SIM-TC-056 — the classification table is closed: every tabled pair has
   * exactly the tier SIM-REQ-CAG-004 specifies, and every untabled pair has
   * none. The adjacent pairs are checked explicitly because an off-by-one in the
   * key packing would show up there first and nowhere else.
   */
  @Test
  @TestCase("SIM-TC-056")
  @Requirement({"SIM-REQ-CAG-SAFE-002", "SIM-REQ-CAG-SAFE-004"})
  void classificationTableIsClosedAndHasNoDefaultTier() {
    assertEquals(AuthorityTier.BENIGN_WRITE, ClassificationTable.tierOf(17, 1).orElseThrow());
    assertEquals(AuthorityTier.STATE_CHANGING_WRITE,
        ClassificationTable.tierOf(3, 1).orElseThrow());
    assertEquals(AuthorityTier.STATE_CHANGING_WRITE,
        ClassificationTable.tierOf(3, 5).orElseThrow());
    assertEquals(AuthorityTier.STATE_CHANGING_WRITE,
        ClassificationTable.tierOf(3, 7).orElseThrow());

    // The table's own view of itself agrees with the four assertions above, so
    // an entry added without a test cannot hide.
    assertEquals(List.of("3/1", "3/5", "3/7", "17/1"), ClassificationTable.entries());

    int[][] untabled = {{3, 0}, {3, 2}, {3, 4}, {3, 6}, {3, 8}, {17, 0}, {17, 2}};
    for (int[] pair : untabled) {
      assertEquals(Optional.empty(), ClassificationTable.tierOf(pair[0], pair[1]),
          () -> "TC(" + pair[0] + "," + pair[1] + ") must not resolve to a tier");
      assertRejectedWith("UNCLASSIFIED:", gate.decide(tc(pair[0], pair[1])));
    }

    // Key packing is injective over the decoded domain: no pair outside the
    // table reaches a tabled entry, across the full single-octet field range.
    Set<String> tabled = Set.of("3/1", "3/5", "3/7", "17/1");
    for (int service = 0; service <= 255; service++) {
      for (int subtype = 0; subtype <= 255; subtype++) {
        boolean isTabled = tabled.contains(service + "/" + subtype);
        assertEquals(isTabled, ClassificationTable.tierOf(service, subtype).isPresent(),
            "tier presence for " + service + "/" + subtype);
      }
    }
  }

  /**
   * SIM-TC-057 — application data at and beyond the packet-length boundary. The
   * gate must reach a deterministic decision on all of it, and must never
   * forward something it did not classify.
   */
  @Test
  @TestCase("SIM-TC-057")
  @Requirement({"SIM-REQ-CAG-SAFE-002", "SIM-REQ-CAG-SAFE-003"})
  void oversizedAndBoundaryApplicationDataDecideDeterministically() {
    // Empty application data: decodes, classifies, and follows its content.
    GateDecision empty = gate.decide(tc(17, 1, new byte[0]));
    assertTrue(empty.forwards());
    assertEquals(AuthorityTier.BENIGN_WRITE, empty.tier());

    // Maximum encodable application data on a state-changing pair: held, not
    // forwarded — size does not change the authority tier.
    int maxAppData = PrimaryHeader.PACKET_DATA_LENGTH_MAX + 1
        - TcSecondaryHeader.LENGTH - 2;
    byte[] largest = tc(3, 5, new byte[maxAppData]);
    GateDecision held = gate.decide(largest);
    assertEquals(Outcome.CONFIRMATION_REQUIRED, held.outcome());
    assertFalse(held.forwards(), "a state-changing write is never forwarded on first submission");
    assertEquals(AuthorityTier.STATE_CHANGING_WRITE, held.tier());

    // Beyond the boundary: octets appended past the declared length no longer
    // decode, so they are rejected rather than silently truncated to something
    // that would have classified differently.
    byte[] overlong = Arrays.copyOf(largest, largest.length + 1);
    assertRejectedWith("UNDECODABLE:", gate.decide(overlong));

    // Determinism: the same octets decide the same way every time. The gate
    // holds state, so this is asserted on the rejection path, which holds none.
    GateDecision first = gate.decide(overlong);
    GateDecision second = gate.decide(overlong);
    assertEquals(first.outcome(), second.outcome());
    assertEquals(first.reason(), second.reason());
  }
}
