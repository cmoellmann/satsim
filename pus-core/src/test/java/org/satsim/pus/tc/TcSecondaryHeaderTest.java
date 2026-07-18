package org.satsim.pus.tc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

// Untraced unit tests (engineering hygiene, SDP §5).
class TcSecondaryHeaderTest {

  @Test
  void encodeDecodeRoundTripBoundaryValues() {
    TcSecondaryHeader[] representatives = {
        new TcSecondaryHeader(TcSecondaryHeader.PUS_C_VERSION, 0b0000, 17, 1, 0),
        new TcSecondaryHeader(0, 0, 0, 0, 0),
        new TcSecondaryHeader(15, 15, 255, 255, 65535),
    };
    for (TcSecondaryHeader header : representatives) {
      byte[] encoded = header.encode();
      assertEquals(TcSecondaryHeader.LENGTH, encoded.length);
      assertEquals(header, TcSecondaryHeader.decode(encoded, 0));
    }
  }

  @Test
  void ackFlagBitsAreDecodedIndividually() {
    TcSecondaryHeader header = new TcSecondaryHeader(2, 0b1010, 17, 1, 0);
    assertTrue(header.ackAcceptance());
    assertFalse(header.ackStart());
    assertTrue(header.ackProgress());
    assertFalse(header.ackCompletion());
  }

  @Test
  void ackFlagAllBitsSet() {
    TcSecondaryHeader header = new TcSecondaryHeader(2, 0b1111, 17, 1, 0);
    assertTrue(header.ackAcceptance());
    assertTrue(header.ackStart());
    assertTrue(header.ackProgress());
    assertTrue(header.ackCompletion());
  }

  @Test
  void decodeDoesNotEnforcePusVersion() {
    // ICD §10.2: version acceptance is simulator policy, not a codec concern.
    TcSecondaryHeader header = new TcSecondaryHeader(1, 0, 17, 1, 0);
    byte[] encoded = header.encode();
    assertEquals(header, TcSecondaryHeader.decode(encoded, 0));
  }

  @Test
  void decodeHonorsOffset() {
    TcSecondaryHeader header = new TcSecondaryHeader(2, 0b1001, 17, 1, 0);
    byte[] buf = new byte[8];
    System.arraycopy(header.encode(), 0, buf, 2, TcSecondaryHeader.LENGTH);
    assertEquals(header, TcSecondaryHeader.decode(buf, 2));
  }

  @Test
  void decodeRejectsTruncatedInput() {
    assertThrows(IllegalArgumentException.class, () -> TcSecondaryHeader.decode(new byte[4], 0));
    assertThrows(IllegalArgumentException.class, () -> TcSecondaryHeader.decode(new byte[5], 1));
    assertThrows(IllegalArgumentException.class, () -> TcSecondaryHeader.decode(new byte[5], -1));
  }

  @Test
  void constructorRejectsOutOfRangeFields() {
    assertThrows(IllegalArgumentException.class, () -> new TcSecondaryHeader(16, 0, 17, 1, 0));
    assertThrows(IllegalArgumentException.class, () -> new TcSecondaryHeader(2, 16, 17, 1, 0));
    assertThrows(IllegalArgumentException.class, () -> new TcSecondaryHeader(2, 0, 256, 1, 0));
    assertThrows(IllegalArgumentException.class, () -> new TcSecondaryHeader(2, 0, 17, 256, 0));
    assertThrows(IllegalArgumentException.class, () -> new TcSecondaryHeader(2, 0, 17, 1, 65536));
    assertThrows(IllegalArgumentException.class, () -> new TcSecondaryHeader(-1, 0, 17, 1, 0));
  }
}
