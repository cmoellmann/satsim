package org.satsim.sim;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.satsim.sim.obsw.LoopbackTarget;
import org.satsim.sim.obsw.PusSimulatedObsw;
import org.satsim.sim.time.SimulationScheduler;
import org.satsim.testsupport.Requirement;
import org.satsim.testsupport.TestCase;

/**
 * Determinism replay (ADR-0006 C6): identical initial state and identical
 * scripted TC injection times must produce byte-identical TM streams,
 * timestamps included.
 */
class DeterminismReplayTest {

  private static final HexFormat HEX = HexFormat.of().withDelimiter(" ");

  private static final byte[] V_TC_01 = HEX.parseHex("18 64 C0 00 00 06 20 11 01 00 00 FA 83");
  private static final byte[] V_TC_02 = HEX.parseHex("18 64 C0 01 00 06 2F 11 01 00 00 D8 A9");
  private static final byte[] V_NEG_02 = HEX.parseHex("18 64 C0 00 00 06 10 11 01 00 00 F6 6D");

  /**
   * SIM-TC-011: two runs of the same scripted scenario (pings at 0 s and
   * 1.5 s, a rejected TC at 2 s, run out to 5 s) produce byte-identical TM
   * streams — full-stream hash equality.
   */
  @Test
  @TestCase("SIM-TC-011")
  @Requirement("SIM-REQ-TIME-005")
  void scriptedReplayProducesByteIdenticalTmStreams() throws NoSuchAlgorithmException {
    byte[] firstRun = runScriptedScenario();
    byte[] secondRun = runScriptedScenario();

    MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
    assertArrayEquals(sha256.digest(firstRun), MessageDigest.getInstance("SHA-256").digest(secondRun));
    // Redundant with the hash check, but pinpoints the first diverging octet on failure.
    assertArrayEquals(firstRun, secondRun);
    // M1a (SCR-002): V-TC-01 (ack=0b0000) yields one 21-octet TM(17,2); V-TC-02
    // (ack=0b1111) additionally yields TM(1,1) and TM(1,7), 25 octets each
    // (ICD §10.3); V-NEG-02 now yields one 27-octet TM(1,2) instead of no TM.
    // 21 + (25 + 21 + 25) + 27 = 119.
    assertEquals(119, firstRun.length, "expected TM(17,2) + ST[1] verification reports in the stream");
  }

  /** Runs the fixed script on a fresh chain; returns the concatenated TM stream. */
  private static byte[] runScriptedScenario() {
    PusSimulatedObsw obsw = new PusSimulatedObsw();
    SimulationScheduler scheduler = new SimulationScheduler(new LoopbackTarget(0, obsw));
    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    scheduler.onTm(tm -> stream.writeBytes(tm));
    scheduler.start();

    scheduler.injectTc(V_TC_01);
    scheduler.advanceTo(1_500_000_000L);
    scheduler.injectTc(V_TC_02);
    scheduler.advanceTo(2_000_000_000L);
    scheduler.injectTc(V_NEG_02);
    scheduler.advanceTo(5_000_000_000L);
    scheduler.stop();
    return stream.toByteArray();
  }
}
