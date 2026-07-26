package org.satsim.cag;

import java.util.HexFormat;
import org.satsim.pus.tc.TcPacket;

/**
 * What a held telecommand actually does, in the terms a human confirming it
 * needs: the decoded command identity. Returned with a confirmation-required
 * decision (ICD §8.4) and listed in the gateway-state resource.
 *
 * <p>Deliberately excludes the CCSDS packet sequence count. The sequence count
 * is transport bookkeeping assigned per injection: it differs between the
 * octets previewed when the gate classifies and the octets finally injected,
 * and it changes whenever another operator commands in between. Binding a
 * confirmation to it would make confirmations expire for reasons that have
 * nothing to do with what was confirmed. What a human confirms is "disable
 * housekeeping structure 1", not one particular counter value.
 *
 * @param service PUS service type
 * @param subtype PUS message subtype
 * @param ackFlags acknowledgement flags per ICD §3
 * @param appDataHex application data octets as lowercase hex ({@code ""} if none)
 */
public record CommandSummary(int service, int subtype, int ackFlags, String appDataHex) {

  /** Summarizes a decoded telecommand. */
  public static CommandSummary of(TcPacket packet) {
    return new CommandSummary(
        packet.secondaryHeader().serviceType(),
        packet.secondaryHeader().messageSubtype(),
        packet.secondaryHeader().ackFlags(),
        HexFormat.of().formatHex(packet.applicationData()));
  }

  /**
   * The identity a confirmation is bound to: two submissions with equal
   * identity are the same command, whatever their sequence counts.
   */
  String identity() {
    return "TC(" + service + "," + subtype + ") ack=" + ackFlags + " appData=" + appDataHex;
  }

  @Override
  public String toString() {
    return identity();
  }
}
