package org.satsim.pus.tm;

import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;
import org.satsim.pus.PacketDecodeException;
import org.satsim.pus.PacketDecodeException.Reason;
import org.satsim.pus.ccsds.PacketType;
import org.satsim.pus.ccsds.PrimaryHeader;
import org.satsim.pus.ccsds.SequenceFlags;
import org.satsim.pus.crc.Crc16Ccitt;

/**
 * Full PUS-C TM space packet: primary header (ICD §2) + TM secondary header
 * (ICD §4) + application data + CRC-16 packet error control (ICD §7).
 * [SIM-REQ-PUS-004]
 *
 * <p>Equality, hash code and {@link #toString()} are content-based.
 * {@code applicationData} is defensively copied both on construction and on
 * every read via {@link #applicationData()}.
 *
 * @param primaryHeader the CCSDS primary header; {@code type()} must be
 *     {@link PacketType#TM} and {@code secondaryHeaderFlag()} must be true,
 *     and {@code dataFieldOctets()} must equal
 *     {@link TmSecondaryHeader#LENGTH} + {@code applicationData.length} + 2
 * @param secondaryHeader the PUS-C TM secondary header
 * @param applicationData the application data octets
 */
public record TmPacket(PrimaryHeader primaryHeader, TmSecondaryHeader secondaryHeader, byte[] applicationData) {

  /** Packet error control (CRC-16) trailer size in octets, ICD §7. */
  private static final int CRC_LENGTH = 2;

  public TmPacket {
    if (primaryHeader == null || secondaryHeader == null || applicationData == null) {
      throw new NullPointerException("primaryHeader, secondaryHeader and applicationData must be non-null");
    }
    if (primaryHeader.type() != PacketType.TM) {
      throw new IllegalArgumentException("primary header type must be TM: " + primaryHeader.type());
    }
    if (!primaryHeader.secondaryHeaderFlag()) {
      throw new IllegalArgumentException("primary header must have secondaryHeaderFlag set for a TM packet");
    }
    int expected = TmSecondaryHeader.LENGTH + applicationData.length + CRC_LENGTH;
    if (primaryHeader.dataFieldOctets() != expected) {
      throw new IllegalArgumentException(
          "primary header dataFieldOctets " + primaryHeader.dataFieldOctets()
              + " inconsistent with secondary header (" + TmSecondaryHeader.LENGTH
              + ") + application data (" + applicationData.length + ") + CRC (" + CRC_LENGTH + ")");
    }
    applicationData = applicationData.clone();
  }

  /** Application data octets (defensively copied). */
  @Override
  public byte[] applicationData() {
    return applicationData.clone();
  }

  /**
   * Builds a consistent TM packet with a primary header per ICD system
   * conventions: version 0, TM, secondary header present, unsegmented.
   */
  public static TmPacket of(int apid, int sequenceCount, TmSecondaryHeader sec, byte[] appData) {
    int dataFieldOctets = TmSecondaryHeader.LENGTH + appData.length + CRC_LENGTH;
    PrimaryHeader primaryHeader = new PrimaryHeader(
        0, PacketType.TM, true, apid, SequenceFlags.UNSEGMENTED, sequenceCount, dataFieldOctets - 1);
    return new TmPacket(primaryHeader, sec, appData);
  }

  /** Encodes this packet: primary header + secondary header + application data + CRC-16 (ICD §7). */
  public byte[] encode() {
    byte[] primary = primaryHeader.encode();
    byte[] secondary = secondaryHeader.encode();
    byte[] out = new byte[primary.length + secondary.length + applicationData.length + CRC_LENGTH];
    int pos = 0;
    System.arraycopy(primary, 0, out, pos, primary.length);
    pos += primary.length;
    System.arraycopy(secondary, 0, out, pos, secondary.length);
    pos += secondary.length;
    System.arraycopy(applicationData, 0, out, pos, applicationData.length);
    pos += applicationData.length;
    int crc = Crc16Ccitt.compute(out, 0, pos);
    out[pos] = (byte) (crc >> 8);
    out[pos + 1] = (byte) crc;
    return out;
  }

  /**
   * Decodes a TM packet, checking in order: minimum structural length,
   * packet data length consistency, then CRC-16 (ICD §7). No PUS-version or
   * APID policy checks are performed here (simulator policy, ICD §10.2).
   *
   * @throws PacketDecodeException with the reason of the first violated check
   */
  public static TmPacket decode(byte[] packet) throws PacketDecodeException {
    int minLength = PrimaryHeader.LENGTH + TmSecondaryHeader.LENGTH + CRC_LENGTH;
    if (packet.length < minLength) {
      throw new PacketDecodeException(Reason.TOO_SHORT,
          "packet too short: " + packet.length + " octets, need at least " + minLength);
    }
    PrimaryHeader primaryHeader = PrimaryHeader.decode(packet, 0);
    int expectedLength = PrimaryHeader.LENGTH + primaryHeader.dataFieldOctets();
    if (packet.length != expectedLength) {
      throw new PacketDecodeException(Reason.LENGTH_MISMATCH,
          "packet length " + packet.length + " does not match primary header data field (" + expectedLength + ")");
    }
    if (!Crc16Ccitt.verify(packet)) {
      throw new PacketDecodeException(Reason.CRC_ERROR, "CRC-16 check failed");
    }
    TmSecondaryHeader secondaryHeader = TmSecondaryHeader.decode(packet, PrimaryHeader.LENGTH);
    int appDataOffset = PrimaryHeader.LENGTH + TmSecondaryHeader.LENGTH;
    int appDataLength = packet.length - appDataOffset - CRC_LENGTH;
    byte[] appData = Arrays.copyOfRange(packet, appDataOffset, appDataOffset + appDataLength);
    return new TmPacket(primaryHeader, secondaryHeader, appData);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof TmPacket other)) {
      return false;
    }
    return primaryHeader.equals(other.primaryHeader)
        && secondaryHeader.equals(other.secondaryHeader)
        && Arrays.equals(applicationData, other.applicationData);
  }

  @Override
  public int hashCode() {
    return Objects.hash(primaryHeader, secondaryHeader, Arrays.hashCode(applicationData));
  }

  @Override
  public String toString() {
    return "TmPacket[primaryHeader=" + primaryHeader + ", secondaryHeader=" + secondaryHeader
        + ", applicationData=" + HexFormat.of().formatHex(applicationData) + "]";
  }
}
