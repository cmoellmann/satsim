package org.satsim.sim.web;

/**
 * REST TC submission body (ICD §8): either raw {@code hex} (a complete
 * encoded space packet, injected verbatim), or a structured compose with
 * {@code service}/{@code subtype} (required), {@code ackFlags} (ICD §3 bit
 * layout, default 0) and {@code appDataHex} (default empty)
 * [SIM-REQ-UI-001].
 */
public record TcSubmission(
    String hex, Integer service, Integer subtype, Integer ackFlags, String appDataHex) {}
