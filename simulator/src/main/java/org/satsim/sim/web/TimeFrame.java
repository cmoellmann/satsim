package org.satsim.sim.web;

import org.satsim.pus.time.CucTime;

/**
 * WebSocket time frame (ICD §8.2, {@code kind:"time"}): the current simulated
 * time (OBT) as CUC fields plus decimal seconds. Published on session connect
 * and at the simulated-time publication quantum [SIM-REQ-UI-005].
 */
public record TimeFrame(String kind, long timeCoarse, int timeFine, double timeSeconds) {

  /** Builds a time frame for the simulated time {@code nanos} since epoch. */
  public static TimeFrame ofNanos(long nanos) {
    CucTime time = CucTime.ofNanos(nanos);
    return new TimeFrame("time", time.coarse(), time.fine(), time.coarse() + time.fine() / 65536.0);
  }
}
