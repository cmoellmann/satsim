package org.satsim.sim.web;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST TC submission endpoint (ICD §8): {@code POST /api/tc} injects a TC
 * into the simulation and returns the injected packet's hex;
 * {@code POST /api/tc/preview} returns the hex a structured compose would
 * produce without injecting [SIM-REQ-UI-003, SIM-REQ-UI-004].
 */
@RestController
@RequestMapping("/api/tc")
public class TcController {

  private final SimulationService simulation;

  public TcController(SimulationService simulation) {
    this.simulation = simulation;
  }

  /** Injects the submitted TC; responds with the injected packet's hex. */
  @PostMapping
  public Map<String, String> send(@RequestBody TcSubmission submission) {
    return Map.of("hex", simulation.sendTc(submission));
  }

  /** Encodes without injecting (frontend hex preview). */
  @PostMapping("/preview")
  public Map<String, String> preview(@RequestBody TcSubmission submission) {
    return Map.of("hex", simulation.previewTc(submission));
  }

  /** Invalid submissions (bad hex, missing fields, out-of-range values) → 400. */
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
  }
}
