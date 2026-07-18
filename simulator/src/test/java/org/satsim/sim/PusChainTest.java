package org.satsim.sim;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.satsim.pus.PacketDecodeException;
import org.satsim.pus.tm.TmPacket;
import org.satsim.sim.obsw.LoopbackTarget;
import org.satsim.sim.obsw.PusSimulatedObsw;
import org.satsim.sim.obsw.PusSimulatedObsw.RejectReason;
import org.satsim.sim.time.SimulationScheduler;
import org.satsim.testsupport.Requirement;
import org.satsim.testsupport.TestCase;

/**
 * End-to-end ST[17] chain through the loopback OBSW target driven by the
 * simulation scheduler: ICD §6 vectors in, ICD §6 vectors out.
 */
class PusChainTest {

  private static final HexFormat HEX = HexFormat.of().withDelimiter(" ");

  // ICD §6.1/§6.2 reference vectors.
  private static final byte[] V_TC_01 = HEX.parseHex("18 64 C0 00 00 06 20 11 01 00 00 FA 83");
  private static final byte[] V_TC_02 = HEX.parseHex("18 64 C0 01 00 06 2F 11 01 00 00 D8 A9");
  private static final byte[] V_TM_01 =
      HEX.parseHex("08 64 C0 00 00 0E 20 11 02 00 00 00 00 00 00 00 00 00 00 0C 46");
  private static final byte[] V_TM_02 =
      HEX.parseHex("08 64 C0 01 00 0E 20 11 02 00 01 00 00 00 00 00 01 80 00 63 E9");

  // ICD §6.3 negative vectors: V-NEG-01 (CRC failure), V-NEG-02 (PUS version 1).
  private static final byte[] V_NEG_01 = HEX.parseHex("18 64 C0 00 00 06 20 11 01 00 00 FA 84");
  private static final byte[] V_NEG_02 = HEX.parseHex("18 64 C0 00 00 06 10 11 01 00 00 F6 6D");

  // ICD §6.6: V-TC-06, V-TM-05/06/07/08 (ST[1] request verification subset, SCR-002).
  private static final byte[] V_TC_06 = HEX.parseHex("18 64 C0 00 00 06 29 11 01 00 00 52 FF");
  private static final byte[] V_TM_05 =
      HEX.parseHex("08 64 C0 00 00 12 20 01 01 00 00 00 00 00 00 00 00 00 00 18 64 C0 00 1C BC");
  private static final byte[] V_TM_06 =
      HEX.parseHex("08 64 C0 01 00 0E 20 11 02 00 00 00 00 00 00 00 00 00 00 A4 62");
  private static final byte[] V_TM_07 =
      HEX.parseHex("08 64 C0 02 00 12 20 01 07 00 00 00 00 00 00 00 00 00 00 18 64 C0 00 A1 B1");
  private static final byte[] V_TM_08 = HEX.parseHex(
      "08 64 C0 00 00 14 20 01 02 00 00 00 00 00 00 00 00 00 00 18 64 C0 00 00 01 BC E4");

  /** A freshly started chain: obsw + zero-delay loopback + scheduler. */
  private record Chain(
      PusSimulatedObsw obsw, SimulationScheduler scheduler, List<byte[]> tms) {

    static Chain start() {
      PusSimulatedObsw obsw = new PusSimulatedObsw();
      SimulationScheduler scheduler = new SimulationScheduler(new LoopbackTarget(0, obsw));
      List<byte[]> tms = new ArrayList<>();
      scheduler.onTm(tms::add);
      scheduler.start();
      return new Chain(obsw, scheduler, tms);
    }

    void inject(byte[] tc) {
      scheduler.injectTc(tc);
    }
  }

  /**
   * SIM-TC-008: injecting V-TC-01 at T=0 with fresh counters yields exactly
   * one TM(17,2) on APID 100, byte-identical to V-TM-01.
   */
  @Test
  @TestCase("SIM-TC-008")
  @Requirement({"SIM-REQ-PUS-008", "SIM-REQ-PUS-010"})
  void validPingYieldsExactlyOneConnectionTestReport() throws PacketDecodeException {
    Chain chain = Chain.start();
    chain.inject(V_TC_01);
    chain.scheduler().advanceBy(1_000_000_000L);

    assertEquals(1, chain.tms().size());
    assertArrayEquals(V_TM_01, chain.tms().get(0));
    TmPacket tm = TmPacket.decode(chain.tms().get(0));
    assertEquals(100, tm.primaryHeader().apid());
    assertEquals(17, tm.secondaryHeader().serviceType());
    assertEquals(2, tm.secondaryHeader().messageSubtype());
  }

  /**
   * SIM-TC-006: V-NEG-01 (CRC failure) is rejected: no TM is emitted and the
   * rejection is observable on the obsw's rejection queue.
   */
  @Test
  @TestCase("SIM-TC-006")
  @Requirement("SIM-REQ-PUS-005")
  void crcFailureIsRejectedWithoutTm() {
    Chain chain = Chain.start();
    chain.inject(V_NEG_01);
    chain.scheduler().advanceBy(1_000_000_000L);

    assertEquals(0, chain.tms().size());
    assertEquals(1, chain.obsw().rejections().size());
    assertEquals(RejectReason.NOT_A_PACKET, chain.obsw().rejections().get(0).reason());
  }

  /**
   * SIM-TC-007 (amended per SCR-002): V-NEG-02 (PUS version 1, valid CRC) is
   * rejected: from M1a this yields exactly one TM(1,2) failure report and no
   * TM(17,2) — the former "no TM emitted" criterion applied only before M1a.
   * See SIM-TC-024 for the byte-identical failure-report check.
   */
  @Test
  @TestCase("SIM-TC-007")
  @Requirement("SIM-REQ-PUS-006")
  void pusVersionRejectionYieldsNoServiceTm() throws PacketDecodeException {
    Chain chain = Chain.start();
    chain.inject(V_NEG_02);
    chain.scheduler().advanceBy(1_000_000_000L);

    assertEquals(1, chain.tms().size());
    TmPacket tm = TmPacket.decode(chain.tms().get(0));
    assertEquals(1, tm.secondaryHeader().serviceType());
    assertEquals(2, tm.secondaryHeader().messageSubtype());
    assertEquals(1, chain.obsw().rejections().size());
    assertEquals(
        RejectReason.ILLEGAL_PUS_VERSION, chain.obsw().rejections().get(0).reason());
  }

  /**
   * SIM-TC-023: fresh start, injecting V-TC-06 (ack=0b1001) at T=0 yields
   * exactly TM(1,1), TM(17,2), TM(1,7) in that order, byte-identical to
   * V-TM-05/06/07; separately, a fresh start injecting V-TC-01 (ack=0b0000)
   * yields exactly one TM(17,2) and no ST[1] report.
   */
  @Test
  @TestCase("SIM-TC-023")
  @Requirement({"SIM-REQ-VER-001", "SIM-REQ-VER-002"})
  void ackFlagsDriveVerificationReportSequence() throws PacketDecodeException {
    Chain chain = Chain.start();
    chain.inject(V_TC_06);
    chain.scheduler().advanceBy(1_000_000_000L);

    assertEquals(3, chain.tms().size());
    assertArrayEquals(V_TM_05, chain.tms().get(0));
    assertArrayEquals(V_TM_06, chain.tms().get(1));
    assertArrayEquals(V_TM_07, chain.tms().get(2));

    Chain noAck = Chain.start();
    noAck.inject(V_TC_01);
    noAck.scheduler().advanceBy(1_000_000_000L);

    assertEquals(1, noAck.tms().size());
    assertArrayEquals(V_TM_01, noAck.tms().get(0));
    TmPacket tm = TmPacket.decode(noAck.tms().get(0));
    assertEquals(17, tm.secondaryHeader().serviceType());
    assertEquals(2, tm.secondaryHeader().messageSubtype());
  }

  /**
   * SIM-TC-024: fresh start, injecting V-NEG-02 at T=0 yields exactly one
   * TM(1,2), byte-identical to V-TM-08 (failure code 0x0001), and the
   * rejection queue still records ILLEGAL_PUS_VERSION.
   */
  @Test
  @TestCase("SIM-TC-024")
  @Requirement("SIM-REQ-VER-003")
  void acceptanceFailureYieldsFailureReport() {
    Chain chain = Chain.start();
    chain.inject(V_NEG_02);
    chain.scheduler().advanceBy(1_000_000_000L);

    assertEquals(1, chain.tms().size());
    assertArrayEquals(V_TM_08, chain.tms().get(0));
    assertEquals(1, chain.obsw().rejections().size());
    assertEquals(
        RejectReason.ILLEGAL_PUS_VERSION, chain.obsw().rejections().get(0).reason());
  }

  /**
   * SIM-TC-009: two consecutive pings yield TM sequence counts n, n+1 and
   * message type counters m, m+1; wrap verified at forced presets 16383
   * (sequence count) and 65535 (message type counter).
   */
  @Test
  @TestCase("SIM-TC-009")
  @Requirement("SIM-REQ-PUS-009")
  void countersIncrementAndWrap() throws PacketDecodeException {
    // V-TC-02 (ack=0b1111) is not used here for the second ping: from M1a its
    // acceptance ack flag additionally emits a TM(1,1) ahead of the TM(17,2),
    // which would shift the very APID-wide sequence count this test measures
    // (ack-flag-driven sequencing is exercised separately by SIM-TC-023).
    // V-TC-01 (ack=0b0000) is re-injected instead to isolate ST[17] counter
    // behavior.
    Chain chain = Chain.start();
    chain.inject(V_TC_01);
    chain.scheduler().advanceBy(1_000_000L);
    chain.inject(V_TC_01);
    chain.scheduler().advanceBy(1_000_000L);

    assertEquals(2, chain.tms().size());
    TmPacket first = TmPacket.decode(chain.tms().get(0));
    TmPacket second = TmPacket.decode(chain.tms().get(1));
    assertEquals(first.primaryHeader().sequenceCount() + 1, second.primaryHeader().sequenceCount());
    assertEquals(
        first.secondaryHeader().messageTypeCounter() + 1,
        second.secondaryHeader().messageTypeCounter());

    Chain wrap = Chain.start();
    wrap.obsw().presetTmSequenceCount(16383);
    wrap.obsw().presetMessageTypeCounter(17, 2, 65535);
    wrap.inject(V_TC_01);
    wrap.scheduler().advanceBy(1_000_000L);
    wrap.inject(V_TC_01);
    wrap.scheduler().advanceBy(1_000_000L);

    TmPacket atMax = TmPacket.decode(wrap.tms().get(0));
    TmPacket wrapped = TmPacket.decode(wrap.tms().get(1));
    assertEquals(16383, atMax.primaryHeader().sequenceCount());
    assertEquals(65535, atMax.secondaryHeader().messageTypeCounter());
    assertEquals(0, wrapped.primaryHeader().sequenceCount());
    assertEquals(0, wrapped.secondaryHeader().messageTypeCounter());
  }

  // Untraced unit tests (engineering hygiene, SDP §5).

  @Test
  void secondPingAtOnePointFiveSecondsReproducesVectorTm02() {
    // V-TC-02 is not used for the second ping for the same reason as in
    // countersIncrementAndWrap above: its ack=0b1111 would insert a TM(1,1)
    // ahead of the TM(17,2) from M1a, shifting its sequence count away from
    // the seq count=1 that V-TM-02 requires. V-TC-01 (ack=0b0000) is
    // re-injected instead.
    Chain chain = Chain.start();
    chain.inject(V_TC_01);
    chain.scheduler().advanceTo(1_500_000_000L);
    chain.inject(V_TC_01);
    chain.scheduler().advanceBy(1_000_000L);

    assertEquals(2, chain.tms().size());
    assertArrayEquals(V_TM_01, chain.tms().get(0));
    assertArrayEquals(V_TM_02, chain.tms().get(1));
  }

  @Test
  void unimplementedServiceIsRejectedObservably() throws PacketDecodeException {
    Chain chain = Chain.start();
    // Structurally valid TC(3,1)-style packet is not implemented in M1.
    byte[] tc31 = HEX.parseHex("18 64 C0 00 00 12 20 03 01 00 00 00 02 00 00 13 88 00 02 00 01 00 03 8D CE");
    chain.inject(tc31);
    chain.scheduler().advanceBy(1_000_000L);

    assertEquals(1, chain.tms().size());
    TmPacket tm = TmPacket.decode(chain.tms().get(0));
    assertEquals(1, tm.secondaryHeader().serviceType());
    assertEquals(2, tm.secondaryHeader().messageSubtype());
    byte[] appData = tm.applicationData();
    assertEquals(0x00, appData[appData.length - 2]);
    assertEquals(0x02, appData[appData.length - 1]);
    assertEquals(1, chain.obsw().rejections().size());
    assertEquals(
        RejectReason.ILLEGAL_SERVICE_OR_SUBTYPE, chain.obsw().rejections().get(0).reason());
  }

  @Test
  void masterClockMatchesSlaveTimeAtGrantBoundaries() {
    Chain chain = Chain.start();
    chain.scheduler().advanceBy(250_000_000L);
    assertEquals(250_000_000L, chain.scheduler().clock().nanos());
    chain.scheduler().advanceTo(1_500_000_000L);
    assertEquals(1_500_000_000L, chain.scheduler().clock().nanos());
    assertTrue(chain.tms().isEmpty());
  }
}
