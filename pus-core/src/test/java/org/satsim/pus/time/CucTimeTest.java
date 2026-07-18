package org.satsim.pus.time;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HexFormat;
import org.junit.jupiter.api.Test;

// Untraced unit tests (engineering hygiene, SDP §5).
class CucTimeTest {

  @Test
  void ofNanosMatchesIcdExample() {
    // ICD §5 example: T = 1.5 s -> coarse 00 00 00 01, fine 80 00.
    CucTime time = CucTime.ofNanos(1_500_000_000L);
    assertEquals(1L, time.coarse());
    assertEquals(0x8000, time.fine());
    assertArrayEquals(
        HexFormat.of().parseHex("000000018000"), time.encode());
  }

  @Test
  void ofNanosZeroIsZero() {
    CucTime time = CucTime.ofNanos(0L);
    assertEquals(0L, time.coarse());
    assertEquals(0, time.fine());
  }

  @Test
  void ofNanosRejectsNegative() {
    assertThrows(IllegalArgumentException.class, () -> CucTime.ofNanos(-1L));
  }

  @Test
  void ofNanosRoundsHalfUp() {
    // fracNanos = 999_999_999 rounds up to fine = 65536, which carries into coarse.
    CucTime time = CucTime.ofNanos(1_999_999_999L);
    assertEquals(2L, time.coarse());
    assertEquals(0, time.fine());
  }

  @Test
  void ofNanosRejectsCoarseOverflowAfterCarry() {
    long nanosAtMaxCoarseWithCarry = CucTime.COARSE_MAX * 1_000_000_000L + 999_999_999L;
    assertThrows(IllegalArgumentException.class, () -> CucTime.ofNanos(nanosAtMaxCoarseWithCarry));
  }

  @Test
  void ofNanosAcceptsMaxCoarseWithoutCarry() {
    long nanosAtMaxCoarse = CucTime.COARSE_MAX * 1_000_000_000L;
    CucTime time = CucTime.ofNanos(nanosAtMaxCoarse);
    assertEquals(CucTime.COARSE_MAX, time.coarse());
    assertEquals(0, time.fine());
  }

  @Test
  void toNanosRoundTripsThroughOfNanos() {
    for (long nanos : new long[] {0L, 1L, 500_000_000L, 1_500_000_000L, 999_999_999L, 3_141_592_653L}) {
      CucTime time = CucTime.ofNanos(nanos);
      // round(fine * 1e9 / 65536) has resolution ~15.26 microseconds; allow that tolerance.
      long delta = Math.abs(nanos - time.toNanos());
      assertTrue(delta <= 15_260, "round-trip failed for " + nanos + ": delta=" + delta);
    }
  }

  @Test
  void toNanosIsExactForIcdExample() {
    assertEquals(1_500_000_000L, new CucTime(1, 0x8000).toNanos());
  }

  @Test
  void encodeDecodeRoundTrip() {
    CucTime time = new CucTime(CucTime.COARSE_MAX, CucTime.FINE_MAX);
    assertEquals(time, CucTime.decode(time.encode(), 0));

    CucTime zero = new CucTime(0, 0);
    assertEquals(zero, CucTime.decode(zero.encode(), 0));
  }

  @Test
  void decodeHonorsOffset() {
    CucTime time = new CucTime(1, 0x8000);
    byte[] buf = new byte[10];
    System.arraycopy(time.encode(), 0, buf, 3, CucTime.LENGTH);
    assertEquals(time, CucTime.decode(buf, 3));
  }

  @Test
  void decodeRejectsTruncatedInput() {
    assertThrows(IllegalArgumentException.class, () -> CucTime.decode(new byte[5], 0));
    assertThrows(IllegalArgumentException.class, () -> CucTime.decode(new byte[6], 1));
    assertThrows(IllegalArgumentException.class, () -> CucTime.decode(new byte[6], -1));
  }

  @Test
  void constructorRejectsOutOfRangeFields() {
    assertThrows(IllegalArgumentException.class, () -> new CucTime(-1, 0));
    assertThrows(IllegalArgumentException.class, () -> new CucTime(CucTime.COARSE_MAX + 1, 0));
    assertThrows(IllegalArgumentException.class, () -> new CucTime(0, -1));
    assertThrows(IllegalArgumentException.class, () -> new CucTime(0, CucTime.FINE_MAX + 1));
  }
}
