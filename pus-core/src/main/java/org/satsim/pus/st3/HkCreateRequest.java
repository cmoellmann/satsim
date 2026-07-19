package org.satsim.pus.st3;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.satsim.pus.PacketDecodeException;
import org.satsim.pus.PacketDecodeException.Reason;

/**
 * TC(3,1) create-housekeeping-report-structure application data per ICD §9.2:
 * SID (16 bits) + collection interval (32 bits, milliseconds of simulated
 * time) + N1 (16 bits, parameter count) + N1 x parameter ID (16 bits each,
 * report order = list order). [SIM-REQ-HK-001]
 *
 * <p>Only structural validation is performed here: SID/interval range,
 * N1 in 1..16, and exact application data length. Semantic validation (SID
 * value domains such as SID 0 or SID 1 being reserved, unknown parameter
 * IDs, the ICD §9.2 minimum collection interval) is explicitly out of scope
 * for this codec and is simulator policy (ICD §9.1/§10.2); such values decode
 * successfully here.
 *
 * @param sid structure ID, 0..65535 (ICD: 1-65535 valid, 0 invalid - not
 *     enforced here)
 * @param collectionIntervalMs collection interval in milliseconds of
 *     simulated time, 0..0xFFFFFFFF (unsigned 32-bit, held in a long; ICD:
 *     minimum 100 - not enforced here)
 * @param parameterIds parameter IDs in report order, size 1..16
 */
public record HkCreateRequest(int sid, long collectionIntervalMs, List<Integer> parameterIds) {

  /** SID (2) + collection interval (4) + N1 (2), before the parameter list. */
  private static final int HEADER_LENGTH = 8;

  /** ICD §9.2: N1 parameter count range. */
  private static final int MIN_PARAMS = 1;

  private static final int MAX_PARAMS = 16;

  private static final long UINT32_MAX = 0xFFFFFFFFL;

  public HkCreateRequest {
    requireRange(sid, 0, 65535, "sid");
    requireRange(collectionIntervalMs, 0, UINT32_MAX, "collectionIntervalMs");
    Objects.requireNonNull(parameterIds, "parameterIds must be non-null");
    if (parameterIds.size() < MIN_PARAMS || parameterIds.size() > MAX_PARAMS) {
      throw new IllegalArgumentException(
          "parameterIds size out of range [" + MIN_PARAMS + ".." + MAX_PARAMS + "]: " + parameterIds.size());
    }
    for (int id : parameterIds) {
      requireRange(id, 0, 65535, "parameterId");
    }
    parameterIds = List.copyOf(parameterIds);
  }

  /** Encodes this request's application data per ICD §9.2. */
  public byte[] encode() {
    int n1 = parameterIds.size();
    byte[] out = new byte[HEADER_LENGTH + 2 * n1];
    out[0] = (byte) (sid >> 8);
    out[1] = (byte) sid;
    out[2] = (byte) (collectionIntervalMs >> 24);
    out[3] = (byte) (collectionIntervalMs >> 16);
    out[4] = (byte) (collectionIntervalMs >> 8);
    out[5] = (byte) collectionIntervalMs;
    out[6] = (byte) (n1 >> 8);
    out[7] = (byte) n1;
    int pos = HEADER_LENGTH;
    for (int id : parameterIds) {
      out[pos] = (byte) (id >> 8);
      out[pos + 1] = (byte) id;
      pos += 2;
    }
    return out;
  }

  /**
   * Decodes TC(3,1) application data per ICD §9.2, checking in order: minimum
   * length (header fields present), N1 in 1..16, exact length
   * {@code 8 + 2*N1}. No semantic checks (ICD §9.1) are performed.
   *
   * @throws PacketDecodeException with the reason of the first violated check
   */
  public static HkCreateRequest decode(byte[] appData) throws PacketDecodeException {
    Objects.requireNonNull(appData, "appData must be non-null");
    if (appData.length < HEADER_LENGTH) {
      throw new PacketDecodeException(Reason.TOO_SHORT,
          "TC(3,1) application data too short: " + appData.length + " octets, need at least " + HEADER_LENGTH
              + " (SID + collection interval + N1)");
    }
    int sid = ((appData[0] & 0xFF) << 8) | (appData[1] & 0xFF);
    long interval = ((long) (appData[2] & 0xFF) << 24)
        | ((long) (appData[3] & 0xFF) << 16)
        | ((long) (appData[4] & 0xFF) << 8)
        | (long) (appData[5] & 0xFF);
    int n1 = ((appData[6] & 0xFF) << 8) | (appData[7] & 0xFF);
    if (n1 < MIN_PARAMS || n1 > MAX_PARAMS) {
      throw new PacketDecodeException(Reason.FIELD_OUT_OF_RANGE,
          "TC(3,1) N1 (parameter count) out of range [" + MIN_PARAMS + ".." + MAX_PARAMS + "]: " + n1);
    }
    int expectedLength = HEADER_LENGTH + 2 * n1;
    if (appData.length != expectedLength) {
      throw new PacketDecodeException(Reason.LENGTH_MISMATCH,
          "TC(3,1) application data length " + appData.length
              + " does not match N1-derived length (" + expectedLength + ")");
    }
    List<Integer> parameterIds = new ArrayList<>(n1);
    int pos = HEADER_LENGTH;
    for (int i = 0; i < n1; i++) {
      parameterIds.add(((appData[pos] & 0xFF) << 8) | (appData[pos + 1] & 0xFF));
      pos += 2;
    }
    return new HkCreateRequest(sid, interval, parameterIds);
  }

  private static void requireRange(long value, long min, long max, String field) {
    if (value < min || value > max) {
      throw new IllegalArgumentException(field + " out of range [" + min + ".." + max + "]: " + value);
    }
  }
}
