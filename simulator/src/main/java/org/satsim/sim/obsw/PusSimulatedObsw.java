package org.satsim.sim.obsw;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.satsim.pus.PacketDecodeException;
import org.satsim.pus.tc.TcPacket;
import org.satsim.pus.tc.TcSecondaryHeader;
import org.satsim.pus.time.CucTime;
import org.satsim.pus.tm.TmPacket;
import org.satsim.pus.tm.TmSecondaryHeader;

/**
 * The PUS-C implementation of the simulated on-board software, tailored per the ICD: single
 * APID 100 (ADR-0003), strict PUS-C (ADR-0002). M1 service set: ST[17]
 * connection test — a valid TC(17,1) yields exactly one TM(17,2)
 * [SIM-REQ-PUS-008, SIM-REQ-PUS-010].
 *
 * <p>Acceptance checks in ICD §10.2 order: CRC/structure (via
 * {@link TcPacket#decode}), then PUS version = 2, then service/subtype
 * implemented. In M1 every rejection discards the TC without TM; each
 * rejection is observable in the log and on {@link #rejections()}
 * [SIM-REQ-PUS-005, SIM-REQ-PUS-006]. ST[1] failure reports arrive with M1a
 * (SCR-002).
 *
 * <p>TM packet sequence counts increment per APID (wrap at
 * {@code 16383}); message type counters increment per (service, subtype)
 * (wrap at {@code 65535}) [SIM-REQ-PUS-009]. TM time is CUC 4+2 from the
 * simulated time passed in by the hosting target [SIM-REQ-TIME-002].
 *
 * <p>Not thread-safe: owned by its OBSW target, which is driven solely by the
 * time master (ADR-0006).
 */
public final class PusSimulatedObsw implements SimulatedObsw {

  /** Why a TC was discarded (M1); aligns with the ICD §10.4 codes for M1a. */
  public enum RejectReason {
    /** Structural decode failure or CRC error (ICD §6.3: silent discard). */
    NOT_A_PACKET,
    /** TC packet PUS version number is not 2 (ICD §10.4 code 0x0001). */
    ILLEGAL_PUS_VERSION,
    /** Service type or message subtype not implemented (ICD §10.4 code 0x0002). */
    ILLEGAL_SERVICE_OR_SUBTYPE
  }

  /**
   * One observable TC rejection (SIM-TC-006: log/queue observability;
   * SIM-REQ-UI-007: rejection frames). {@code nanos} is the simulated time
   * at which the TC was rejected.
   */
  public record Rejection(RejectReason reason, String packetHex, long nanos) {}

  /** The single APID of the simulated spacecraft (ICD §2, ADR-0003). */
  public static final int APID = 100;

  private static final Logger LOG = Logger.getLogger(PusSimulatedObsw.class.getName());
  private static final HexFormat HEX = HexFormat.of();

  private final List<Rejection> rejections = new ArrayList<>();
  private final Map<Integer, Integer> typeCounters = new HashMap<>();
  private int tmSequenceCount;
  private java.util.function.Consumer<Rejection> rejectionListener;

  @Override
  public List<byte[]> handleTc(byte[] tcPacket, long nowNanos) {
    TcPacket tc;
    try {
      tc = TcPacket.decode(tcPacket);
    } catch (PacketDecodeException e) {
      reject(RejectReason.NOT_A_PACKET, tcPacket, e.getMessage(), nowNanos);
      return List.of();
    }
    TcSecondaryHeader sec = tc.secondaryHeader();
    if (sec.pusVersion() != TcSecondaryHeader.PUS_C_VERSION) {
      reject(RejectReason.ILLEGAL_PUS_VERSION, tcPacket, "PUS version " + sec.pusVersion(), nowNanos);
      return List.of();
    }
    if (sec.serviceType() != 17 || sec.messageSubtype() != 1) {
      reject(RejectReason.ILLEGAL_SERVICE_OR_SUBTYPE, tcPacket,
          "TC(" + sec.serviceType() + "," + sec.messageSubtype() + ") not implemented", nowNanos);
      return List.of();
    }
    return List.of(buildTm(17, 2, new byte[0], nowNanos));
  }

  /**
   * Snapshot of the rejections observed so far, in order of occurrence
   * (SIM-TC-006 observability queue).
   */
  public List<Rejection> rejections() {
    return List.copyOf(rejections);
  }

  /**
   * Registers the single listener notified of every rejection as it occurs
   * (on the simulation thread), in addition to the {@link #rejections()}
   * queue. Feeds the ICD §8.2 rejection frames [SIM-REQ-UI-007].
   */
  public void onRejection(java.util.function.Consumer<Rejection> listener) {
    this.rejectionListener = listener;
  }

  /**
   * Presets the per-APID TM packet sequence count (SIM-TC-009 wrap testing).
   *
   * @param value next sequence count to use, 0–16383
   */
  public void presetTmSequenceCount(int value) {
    requireRange(value, 0, 16383, "sequence count");
    tmSequenceCount = value;
  }

  /**
   * Presets the message type counter for one (service, subtype)
   * (SIM-TC-009 wrap testing).
   *
   * @param value next counter value to use, 0–65535
   */
  public void presetMessageTypeCounter(int serviceType, int messageSubtype, int value) {
    requireRange(value, 0, 65535, "message type counter");
    typeCounters.put(typeKey(serviceType, messageSubtype), value);
  }

  private byte[] buildTm(int serviceType, int messageSubtype, byte[] appData, long nowNanos) {
    int key = typeKey(serviceType, messageSubtype);
    int typeCounter = typeCounters.getOrDefault(key, 0);
    typeCounters.put(key, (typeCounter + 1) & 0xFFFF);
    TmSecondaryHeader sec = new TmSecondaryHeader(
        2, 0, serviceType, messageSubtype, typeCounter, 0, CucTime.ofNanos(nowNanos));
    TmPacket tm = TmPacket.of(APID, tmSequenceCount, sec, appData);
    tmSequenceCount = (tmSequenceCount + 1) & 0x3FFF;
    return tm.encode();
  }

  private void reject(RejectReason reason, byte[] tcPacket, String detail, long nowNanos) {
    Rejection rejection = new Rejection(reason, HEX.formatHex(tcPacket), nowNanos);
    rejections.add(rejection);
    LOG.warning(() -> "TC rejected (" + reason + "): " + detail
        + " [" + rejection.packetHex() + "]");
    if (rejectionListener != null) {
      rejectionListener.accept(rejection);
    }
  }

  private static int typeKey(int serviceType, int messageSubtype) {
    return (serviceType << 8) | messageSubtype;
  }

  private static void requireRange(int value, int min, int max, String field) {
    if (value < min || value > max) {
      throw new IllegalArgumentException(field + " out of range [" + min + ".." + max + "]: " + value);
    }
  }
}
