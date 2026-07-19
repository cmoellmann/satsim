package org.satsim.pus;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.satsim.pus.st3.HkCreateRequest;
import org.satsim.pus.st3.HkParameter;
import org.satsim.pus.st3.HkReport;
import org.satsim.pus.st3.HkSidList;
import org.satsim.pus.tc.TcPacket;
import org.satsim.pus.tc.TcSecondaryHeader;
import org.satsim.pus.time.CucTime;
import org.satsim.pus.tm.TmPacket;
import org.satsim.pus.tm.TmSecondaryHeader;
import org.satsim.testsupport.Requirement;
import org.satsim.testsupport.TestCase;

/**
 * ICD §6.4/§6.5 ST[3] housekeeping reference vectors (added per SCR-001).
 */
class St3ReferenceVectorTest {

  private static final HexFormat HEX = HexFormat.of().withDelimiter(" ");

  // ICD §6.4: V-TC-03/04/05.
  private static final byte[] V_TC_03 = HEX.parseHex(
      "18 64 C0 00 00 12 20 03 01 00 00 00 02 00 00 13 88 00 02 00 01 00 03 8D CE");
  private static final byte[] V_TC_04 =
      HEX.parseHex("18 64 C0 01 00 0A 20 03 05 00 00 00 01 00 02 15 41");
  private static final byte[] V_TC_05 =
      HEX.parseHex("18 64 C0 02 00 0A 20 03 07 00 00 00 01 00 02 70 3D");

  // ICD §6.5: V-TM-03/04.
  private static final byte[] V_TM_03 = HEX.parseHex(
      "08 64 C0 00 00 1A 20 03 19 00 00 00 00 00 00 00 01 00 00 00 01 00 00 00 00 00 00 00 00 0D C0 25 61");
  private static final byte[] V_TM_04 = HEX.parseHex(
      "08 64 C0 01 00 1A 20 03 19 00 01 00 00 00 00 00 02 00 00 00 01 00 00 00 00 00 00 00 01 0D D4 52 A3");

  /** Default structure SID 1 parameter order per ICD §9.6. */
  private static final List<HkParameter> SID1_ORDER = List.of(HkParameter.P001, HkParameter.P002, HkParameter.P003);

  /**
   * SIM-TC-018: V-TC-03/04/05 and V-TM-03/04 encode byte-identically and
   * decode without error to the ICD-specified field values. One test method
   * for both directions: the gate requires exactly one implementing test per
   * SVS case (SIM-REQ-QA-001).
   */
  @Test
  @TestCase("SIM-TC-018")
  @Requirement({"SIM-REQ-PUS-007", "SIM-REQ-HK-002"})
  void encodesAndDecodesReferenceVectorsToIcdSpecifiedFields() throws PacketDecodeException {
    decodesToIcdSpecifiedFields();
    encodeReproducesReferenceVectors();
  }

  private void decodesToIcdSpecifiedFields() throws PacketDecodeException {
    TcPacket tc03 = TcPacket.decode(V_TC_03);
    assertEquals(0, tc03.primaryHeader().sequenceCount());
    assertEquals(0b0000, tc03.secondaryHeader().ackFlags());
    assertEquals(3, tc03.secondaryHeader().serviceType());
    assertEquals(1, tc03.secondaryHeader().messageSubtype());
    HkCreateRequest create = HkCreateRequest.decode(tc03.applicationData());
    assertEquals(2, create.sid());
    assertEquals(5000L, create.collectionIntervalMs());
    assertEquals(List.of(1, 3), create.parameterIds());

    TcPacket tc04 = TcPacket.decode(V_TC_04);
    assertEquals(1, tc04.primaryHeader().sequenceCount());
    assertEquals(3, tc04.secondaryHeader().serviceType());
    assertEquals(5, tc04.secondaryHeader().messageSubtype());
    HkSidList enable = HkSidList.decode(tc04.applicationData());
    assertEquals(List.of(2), enable.sids());

    TcPacket tc05 = TcPacket.decode(V_TC_05);
    assertEquals(2, tc05.primaryHeader().sequenceCount());
    assertEquals(3, tc05.secondaryHeader().serviceType());
    assertEquals(7, tc05.secondaryHeader().messageSubtype());
    HkSidList disable = HkSidList.decode(tc05.applicationData());
    assertEquals(List.of(2), disable.sids());

    TmPacket tm03 = TmPacket.decode(V_TM_03);
    assertEquals(0, tm03.primaryHeader().sequenceCount());
    assertEquals(3, tm03.secondaryHeader().serviceType());
    assertEquals(25, tm03.secondaryHeader().messageSubtype());
    assertEquals(0, tm03.secondaryHeader().messageTypeCounter());
    assertEquals(new CucTime(1, 0), tm03.secondaryHeader().time());
    HkReport report03 = HkReport.decode(tm03.applicationData(), SID1_ORDER);
    assertEquals(1, report03.sid());
    assertEquals(List.of(0L, 0L, 3520L), report03.values());

    TmPacket tm04 = TmPacket.decode(V_TM_04);
    assertEquals(1, tm04.primaryHeader().sequenceCount());
    assertEquals(3, tm04.secondaryHeader().serviceType());
    assertEquals(25, tm04.secondaryHeader().messageSubtype());
    assertEquals(1, tm04.secondaryHeader().messageTypeCounter());
    assertEquals(new CucTime(2, 0), tm04.secondaryHeader().time());
    HkReport report04 = HkReport.decode(tm04.applicationData(), SID1_ORDER);
    assertEquals(1, report04.sid());
    assertEquals(List.of(0L, 1L, 3540L), report04.values());
  }

  /**
   * Rebuilding each vector's packet from its ICD-specified field values and
   * re-encoding it reproduces the vector byte-for-byte.
   */
  private void encodeReproducesReferenceVectors() {
    HkCreateRequest create = new HkCreateRequest(2, 5000L, List.of(1, 3));
    TcPacket tc03 = TcPacket.of(100, 0, new TcSecondaryHeader(2, 0b0000, 3, 1, 0), create.encode());
    assertArrayEquals(V_TC_03, tc03.encode());

    HkSidList enable = new HkSidList(List.of(2));
    TcPacket tc04 = TcPacket.of(100, 1, new TcSecondaryHeader(2, 0b0000, 3, 5, 0), enable.encode());
    assertArrayEquals(V_TC_04, tc04.encode());

    HkSidList disable = new HkSidList(List.of(2));
    TcPacket tc05 = TcPacket.of(100, 2, new TcSecondaryHeader(2, 0b0000, 3, 7, 0), disable.encode());
    assertArrayEquals(V_TC_05, tc05.encode());

    HkReport report03 = new HkReport(1, List.of(0L, 0L, 3520L));
    TmPacket tm03 = TmPacket.of(
        100, 0, new TmSecondaryHeader(2, 0, 3, 25, 0, 0, CucTime.ofNanos(1_000_000_000L)),
        report03.encode(SID1_ORDER));
    assertArrayEquals(V_TM_03, tm03.encode());

    HkReport report04 = new HkReport(1, List.of(0L, 1L, 3540L));
    TmPacket tm04 = TmPacket.of(
        100, 1, new TmSecondaryHeader(2, 0, 3, 25, 1, 0, CucTime.ofNanos(2_000_000_000L)),
        report04.encode(SID1_ORDER));
    assertArrayEquals(V_TM_04, tm04.encode());
  }
}
