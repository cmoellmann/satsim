package org.satsim.pus.crc;

/**
 * Packet Error Control CRC-16 per ICD §7 (CRC-CCITT variant used by
 * ECSS-E-ST-70-41C / CCSDS 133.0-B): polynomial 0x1021, initial value 0xFFFF,
 * no input/output reflection, no final XOR. The CRC is computed over the
 * entire packet except its last two octets and appended big-endian.
 *
 * <p>Sanity anchors (ICD §7): ASCII "123456789" → 0x29B1; octets 00 00 →
 * 0x1D0F; octets AB CD EF 01 → 0x04A2. [SIM-REQ-PUS-002]
 */
public final class Crc16Ccitt {

  private static final int POLYNOMIAL = 0x1021;
  private static final int INITIAL_VALUE = 0xFFFF;

  private Crc16Ccitt() {
  }

  /**
   * Computes the CRC-16 over {@code data[offset .. offset+length-1]}.
   *
   * @return the CRC in the range 0x0000–0xFFFF
   */
  public static int compute(byte[] data, int offset, int length) {
    if (offset < 0 || length < 0 || offset + length > data.length) {
      throw new IndexOutOfBoundsException(
          "offset=" + offset + ", length=" + length + ", data.length=" + data.length);
    }
    int crc = INITIAL_VALUE;
    for (int i = offset; i < offset + length; i++) {
      crc ^= (data[i] & 0xFF) << 8;
      for (int bit = 0; bit < 8; bit++) {
        if ((crc & 0x8000) != 0) {
          crc = ((crc << 1) ^ POLYNOMIAL) & 0xFFFF;
        } else {
          crc = (crc << 1) & 0xFFFF;
        }
      }
    }
    return crc;
  }

  /** Computes the CRC-16 over all of {@code data}. */
  public static int compute(byte[] data) {
    return compute(data, 0, data.length);
  }

  /**
   * Verifies a complete packet whose last two octets carry the CRC big-endian:
   * recomputes the CRC over all but the last two octets and compares.
   *
   * @param packet complete packet including the two trailing CRC octets
   * @return true if the trailing CRC matches the recomputed value
   * @throws IllegalArgumentException if the packet is shorter than 3 octets
   *     (nothing to protect plus 2 CRC octets)
   */
  public static boolean verify(byte[] packet) {
    if (packet.length < 3) {
      throw new IllegalArgumentException(
          "packet too short for payload + 2 CRC octets: " + packet.length);
    }
    int stored = ((packet[packet.length - 2] & 0xFF) << 8) | (packet[packet.length - 1] & 0xFF);
    return compute(packet, 0, packet.length - 2) == stored;
  }
}
