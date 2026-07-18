package org.satsim.pus.tc;

/**
 * PUS-C TC secondary header (5 octets) per ICD §3.
 *
 * <p>Acknowledgement flags: bit3 = acceptance, bit2 = start, bit1 = progress,
 * bit0 = completion. {@link #decode(byte[], int)} does not enforce
 * {@code pusVersion == 2}; accepting or rejecting an unsupported PUS version
 * is simulator policy (ICD §10.2), not a codec concern. [SIM-REQ-PUS-003]
 *
 * @param pusVersion TC packet PUS version number, 0–15 (ICD: 2 for PUS-C)
 * @param ackFlags acknowledgement flags, 0–15 (ICD §3 bit layout)
 * @param serviceType PUS service type, 0–255
 * @param messageSubtype PUS message subtype, 0–255
 * @param sourceId source ID, 0–65535 (ICD: 0, single ground source, ADR-0002)
 */
public record TcSecondaryHeader(
    int pusVersion, int ackFlags, int serviceType, int messageSubtype, int sourceId) {

  /** Encoded size of the TC secondary header in octets. */
  public static final int LENGTH = 5;

  /** PUS-C version number per ICD §3. */
  public static final int PUS_C_VERSION = 2;

  public TcSecondaryHeader {
    requireRange(pusVersion, 0, 15, "pusVersion");
    requireRange(ackFlags, 0, 15, "ackFlags");
    requireRange(serviceType, 0, 255, "serviceType");
    requireRange(messageSubtype, 0, 255, "messageSubtype");
    requireRange(sourceId, 0, 65535, "sourceId");
  }

  /** Acceptance acknowledgement requested (ackFlags bit3). */
  public boolean ackAcceptance() {
    return (ackFlags & 0b1000) != 0;
  }

  /** Start acknowledgement requested (ackFlags bit2). */
  public boolean ackStart() {
    return (ackFlags & 0b0100) != 0;
  }

  /** Progress acknowledgement requested (ackFlags bit1). */
  public boolean ackProgress() {
    return (ackFlags & 0b0010) != 0;
  }

  /** Completion acknowledgement requested (ackFlags bit0). */
  public boolean ackCompletion() {
    return (ackFlags & 0b0001) != 0;
  }

  /** Encodes this header into a new 5-octet array per ICD §3. */
  public byte[] encode() {
    byte[] out = new byte[LENGTH];
    out[0] = (byte) ((pusVersion << 4) | ackFlags);
    out[1] = (byte) serviceType;
    out[2] = (byte) messageSubtype;
    out[3] = (byte) (sourceId >> 8);
    out[4] = (byte) sourceId;
    return out;
  }

  /**
   * Decodes a TC secondary header from {@code data} starting at
   * {@code offset}. {@code pusVersion} is not checked against
   * {@link #PUS_C_VERSION} here (ICD §10.2, simulator policy).
   *
   * @throws IllegalArgumentException if fewer than 5 octets are available
   */
  public static TcSecondaryHeader decode(byte[] data, int offset) {
    if (offset < 0 || offset + LENGTH > data.length) {
      throw new IllegalArgumentException(
          "need " + LENGTH + " octets at offset " + offset + ", have " + data.length);
    }
    int byte0 = data[offset] & 0xFF;
    int serviceType = data[offset + 1] & 0xFF;
    int messageSubtype = data[offset + 2] & 0xFF;
    int sourceId = ((data[offset + 3] & 0xFF) << 8) | (data[offset + 4] & 0xFF);
    return new TcSecondaryHeader((byte0 >> 4) & 0xF, byte0 & 0xF, serviceType, messageSubtype, sourceId);
  }

  private static void requireRange(int value, int min, int max, String field) {
    if (value < min || value > max) {
      throw new IllegalArgumentException(field + " out of range [" + min + ".." + max + "]: " + value);
    }
  }
}
