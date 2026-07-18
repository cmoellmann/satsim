package org.satsim.sim.web;

import org.satsim.pus.tm.TmPacket;

/**
 * WebSocket TM frame (ICD §8): raw packet hex plus decoded header fields
 * [SIM-REQ-UI-002, SIM-REQ-UI-003]. {@code decoded} is null only if an
 * emitted TM unexpectedly fails to decode (logged as a defect).
 */
public record TmFrame(String hex, Decoded decoded) {

  /** Decoded primary and TM secondary header fields per ICD §2/§4/§5. */
  public record Decoded(
      int apid,
      int sequenceCount,
      int service,
      int subtype,
      int messageTypeCounter,
      int destinationId,
      long timeCoarse,
      int timeFine,
      double timeSeconds) {

    /** Extracts the reported fields from a decoded TM packet. */
    public static Decoded of(TmPacket tm) {
      return new Decoded(
          tm.primaryHeader().apid(),
          tm.primaryHeader().sequenceCount(),
          tm.secondaryHeader().serviceType(),
          tm.secondaryHeader().messageSubtype(),
          tm.secondaryHeader().messageTypeCounter(),
          tm.secondaryHeader().destinationId(),
          tm.secondaryHeader().time().coarse(),
          tm.secondaryHeader().time().fine(),
          tm.secondaryHeader().time().coarse() + tm.secondaryHeader().time().fine() / 65536.0);
    }
  }
}
