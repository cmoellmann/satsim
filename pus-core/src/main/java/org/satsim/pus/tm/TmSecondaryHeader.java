package org.satsim.pus.tm;

import org.satsim.pus.time.CucTime;

/**
 * PUS-C TM secondary header (13 octets) per ICD §4. [SIM-REQ-PUS-004]
 *
 * @param pusVersion TM packet PUS version number, 0–15 (ICD: 2 for PUS-C)
 * @param timeReferenceStatus spacecraft time reference status, 0–15 (ICD: 0)
 * @param serviceType PUS service type, 0–255
 * @param messageSubtype PUS message subtype, 0–255
 * @param messageTypeCounter message type counter per (service, subtype), 0–65535
 * @param destinationId destination ID, 0–65535 (ICD: 0, single ground destination)
 * @param time on-board time, CUC 4+2 per ICD §5
 */
public record TmSecondaryHeader(
    int pusVersion,
    int timeReferenceStatus,
    int serviceType,
    int messageSubtype,
    int messageTypeCounter,
    int destinationId,
    CucTime time) {

  /** Encoded size of the TM secondary header in octets. */
  public static final int LENGTH = 13;

  public TmSecondaryHeader {
    requireRange(pusVersion, 0, 15, "pusVersion");
    requireRange(timeReferenceStatus, 0, 15, "timeReferenceStatus");
    requireRange(serviceType, 0, 255, "serviceType");
    requireRange(messageSubtype, 0, 255, "messageSubtype");
    requireRange(messageTypeCounter, 0, 65535, "messageTypeCounter");
    requireRange(destinationId, 0, 65535, "destinationId");
    if (time == null) {
      throw new NullPointerException("time must be non-null");
    }
  }

  /** Encodes this header into a new 13-octet array per ICD §4. */
  public byte[] encode() {
    byte[] out = new byte[LENGTH];
    out[0] = (byte) ((pusVersion << 4) | timeReferenceStatus);
    out[1] = (byte) serviceType;
    out[2] = (byte) messageSubtype;
    out[3] = (byte) (messageTypeCounter >> 8);
    out[4] = (byte) messageTypeCounter;
    out[5] = (byte) (destinationId >> 8);
    out[6] = (byte) destinationId;
    System.arraycopy(time.encode(), 0, out, 7, CucTime.LENGTH);
    return out;
  }

  /**
   * Decodes a TM secondary header from {@code data} starting at
   * {@code offset}.
   *
   * @throws IllegalArgumentException if fewer than 13 octets are available
   */
  public static TmSecondaryHeader decode(byte[] data, int offset) {
    if (offset < 0 || offset + LENGTH > data.length) {
      throw new IllegalArgumentException(
          "need " + LENGTH + " octets at offset " + offset + ", have " + data.length);
    }
    int byte0 = data[offset] & 0xFF;
    int serviceType = data[offset + 1] & 0xFF;
    int messageSubtype = data[offset + 2] & 0xFF;
    int messageTypeCounter = ((data[offset + 3] & 0xFF) << 8) | (data[offset + 4] & 0xFF);
    int destinationId = ((data[offset + 5] & 0xFF) << 8) | (data[offset + 6] & 0xFF);
    CucTime time = CucTime.decode(data, offset + 7);
    return new TmSecondaryHeader(
        (byte0 >> 4) & 0xF, byte0 & 0xF, serviceType, messageSubtype, messageTypeCounter, destinationId, time);
  }

  private static void requireRange(int value, int min, int max, String field) {
    if (value < min || value > max) {
      throw new IllegalArgumentException(field + " out of range [" + min + ".." + max + "]: " + value);
    }
  }
}
