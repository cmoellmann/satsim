package org.satsim.pus.ccsds;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.satsim.testsupport.Requirement;
import org.satsim.testsupport.TestCase;

class PrimaryHeaderTest {

  /**
   * SIM-TC-002: encode→decode is identity for representative field
   * combinations including boundary values (APID 0/2047, sequence count
   * 0/16383, min/max packet data length).
   */
  @Test
  @TestCase("SIM-TC-002")
  @Requirement("SIM-REQ-PUS-001")
  void encodeDecodeRoundTripIsIdentity() {
    List<PrimaryHeader> representatives = List.of(
        // ICD system convention: TC, APID 100, unsegmented, secondary header
        new PrimaryHeader(0, PacketType.TC, true, 100, SequenceFlags.UNSEGMENTED, 1, 11),
        // ICD system convention: TM counterpart
        new PrimaryHeader(0, PacketType.TM, true, 100, SequenceFlags.UNSEGMENTED, 42, 21),
        // boundary: all minimum values
        new PrimaryHeader(0, PacketType.TM, false, 0, SequenceFlags.CONTINUATION, 0, 0),
        // boundary: all maximum values
        new PrimaryHeader(7, PacketType.TC, true, PrimaryHeader.APID_MAX,
            SequenceFlags.UNSEGMENTED, PrimaryHeader.SEQUENCE_COUNT_MAX,
            PrimaryHeader.PACKET_DATA_LENGTH_MAX),
        // boundary mixes: max APID with min count and vice versa
        new PrimaryHeader(0, PacketType.TC, true, PrimaryHeader.APID_MAX,
            SequenceFlags.FIRST, 0, 0),
        new PrimaryHeader(0, PacketType.TM, false, 0, SequenceFlags.LAST,
            PrimaryHeader.SEQUENCE_COUNT_MAX, PrimaryHeader.PACKET_DATA_LENGTH_MAX));

    for (PrimaryHeader header : representatives) {
      byte[] encoded = header.encode();
      assertEquals(PrimaryHeader.LENGTH, encoded.length);
      assertEquals(header, PrimaryHeader.decode(encoded, 0), "round-trip failed for " + header);
    }
  }

  // Untraced unit tests (engineering hygiene, SDP §5).

  @Test
  void encodesKnownExampleBitExact() {
    // version 0, TC, sec-hdr present, APID 100 → 0x18 0x64;
    // unsegmented, count 1 → 0xC0 0x01; data length field 11 → 0x00 0x0B.
    PrimaryHeader header =
        new PrimaryHeader(0, PacketType.TC, true, 100, SequenceFlags.UNSEGMENTED, 1, 11);
    assertArrayEquals(
        new byte[] {0x18, 0x64, (byte) 0xC0, 0x01, 0x00, 0x0B}, header.encode());
  }

  @Test
  void decodeHonorsOffset() {
    PrimaryHeader header =
        new PrimaryHeader(0, PacketType.TM, true, 100, SequenceFlags.UNSEGMENTED, 7, 3);
    byte[] buf = new byte[10];
    System.arraycopy(header.encode(), 0, buf, 4, PrimaryHeader.LENGTH);
    assertEquals(header, PrimaryHeader.decode(buf, 4));
  }

  @Test
  void dataFieldOctetsIsLengthFieldPlusOne() {
    PrimaryHeader header =
        new PrimaryHeader(0, PacketType.TC, true, 100, SequenceFlags.UNSEGMENTED, 0, 0);
    assertEquals(1, header.dataFieldOctets());
  }

  @Test
  void constructorRejectsOutOfRangeFields() {
    assertThrows(IllegalArgumentException.class, () ->
        new PrimaryHeader(8, PacketType.TC, true, 100, SequenceFlags.UNSEGMENTED, 0, 0));
    assertThrows(IllegalArgumentException.class, () ->
        new PrimaryHeader(0, PacketType.TC, true, 2048, SequenceFlags.UNSEGMENTED, 0, 0));
    assertThrows(IllegalArgumentException.class, () ->
        new PrimaryHeader(0, PacketType.TC, true, -1, SequenceFlags.UNSEGMENTED, 0, 0));
    assertThrows(IllegalArgumentException.class, () ->
        new PrimaryHeader(0, PacketType.TC, true, 100, SequenceFlags.UNSEGMENTED, 16384, 0));
    assertThrows(IllegalArgumentException.class, () ->
        new PrimaryHeader(0, PacketType.TC, true, 100, SequenceFlags.UNSEGMENTED, 0, 65536));
  }

  @Test
  void decodeRejectsTruncatedInput() {
    assertThrows(IllegalArgumentException.class, () -> PrimaryHeader.decode(new byte[5], 0));
    assertThrows(IllegalArgumentException.class, () -> PrimaryHeader.decode(new byte[6], 1));
    assertThrows(IllegalArgumentException.class, () -> PrimaryHeader.decode(new byte[6], -1));
  }
}
