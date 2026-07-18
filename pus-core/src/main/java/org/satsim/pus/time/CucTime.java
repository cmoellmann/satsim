package org.satsim.pus.time;

/**
 * On-board time, CCSDS Unsegmented Time Code 4+2 per ICD §5 / CCSDS 301.0-B:
 * 4 octets coarse time (seconds) + 2 octets fine time (LSB = 2^-16 s). The
 * P-field is not transmitted (implicit, agency-defined epoch). Epoch is
 * 2026-01-01T00:00:00 UTC (ADR-0004); this time is sourced exclusively from
 * {@code SimulationClock} (ADR-0004/ADR-0006 C1). [SIM-REQ-TIME-002]
 *
 * <p>Example (ICD §5): T = 1.5 s → coarse {@code 00 00 00 01}, fine
 * {@code 80 00}.
 *
 * @param coarse seconds since epoch, 0..4294967295 (unsigned 32-bit, held in a long)
 * @param fine fractional seconds in units of 2^-16 s, 0..65535
 */
public record CucTime(long coarse, int fine) {

  /** Encoded size of a CUC 4+2 time in octets. */
  public static final int LENGTH = 6;

  /** Maximum representable coarse time (unsigned 32-bit). */
  public static final long COARSE_MAX = 4_294_967_295L;

  /** Maximum representable fine time (unsigned 16-bit). */
  public static final int FINE_MAX = 65535;

  private static final long NANOS_PER_SECOND = 1_000_000_000L;
  private static final long FINE_UNITS = 65536L;

  public CucTime {
    requireRange(coarse, 0, COARSE_MAX, "coarse");
    requireRange(fine, 0, FINE_MAX, "fine");
  }

  /**
   * Encodes this time into a new 6-octet array: 4 octets coarse + 2 octets
   * fine, big-endian (ICD §5).
   */
  public byte[] encode() {
    byte[] out = new byte[LENGTH];
    out[0] = (byte) (coarse >> 24);
    out[1] = (byte) (coarse >> 16);
    out[2] = (byte) (coarse >> 8);
    out[3] = (byte) coarse;
    out[4] = (byte) (fine >> 8);
    out[5] = (byte) fine;
    return out;
  }

  /**
   * Decodes a CUC 4+2 time from {@code data} starting at {@code offset}.
   *
   * @throws IllegalArgumentException if fewer than 6 octets are available
   */
  public static CucTime decode(byte[] data, int offset) {
    if (offset < 0 || offset + LENGTH > data.length) {
      throw new IllegalArgumentException(
          "need " + LENGTH + " octets at offset " + offset + ", have " + data.length);
    }
    long coarse = ((data[offset] & 0xFFL) << 24)
        | ((data[offset + 1] & 0xFFL) << 16)
        | ((data[offset + 2] & 0xFFL) << 8)
        | (data[offset + 3] & 0xFFL);
    int fine = ((data[offset + 4] & 0xFF) << 8) | (data[offset + 5] & 0xFF);
    return new CucTime(coarse, fine);
  }

  /**
   * Builds a {@code CucTime} from nanoseconds of simulated time since epoch
   * (ICD §5). {@code fine = round(fraction * 65536)}, computed in integer
   * arithmetic, half-up: {@code (fracNanos * 65536 + 500_000_000) / 1_000_000_000}.
   * A fine value that rounds up to 65536 carries into coarse (fine becomes 0,
   * coarse is incremented).
   *
   * @throws IllegalArgumentException if {@code nanosSinceEpoch} is negative,
   *     or if the resulting coarse seconds (after any carry) would overflow
   *     32 bits
   */
  public static CucTime ofNanos(long nanosSinceEpoch) {
    if (nanosSinceEpoch < 0) {
      throw new IllegalArgumentException("nanosSinceEpoch must be >= 0: " + nanosSinceEpoch);
    }
    long coarse = nanosSinceEpoch / NANOS_PER_SECOND;
    long fracNanos = nanosSinceEpoch % NANOS_PER_SECOND;
    long fine = (fracNanos * FINE_UNITS + NANOS_PER_SECOND / 2) / NANOS_PER_SECOND;
    if (fine == FINE_UNITS) {
      fine = 0;
      coarse += 1;
    }
    if (coarse > COARSE_MAX) {
      throw new IllegalArgumentException(
          "coarse seconds overflow 32 bits for nanosSinceEpoch=" + nanosSinceEpoch);
    }
    return new CucTime(coarse, (int) fine);
  }

  /**
   * Converts this time to nanoseconds since epoch (inverse of
   * {@link #ofNanos(long)}): {@code round(fine * 1e9 / 65536)}, integer
   * arithmetic, half-up.
   */
  public long toNanos() {
    long fracNanos = ((long) fine * NANOS_PER_SECOND + FINE_UNITS / 2) / FINE_UNITS;
    return coarse * NANOS_PER_SECOND + fracNanos;
  }

  private static void requireRange(long value, long min, long max, String field) {
    if (value < min || value > max) {
      throw new IllegalArgumentException(field + " out of range [" + min + ".." + max + "]: " + value);
    }
  }
}
