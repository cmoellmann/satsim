package org.satsim.pus.ccsds;

/**
 * CCSDS sequence flags per ICD §2. SatSim uses {@link #UNSEGMENTED} only, but
 * the codec is defined over all values so decoding never loses information.
 */
public enum SequenceFlags {
  CONTINUATION(0b00),
  FIRST(0b01),
  LAST(0b10),
  UNSEGMENTED(0b11);

  private final int bits;

  SequenceFlags(int bits) {
    this.bits = bits;
  }

  public int bits() {
    return bits;
  }

  static SequenceFlags fromBits(int bits) {
    return switch (bits & 0b11) {
      case 0b00 -> CONTINUATION;
      case 0b01 -> FIRST;
      case 0b10 -> LAST;
      default -> UNSEGMENTED;
    };
  }
}
