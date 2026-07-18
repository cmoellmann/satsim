package org.satsim.sim.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import java.util.HexFormat;
import org.satsim.pus.tc.TcPacket;
import org.satsim.pus.time.CucTime;

/**
 * Response body of {@code POST /api/tc} (ICD §8.1): injected packet hex,
 * injection OBT, the sequence count (ground counter for structured composes,
 * from the decoded packet for raw injections), and the decoded TC fields —
 * or {@code decodeError} naming the first failed ICD §6.3 check when a raw
 * injection is undecodable [SIM-REQ-UI-006].
 */
@JsonInclude(Include.NON_NULL)
public record TcSendResponse(
    String hex,
    long timeCoarse,
    int timeFine,
    double timeSeconds,
    Integer sequenceCount,
    Decoded decoded,
    String decodeError) {

  /** Decoded TC fields per ICD §2/§3. */
  public record Decoded(
      int apid,
      int sequenceCount,
      int pusVersion,
      int ackFlags,
      int service,
      int subtype,
      int sourceId,
      String appDataHex) {

    /** Extracts the reported fields from a decoded TC packet. */
    public static Decoded of(TcPacket tc) {
      return new Decoded(
          tc.primaryHeader().apid(),
          tc.primaryHeader().sequenceCount(),
          tc.secondaryHeader().pusVersion(),
          tc.secondaryHeader().ackFlags(),
          tc.secondaryHeader().serviceType(),
          tc.secondaryHeader().messageSubtype(),
          tc.secondaryHeader().sourceId(),
          HexFormat.of().formatHex(tc.applicationData()));
    }
  }

  /** Builds the response for an injection at simulated time {@code nanos}. */
  public static TcSendResponse of(
      String hex, long nanos, Integer sequenceCount, Decoded decoded, String decodeError) {
    CucTime time = CucTime.ofNanos(nanos);
    return new TcSendResponse(
        hex,
        time.coarse(),
        time.fine(),
        time.coarse() + time.fine() / 65536.0,
        sequenceCount,
        decoded,
        decodeError);
  }
}
