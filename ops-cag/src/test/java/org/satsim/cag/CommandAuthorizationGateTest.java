package org.satsim.cag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.satsim.cag.GateDecision.Outcome;
import org.satsim.pus.tc.TcPacket;
import org.satsim.pus.tc.TcSecondaryHeader;

/**
 * Unit tests for the gate (untraced, engineering hygiene per SDP §5 — the
 * traced validation of this behaviour is SIM-TC-048..052 against the real
 * gateway process).
 */
class CommandAuthorizationGateTest {

  /** ICD §6 reference vectors. */
  private static final byte[] V_TC_01 = hex("18 64 C0 00 00 06 20 11 01 00 00 FA 83");
  private static final byte[] V_NEG_01 = hex("18 64 C0 00 00 06 20 11 01 00 00 FA 84");
  private static final byte[] V_NEG_02 = hex("18 64 C0 00 00 06 10 11 01 00 00 F6 6D");
  private static final byte[] V_TC_05 = hex("18 64 C0 02 00 0A 20 03 07 00 00 00 01 00 02 70 3D");

  /** An in-memory channel: the gate is specified against the interface. */
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

    void record(String token) {
      tokens.add(token);
    }
  }

  private final MemoryChannel channel = new MemoryChannel();
  private final CommandAuthorizationGate gate =
      new CommandAuthorizationGate((service, subtype) -> true, channel);

  private static byte[] hex(String spaced) {
    return HexFormat.of().parseHex(spaced.replace(" ", ""));
  }

  @Test
  void forwardsBenignWriteWithItsTier() {
    GateDecision decision = gate.decide(V_TC_01);
    assertTrue(decision.forwards());
    assertEquals(AuthorityTier.BENIGN_WRITE, decision.tier());
    assertEquals(17, decision.command().service());
    assertNull(decision.token());
  }

  @Test
  void rejectsUndecodableOctetsWithoutATier() {
    for (byte[] octets : new byte[][] {V_NEG_01, new byte[] {0}, hex("18 64 C0 00 00 06")}) {
      GateDecision decision = gate.decide(octets);
      assertEquals(Outcome.REJECT, decision.outcome());
      assertTrue(decision.reason().startsWith("UNDECODABLE"), decision.reason());
      assertNull(decision.tier(), "no tier may be invented for octets that did not decode");
    }
  }

  @Test
  void forwardsDecodableButSemanticallyInvalidOctets() {
    // V-NEG-02 carries PUS version 1. The codec does not enforce the version
    // (ICD §10.2 is spacecraft policy), so the gate classifies it as the ping
    // it is and the spacecraft's own rejection path stays reachable.
    GateDecision decision = gate.decide(V_NEG_02);
    assertTrue(decision.forwards());
    assertEquals(AuthorityTier.BENIGN_WRITE, decision.tier());
  }

  @Test
  void rejectsTableGapRatherThanDefaultingToForward() {
    // TC(17,3): well-formed and allowed by this test's allowlist, but not
    // tabled. Encoded rather than hand-written — it is a fixture, not an ICD
    // reference vector, and its CRC must be right for the point to be made.
    GateDecision decision = gate.decide(TcPacket.of(100, 0,
        new TcSecondaryHeader(2, 0, 17, 3, 0), new byte[0]).encode());
    assertEquals(Outcome.REJECT, decision.outcome());
    assertTrue(decision.reason().startsWith("UNCLASSIFIED"), decision.reason());
    assertNull(decision.tier(), "an unclassified command has no tier");
    assertEquals(17, decision.command().service(), "but it did decode, so it is reported");
  }

  @Test
  void rejectsOutsideTheAllowlistButKeepsTheTierAsEvidence() {
    CommandAuthorizationGate restricted = new CommandAuthorizationGate(
        (service, subtype) -> service == 17, new MemoryChannel());
    GateDecision decision = restricted.decide(V_TC_05);
    assertEquals(Outcome.REJECT, decision.outcome());
    assertTrue(decision.reason().startsWith("ALLOWLIST_DENIED"), decision.reason());
    assertEquals(AuthorityTier.STATE_CHANGING_WRITE, decision.tier());
  }

  @Test
  void holdsStateChangingWriteAndForwardsOnlyOnceConfirmed() {
    GateDecision held = gate.decide(V_TC_05);
    assertEquals(Outcome.CONFIRMATION_REQUIRED, held.outcome());
    assertEquals(AuthorityTier.STATE_CHANGING_WRITE, held.tier());
    assertEquals(1, gate.pendingHolds().size());

    // Unconfirmed re-submission stays held.
    assertEquals(Outcome.CONFIRMATION_REQUIRED, gate.decide(V_TC_05).outcome());

    channel.record(held.token());
    assertTrue(gate.decide(V_TC_05).forwards(), "a recorded confirmation releases the command");

    // Single use: consumed, so the next identical command is held afresh.
    GateDecision heldAgain = gate.decide(V_TC_05);
    assertEquals(Outcome.CONFIRMATION_REQUIRED, heldAgain.outcome());
    assertNotEquals(held.token(), heldAgain.token());
    assertTrue(channel.recorded().isEmpty(), "the honoured confirmation is discarded");
  }

  @Test
  void confirmationIsBoundToTheCommandItWasIssuedFor() {
    GateDecision heldDisable = gate.decide(V_TC_05);
    channel.record(heldDisable.token());

    // A different command must not ride on that confirmation.
    byte[] enable = hex("18 64 C0 01 00 0A 20 03 05 00 00 00 01 00 02 15 41");
    assertEquals(Outcome.CONFIRMATION_REQUIRED, gate.decide(enable).outcome());
    assertTrue(gate.decide(V_TC_05).forwards(), "the confirmed command is still releasable");
  }

  @Test
  void discardsConfirmationsForTokensItDoesNotHold() {
    channel.record("H99-deadbeef");
    GateDecision decision = gate.decide(V_TC_01);
    assertEquals(java.util.List.of("H99-deadbeef"), decision.ignoredConfirmations());
    assertTrue(channel.recorded().isEmpty(), "an unknown token is discarded unhonoured");
  }

  @Test
  void clearsTheChannelOnConstructionSoNothingSurvivesARestart() {
    MemoryChannel stale = new MemoryChannel();
    stale.record("H1-abcdef12");
    new CommandAuthorizationGate((service, subtype) -> true, stale);
    assertTrue(stale.recorded().isEmpty(),
        "a confirmation recorded before the gate existed may release nothing");
  }

  @Test
  void decisionInvariantsAreEnforced() {
    assertThrows(IllegalArgumentException.class, () -> new GateDecision(
        Outcome.FORWARD, "  ", null, null, null, java.util.List.of()),
        "a blank reason is not evidence");
    assertThrows(IllegalArgumentException.class, () -> new GateDecision(
        Outcome.CONFIRMATION_REQUIRED, "held", null, null, null, java.util.List.of()),
        "a held command without a token could never be released");
    assertThrows(IllegalArgumentException.class, () -> new GateDecision(
        Outcome.FORWARD, "ok", null, "H1-x", null, java.util.List.of()),
        "a forwarded command carries no token");
  }

  @Test
  void budgetRejectionKeepsTheClassifiedTier() {
    GateDecision forwarded = gate.decide(V_TC_01);
    GateDecision exhausted = forwarded.rejectedInstead("BUDGET_EXHAUSTED: none left");
    assertEquals(Outcome.REJECT, exhausted.outcome());
    assertEquals(AuthorityTier.BENIGN_WRITE, exhausted.tier());
    assertFalse(exhausted.forwards());
  }

  @Test
  void tableNamesEveryClassifiedTelecommand() {
    assertEquals(java.util.List.of("3/1", "3/5", "3/7", "17/1"), ClassificationTable.entries());
    assertTrue(ClassificationTable.tierOf(8, 1).isEmpty());
  }
}
