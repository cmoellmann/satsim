package org.satsim.pus;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.satsim.pus.ccsds.PacketType;
import org.satsim.pus.ccsds.SequenceFlags;
import org.satsim.pus.tc.TcPacket;
import org.satsim.pus.time.CucTime;
import org.satsim.pus.tm.TmPacket;
import org.satsim.testsupport.Requirement;
import org.satsim.testsupport.TestCase;

class ReferenceVectorDecodeTest {

  private static final HexFormat HEX = HexFormat.of().withDelimiter(" ");

  // ICD §6.1: V-TC-01, V-TC-02.
  private static final byte[] V_TC_01 = HEX.parseHex("18 64 C0 00 00 06 20 11 01 00 00 FA 83");
  private static final byte[] V_TC_02 = HEX.parseHex("18 64 C0 01 00 06 2F 11 01 00 00 D8 A9");

  // ICD §6.2: V-TM-01, V-TM-02.
  private static final byte[] V_TM_01 =
      HEX.parseHex("08 64 C0 00 00 0E 20 11 02 00 00 00 00 00 00 00 00 00 00 0C 46");
  private static final byte[] V_TM_02 =
      HEX.parseHex("08 64 C0 01 00 0E 20 11 02 00 01 00 00 00 00 00 01 80 00 63 E9");

  /**
   * SIM-TC-005: V-TC-01/02 and V-TM-01/02 decode without error; every decoded
   * field equals the ICD-specified value.
   */
  @Test
  @TestCase("SIM-TC-005")
  @Requirement("SIM-REQ-PUS-007")
  void decodesReferenceVectorsToIcdSpecifiedFields() throws PacketDecodeException {
    TcPacket tc01 = TcPacket.decode(V_TC_01);
    assertEquals(0, tc01.primaryHeader().versionNumber());
    assertEquals(PacketType.TC, tc01.primaryHeader().type());
    assertTrue(tc01.primaryHeader().secondaryHeaderFlag());
    assertEquals(100, tc01.primaryHeader().apid());
    assertEquals(SequenceFlags.UNSEGMENTED, tc01.primaryHeader().sequenceFlags());
    assertEquals(0, tc01.primaryHeader().sequenceCount());
    assertEquals(6, tc01.primaryHeader().packetDataLength());
    assertEquals(2, tc01.secondaryHeader().pusVersion());
    assertEquals(0, tc01.secondaryHeader().ackFlags());
    assertEquals(17, tc01.secondaryHeader().serviceType());
    assertEquals(1, tc01.secondaryHeader().messageSubtype());
    assertEquals(0, tc01.secondaryHeader().sourceId());
    assertArrayEquals(new byte[0], tc01.applicationData());

    TcPacket tc02 = TcPacket.decode(V_TC_02);
    assertEquals(1, tc02.primaryHeader().sequenceCount());
    assertEquals(0b1111, tc02.secondaryHeader().ackFlags());
    assertEquals(17, tc02.secondaryHeader().serviceType());
    assertEquals(1, tc02.secondaryHeader().messageSubtype());
    assertEquals(0, tc02.secondaryHeader().sourceId());
    assertArrayEquals(new byte[0], tc02.applicationData());

    TmPacket tm01 = TmPacket.decode(V_TM_01);
    assertEquals(0, tm01.primaryHeader().versionNumber());
    assertEquals(PacketType.TM, tm01.primaryHeader().type());
    assertTrue(tm01.primaryHeader().secondaryHeaderFlag());
    assertEquals(100, tm01.primaryHeader().apid());
    assertEquals(SequenceFlags.UNSEGMENTED, tm01.primaryHeader().sequenceFlags());
    assertEquals(0, tm01.primaryHeader().sequenceCount());
    assertEquals(14, tm01.primaryHeader().packetDataLength());
    assertEquals(2, tm01.secondaryHeader().pusVersion());
    assertEquals(0, tm01.secondaryHeader().timeReferenceStatus());
    assertEquals(17, tm01.secondaryHeader().serviceType());
    assertEquals(2, tm01.secondaryHeader().messageSubtype());
    assertEquals(0, tm01.secondaryHeader().messageTypeCounter());
    assertEquals(0, tm01.secondaryHeader().destinationId());
    assertEquals(new CucTime(0, 0), tm01.secondaryHeader().time());
    assertArrayEquals(new byte[0], tm01.applicationData());

    TmPacket tm02 = TmPacket.decode(V_TM_02);
    assertEquals(1, tm02.primaryHeader().sequenceCount());
    assertEquals(1, tm02.secondaryHeader().messageTypeCounter());
    assertEquals(0, tm02.secondaryHeader().destinationId());
    assertEquals(new CucTime(1, 0x8000), tm02.secondaryHeader().time());
    assertArrayEquals(new byte[0], tm02.applicationData());
  }
}
