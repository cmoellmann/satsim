package org.satsim.pus;

/**
 * Checked exception raised when decoding a space packet fails a structural or
 * error-control check (ICD §6.3/§7). Decoding stops at the first violated
 * check, in the fixed order: minimum length, packet data length consistency,
 * CRC-16. No PUS-version or APID policy checks are performed by decoders;
 * those are simulator policy (ICD §10.2). [SIM-REQ-PUS-005]
 */
public final class PacketDecodeException extends Exception {

  private static final long serialVersionUID = 1L;

  /** Category of decode failure. */
  public enum Reason {
    /** Fewer octets than the minimum structural length (primary + secondary header + CRC). */
    TOO_SHORT,
    /** Packet length does not match the primary header's declared packet data length. */
    LENGTH_MISMATCH,
    /** CRC-16 verification (ICD §7) failed. */
    CRC_ERROR
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
