package org.satsim.pus;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.satsim.pus.tc.TcPacket;
import org.satsim.pus.tc.TcSecondaryHeader;
import org.satsim.pus.time.CucTime;
import org.satsim.pus.tm.TmPacket;
import org.satsim.pus.tm.TmSecondaryHeader;
import org.satsim.testsupport.Requirement;
import org.satsim.testsupport.TestCase;

/**
 * ICD §6.6 ST[1] request verification reference vectors (added per SCR-002),
 * plus the clarified V-NEG-02 negative vector from ICD §6.3.
 */
class St1ReferenceVectorTest {

  private static final HexFormat HEX = HexFormat.of().withDelimiter(" ");

  // ICD §6.6: V-TC-06.
  private static final byte[] V_TC_06 = HEX.parseHex("18 64 C0 00 00 06 29 11 01 00 00 52 FF");

  // ICD §6.3: V-NEG-02 (clarified bytes: PUS version 1, CRC recomputed).
  private static final byte[] V_NEG_02 = HEX.parseHex("18 64 C0 00 00 06 10 11 01 00 00 F6 6D");

  // ICD §6.6: V-TM-05..08.
  private static final byte[] V_TM_05 =
      HEX.parseHex("08 64 C0 00 00 12 20 01 01 00 00 00 00 00 00 00 00 00 00 18 64 C0 00 1C BC");
  private static final byte[] V_TM_06 =
      HEX.parseHex("08 64 C0 01 00 0E 20 11 02 00 00 00 00 00 00 00 00 00 00 A4 62");
  private static final byte[] V_TM_07 =
      HEX.parseHex("08 64 C0 02 00 12 20 01 07 00 00 00 00 00 00 00 00 00 00 18 64 C0 00 A1 B1");
  private static final byte[] V_TM_08 = HEX.parseHex(
      "08 64 C0 00 00 14 20 01 02 00 00 00 00 00 00 00 00 00 00 18 64 C0 00 00 01 BC E4");

  private static final byte[] REQUEST_ID = HEX.parseHex("18 64 C0 00");

  /**
   * SIM-TC-022: V-TC-06, V-NEG-02 (clarified bytes) and V-TM-05/06/07/08
   * encode byte-identically and decode without error to the ICD-specified
   * field values. One test method for both directions: the gate requires
   * exactly one implementing test per SVS case (SIM-REQ-QA-001).
   */
  @Test
  @TestCase("SIM-TC-022")
  @Requirement({"SIM-REQ-PUS-007", "SIM-REQ-VER-001"})
  void encodesAndDecodesReferenceVectorsToIcdSpecifiedFields() throws PacketDecodeException {
    decodesToIcdSpecifiedFields();
    encodeReproducesReferenceVectors();
  }

  private void decodesToIcdSpecifiedFields() throws PacketDecodeException {
    TcPacket tc06 = TcPacket.decode(V_TC_06);
    assertEquals(0b1001, tc06.secondaryHeader().ackFlags());
    assertEquals(17, tc06.secondaryHeader().serviceType());
    assertEquals(1, tc06.secondaryHeader().messageSubtype());
    assertArrayEquals(new byte[0], tc06.applicationData());

    // Version policy is the simulator's, not the codec's: decode must succeed.
    TcPacket neg02 = TcPacket.decode(V_NEG_02);
    assertEquals(1, neg02.secondaryHeader().pusVersion());
    assertEquals(0, neg02.secondaryHeader().ackFlags());
    assertEquals(17, neg02.secondaryHeader().serviceType());
    assertEquals(1, neg02.secondaryHeader().messageSubtype());

    TmPacket tm05 = TmPacket.decode(V_TM_05);
    assertEquals(1, tm05.secondaryHeader().serviceType());
    assertEquals(1, tm05.secondaryHeader().messageSubtype());
    assertEquals(0, tm05.primaryHeader().sequenceCount());
    assertEquals(0, tm05.secondaryHeader().messageTypeCounter());
    assertEquals(new CucTime(0, 0), tm05.secondaryHeader().time());
    assertArrayEquals(REQUEST_ID, tm05.applicationData());

    TmPacket tm06 = TmPacket.decode(V_TM_06);
    assertEquals(17, tm06.secondaryHeader().serviceType());
    assertEquals(2, tm06.secondaryHeader().messageSubtype());
    assertEquals(1, tm06.primaryHeader().sequenceCount());
    assertEquals(0, tm06.secondaryHeader().messageTypeCounter());
    assertEquals(new CucTime(0, 0), tm06.secondaryHeader().time());
    assertArrayEquals(new byte[0], tm06.applicationData());

    TmPacket tm07 = TmPacket.decode(V_TM_07);
    assertEquals(1, tm07.secondaryHeader().serviceType());
    assertEquals(7, tm07.secondaryHeader().messageSubtype());
    assertEquals(2, tm07.primaryHeader().sequenceCount());
    assertEquals(0, tm07.secondaryHeader().messageTypeCounter());
    assertEquals(new CucTime(0, 0), tm07.secondaryHeader().time());
    assertArrayEquals(REQUEST_ID, tm07.applicationData());

    TmPacket tm08 = TmPacket.decode(V_TM_08);
    assertEquals(1, tm08.secondaryHeader().serviceType());
    assertEquals(2, tm08.secondaryHeader().messageSubtype());
    assertEquals(0, tm08.primaryHeader().sequenceCount());
    assertEquals(0, tm08.secondaryHeader().messageTypeCounter());
    assertEquals(new CucTime(0, 0), tm08.secondaryHeader().time());
    assertArrayEquals(HEX.parseHex("18 64 C0 00 00 01"), tm08.applicationData());
  }

  /**
   * Rebuilding each vector's packet from its ICD-specified field values and
   * re-encoding it reproduces the vector byte-for-byte.
   */
  private void encodeReproducesReferenceVectors() {
    TcPacket tc06 = TcPacket.of(100, 0, new TcSecondaryHeader(2, 0b1001, 17, 1, 0), new byte[0]);
    assertArrayEquals(V_TC_06, tc06.encode());

    TcPacket neg02 = TcPacket.of(100, 0, new TcSecondaryHeader(1, 0, 17, 1, 0), new byte[0]);
    assertArrayEquals(V_NEG_02, neg02.encode());

    TmPacket tm05 = TmPacket.of(
        100, 0, new TmSecondaryHeader(2, 0, 1, 1, 0, 0, CucTime.ofNanos(0)), REQUEST_ID);
    assertArrayEquals(V_TM_05, tm05.encode());

    TmPacket tm06 = TmPacket.of(
        100, 1, new TmSecondaryHeader(2, 0, 17, 2, 0, 0, CucTime.ofNanos(0)), new byte[0]);
    assertArrayEquals(V_TM_06, tm06.encode());

    TmPacket tm07 = TmPacket.of(
        100, 2, new TmSecondaryHeader(2, 0, 1, 7, 0, 0, CucTime.ofNanos(0)), REQUEST_ID);
    assertArrayEquals(V_TM_07, tm07.encode());

    TmPacket tm08 = TmPacket.of(
        100, 0, new TmSecondaryHeader(2, 0, 1, 2, 0, 0, CucTime.ofNanos(0)),
        HEX.parseHex("18 64 C0 00 00 01"));
    assertArrayEquals(V_TM_08, tm08.encode());
  }
}
