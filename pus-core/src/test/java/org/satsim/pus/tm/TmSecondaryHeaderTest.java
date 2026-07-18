package org.satsim.pus.tm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.satsim.pus.time.CucTime;

// Untraced unit tests (engineering hygiene, SDP §5).
class TmSecondaryHeaderTest {

  @Test
  void encodeDecodeRoundTripBoundaryValues() {
    TmSecondaryHeader[] representatives = {
        new TmSecondaryHeader(2, 0, 17, 2, 0, 0, CucTime.ofNanos(0)),
        new TmSecondaryHeader(0, 0, 0, 0, 0, 0, new CucTime(0, 0)),
        new TmSecondaryHeader(15, 15, 255, 255, 65535, 65535, new CucTime(CucTime.COARSE_MAX, CucTime.FINE_MAX)),
    };
    for (TmSecondaryHeader header : representatives) {
      byte[] encoded = header.encode();
      assertEquals(TmSecondaryHeader.LENGTH, encoded.length);
      assertEquals(header, TmSecondaryHeader.decode(encoded, 0));
    }
  }

  @Test
  void decodeHonorsOffset() {
    TmSecondaryHeader header = new TmSecondaryHeader(2, 0, 17, 2, 1, 0, CucTime.ofNanos(1_500_000_000L));
    byte[] buf = new byte[20];
    System.arraycopy(header.encode(), 0, buf, 5, TmSecondaryHeader.LENGTH);
    assertEquals(header, TmSecondaryHeader.decode(buf, 5));
  }

  @Test
  void decodeRejectsTruncatedInput() {
    assertThrows(IllegalArgumentException.class, () -> TmSecondaryHeader.decode(new byte[12], 0));
    assertThrows(IllegalArgumentException.class, () -> TmSecondaryHeader.decode(new byte[13], 1));
    assertThrows(IllegalArgumentException.class, () -> TmSecondaryHeader.decode(new byte[13], -1));
  }

  @Test
  void constructorRejectsOutOfRangeFieldsAndNullTime() {
    CucTime time = CucTime.ofNanos(0);
    assertThrows(IllegalArgumentException.class, () -> new TmSecondaryHeader(16, 0, 17, 2, 0, 0, time));
    assertThrows(IllegalArgumentException.class, () -> new TmSecondaryHeader(2, 16, 17, 2, 0, 0, time));
    assertThrows(IllegalArgumentException.class, () -> new TmSecondaryHeader(2, 0, 256, 2, 0, 0, time));
    assertThrows(IllegalArgumentException.class, () -> new TmSecondaryHeader(2, 0, 17, 256, 0, 0, time));
    assertThrows(IllegalArgumentException.class, () -> new TmSecondaryHeader(2, 0, 17, 2, 65536, 0, time));
    assertThrows(IllegalArgumentException.class, () -> new TmSecondaryHeader(2, 0, 17, 2, 0, 65536, time));
    assertThrows(NullPointerException.class, () -> new TmSecondaryHeader(2, 0, 17, 2, 0, 0, null));
  }
}
