package org.satsim.pus.ccsds;

/**
 * CCSDS Space Packet primary header (6 octets) per ICD §2 / CCSDS 133.0-B.
 *
 * <p>{@code packetDataLength} carries the raw 16-bit field value, i.e. the
 * number of octets in the packet data field <em>minus one</em> (ICD §2);
 * {@link #dataFieldOctets()} returns the actual octet count. All fields cover
 * their full CCSDS ranges; system conventions (version 0, APID 100,
 * unsegmented, secondary header present) are the callers' contract, not
 * enforced here. [SIM-REQ-PUS-001]
 *
 * @param versionNumber packet version number, 0–7 (ICD: always 0)
 * @param type packet type bit (TC/TM)
 * @param secondaryHeaderFlag true if a secondary header is present (ICD: always true)
 * @param apid application process identifier, 0–2047 (ICD: 100)
 * @param sequenceFlags segmentation flags (ICD: unsegmented)
 * @param sequenceCount packet sequence count per source, 0–16383
 * @param packetDataLength data field octet count minus one, 0–65535
 */
public record PrimaryHeader(
    int versionNumber,
    PacketType type,
    boolean secondaryHeaderFlag,
    int apid,
    SequenceFlags sequenceFlags,
    int sequenceCount,
    int packetDataLength) {

  /** Encoded size of the primary header in octets. */
  public static final int LENGTH = 6;

  public static final int APID_MAX = 2047;
  public static final int SEQUENCE_COUNT_MAX = 16383;
  public static final int PACKET_DATA_LENGTH_MAX = 65535;

  public PrimaryHeader {
    requireRange(versionNumber, 0, 7, "versionNumber");
    if (type == null || sequenceFlags == null) {
      throw new NullPointerException("type and sequenceFlags must be non-null");
    }
    requireRange(apid, 0, APID_MAX, "apid");
    requireRange(sequenceCount, 0, SEQUENCE_COUNT_MAX, "sequenceCount");
    requireRange(packetDataLength, 0, PACKET_DATA_LENGTH_MAX, "packetDataLength");
  }

  /** Number of octets in the packet data field (= {@code packetDataLength} + 1). */
  public int dataFieldOctets() {
    return packetDataLength + 1;
  }

  /** Encodes this header into a new 6-octet array per ICD §2. */
  public byte[] encode() {
    byte[] out = new byte[LENGTH];
    int word0 = (versionNumber << 13)
        | (type.bit() << 12)
        | ((secondaryHeaderFlag ? 1 : 0) << 11)
        | apid;
    int word1 = (sequenceFlags.bits() << 14) | sequenceCount;
    out[0] = (byte) (word0 >> 8);
    out[1] = (byte) word0;
    out[2] = (byte) (word1 >> 8);
    out[3] = (byte) word1;
    out[4] = (byte) (packetDataLength >> 8);
    out[5] = (byte) packetDataLength;
    return out;
  }

  /**
   * Decodes a primary header from {@code data} starting at {@code offset}.
   *
   * @throws IllegalArgumentException if fewer than 6 octets are available
   */
  public static PrimaryHeader decode(byte[] data, int offset) {
    if (offset < 0 || offset + LENGTH > data.length) {
      throw new IllegalArgumentException(
          "need " + LENGTH + " octets at offset " + offset + ", have " + data.length);
    }
    int word0 = ((data[offset] & 0xFF) << 8) | (data[offset + 1] & 0xFF);
    int word1 = ((data[offset + 2] & 0xFF) << 8) | (data[offset + 3] & 0xFF);
    int length = ((data[offset + 4] & 0xFF) << 8) | (data[offset + 5] & 0xFF);
    return new PrimaryHeader(
        (word0 >> 13) & 0b111,
        PacketType.fromBit((word0 >> 12) & 1),
        ((word0 >> 11) & 1) != 0,
        word0 & 0x7FF,
        SequenceFlags.fromBits((word1 >> 14) & 0b11),
        word1 & 0x3FFF,
        length);
  }

  private static void requireRange(int value, int min, int max, String field) {
    if (value < min || value > max) {
      throw new IllegalArgumentException(field + " out of range [" + min + ".." + max + "]: " + value);
    }
  }
}
