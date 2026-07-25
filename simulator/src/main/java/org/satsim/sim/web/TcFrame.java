package org.satsim.sim.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * WebSocket TC frame (ICD §8.2 Issue 7, {@code kind:"tc"}): uplink
 * observation channel — one frame per ICD §8.1 injection, broadcast to all
 * sessions including the submitting one, so every observer sees the command
 * traffic of the shared spacecraft [SIM-REQ-UI-017, SCR-009]. Field contents
 * are those of the §8.1 response for the same injection, correlated by
 * {@code injectionId}.
 */
@JsonInclude(Include.NON_NULL)
public record TcFrame(
    String kind,
    long injectionId,
    String hex,
    long timeCoarse,
    int timeFine,
    double timeSeconds,
    Integer sequenceCount,
    TcSendResponse.Decoded decoded,
    String decodeError) {

  /** Builds the broadcast frame for the injection described by {@code response}. */
  public static TcFrame of(TcSendResponse response) {
    return new TcFrame(
        "tc",
        response.injectionId(),
        response.hex(),
        response.timeCoarse(),
        response.timeFine(),
        response.timeSeconds(),
        response.sequenceCount(),
        response.decoded(),
        response.decodeError());
  }
}
