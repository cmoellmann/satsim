package org.satsim.pus.tc;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.satsim.pus.PacketDecodeException;
import org.satsim.pus.ccsds.PacketType;
import org.satsim.pus.ccsds.PrimaryHeader;
import org.satsim.pus.ccsds.SequenceFlags;
import org.satsim.testsupport.Requirement;
import org.satsim.testsupport.TestCase;

class TcPacketTest {

  private static final HexFormat HEX = HexFormat.of().withDelimiter(" ");

  // ICD §6.1: V-TC-01, V-TC-02.
  private static final byte[] V_TC_01 = HEX.parseHex("18 64 C0 00 00 06 20 11 01 00 00 FA 83");
  private static final byte[] V_TC_02 = HEX.parseHex("18 64 C0 01 00 06 2F 11 01 00 00 D8 A9");

  // ICD §6.3: V-NEG-01 = V-TC-01 with last octet changed to 84 (CRC failure).
  private static final byte[] V_NEG_01 = HEX.parseHex("18 64 C0 00 00 06 20 11 01 00 00 FA 84");

  /**
   * SIM-TC-003: encoder output byte-identical to V-TC-01 and V-TC-02
   * (ICD §6.1).
   */
  @Test
  @TestCase("SIM-TC-003")
  @Requirement({"SIM-REQ-PUS-007", "SIM-REQ-PUS-003"})
  void encodeMatchesReferenceVectors() {
    TcPacket ping = TcPacket.of(100, 0, new TcSecondaryHeader(2, 0b0000, 17, 1, 0), new byte[0]);
    assertArrayEquals(V_TC_01, ping.encode());

    TcPacket pingWithAllAcks = TcPacket.of(100, 1, new TcSecondaryHeader(2, 0b1111, 17, 1, 0), new byte[0]);
    assertArrayEquals(V_TC_02, pingWithAllAcks.encode());
  }

  // Untraced unit tests (engineering hygiene, SDP §5).

  @Test
  void decodeRejectsCrcFailure() {
    PacketDecodeException ex = assertThrows(PacketDecodeException.class, () -> TcPacket.decode(V_NEG_01));
    assertEquals(PacketDecodeException.Reason.CRC_ERROR, ex.reason());
  }

  @Test
  void decodeRejectsTooShortPacket() {
    byte[] tooShort = new byte[PrimaryHeader.LENGTH + TcSecondaryHeader.LENGTH];
    PacketDecodeException ex = assertThrows(PacketDecodeException.class, () -> TcPacket.decode(tooShort));
    assertEquals(PacketDecodeException.Reason.TOO_SHORT, ex.reason());
  }

  @Test
  void decodeRejectsLengthMismatch() {
    // Non-empty app data so a 1-octet truncation still clears the minimum
    // structural length and exercises the length-consistency check instead.
    byte[] withAppData =
        TcPacket.of(100, 0, new TcSecondaryHeader(2, 0, 17, 1, 0), new byte[] {0x01, 0x02, 0x03}).encode();

    byte[] truncated = new byte[withAppData.length - 1];
    System.arraycopy(withAppData, 0, truncated, 0, truncated.length);
    PacketDecodeException ex = assertThrows(PacketDecodeException.class, () -> TcPacket.decode(truncated));
    assertEquals(PacketDecodeException.Reason.LENGTH_MISMATCH, ex.reason());

    byte[] oversized = new byte[withAppData.length + 1];
    System.arraycopy(withAppData, 0, oversized, 0, withAppData.length);
    ex = assertThrows(PacketDecodeException.class, () -> TcPacket.decode(oversized));
    assertEquals(PacketDecodeException.Reason.LENGTH_MISMATCH, ex.reason());
  }

  @Test
  void constructorRejectsInconsistentPrimaryHeader() {
    TcSecondaryHeader sec = new TcSecondaryHeader(2, 0, 17, 1, 0);

    // wrong packet type
    assertThrows(IllegalArgumentException.class, () -> new TcPacket(
        new PrimaryHeader(0, PacketType.TM, true, 100, SequenceFlags.UNSEGMENTED, 0, 6), sec, new byte[0]));

    // secondary header flag not set
    assertThrows(IllegalArgumentException.class, () -> new TcPacket(
        new PrimaryHeader(0, PacketType.TC, false, 100, SequenceFlags.UNSEGMENTED, 0, 6), sec, new byte[0]));

    // dataFieldOctets inconsistent with secondary header + app data + CRC
    assertThrows(IllegalArgumentException.class, () -> new TcPacket(
        new PrimaryHeader(0, PacketType.TC, true, 100, SequenceFlags.UNSEGMENTED, 0, 5), sec, new byte[0]));
  }

  @Test
  void applicationDataIsDefensivelyCopied() {
    byte[] appData = {0x01, 0x02};
    TcSecondaryHeader sec = new TcSecondaryHeader(2, 0, 17, 1, 0);
    TcPacket packet = TcPacket.of(100, 0, sec, appData);

    appData[0] = 0x7F; // mutate the caller's array after construction
    assertArrayEquals(new byte[] {0x01, 0x02}, packet.applicationData());

    packet.applicationData()[0] = 0x7F; // mutate a returned copy
    assertArrayEquals(new byte[] {0x01, 0x02}, packet.applicationData());
  }

  @Test
  void equalsAndHashCodeAreContentBased() {
    TcSecondaryHeader sec = new TcSecondaryHeader(2, 0, 17, 1, 0);
    TcPacket a = TcPacket.of(100, 0, sec, new byte[] {0x01});
    TcPacket b = TcPacket.of(100, 0, sec, new byte[] {0x01});
    TcPacket c = TcPacket.of(100, 0, sec, new byte[] {0x02});

    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
    assertFalse(a.equals(c));
  }
}
