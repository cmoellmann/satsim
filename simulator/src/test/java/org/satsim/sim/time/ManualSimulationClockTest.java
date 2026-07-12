package org.satsim.sim.time;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Untraced unit tests (engineering hygiene, SDP §5); CUC encoding is
 * validated against ICD vectors in M1 (SIM-TC-004). */
class ManualSimulationClockTest {

  @Test
  void startsAtEpochAndAdvances() {
    ManualSimulationClock clock = new ManualSimulationClock();
    assertEquals(0, clock.nanos());
    clock.advance(1_500_000_000L);
    assertEquals(1_500_000_000L, clock.nanos());
    clock.advance(0);
    assertEquals(1_500_000_000L, clock.nanos());
  }

  @Test
  void rejectsNegativeAdvance() {
    ManualSimulationClock clock = new ManualSimulationClock();
    assertThrows(IllegalArgumentException.class, () -> clock.advance(-1));
  }

  @Test
  void cucOfEpochIsAllZero() {
    assertArrayEquals(new byte[6], new ManualSimulationClock().toCuc());
  }

  @Test
  void cucCoarseFieldCountsWholeSeconds() {
    ManualSimulationClock clock = new ManualSimulationClock();
    clock.advance(2_000_000_000L);
    assertArrayEquals(new byte[] {0, 0, 0, 2, 0, 0}, clock.toCuc());
  }
}
