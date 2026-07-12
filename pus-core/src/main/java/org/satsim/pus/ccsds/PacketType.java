package org.satsim.pus.ccsds;

/** CCSDS packet type bit per ICD §2: 1 = telecommand, 0 = telemetry. */
public enum PacketType {
  TM(0),
  TC(1);

  private final int bit;

  PacketType(int bit) {
    this.bit = bit;
  }

  public int bit() {
    return bit;
  }

  static PacketType fromBit(int bit) {
    return bit == 0 ? TM : TC;
  }
}
