package org.satsim.pus.tm;

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
import org.satsim.pus.time.CucTime;
import org.satsim.testsupport.Requirement;
import org.satsim.testsupport.TestCase;

class TmPacketTest {

  private static final HexFormat HEX = HexFormat.of().withDelimiter(" ");

  // ICD §6.2: V-TM-01, V-TM-02.
  private static final byte[] V_TM_01 =
      HEX.parseHex("08 64 C0 00 00 0E 20 11 02 00 00 00 00 00 00 00 00 00 00 0C 46");
  private static final byte[] V_TM_02 =
      HEX.parseHex("08 64 C0 01 00 0E 20 11 02 00 01 00 00 00 00 00 01 80 00 63 E9");

  /**
   * SIM-TC-004: encoder output byte-identical to V-TM-01 and V-TM-02
   * (ICD §6.2), including the CUC time field.
   */
  @Test
  @TestCase("SIM-TC-004")
  @Requirement({"SIM-REQ-PUS-007", "SIM-REQ-PUS-004", "SIM-REQ-TIME-002"})
  void encodeMatchesReferenceVectors() {
    TmPacket report = TmPacket.of(
        100, 0, new TmSecondaryHeader(2, 0, 17, 2, 0, 0, CucTime.ofNanos(0)), new byte[0]);
    assertArrayEquals(V_TM_01, report.encode());

    TmPacket secondReport = TmPacket.of(
        100, 1, new TmSecondaryHeader(2, 0, 17, 2, 1, 0, CucTime.ofNanos(1_500_000_000L)), new byte[0]);
    assertArrayEquals(V_TM_02, secondReport.encode());
  }

  // Untraced unit tests (engineering hygiene, SDP §5).

  @Test
  void decodeRejectsCrcFailure() {
    byte[] corrupted = V_TM_01.clone();
    corrupted[corrupted.length - 1] ^= 0x01;
    PacketDecodeException ex = assertThrows(PacketDecodeException.class, () -> TmPacket.decode(corrupted));
    assertEquals(PacketDecodeException.Reason.CRC_ERROR, ex.reason());
  }

  @Test
  void decodeRejectsTooShortPacket() {
    byte[] tooShort = new byte[PrimaryHeader.LENGTH + TmSecondaryHeader.LENGTH];
    PacketDecodeException ex = assertThrows(PacketDecodeException.class, () -> TmPacket.decode(tooShort));
    assertEquals(PacketDecodeException.Reason.TOO_SHORT, ex.reason());
  }

  @Test
  void decodeRejectsLengthMismatch() {
    // Non-empty app data so a 1-octet truncation still clears the minimum
    // structural length and exercises the length-consistency check instead.
    byte[] withAppData = TmPacket.of(
        100, 0, new TmSecondaryHeader(2, 0, 17, 2, 0, 0, CucTime.ofNanos(0)), new byte[] {0x01, 0x02, 0x03})
        .encode();

    byte[] truncated = new byte[withAppData.length - 1];
    System.arraycopy(withAppData, 0, truncated, 0, truncated.length);
    PacketDecodeException ex = assertThrows(PacketDecodeException.class, () -> TmPacket.decode(truncated));
    assertEquals(PacketDecodeException.Reason.LENGTH_MISMATCH, ex.reason());

    byte[] oversized = new byte[withAppData.length + 1];
    System.arraycopy(withAppData, 0, oversized, 0, withAppData.length);
    ex = assertThrows(PacketDecodeException.class, () -> TmPacket.decode(oversized));
    assertEquals(PacketDecodeException.Reason.LENGTH_MISMATCH, ex.reason());
  }

  @Test
  void constructorRejectsInconsistentPrimaryHeader() {
    TmSecondaryHeader sec = new TmSecondaryHeader(2, 0, 17, 2, 0, 0, CucTime.ofNanos(0));

    // wrong packet type
    assertThrows(IllegalArgumentException.class, () -> new TmPacket(
        new PrimaryHeader(0, PacketType.TC, true, 100, SequenceFlags.UNSEGMENTED, 0, 14), sec, new byte[0]));

    // secondary header flag not set
    assertThrows(IllegalArgumentException.class, () -> new TmPacket(
        new PrimaryHeader(0, PacketType.TM, false, 100, SequenceFlags.UNSEGMENTED, 0, 14), sec, new byte[0]));

    // dataFieldOctets inconsistent with secondary header + app data + CRC
    assertThrows(IllegalArgumentException.class, () -> new TmPacket(
        new PrimaryHeader(0, PacketType.TM, true, 100, SequenceFlags.UNSEGMENTED, 0, 13), sec, new byte[0]));
  }

  @Test
  void applicationDataIsDefensivelyCopied() {
    byte[] appData = {0x01, 0x02};
    TmSecondaryHeader sec = new TmSecondaryHeader(2, 0, 17, 2, 0, 0, CucTime.ofNanos(0));
    TmPacket packet = TmPacket.of(100, 0, sec, appData);

    appData[0] = 0x7F; // mutate the caller's array after construction
    assertArrayEquals(new byte[] {0x01, 0x02}, packet.applicationData());

    packet.applicationData()[0] = 0x7F; // mutate a returned copy
    assertArrayEquals(new byte[] {0x01, 0x02}, packet.applicationData());
  }

  @Test
  void equalsAndHashCodeAreContentBased() {
    TmSecondaryHeader sec = new TmSecondaryHeader(2, 0, 17, 2, 0, 0, CucTime.ofNanos(0));
    TmPacket a = TmPacket.of(100, 0, sec, new byte[] {0x01});
    TmPacket b = TmPacket.of(100, 0, sec, new byte[] {0x01});
    TmPacket c = TmPacket.of(100, 0, sec, new byte[] {0x02});

    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
    assertFalse(a.equals(c));
  }
}
