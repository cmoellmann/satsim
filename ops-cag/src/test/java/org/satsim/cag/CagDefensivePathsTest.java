package org.satsim.cag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for the gate's defensive and error-handling paths (untraced,
 * engineering hygiene per SDP §5; the traced validation of fail-closed
 * behaviour is SIM-TC-053…057).
 *
 * <p>These exist for the Category B coverage bar [SIM-REQ-QA-004]: the error
 * handling of a safety barrier is the part least likely to be exercised in
 * normal operation and most likely to matter when it is. Each test below covers
 * a branch that no functional case reaches.
 */
class CagDefensivePathsTest {

  // ---------------------------------------------------------------- channel

  @Test
  void unreadableChannelRecordsNothingRatherThanFailing(@TempDir Path temp) throws IOException {
    Path directory = temp.resolve("confirmations");
    FileConfirmationChannel channel = new FileConfirmationChannel(directory);
    Files.delete(directory);

    // Fail closed in the safe direction (CAG-SSA FM-04): an unreadable channel
    // confirms nothing, so held commands stay held instead of being released.
    assertEquals(Set.of(), channel.recorded());
  }

  @Test
  void nonRegularEntriesAreNotConfirmations(@TempDir Path temp) throws IOException {
    FileConfirmationChannel channel = new FileConfirmationChannel(temp.resolve("c"));
    Files.createDirectory(channel.directory().resolve("a-subdirectory"));
    Files.writeString(channel.directory().resolve("H1-abcdef01"), "confirmed");

    assertEquals(Set.of("H1-abcdef01"), channel.recorded(),
        "a directory in the confirmation folder is not a recorded confirmation");
  }

  @Test
  void channelExposesItsDirectoryForTheOperatorHint(@TempDir Path temp) {
    Path directory = temp.resolve("confirmations");
    assertEquals(directory, new FileConfirmationChannel(directory).directory());
  }

  @Test
  void anUncreatableChannelDirectoryFailsLoudly(@TempDir Path temp) throws IOException {
    Path file = Files.writeString(temp.resolve("not-a-directory"), "x");

    // The channel cannot silently degrade to "no confirmations ever": that
    // would be indistinguishable from a working channel with nothing recorded.
    assertThrows(UncheckedIOException.class,
        () -> new FileConfirmationChannel(file.resolve("child")));
  }

  @Test
  void anUndiscardableConfirmationFailsLoudly(@TempDir Path temp) throws IOException {
    FileConfirmationChannel channel = new FileConfirmationChannel(temp.resolve("c"));
    Path blocked = Files.createDirectory(channel.directory().resolve("H1-blocked"));
    Files.writeString(blocked.resolve("occupant"), "x");

    // Discarding is how a confirmation is consumed. If it cannot be consumed
    // the gate must not proceed as though it had been.
    assertThrows(UncheckedIOException.class, () -> channel.discard("H1-blocked"));
  }

  @Test
  void clearingAnEmptyChannelIsANoOp(@TempDir Path temp) {
    FileConfirmationChannel channel = new FileConfirmationChannel(temp.resolve("c"));
    channel.clear();
    assertEquals(Set.of(), channel.recorded());
  }

  // ------------------------------------------------------------ CagConfirm

  private static final class Streams {
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final ByteArrayOutputStream err = new ByteArrayOutputStream();

    int run(String... args) throws IOException {
      return CagConfirm.run(args,
          new PrintStream(out, true, StandardCharsets.UTF_8),
          new PrintStream(err, true, StandardCharsets.UTF_8));
    }

    String err() {
      return err.toString(StandardCharsets.UTF_8);
    }

    String out() {
      return out.toString(StandardCharsets.UTF_8);
    }
  }

  @Test
  void confirmToolRejectsEveryMalformedInvocation(@TempDir Path temp) throws IOException {
    String dir = temp.resolve("c").toString();
    List<String[]> malformed = List.of(
        new String[] {},
        new String[] {dir},
        new String[] {dir, "H1-abc", "extra"},
        new String[] {"", "H1-abc"},
        new String[] {dir, "  "});

    for (String[] args : malformed) {
      Streams streams = new Streams();
      assertEquals(2, streams.run(args), "usage error exit code");
      assertTrue(streams.err().contains("usage:"), "usage message");
    }
  }

  @Test
  void confirmToolRefusesTokensThatAreNotPlainFileNames(@TempDir Path temp) throws IOException {
    String dir = temp.resolve("c").toString();
    // "H1..abc" carries no separator at all — it is refused for the ".."
    // alone, which is the only one of the three checks a separator test misses.
    for (String token : List.of("../escape", "sub/token", "sub\\token", "H1..abc")) {
      Streams streams = new Streams();
      assertEquals(2, streams.run(dir, token), "path-like token must be refused: " + token);
      assertTrue(streams.err().contains("refusing a token"), streams.err());
    }
  }

  @Test
  void confirmToolRecordsAConfirmationTheGateThenHonours(@TempDir Path temp) throws IOException {
    Path directory = temp.resolve("c");
    Streams streams = new Streams();

    assertEquals(0, streams.run(directory.toString(), " H1-abcdef01 "));
    assertTrue(streams.out().contains("confirmation recorded"));
    assertEquals(Set.of("H1-abcdef01"), new FileConfirmationChannel(directory).recorded(),
        "the token is trimmed and recorded under its plain name");
  }

  // ---------------------------------------------------------- GateDecision

  @Test
  void aDecisionWithoutAUsableReasonIsRejectedAtConstruction() {
    assertThrows(IllegalArgumentException.class,
        () -> new GateDecision(null, "reason", null, null, null, List.of()));
    assertThrows(IllegalArgumentException.class,
        () -> new GateDecision(GateDecision.Outcome.REJECT, null, null, null, null, List.of()));
    assertThrows(IllegalArgumentException.class,
        () -> new GateDecision(GateDecision.Outcome.REJECT, "  ", null, null, null, List.of()));
  }

  @Test
  void aConfirmationTokenIsPresentExactlyForAHold() {
    assertThrows(IllegalArgumentException.class,
        () -> new GateDecision(GateDecision.Outcome.FORWARD, "r", null, "H1", null, List.of()));
    assertThrows(IllegalArgumentException.class,
        () -> new GateDecision(
            GateDecision.Outcome.CONFIRMATION_REQUIRED, "r", null, null, null, List.of()));
  }

  @Test
  void everyOutcomeHasTheOpsLogNameItIsRecordedUnder() {
    assertEquals("forward", decision(GateDecision.Outcome.FORWARD, null).outcomeName());
    assertEquals("reject", decision(GateDecision.Outcome.REJECT, null).outcomeName());
    assertEquals("confirmation-required",
        decision(GateDecision.Outcome.CONFIRMATION_REQUIRED, "H1-abcdef01").outcomeName());
  }

  @Test
  void aBudgetRejectionKeepsTheClassificationAsEvidence() {
    CommandSummary command = new CommandSummary(17, 1, 9, "");
    GateDecision forward = new GateDecision(GateDecision.Outcome.FORWARD,
        "AUTHORIZED", AuthorityTier.BENIGN_WRITE, null, command, List.of("H9-stale"));

    GateDecision rejected = forward.rejectedInstead("BUDGET_EXHAUSTED");

    assertEquals(GateDecision.Outcome.REJECT, rejected.outcome());
    assertFalse(rejected.forwards());
    assertEquals("BUDGET_EXHAUSTED", rejected.reason());
    // The tier survives: a control outside the gate refused an injection the
    // gate had classified, and the record should still say what it was.
    assertEquals(AuthorityTier.BENIGN_WRITE, rejected.tier());
    assertEquals(command, rejected.command());
    assertEquals(List.of("H9-stale"), rejected.ignoredConfirmations());
  }

  private static GateDecision decision(GateDecision.Outcome outcome, String token) {
    return new GateDecision(outcome, "reason", null, token, null, List.of());
  }

  // -------------------------------------------------------- CommandSummary

  @Test
  void commandSummaryRendersAsItsIdentity() {
    CommandSummary command = new CommandSummary(3, 5, 9, "0001");
    assertEquals("TC(3,5) ack=9 appData=0001", command.toString());
  }

  // --------------------------------------------------------- AuthorityTier

  @Test
  void onlyStateChangingWritesNeedAConfirmation() {
    assertTrue(AuthorityTier.OBSERVATION.forwardsWithoutConfirmation());
    assertTrue(AuthorityTier.BENIGN_WRITE.forwardsWithoutConfirmation());
    assertFalse(AuthorityTier.STATE_CHANGING_WRITE.forwardsWithoutConfirmation());
    // valueOf covers the enum's implicit members, which JaCoCo counts.
    assertEquals(AuthorityTier.OBSERVATION, AuthorityTier.valueOf("OBSERVATION"));
  }
}
