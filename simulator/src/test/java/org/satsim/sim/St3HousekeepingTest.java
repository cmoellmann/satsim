package org.satsim.sim;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.satsim.pus.PacketDecodeException;
import org.satsim.pus.st3.HkCreateRequest;
import org.satsim.pus.st3.HkParameter;
import org.satsim.pus.st3.HkReport;
import org.satsim.pus.st3.HkSidList;
import org.satsim.pus.tc.TcPacket;
import org.satsim.pus.tc.TcSecondaryHeader;
import org.satsim.pus.tm.TmPacket;
import org.satsim.sim.obsw.LoopbackTarget;
import org.satsim.sim.obsw.PusSimulatedObsw;
import org.satsim.sim.time.SimulationScheduler;
import org.satsim.testsupport.Requirement;
import org.satsim.testsupport.TestCase;

/**
 * ST[3] housekeeping subset through the loopback chain (ICD §9, SCR-001):
 * default-SID periodic reporting against the ICD §6.5 vectors, structure
 * lifecycle, and the OP-3 semantic-error failure reports against §6.7.
 */
class St3HousekeepingTest {

  private static final HexFormat HEX = HexFormat.of().withDelimiter(" ");

  // ICD §6.4: V-TC-03/04/05.
  private static final byte[] V_TC_03 = HEX.parseHex(
      "18 64 C0 00 00 12 20 03 01 00 00 00 02 00 00 13 88 00 02 00 01 00 03 8D CE");
  private static final byte[] V_TC_04 =
      HEX.parseHex("18 64 C0 01 00 0A 20 03 05 00 00 00 01 00 02 15 41");
  private static final byte[] V_TC_05 =
      HEX.parseHex("18 64 C0 02 00 0A 20 03 07 00 00 00 01 00 02 70 3D");

  // ICD §6.5: V-TM-03/04.
  private static final byte[] V_TM_03 = HEX.parseHex(
      "08 64 C0 00 00 1A 20 03 19 00 00 00 00 00 00 00 01 00 00 00 01 00 00 00 00 00 00 00 00 0D C0 25 61");
  private static final byte[] V_TM_04 = HEX.parseHex(
      "08 64 C0 01 00 1A 20 03 19 00 01 00 00 00 00 00 02 00 00 00 01 00 00 00 00 00 00 00 01 0D D4 52 A3");

  // ICD §6.7 (OP-3 resolution): V-NEG-03, V-TM-09.
  private static final byte[] V_NEG_03 =
      HEX.parseHex("18 64 C0 00 00 0A 20 03 05 00 00 00 01 00 63 6A B3");
  private static final byte[] V_TM_09 = HEX.parseHex(
      "08 64 C0 00 00 14 20 01 08 00 00 00 00 00 00 00 00 00 00 18 64 C0 00 00 06 6A D7");

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
  }

  /** Encodes a TC(3,subtype) with ack=0b0000 and the given app data / ground seq count. */
  private static byte[] st3Tc(int subtype, byte[] appData, int sequenceCount) {
    return TcPacket.of(
        100, sequenceCount, new TcSecondaryHeader(2, 0b0000, 3, subtype, 0), appData).encode();
  }

  /** TM(3,25) reports among {@code tms} with the given SID, decode-checked. */
  private static List<TmPacket> hkReports(List<byte[]> tms, int sid) throws PacketDecodeException {
    List<TmPacket> reports = new ArrayList<>();
    for (byte[] raw : tms) {
      TmPacket tm = TmPacket.decode(raw);
      if (tm.secondaryHeader().serviceType() == 3 && tm.secondaryHeader().messageSubtype() == 25) {
        int reportSid = ((tm.applicationData()[0] & 0xFF) << 8) | (tm.applicationData()[1] & 0xFF);
        if (reportSid == sid) {
          reports.add(tm);
        }
      }
    }
    return reports;
  }

  /**
   * SIM-TC-019: fresh start, no TC traffic — no report before T=1.0 s; the
   * reports at T=1.0 s and T=2.0 s are byte-identical to V-TM-03 and V-TM-04.
   */
  @Test
  @TestCase("SIM-TC-019")
  @Requirement({"SIM-REQ-HK-003", "SIM-REQ-HK-002"})
  void defaultSidReportsPeriodicallyFromStartMatchingVectors() {
    Chain chain = Chain.start();
    chain.scheduler().advanceBy(999_999_999L);
    assertEquals(0, chain.tms().size());

    chain.scheduler().advanceTo(2_500_000_000L);
    assertEquals(2, chain.tms().size());
    assertArrayEquals(V_TM_03, chain.tms().get(0));
    assertArrayEquals(V_TM_04, chain.tms().get(1));
  }

  /**
   * SIM-TC-020: V-TC-03 creates SID 2 disabled (no SID 2 report); after
   * V-TC-04, SID 2 reports at 5.0 s simulated intervals with parameters
   * {P001, P003}; after V-TC-05, no further SID 2 reports; SID 1 reporting
   * is unaffected throughout.
   */
  @Test
  @TestCase("SIM-TC-020")
  @Requirement({"SIM-REQ-HK-001", "SIM-REQ-HK-004"})
  void structureLifecycleCreateEnableDisable() throws PacketDecodeException {
    Chain chain = Chain.start();
    chain.scheduler().injectTc(V_TC_03);
    chain.scheduler().advanceTo(6_000_000_000L);
    assertEquals(0, hkReports(chain.tms(), 2).size());

    // Enable at T=6 s: SID 2 due at 11 s and 16 s (t0 + k*interval, ICD §9.3).
    chain.scheduler().injectTc(V_TC_04);
    chain.scheduler().advanceTo(16_500_000_000L);
    List<TmPacket> sid2 = hkReports(chain.tms(), 2);
    assertEquals(2, sid2.size());
    assertEquals(11, sid2.get(0).secondaryHeader().time().coarse());
    assertEquals(16, sid2.get(1).secondaryHeader().time().coarse());
    for (TmPacket report : sid2) {
      HkReport decoded = HkReport.decode(
          report.applicationData(), List.of(HkParameter.P001, HkParameter.P003));
      assertEquals(2, decoded.sid());
      assertEquals(2, decoded.values().size());
    }

    // Disable at T=16.5 s: no further SID 2 reports.
    chain.scheduler().injectTc(V_TC_05);
    chain.scheduler().advanceTo(30_000_000_000L);
    assertEquals(2, hkReports(chain.tms(), 2).size());

    // SID 1 unaffected throughout: one report per second, T=1..30.
    List<TmPacket> sid1 = hkReports(chain.tms(), 1);
    assertEquals(30, sid1.size());
    for (int i = 0; i < sid1.size(); i++) {
      assertEquals(i + 1, sid1.get(i).secondaryHeader().time().coarse());
    }
  }

  /**
   * SIM-TC-033: V-NEG-03 at T=0 yields exactly one TM(1,8), byte-identical
   * to V-TM-09, no TM(1,1)/TM(1,7), and no housekeeping state change; each
   * ICD §9.1 semantic error class yields exactly one TM(1,8) carrying its
   * §10.4 failure code with the configuration unchanged.
   */
  @Test
  @TestCase("SIM-TC-033")
  @Requirement({"SIM-REQ-VER-003", "SIM-REQ-HK-001", "SIM-REQ-HK-004"})
  void semanticErrorsYieldCompletionFailureReports() throws PacketDecodeException {
    Chain chain = Chain.start();
    chain.scheduler().injectTc(V_NEG_03);
    chain.scheduler().advanceBy(500_000_000L);
    assertEquals(1, chain.tms().size());
    assertArrayEquals(V_TM_09, chain.tms().get(0));

    // Each §9.1 error class on a fresh chain: exactly one TM(1,8) per TC,
    // with the §9.2-ordered §10.4 code; no success or service TM.
    record ErrorCase(byte[] appData, int subtype, int expectedCode) {}
    List<ErrorCase> cases = List.of(
        new ErrorCase(new HkCreateRequest(0, 5000, List.of(1)).encode(), 1, 0x0004),
        new ErrorCase(new HkCreateRequest(1, 5000, List.of(1)).encode(), 1, 0x0004),
        new ErrorCase(new HkCreateRequest(7, 50, List.of(1)).encode(), 1, 0x0007),
        new ErrorCase(new HkCreateRequest(7, 5000, List.of(0x00FF)).encode(), 1, 0x0008),
        new ErrorCase(new HkSidList(List.of(99)).encode(), 7, 0x0006));
    int sequenceCount = 0;
    for (ErrorCase errorCase : cases) {
      Chain fresh = Chain.start();
      fresh.scheduler().injectTc(st3Tc(errorCase.subtype(), errorCase.appData(), sequenceCount++));
      fresh.scheduler().advanceBy(500_000_000L);
      assertEquals(1, fresh.tms().size());
      TmPacket tm = TmPacket.decode(fresh.tms().get(0));
      assertEquals(1, tm.secondaryHeader().serviceType());
      assertEquals(8, tm.secondaryHeader().messageSubtype());
      byte[] appData = tm.applicationData();
      int code = ((appData[4] & 0xFF) << 8) | (appData[5] & 0xFF);
      assertEquals(errorCase.expectedCode(), code);
    }

    // DUPLICATE_SID needs an existing structure: create SID 2, create again.
    Chain duplicate = Chain.start();
    byte[] create2 = new HkCreateRequest(2, 5000, List.of(1)).encode();
    duplicate.scheduler().injectTc(st3Tc(1, create2, 0));
    duplicate.scheduler().advanceBy(1_000_000L);
    assertEquals(0, duplicate.tms().size());
    duplicate.scheduler().injectTc(st3Tc(1, create2, 1));
    duplicate.scheduler().advanceBy(1_000_000L);
    assertEquals(1, duplicate.tms().size());
    TmPacket tm18 = TmPacket.decode(duplicate.tms().get(0));
    assertEquals(8, tm18.secondaryHeader().messageSubtype());
    assertEquals(0x0005, ((tm18.applicationData()[4] & 0xFF) << 8) | (tm18.applicationData()[5] & 0xFF));

    // No housekeeping state change from any failed TC: advancing the V-NEG-03
    // chain further yields only SID 1 reports (no SID 99, no other SID).
    chain.scheduler().advanceTo(3_000_000_000L);
    List<TmPacket> allReports = hkReports(chain.tms(), 1);
    assertEquals(3, allReports.size());
    for (byte[] raw : chain.tms()) {
      TmPacket tm = TmPacket.decode(raw);
      if (tm.secondaryHeader().serviceType() == 3) {
        int sid = ((tm.applicationData()[0] & 0xFF) << 8) | (tm.applicationData()[1] & 0xFF);
        assertEquals(1, sid);
      }
    }
  }

  // Untraced unit tests (engineering hygiene, SDP §5).

  @Test
  void acceptedSt3TcWithAckFlagsGetsAcceptanceAndCompletionReports() throws PacketDecodeException {
    Chain chain = Chain.start();
    byte[] create = TcPacket.of(100, 0,
        new TcSecondaryHeader(2, 0b1001, 3, 1, 0),
        new HkCreateRequest(2, 5000, List.of(1)).encode()).encode();
    chain.scheduler().injectTc(create);
    chain.scheduler().advanceBy(1_000_000L);

    assertEquals(2, chain.tms().size());
    TmPacket acceptance = TmPacket.decode(chain.tms().get(0));
    assertEquals(1, acceptance.secondaryHeader().serviceType());
    assertEquals(1, acceptance.secondaryHeader().messageSubtype());
    TmPacket completion = TmPacket.decode(chain.tms().get(1));
    assertEquals(7, completion.secondaryHeader().messageSubtype());
  }

  @Test
  void malformedSt3AppDataYieldsAcceptanceFailure0x0003() throws PacketDecodeException {
    Chain chain = Chain.start();
    // TC(3,1) with truncated application data: N1 says 2 parameters, one present.
    byte[] truncated = HEX.parseHex("00 02 00 00 13 88 00 02 00 01");
    chain.scheduler().injectTc(st3Tc(1, truncated, 0));
    chain.scheduler().advanceBy(1_000_000L);

    assertEquals(1, chain.tms().size());
    TmPacket tm = TmPacket.decode(chain.tms().get(0));
    assertEquals(1, tm.secondaryHeader().serviceType());
    assertEquals(2, tm.secondaryHeader().messageSubtype());
    byte[] appData = tm.applicationData();
    assertEquals(0x0003, ((appData[4] & 0xFF) << 8) | (appData[5] & 0xFF));
  }

  @Test
  void enablingEnabledAndDisablingDisabledIsNoOp() throws PacketDecodeException {
    Chain chain = Chain.start();
    // Re-enabling the running default SID 1 must not shift its schedule.
    chain.scheduler().advanceTo(500_000_000L);
    chain.scheduler().injectTc(st3Tc(5, new HkSidList(List.of(1)).encode(), 0));
    chain.scheduler().advanceTo(2_500_000_000L);
    List<TmPacket> sid1 = hkReports(chain.tms(), 1);
    assertEquals(2, sid1.size());
    assertEquals(1, sid1.get(0).secondaryHeader().time().coarse());
    assertEquals(2, sid1.get(1).secondaryHeader().time().coarse());

    // Disabling a disabled structure is equally a no-op (not an error).
    chain.scheduler().injectTc(st3Tc(1, new HkCreateRequest(2, 5000, List.of(1)).encode(), 1));
    chain.scheduler().injectTc(st3Tc(7, new HkSidList(List.of(2)).encode(), 2));
    chain.scheduler().advanceBy(1_000_000L);
    assertTrue(hkReports(chain.tms(), 2).isEmpty());
    assertEquals(0, chain.obsw().rejections().size());
  }

  @Test
  void batteryVoltageFollowsTriangleWaveOverSimulatedTime() throws PacketDecodeException {
    // Rising edge at T=1 s (3520 mV, V-TM-03) is covered by SIM-TC-019; check
    // the falling edge: T=31 s -> p=31000 -> 4100 - 1000/50 = 4080 mV.
    Chain chain = Chain.start();
    chain.scheduler().advanceTo(31_000_000_000L);
    List<TmPacket> sid1 = hkReports(chain.tms(), 1);
    TmPacket at31 = sid1.get(30);
    assertEquals(31, at31.secondaryHeader().time().coarse());
    HkReport decoded = HkReport.decode(at31.applicationData(),
        List.of(HkParameter.P001, HkParameter.P002, HkParameter.P003));
    assertEquals(4080L, decoded.values().get(2));
  }
}
