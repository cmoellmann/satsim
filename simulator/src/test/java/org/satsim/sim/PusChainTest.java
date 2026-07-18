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
import org.satsim.sim.obsw.PusSpacecraftApplication;
import org.satsim.sim.obsw.PusSpacecraftApplication.RejectReason;
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

  /** A freshly started chain: application + zero-delay loopback + scheduler. */
  private record Chain(
      PusSpacecraftApplication application, SimulationScheduler scheduler, List<byte[]> tms) {

    static Chain start() {
      PusSpacecraftApplication application = new PusSpacecraftApplication();
      SimulationScheduler scheduler = new SimulationScheduler(new LoopbackTarget(0, application));
      List<byte[]> tms = new ArrayList<>();
      scheduler.onTm(tms::add);
      scheduler.start();
      return new Chain(application, scheduler, tms);
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
   * rejection is observable on the application's rejection queue.
   */
  @Test
  @TestCase("SIM-TC-006")
  @Requirement("SIM-REQ-PUS-005")
  void crcFailureIsRejectedWithoutTm() {
    Chain chain = Chain.start();
    chain.inject(V_NEG_01);
    chain.scheduler().advanceBy(1_000_000_000L);

    assertEquals(0, chain.tms().size());
    assertEquals(1, chain.application().rejections().size());
    assertEquals(RejectReason.NOT_A_PACKET, chain.application().rejections().get(0).reason());
  }

  /**
   * SIM-TC-007: V-NEG-02 (PUS version 1, valid CRC) is rejected: no TM(17,2)
   * is emitted — in M1 no TM of any kind exists (the TM(1,2) acceptance
   * failure report arrives with M1a, SIM-TC-024).
   */
  @Test
  @TestCase("SIM-TC-007")
  @Requirement("SIM-REQ-PUS-006")
  void pusVersionRejectionYieldsNoTm() {
    Chain chain = Chain.start();
    chain.inject(V_NEG_02);
    chain.scheduler().advanceBy(1_000_000_000L);

    assertEquals(0, chain.tms().size());
    assertEquals(1, chain.application().rejections().size());
    assertEquals(
        RejectReason.ILLEGAL_PUS_VERSION, chain.application().rejections().get(0).reason());
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
    Chain chain = Chain.start();
    chain.inject(V_TC_01);
    chain.scheduler().advanceBy(1_000_000L);
    chain.inject(V_TC_02);
    chain.scheduler().advanceBy(1_000_000L);

    assertEquals(2, chain.tms().size());
    TmPacket first = TmPacket.decode(chain.tms().get(0));
    TmPacket second = TmPacket.decode(chain.tms().get(1));
    assertEquals(first.primaryHeader().sequenceCount() + 1, second.primaryHeader().sequenceCount());
    assertEquals(
        first.secondaryHeader().messageTypeCounter() + 1,
        second.secondaryHeader().messageTypeCounter());

    Chain wrap = Chain.start();
    wrap.application().presetTmSequenceCount(16383);
    wrap.application().presetMessageTypeCounter(17, 2, 65535);
    wrap.inject(V_TC_01);
    wrap.scheduler().advanceBy(1_000_000L);
    wrap.inject(V_TC_02);
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
    Chain chain = Chain.start();
    chain.inject(V_TC_01);
    chain.scheduler().advanceTo(1_500_000_000L);
    chain.inject(V_TC_02);
    chain.scheduler().advanceBy(1_000_000L);

    assertEquals(2, chain.tms().size());
    assertArrayEquals(V_TM_01, chain.tms().get(0));
    assertArrayEquals(V_TM_02, chain.tms().get(1));
  }

  @Test
  void unimplementedServiceIsRejectedObservably() {
    Chain chain = Chain.start();
    // Structurally valid TC(3,1)-style packet is not implemented in M1.
    byte[] tc31 = HEX.parseHex("18 64 C0 00 00 12 20 03 01 00 00 00 02 00 00 13 88 00 02 00 01 00 03 8D CE");
    chain.inject(tc31);
    chain.scheduler().advanceBy(1_000_000L);

    assertEquals(0, chain.tms().size());
    assertEquals(1, chain.application().rejections().size());
    assertEquals(
        RejectReason.ILLEGAL_SERVICE_OR_SUBTYPE, chain.application().rejections().get(0).reason());
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
