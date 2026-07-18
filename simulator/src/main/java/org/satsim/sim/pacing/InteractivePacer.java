package org.satsim.sim.pacing;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.satsim.sim.web.SimulationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Interactive 1:1 pacing policy: advances simulated time by one wall-clock
 * tick per elapsed wall-clock tick, so the simulation runs in real time for
 * interactive frontend use. This package is the single sanctioned location
 * for wall-clock use (CLAUDE.md rule 2 tailoring; see
 * config/checkstyle/suppressions.xml) — here it is confined to the tick
 * scheduling of the executor. Simulation logic itself remains driven purely
 * by the granted simulated-time budgets [SIM-REQ-TIME-001].
 *
 * <p>Pacing is a policy on top of the time master, not part of it: scripted
 * and automated runs bypass this class entirely and stay deterministic
 * (SIM-TC-011).
 */
@Component
public final class InteractivePacer {

  private final SimulationService simulation;
  private final long tickMillis;
  private final ScheduledExecutorService ticker =
      Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "sim-pacer");
        thread.setDaemon(true);
        return thread;
      });

  public InteractivePacer(
      SimulationService simulation, @Value("${satsim.pacing.tick-millis:20}") long tickMillis) {
    this.simulation = simulation;
    this.tickMillis = tickMillis;
  }

  @PostConstruct
  void start() {
    if (tickMillis < 0) {
      throw new IllegalStateException("satsim.pacing.tick-millis must be >= 0: " + tickMillis);
    }
    if (tickMillis == 0) {
      // Pacing disabled: simulated time advances only on explicit
      // SimulationService.advanceBy calls (deterministic web tests, SIM-TC-027..029).
      return;
    }
    long tickNanos = tickMillis * 1_000_000L;
    ticker.scheduleAtFixedRate(
        () -> simulation.advanceBy(tickNanos), tickMillis, tickMillis, TimeUnit.MILLISECONDS);
  }

  @PreDestroy
  void stop() {
    ticker.shutdownNow();
  }
}
