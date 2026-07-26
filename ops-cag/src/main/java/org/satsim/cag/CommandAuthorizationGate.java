package org.satsim.cag;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.satsim.pus.PacketDecodeException;
import org.satsim.pus.tc.TcPacket;

/**
 * The Command Authorization Gate: the one component that decides which
 * telecommands reach the spacecraft (ADR-0007, SDP §1.1, ICD §8.4).
 *
 * <p>Engineered to the ECSS Category B <em>technical</em> bar inside an
 * otherwise Category D product, because it carries the whole consequence of an
 * untrusted operator client's advice. Everything about it is in service of
 * being small enough to verify: JDK plus {@code pus-core}, no framework, no
 * network, no threads of its own, no dynamic policy, no formatting, no retries.
 *
 * <p>Every injection is decided on the <strong>decoded content of its octets
 * alone</strong> [SIM-REQ-CAG-002] — never on the invoking tool or on
 * operator-declared parameters, which an AI client controls and which
 * therefore cannot be an authorization input. The decision is one of three
 * (ICD §8.4):
 *
 * <ul>
 *   <li><strong>reject</strong> — the octets do not decode, their
 *       (service, subtype) has no classification-table entry, or the pair is
 *       outside the configured allowlist. Fail-closed [SIM-REQ-CAG-003]: a
 *       command the gate could not classify is never forwarded.</li>
 *   <li><strong>confirmation required</strong> — the command changes
 *       spacecraft state. Held, with nothing injected, until a confirmation is
 *       recorded through a {@link ConfirmationChannel} the submitter cannot
 *       reach [SIM-REQ-CAG-005].</li>
 *   <li><strong>forward</strong> — observation and benign writes, and held
 *       commands whose confirmation has been recorded.</li>
 * </ul>
 *
 * <p><strong>Limits.</strong> This gate bounds one failure mode: an operator
 * proposing a command type it has no authority for. It does not bound a valid
 * command at the wrong moment, a harmful sequence of individually authorized
 * commands, a correct command derived from misread telemetry, an omission, or a
 * confirmation obtained on false pretenses — nor, on the same machine, an
 * operator with arbitrary shell execution bypassing the tool path entirely.
 * ADR-0007 C8 states these verbatim and they are not to be restated more
 * favourably.
 *
 * <p>Not thread-safe by construction; {@code decide} is synchronized because
 * the gateway serves MCP calls from more than one thread.
 */
public final class CommandAuthorizationGate {

  /** Session-level restriction on which (service, subtype) pairs may be commanded. */
  public interface Allowlist {
    /** Whether this pair is permitted for the session. */
    boolean allows(int service, int subtype);
  }

  /** One telecommand held awaiting confirmation. */
  private record Hold(String token, String identity, CommandSummary command) {
  }

  private final Allowlist allowlist;
  private final ConfirmationChannel channel;
  private final Map<String, Hold> holds = new LinkedHashMap<>();
  private int issuedTokens;

  /**
   * @param allowlist the session's permitted (service, subtype) pairs
   * @param channel the out-of-band confirmation channel; cleared here, since no
   *     confirmation recorded before this gate existed may release anything
   */
  public CommandAuthorizationGate(Allowlist allowlist, ConfirmationChannel channel) {
    this.allowlist = allowlist;
    this.channel = channel;
    channel.clear();
  }

  /**
   * Authorizes one injection.
   *
   * @param octets the complete space packet the caller intends to inject
   * @return the decision; the caller injects if and only if
   *     {@link GateDecision#forwards()}
   */
  public synchronized GateDecision decide(byte[] octets) {
    List<String> ignored = discardUnknownConfirmations();

    TcPacket packet;
    try {
      packet = TcPacket.decode(octets);
    } catch (PacketDecodeException e) {
      return reject("UNDECODABLE: " + e.reason() + " — " + e.getMessage(), null, null, ignored);
    } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
      // A structurally impossible packet: decode's own guards did not catch it,
      // so it fails here rather than reaching the spacecraft.
      return reject("UNDECODABLE: malformed octets — " + e.getMessage(), null, null, ignored);
    }

    CommandSummary command = CommandSummary.of(packet);
    Optional<AuthorityTier> classified =
        ClassificationTable.tierOf(command.service(), command.subtype());
    if (classified.isEmpty()) {
      return reject("UNCLASSIFIED: " + command.identity()
          + " has no entry in the classification table; the gate does not forward"
          + " telecommands it cannot classify", null, command, ignored);
    }
    AuthorityTier tier = classified.get();

    if (!allowlist.allows(command.service(), command.subtype())) {
      return reject("ALLOWLIST_DENIED: " + command.identity()
          + " is not permitted for this session", tier, command, ignored);
    }

    if (tier.forwardsWithoutConfirmation()) {
      return new GateDecision(GateDecision.Outcome.FORWARD,
          "AUTHORIZED: " + command.identity() + " classified " + tier,
          tier, null, command, ignored);
    }
    return decideStateChanging(command, tier, ignored);
  }

  /**
   * State-changing writes: released by a recorded confirmation for an
   * outstanding hold of the same command, otherwise held under a fresh token.
   */
  private GateDecision decideStateChanging(
      CommandSummary command, AuthorityTier tier, List<String> ignored) {
    String identity = command.identity();
    Set<String> confirmed = channel.recorded();
    for (Hold hold : List.copyOf(holds.values())) {
      if (hold.identity().equals(identity) && confirmed.contains(hold.token())) {
        // Single use: both the hold and its confirmation are consumed, so the
        // next submission of the same command is held again.
        holds.remove(hold.token());
        channel.discard(hold.token());
        return new GateDecision(GateDecision.Outcome.FORWARD,
            "CONFIRMED: " + identity + " released by recorded confirmation " + hold.token(),
            tier, null, command, ignored);
      }
    }

    String token = nextToken(identity);
    holds.put(token, new Hold(token, identity, command));
    return new GateDecision(GateDecision.Outcome.CONFIRMATION_REQUIRED,
        "HELD: " + identity + " changes spacecraft state and is not forwarded until"
            + " confirmation " + token + " is recorded out of band",
        tier, token, command, ignored);
  }

  /**
   * Discards recorded tokens matching no pending hold. The gate honours only
   * tokens it issued and still holds, so a stale or invented confirmation
   * releases nothing; the discarded tokens are returned to be logged.
   */
  private List<String> discardUnknownConfirmations() {
    List<String> ignored = new ArrayList<>();
    for (String token : channel.recorded()) {
      if (!holds.containsKey(token)) {
        channel.discard(token);
        ignored.add(token);
      }
    }
    return ignored;
  }

  /** Pending holds, newest last, for the gateway-state resource (ICD §8.4). */
  public synchronized List<Map<String, Object>> pendingHolds() {
    List<Map<String, Object>> pending = new ArrayList<>();
    for (Hold hold : holds.values()) {
      pending.add(Map.of("token", hold.token(), "command", hold.command().identity()));
    }
    return List.copyOf(pending);
  }

  private GateDecision reject(
      String reason, AuthorityTier tier, CommandSummary command, List<String> ignored) {
    return new GateDecision(
        GateDecision.Outcome.REJECT, reason, tier, null, command, ignored);
  }

  /**
   * A token unique within the session and bound to the command's identity, so
   * a confirmation cannot be transplanted onto a different command. Sequential
   * rather than random: the gate stays deterministic, which is what its
   * verification bar asks of it. Secrecy would buy nothing — no MCP tool can
   * record a confirmation whatever the token is.
   */
  private String nextToken(String identity) {
    issuedTokens++;
    return "H" + issuedTokens + "-" + digest(identity).substring(0, 8);
  }

  private static String digest(String identity) {
    try {
      MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(sha256.digest(identity.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is required of every JDK", e);
    }
  }
}
