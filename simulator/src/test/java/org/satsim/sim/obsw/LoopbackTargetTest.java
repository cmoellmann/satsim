package org.satsim.sim.obsw;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.satsim.sim.time.Consumed;
import org.satsim.sim.time.StopReason;
import org.satsim.testsupport.Requirement;
import org.satsim.testsupport.TestCase;

class LoopbackTargetTest {

  private static final long PROCESSING = 1_000_000L; // 1 ms simulated

  /** M0-style echo obsw: keeps these transport-contract tests packet-agnostic. */
  private static final SimulatedObsw ECHO = (tc, now) -> List.of(tc);

  private static LoopbackTarget runningTarget() {
    LoopbackTarget target = new LoopbackTarget(PROCESSING, ECHO);
    target.initialize();
    return target;
  }

  /**
   * SIM-TC-010: grant(b) returns consumed &le; b with a stop reason; early
   * return on a pending TM event reports the exact consumed time.
   */
  @Test
  @TestCase("SIM-TC-010")
  @Requirement({"SIM-REQ-TIME-004", "SIM-REQ-LINK-001"})
  void grantConsumeContractOnLoopback() {
    LoopbackTarget target = runningTarget();
    List<byte[]> tms = new ArrayList<>();
    target.onTm(tms::add);

    // Idle: full budget consumed, no early return.
    Consumed idle = target.grant(500_000L);
    assertTrue(idle.nanosConsumed() <= 500_000L);
    assertEquals(500_000L, idle.nanosConsumed());
    assertEquals(StopReason.BUDGET_EXHAUSTED, idle.reason());

    // TC pending, budget smaller than remaining processing time: no event yet.
    byte[] tc = {0x18, 0x64, (byte) 0xC0, 0x01, 0x00, 0x0B};
    target.sendTc(tc);
    Consumed partial = target.grant(400_000L);
    assertEquals(400_000L, partial.nanosConsumed());
    assertEquals(StopReason.BUDGET_EXHAUSTED, partial.reason());
    assertEquals(0, tms.size());

    // Budget beyond the event: early return exactly at the TM emission point.
    // 600_000 ns of the 1 ms processing remain; grant much more than that.
    Consumed event = target.grant(10_000_000L);
    assertTrue(event.nanosConsumed() <= 10_000_000L);
    assertEquals(600_000L, event.nanosConsumed());
    assertEquals(StopReason.EVENT_PENDING, event.reason());
    assertEquals(1, tms.size());
    assertArrayEquals(tc, tms.get(0));
  }

  // Untraced unit tests (engineering hygiene, SDP §5).

  @Test
  void eventAtExactBudgetBoundaryTakesPrecedence() {
    LoopbackTarget target = runningTarget();
    List<byte[]> tms = new ArrayList<>();
    target.onTm(tms::add);
    target.sendTc(new byte[] {1});
    Consumed consumed = target.grant(PROCESSING);
    assertEquals(PROCESSING, consumed.nanosConsumed());
    assertEquals(StopReason.EVENT_PENDING, consumed.reason());
    assertEquals(1, tms.size());
  }

  @Test
  void multipleTcsYieldTmsInFifoOrderAcrossGrants() {
    LoopbackTarget target = runningTarget();
    List<byte[]> tms = new ArrayList<>();
    target.onTm(tms::add);
    target.sendTc(new byte[] {1});
    target.sendTc(new byte[] {2});
    assertEquals(StopReason.EVENT_PENDING, target.grant(10 * PROCESSING).reason());
    assertEquals(StopReason.EVENT_PENDING, target.grant(10 * PROCESSING).reason());
    assertEquals(2, tms.size());
    assertArrayEquals(new byte[] {1}, tms.get(0));
    assertArrayEquals(new byte[] {2}, tms.get(1));
  }

  @Test
  void tmEmittedBeforeConsumerRegistrationIsBufferedNotLost() {
    LoopbackTarget target = runningTarget();
    target.sendTc(new byte[] {7});
    target.grant(PROCESSING);
    List<byte[]> tms = new ArrayList<>();
    target.onTm(tms::add);
    assertEquals(1, tms.size());
    assertArrayEquals(new byte[] {7}, tms.get(0));
  }

  @Test
  void sentTcPacketIsCopiedAgainstLaterMutation() {
    LoopbackTarget target = runningTarget();
    List<byte[]> tms = new ArrayList<>();
    target.onTm(tms::add);
    byte[] tc = {5};
    target.sendTc(tc);
    tc[0] = 9;
    target.grant(PROCESSING);
    assertArrayEquals(new byte[] {5}, tms.get(0));
  }

  @Test
  void lifecycleViolationsAreRejected() {
    LoopbackTarget fresh = new LoopbackTarget(PROCESSING, ECHO);
    assertThrows(IllegalStateException.class, () -> fresh.grant(1));
    assertThrows(IllegalStateException.class, () -> fresh.sendTc(new byte[] {1}));

    LoopbackTarget halted = runningTarget();
    halted.shutdown();
    assertThrows(IllegalStateException.class, halted::initialize);
    assertThrows(IllegalStateException.class, () -> halted.sendTc(new byte[] {1}));
  }

  @Test
  void grantAfterShutdownReportsHalted() {
    LoopbackTarget target = runningTarget();
    target.shutdown();
    Consumed consumed = target.grant(1_000L);
    assertEquals(0, consumed.nanosConsumed());
    assertEquals(StopReason.HALTED, consumed.reason());
  }

  @Test
  void invalidArgumentsAreRejected() {
    assertThrows(IllegalArgumentException.class, () -> new LoopbackTarget(-1, ECHO));
    assertThrows(NullPointerException.class, () -> new LoopbackTarget(PROCESSING, null));
    LoopbackTarget target = runningTarget();
    assertThrows(IllegalArgumentException.class, () -> target.grant(0));
    assertThrows(IllegalArgumentException.class, () -> target.grant(-1));
  }

  /** Periodic fake OBSW: one single-byte marker TM per interval, payload = report index. */
  private static final class PeriodicObsw implements SimulatedObsw {
    private final long intervalNanos;
    private long nextDue;
    private byte reportIndex;

    PeriodicObsw(long intervalNanos) {
      this.intervalNanos = intervalNanos;
      this.nextDue = intervalNanos;
    }

    @Override
    public List<byte[]> handleTc(byte[] tcPacket, long nowNanos) {
      return List.of(new byte[] {'T', tcPacket[0]});
    }

    @Override
    public long nextEventNanos() {
      return nextDue;
    }

    @Override
    public List<byte[]> handleTimeEvent(long nowNanos) {
      nextDue = nowNanos + intervalNanos;
      return List.of(new byte[] {'E', reportIndex++});
    }
  }

  @Test
  void grantStopsAtTimeEventAndEmitsAtExactInstant() {
    LoopbackTarget target = new LoopbackTarget(PROCESSING, new PeriodicObsw(1_000_000L));
    target.initialize();
    List<byte[]> tms = new ArrayList<>();
    target.onTm(tms::add);

    Consumed first = target.grant(10_000_000L);
    assertEquals(1_000_000L, first.nanosConsumed());
    assertEquals(StopReason.EVENT_PENDING, first.reason());
    assertEquals(1, tms.size());
    assertArrayEquals(new byte[] {'E', 0}, tms.get(0));

    Consumed second = target.grant(10_000_000L);
    assertEquals(1_000_000L, second.nanosConsumed());
    assertEquals(StopReason.EVENT_PENDING, second.reason());
    assertArrayEquals(new byte[] {'E', 1}, tms.get(1));
  }

  @Test
  void timeEventBeyondBudgetIsNotEmittedEarly() {
    LoopbackTarget target = new LoopbackTarget(PROCESSING, new PeriodicObsw(1_000_000L));
    target.initialize();
    List<byte[]> tms = new ArrayList<>();
    target.onTm(tms::add);

    Consumed partial = target.grant(400_000L);
    assertEquals(400_000L, partial.nanosConsumed());
    assertEquals(StopReason.BUDGET_EXHAUSTED, partial.reason());
    assertEquals(0, tms.size());

    // Event due exactly at the budget boundary takes precedence over exhaustion.
    Consumed boundary = target.grant(600_000L);
    assertEquals(600_000L, boundary.nanosConsumed());
    assertEquals(StopReason.EVENT_PENDING, boundary.reason());
    assertEquals(1, tms.size());
  }

  @Test
  void timeEventBeforeTcDueTimeIsHandledFirstAcrossGrants() {
    // TC due at 1.5 ms (sent at 0.5 ms + 1 ms processing), events at 1 ms and 2 ms.
    LoopbackTarget target = new LoopbackTarget(PROCESSING, new PeriodicObsw(1_000_000L));
    target.initialize();
    List<byte[]> tms = new ArrayList<>();
    target.onTm(tms::add);

    target.grant(500_000L);
    target.sendTc(new byte[] {7});
    assertEquals(500_000L, target.grant(10_000_000L).nanosConsumed()); // event at 1 ms
    assertEquals(500_000L, target.grant(10_000_000L).nanosConsumed()); // TC at 1.5 ms
    assertEquals(500_000L, target.grant(10_000_000L).nanosConsumed()); // event at 2 ms
    assertEquals(3, tms.size());
    assertArrayEquals(new byte[] {'E', 0}, tms.get(0));
    assertArrayEquals(new byte[] {'T', 7}, tms.get(1));
    assertArrayEquals(new byte[] {'E', 1}, tms.get(2));
  }

  @Test
  void timeEventAndTcAtSameInstantEmitEventTmFirst() {
    // TC sent at T=0 with 1 ms processing is due exactly at the 1 ms event.
    LoopbackTarget target = new LoopbackTarget(PROCESSING, new PeriodicObsw(1_000_000L));
    target.initialize();
    List<byte[]> tms = new ArrayList<>();
    target.onTm(tms::add);

    target.sendTc(new byte[] {9});
    Consumed consumed = target.grant(10_000_000L);
    assertEquals(1_000_000L, consumed.nanosConsumed());
    assertEquals(StopReason.EVENT_PENDING, consumed.reason());
    assertEquals(2, tms.size());
    assertArrayEquals(new byte[] {'E', 0}, tms.get(0));
    assertArrayEquals(new byte[] {'T', 9}, tms.get(1));
  }

  @Test
  void timeEventNotAdvancingNextEventIsRejected() {
    SimulatedObsw stuck = new SimulatedObsw() {
      @Override
      public List<byte[]> handleTc(byte[] tcPacket, long nowNanos) {
        return List.of();
      }

      @Override
      public long nextEventNanos() {
        return 1_000L;
      }
    };
    LoopbackTarget target = new LoopbackTarget(PROCESSING, stuck);
    target.initialize();
    assertThrows(IllegalStateException.class, () -> target.grant(10_000L));
  }

  @Test
  void zeroProcessingDelayEmitsTmAtTcArrivalTime() {
    LoopbackTarget target = new LoopbackTarget(0, ECHO);
    target.initialize();
    List<byte[]> tms = new ArrayList<>();
    target.onTm(tms::add);
    target.sendTc(new byte[] {3});
    Consumed consumed = target.grant(1_000L);
    assertEquals(0, consumed.nanosConsumed());
    assertEquals(StopReason.EVENT_PENDING, consumed.reason());
    assertEquals(1, tms.size());
  }
}
