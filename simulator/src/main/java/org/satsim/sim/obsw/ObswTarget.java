package org.satsim.sim.obsw;

import org.satsim.sim.link.SpaceLink;
import org.satsim.sim.time.EmulatorControl;

/**
 * An OBSW target: an execution environment for the (simulated) spacecraft
 * side, plugged in behind the two seams — packet transport
 * ({@link SpaceLink}) and slaved time ({@link EmulatorControl}). Planned
 * targets: in-process loopback (M0), native OBSW process (M3),
 * instruction-level emulators such as QEMU (M5+). The validation suite must
 * pass unchanged against any conforming target [SIM-REQ-LINK-003].
 *
 * <p>Terminology: the accepted ADRs (0001, 0006) call this an "execution
 * back-end"; both terms denote the same concept, ADR wording is retained
 * there by immutability (CLAUDE.md rule 4).
 */
public interface ObswTarget extends EmulatorControl, SpaceLink {
}
