package org.satsim.sim;

import org.satsim.sim.obsw.LoopbackTarget;
import org.satsim.sim.obsw.PusSimulatedObsw;
import org.satsim.sim.time.SimulationScheduler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot entry point of the simulator backend: wires the simulated
 * spacecraft (PUS on-board software behind the loopback OBSW target, ADR-0001) to
 * the simulation time master and exposes the REST/WebSocket API (ICD §8).
 * Spring types stay inside this module (CLAUDE.md rule 5).
 */
@SpringBootApplication
public class SimulatorApplication {

  public static void main(String[] args) {
    SpringApplication.run(SimulatorApplication.class, args);
  }

  /** The simulated on-board software (M1 service set: ST[17]). */
  @Bean
  public PusSimulatedObsw pusSimulatedObsw() {
    return new PusSimulatedObsw();
  }

  /**
   * The time master driving the loopback OBSW target. Zero processing delay
   * per the ICD §6 vectors (TM time = TC injection time).
   */
  @Bean
  public SimulationScheduler simulationScheduler(PusSimulatedObsw pusSimulatedObsw) {
    return new SimulationScheduler(new LoopbackTarget(0, pusSimulatedObsw));
  }
}
