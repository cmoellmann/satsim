package org.satsim.pus.crc;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.satsim.testsupport.Requirement;
import org.satsim.testsupport.TestCase;

class Crc16CcittTest {

  /** SIM-TC-001: all three ICD §7 sanity anchors reproduced exactly. */
  @Test
  @TestCase("SIM-TC-001")
  @Requirement("SIM-REQ-PUS-002")
  void reproducesAllIcdSanityAnchors() {
    assertEquals(0x29B1, Crc16Ccitt.compute("123456789".getBytes(US_ASCII)));
    assertEquals(0x1D0F, Crc16Ccitt.compute(new byte[] {0x00, 0x00}));
    assertEquals(0x04A2,
        Crc16Ccitt.compute(new byte[] {(byte) 0xAB, (byte) 0xCD, (byte) 0xEF, 0x01}));
  }

  // Untraced unit tests (engineering hygiene, SDP §5).

  @Test
  void emptyInputYieldsInitialValue() {
    assertEquals(0xFFFF, Crc16Ccitt.compute(new byte[0]));
  }

  @Test
  void computeHonorsOffsetAndLength() {
    byte[] padded = {0x55, 0x00, 0x00, 0x55};
    assertEquals(0x1D0F, Crc16Ccitt.compute(padded, 1, 2));
  }

  @Test
  void computeRejectsOutOfBoundsRange() {
    assertThrows(IndexOutOfBoundsException.class, () -> Crc16Ccitt.compute(new byte[2], 1, 2));
    assertThrows(IndexOutOfBoundsException.class, () -> Crc16Ccitt.compute(new byte[2], -1, 1));
    assertThrows(IndexOutOfBoundsException.class, () -> Crc16Ccitt.compute(new byte[2], 0, -1));
  }

  @Test
  void verifyAcceptsPacketWithCorrectBigEndianTrailer() {
    // "123456789" + CRC 0x29B1 appended big-endian per ICD §7.
    byte[] packet = new byte[11];
    System.arraycopy("123456789".getBytes(US_ASCII), 0, packet, 0, 9);
    packet[9] = 0x29;
    packet[10] = (byte) 0xB1;
    assertTrue(Crc16Ccitt.verify(packet));
  }

  @Test
  void verifyRejectsCorruptedPacket() {
    byte[] packet = new byte[11];
    System.arraycopy("123456789".getBytes(US_ASCII), 0, packet, 0, 9);
    packet[9] = 0x29;
    packet[10] = (byte) 0xB1;
    packet[3] ^= 0x01; // single bit flip in the protected region
    assertFalse(Crc16Ccitt.verify(packet));
  }

  @Test
  void verifyRejectsByteSwappedTrailer() {
    // Little-endian trailer must fail: appending is big-endian per ICD §7.
    byte[] packet = new byte[11];
    System.arraycopy("123456789".getBytes(US_ASCII), 0, packet, 0, 9);
    packet[9] = (byte) 0xB1;
    packet[10] = 0x29;
    assertFalse(Crc16Ccitt.verify(packet));
  }

  @Test
  void verifyRejectsTooShortPacket() {
    assertThrows(IllegalArgumentException.class, () -> Crc16Ccitt.verify(new byte[2]));
  }
}
