package org.satsim.sim.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.satsim.sim.obsw.LoopbackTarget;

/** Untraced unit tests for the time master (engineering hygiene, SDP §5). */
class SimulationSchedulerTest {

  private static SimulationScheduler started() {
    SimulationScheduler scheduler =
        new SimulationScheduler(new LoopbackTarget(1_000L, (tc, now) -> List.of(tc)));
    scheduler.start();
    return scheduler;
  }

  @Test
  void advanceRequiresStart() {
    SimulationScheduler scheduler =
        new SimulationScheduler(new LoopbackTarget(1_000L, (tc, now) -> List.of(tc)));
    assertThrows(IllegalStateException.class, () -> scheduler.advanceBy(1L));
    assertThrows(IllegalStateException.class, () -> scheduler.injectTc(new byte[] {1}));
  }

  @Test
  void invalidArgumentsAreRejected() {
    assertThrows(NullPointerException.class, () -> new SimulationScheduler(null));
    SimulationScheduler scheduler = started();
    assertThrows(IllegalArgumentException.class, () -> scheduler.advanceBy(-1L));
    assertThrows(IllegalArgumentException.class, () -> scheduler.advanceTo(-1L));
  }

  @Test
  void zeroAdvanceIsANoOp() {
    SimulationScheduler scheduler = started();
    scheduler.advanceBy(0L);
    scheduler.advanceTo(0L);
    assertEquals(0L, scheduler.clock().nanos());
  }

  @Test
  void advanceAfterStopFails() {
    SimulationScheduler scheduler = started();
    scheduler.stop();
    assertThrows(IllegalStateException.class, () -> scheduler.advanceBy(1L));
  }
}
