package org.satsim.sim.web;

import org.satsim.pus.time.CucTime;
import org.satsim.sim.obsw.PusSimulatedObsw.Rejection;

/**
 * WebSocket rejection frame (ICD §8.2, {@code kind:"rejection"}): simulator
 * diagnostic channel for spacecraft-side TC rejections — not spacecraft
 * telemetry. Covers all reject reasons including CRC failures, which by ICD
 * §6.3 never produce TM [SIM-REQ-UI-007].
 */
public record RejectionFrame(
    String kind, String reason, String hex, long timeCoarse, int timeFine, double timeSeconds) {

  /** Builds the frame for one observed rejection. */
  public static RejectionFrame of(Rejection rejection) {
    CucTime time = CucTime.ofNanos(rejection.nanos());
    return new RejectionFrame(
        "rejection",
        rejection.reason().name(),
        rejection.packetHex(),
        time.coarse(),
        time.fine(),
        time.coarse() + time.fine() / 65536.0);
  }
}
