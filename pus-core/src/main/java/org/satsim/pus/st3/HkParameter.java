package org.satsim.pus.st3;

import java.util.Arrays;
import java.util.Optional;

/**
 * ST[3] housekeeping parameter definitions per ICD §9.5. Each parameter has a
 * fixed 16-bit ID and a fixed, unsigned octet width used both in the TC(3,1)
 * create-request parameter list (ICD §9.2) and in the TM(3,25) report value
 * layout (ICD §9.4). Semantic validation of parameter IDs found on the wire
 * (unknown IDs) is not this enum's concern; {@link #byId(int)} is total over
 * {@code int} and returns {@link Optional#empty()} for unknown IDs so that
 * decoding can let such values pass through to the simulator (ICD §9.1).
 * [SIM-REQ-HK-002]
 */
public enum HkParameter {

  /** HK-P001 TC accepted count, uint32 (ICD §9.5). */
  P001(0x0001, 4),

  /** HK-P002 TM emitted count, uint32 (ICD §9.5). */
  P002(0x0002, 4),

  /** HK-P003 battery voltage (synthetic), uint16 (ICD §9.5). */
  P003(0x0003, 2);

  private final int id;
  private final int octets;

  HkParameter(int id, int octets) {
    this.id = id;
    this.octets = octets;
  }

  /** The 16-bit parameter ID per ICD §9.5. */
  public int id() {
    return id;
  }

  /** The fixed encoded width of this parameter's value in octets. */
  public int octets() {
    return octets;
  }

  /**
   * Looks up the parameter with the given ID. Returns {@link Optional#empty()}
   * for an ID not defined by ICD §9.5 - unknown parameter IDs are a semantic
   * validation concern for the simulator (ICD §9.1), not this codec.
   */
  public static Optional<HkParameter> byId(int id) {
    return Arrays.stream(values()).filter(p -> p.id == id).findFirst();
  }
}
