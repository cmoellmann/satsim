package org.satsim.sim.link;

import java.util.function.Consumer;

/**
 * The space link seam (ADR-0001): carries encoded CCSDS space packets between
 * the ground side and the (simulated) spacecraft side. Implementations:
 * in-process loopback (PoC), TCP to native OBSW process (M3), emulator bridges
 * (TSIM/TEMU/QEMU device models, M5+).
 *
 * <p>Payloads are complete space packets as defined in docs/icd.md; framing on
 * external transports per ICD §8. Delivery timing is governed by the time
 * master, not by this interface.
 */
public interface SpaceLink {

  /** Queue one encoded TC space packet for delivery to the spacecraft side. */
  void sendTc(byte[] tcPacket);

  /** Register the consumer for TM space packets arriving from the spacecraft side. */
  void onTm(Consumer<byte[]> tmConsumer);
}
