package org.satsim.pus.st3;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.satsim.pus.PacketDecodeException;
import org.satsim.pus.PacketDecodeException.Reason;

/**
 * TC(3,5) / TC(3,7) enable/disable-periodic-generation application data per
 * ICD §9.3: N (16 bits, structure count) + N x structure ID (16 bits each).
 * [SIM-REQ-HK-004]
 *
 * <p>Only structural validation is performed here: N in 1..16 and exact
 * application data length. Semantic validation (whether each SID is an
 * existing structure) is explicitly out of scope for this codec and is
 * simulator policy (ICD §9.1/§10.2); unknown SIDs decode successfully here.
 *
 * @param sids structure IDs, size 1..16, each 0..65535
 */
public record HkSidList(List<Integer> sids) {

  /** N (SID count) field width. */
  private static final int HEADER_LENGTH = 2;

  /** ICD §9.3: N (SID count) range. */
  private static final int MIN_COUNT = 1;

  private static final int MAX_COUNT = 16;

  public HkSidList {
    Objects.requireNonNull(sids, "sids must be non-null");
    if (sids.size() < MIN_COUNT || sids.size() > MAX_COUNT) {
      throw new IllegalArgumentException(
          "sids size out of range [" + MIN_COUNT + ".." + MAX_COUNT + "]: " + sids.size());
    }
    for (int sid : sids) {
      requireRange(sid, 0, 65535, "sid");
    }
    sids = List.copyOf(sids);
  }

  /** Encodes this SID list's application data per ICD §9.3. */
  public byte[] encode() {
    int n = sids.size();
    byte[] out = new byte[HEADER_LENGTH + 2 * n];
    out[0] = (byte) (n >> 8);
    out[1] = (byte) n;
    int pos = HEADER_LENGTH;
    for (int sid : sids) {
      out[pos] = (byte) (sid >> 8);
      out[pos + 1] = (byte) sid;
      pos += 2;
    }
    return out;
  }

  /**
   * Decodes TC(3,5)/TC(3,7) application data per ICD §9.3, checking in
   * order: minimum length (N present), N in 1..16, exact length
   * {@code 2 + 2*N}. No semantic checks (ICD §9.1) are performed.
   *
   * @throws PacketDecodeException with the reason of the first violated check
   */
  public static HkSidList decode(byte[] appData) throws PacketDecodeException {
    Objects.requireNonNull(appData, "appData must be non-null");
    if (appData.length < HEADER_LENGTH) {
      throw new PacketDecodeException(Reason.TOO_SHORT,
          "TC(3,5)/TC(3,7) application data too short: " + appData.length + " octets, need at least "
              + HEADER_LENGTH + " (N)");
    }
    int n = ((appData[0] & 0xFF) << 8) | (appData[1] & 0xFF);
    if (n < MIN_COUNT || n > MAX_COUNT) {
      throw new PacketDecodeException(Reason.FIELD_OUT_OF_RANGE,
          "TC(3,5)/TC(3,7) N (SID count) out of range [" + MIN_COUNT + ".." + MAX_COUNT + "]: " + n);
    }
    int expectedLength = HEADER_LENGTH + 2 * n;
    if (appData.length != expectedLength) {
      throw new PacketDecodeException(Reason.LENGTH_MISMATCH,
          "TC(3,5)/TC(3,7) application data length " + appData.length
              + " does not match N-derived length (" + expectedLength + ")");
    }
    List<Integer> sids = new ArrayList<>(n);
    int pos = HEADER_LENGTH;
    for (int i = 0; i < n; i++) {
      sids.add(((appData[pos] & 0xFF) << 8) | (appData[pos + 1] & 0xFF));
      pos += 2;
    }
    return new HkSidList(sids);
  }

  private static void requireRange(int value, int min, int max, String field) {
    if (value < min || value > max) {
      throw new IllegalArgumentException(field + " out of range [" + min + ".." + max + "]: " + value);
    }
  }
}
