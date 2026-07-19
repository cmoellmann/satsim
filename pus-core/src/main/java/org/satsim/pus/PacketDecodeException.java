package org.satsim.pus;

/**
 * Checked exception raised when decoding a space packet, or a service
 * application data field, fails a structural check (ICD §6.3/§7, or the
 * per-service application data layouts, e.g. ICD §9.2/§9.3/§9.4). Space
 * packet decoding (see {@code TcPacket}/{@code TmPacket}) stops at the first
 * violated check, in the fixed order: minimum length, packet data length
 * consistency, CRC-16. Application data codecs (e.g. {@code org.satsim.pus.st3})
 * use the same exception type for their own structural checks (count fields
 * out of range, declared/exact length mismatches); semantic validation is
 * explicitly out of scope for all decoders and is simulator policy
 * (ICD §9.1/§10.2). No PUS-version or APID policy checks are performed by
 * decoders either. [SIM-REQ-PUS-005]
 */
public final class PacketDecodeException extends Exception {

  private static final long serialVersionUID = 1L;

  /** Category of decode failure. */
  public enum Reason {
    /** Fewer octets than the minimum structural length required to decode. */
    TOO_SHORT,
    /** Length does not match the length implied by a declared header/count field. */
    LENGTH_MISMATCH,
    /** CRC-16 verification (ICD §7) failed. */
    CRC_ERROR,
    /** A structurally present count field is outside its permitted range (e.g. ICD §9.2 N1, §9.3 N). */
    FIELD_OUT_OF_RANGE
  }

  private final Reason reason;

  /**
   * @param reason the category of decode failure
   * @param message a human-readable description of the failure
   */
  public PacketDecodeException(Reason reason, String message) {
    super(message);
    if (reason == null) {
      throw new NullPointerException("reason must be non-null");
    }
    this.reason = reason;
  }

  /** The category of decode failure. */
  public Reason reason() {
    return reason;
  }
}
