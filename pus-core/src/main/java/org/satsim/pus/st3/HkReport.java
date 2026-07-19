package org.satsim.pus.st3;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.satsim.pus.PacketDecodeException;
import org.satsim.pus.PacketDecodeException.Reason;

/**
 * TM(3,25) housekeeping parameter report application data per ICD §9.4: SID
 * (16 bits) followed by the parameter values in the reporting structure's
 * fixed order - no count field, since the layout is fixed by the structure
 * definition (ICD §9.2/§9.6). Because of this, {@link #encode(List)} and
 * {@link #decode(byte[], List)} both take the structure's parameter order as
 * an explicit argument. [SIM-REQ-HK-002]
 *
 * <p>Values are unsigned; {@link HkParameter#P001} and {@link HkParameter#P002}
 * (uint32) and {@link HkParameter#P003} (uint16) all fit in a {@code long}.
 *
 * @param sid reporting structure ID, 0..65535
 * @param values parameter values, in the structure's parameter order, each
 *     0..0xFFFFFFFF (the widest defined parameter width, uint32)
 */
public record HkReport(int sid, List<Long> values) {

  /** SID field width, before the parameter values. */
  private static final int HEADER_LENGTH = 2;

  private static final long UINT32_MAX = 0xFFFFFFFFL;

  public HkReport {
    requireRange(sid, 0, 65535, "sid");
    Objects.requireNonNull(values, "values must be non-null");
    if (values.isEmpty()) {
      throw new IllegalArgumentException("values must be non-empty");
    }
    for (long value : values) {
      requireRange(value, 0, UINT32_MAX, "value");
    }
    values = List.copyOf(values);
  }

  /**
   * Encodes this report's application data per ICD §9.4, in the given
   * structure parameter order.
   *
   * @param order the reporting structure's parameters, in report order;
   *     {@code order.size()} must equal {@code values().size()}
   */
  public byte[] encode(List<HkParameter> order) {
    Objects.requireNonNull(order, "order must be non-null");
    if (order.size() != values.size()) {
      throw new IllegalArgumentException(
          "order size (" + order.size() + ") does not match values size (" + values.size() + ")");
    }
    int total = HEADER_LENGTH + order.stream().mapToInt(HkParameter::octets).sum();
    byte[] out = new byte[total];
    out[0] = (byte) (sid >> 8);
    out[1] = (byte) sid;
    int pos = HEADER_LENGTH;
    for (int i = 0; i < order.size(); i++) {
      HkParameter parameter = order.get(i);
      long value = values.get(i);
      long widthMax = (1L << (8 * parameter.octets())) - 1;
      if (value > widthMax) {
        throw new IllegalArgumentException(
            "value " + value + " does not fit " + parameter + " (" + parameter.octets() + " octets)");
      }
      if (parameter.octets() == 4) {
        out[pos] = (byte) (value >> 24);
        out[pos + 1] = (byte) (value >> 16);
        out[pos + 2] = (byte) (value >> 8);
        out[pos + 3] = (byte) value;
      } else {
        out[pos] = (byte) (value >> 8);
        out[pos + 1] = (byte) value;
      }
      pos += parameter.octets();
    }
    return out;
  }

  /**
   * Decodes TM(3,25) application data per ICD §9.4, given the reporting
   * structure's parameter order. The only structural check is the exact
   * application data length {@code 2 + sum(order octets)} - there is no count
   * field, the layout being fixed by the structure definition.
   *
   * @throws PacketDecodeException if {@code appData.length} does not match
   *     the length implied by {@code order}
   */
  public static HkReport decode(byte[] appData, List<HkParameter> order) throws PacketDecodeException {
    Objects.requireNonNull(appData, "appData must be non-null");
    Objects.requireNonNull(order, "order must be non-null");
    int expectedLength = HEADER_LENGTH + order.stream().mapToInt(HkParameter::octets).sum();
    if (appData.length != expectedLength) {
      throw new PacketDecodeException(Reason.LENGTH_MISMATCH,
          "TM(3,25) application data length " + appData.length
              + " does not match structure-derived length (" + expectedLength + ")");
    }
    int sid = ((appData[0] & 0xFF) << 8) | (appData[1] & 0xFF);
    List<Long> values = new ArrayList<>(order.size());
    int pos = HEADER_LENGTH;
    for (HkParameter parameter : order) {
      long value;
      if (parameter.octets() == 4) {
        value = ((long) (appData[pos] & 0xFF) << 24)
            | ((long) (appData[pos + 1] & 0xFF) << 16)
            | ((long) (appData[pos + 2] & 0xFF) << 8)
            | (long) (appData[pos + 3] & 0xFF);
      } else {
        value = ((long) (appData[pos] & 0xFF) << 8) | (long) (appData[pos + 1] & 0xFF);
      }
      values.add(value);
      pos += parameter.octets();
    }
    return new HkReport(sid, values);
  }

  private static void requireRange(long value, long min, long max, String field) {
    if (value < min || value > max) {
      throw new IllegalArgumentException(field + " out of range [" + min + ".." + max + "]: " + value);
    }
  }
}
