package org.satsim.sim.obsw;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;
import org.satsim.pus.PacketDecodeException;
import org.satsim.pus.st3.HkCreateRequest;
import org.satsim.pus.st3.HkParameter;
import org.satsim.pus.st3.HkReport;
import org.satsim.pus.st3.HkSidList;
import org.satsim.pus.tc.TcPacket;
import org.satsim.pus.tc.TcSecondaryHeader;
import org.satsim.pus.time.CucTime;
import org.satsim.pus.tm.TmPacket;
import org.satsim.pus.tm.TmSecondaryHeader;

/**
 * The PUS-C implementation of the simulated on-board software, tailored per the ICD: single
 * APID 100 (ADR-0003), strict PUS-C (ADR-0002). M1 service set: ST[17]
 * connection test — a valid TC(17,1) yields exactly one TM(17,2)
 * [SIM-REQ-PUS-008, SIM-REQ-PUS-010]. M1a adds the ST[1] request
 * verification subset (ICD §10, SCR-002): accepted TCs get TM(1,1)/TM(1,7)
 * success reports gated by their acknowledgement flags, and rejections get a
 * TM(1,2) failure report [SIM-REQ-VER-001, SIM-REQ-VER-002, SIM-REQ-VER-003].
 * M1b adds the ST[3] housekeeping subset (ICD §9, SCR-001): TC(3,1) creates
 * report structures (disabled), TC(3,5)/TC(3,7) enable/disable periodic
 * generation atomically, and enabled structures emit TM(3,25) at every
 * collection interval of simulated time via the {@link SimulatedObsw}
 * time-event pair; the default structure SID 1 reports every second from
 * simulation start [SIM-REQ-HK-001..004].
 *
 * <p>Acceptance checks in ICD §10.2 order: CRC/structure (via
 * {@link TcPacket#decode}), then PUS version = 2, then service/subtype
 * implemented, then application data well-formed (ST[3] codecs). A
 * CRC/structure failure discards the TC silently — no attributable request
 * exists (ICD §6.3) — while the later checks each yield exactly one TM(1,2)
 * failure report in addition to the log/{@link #rejections()} observability
 * [SIM-REQ-PUS-005, SIM-REQ-PUS-006, SIM-REQ-VER-003]. ST[3] semantic errors
 * (ICD §9.1, checked in §9.2/§9.3 order) fail execution with exactly one
 * TM(1,8) and leave the housekeeping configuration unchanged (OP-3).
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

  /** Why a TC was discarded; names align with the ICD §10.4 failure codes. */
  public enum RejectReason {
    /** Structural decode failure or CRC error (ICD §6.3: silent discard, no TM). */
    NOT_A_PACKET,
    /** TC packet PUS version number is not 2 (ICD §10.4 code 0x0001). */
    ILLEGAL_PUS_VERSION,
    /** Service type or message subtype not implemented (ICD §10.4 code 0x0002). */
    ILLEGAL_SERVICE_OR_SUBTYPE,
    /** Application data length or field structure invalid (ICD §10.4 code 0x0003). */
    ILLEGAL_APPLICATION_DATA,
    /** SID 0, or TC(3,1) targeting the reserved SID 1 (ICD §10.4 code 0x0004). */
    ILLEGAL_SID,
    /** TC(3,1) with a SID that already exists (ICD §10.4 code 0x0005). */
    DUPLICATE_SID,
    /** TC(3,5)/TC(3,7) referencing a SID that does not exist (ICD §10.4 code 0x0006). */
    UNKNOWN_SID,
    /** TC(3,1) collection interval below the ICD §9.2 minimum (ICD §10.4 code 0x0007). */
    ILLEGAL_COLLECTION_INTERVAL,
    /** TC(3,1) referencing a parameter ID not defined in ICD §9.5 (ICD §10.4 code 0x0008). */
    UNKNOWN_PARAMETER
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

  /** ICD §9.2: minimum collection interval, milliseconds of simulated time. */
  private static final long MIN_COLLECTION_INTERVAL_MS = 100;

  /** ICD §9.6: the reserved default structure SID. */
  private static final int DEFAULT_SID = 1;

  private static final long UINT32_MASK = 0xFFFFFFFFL;

  /** One housekeeping report structure (ICD §9.2/§9.6): definition plus periodic state. */
  private static final class HkStructure {
    final int sid;
    final long intervalNanos;
    final List<HkParameter> parameters;
    boolean enabled;
    /** Next report due time; meaningful only while {@link #enabled}. */
    long nextDueNanos;

    HkStructure(int sid, long intervalNanos, List<HkParameter> parameters) {
      this.sid = sid;
      this.intervalNanos = intervalNanos;
      this.parameters = parameters;
    }
  }

  private final List<Rejection> rejections = new ArrayList<>();
  private final Map<Integer, Integer> typeCounters = new HashMap<>();
  /** Ascending SID order for deterministic report emission at shared instants. */
  private final TreeMap<Integer, HkStructure> hkStructures = new TreeMap<>();
  private int tmSequenceCount;
  /** HK-P001: TCs accepted (CRC and PUS version valid) since start, uint32 (ICD §9.5). */
  private long tcAcceptedCount;
  /** HK-P002: TMs emitted since start, uint32, sampled before the containing report (ICD §9.5). */
  private long tmEmittedCount;
  private java.util.function.Consumer<Rejection> rejectionListener;

  /** Creates the OBSW at simulation start with the ICD §9.6 default structure enabled. */
  public PusSimulatedObsw() {
    HkStructure defaultStructure = new HkStructure(
        DEFAULT_SID, 1000 * 1_000_000L,
        List.of(HkParameter.P001, HkParameter.P002, HkParameter.P003));
    defaultStructure.enabled = true;
    defaultStructure.nextDueNanos = defaultStructure.intervalNanos;
    hkStructures.put(DEFAULT_SID, defaultStructure);
  }

  @Override
  public List<byte[]> handleTc(byte[] tcPacket, long nowNanos) {
    TcPacket tc;
    try {
      tc = TcPacket.decode(tcPacket);
    } catch (PacketDecodeException e) {
      reject(RejectReason.NOT_A_PACKET, tcPacket, e.getMessage(), nowNanos);
      return List.of();
    }
    // ICD §10.3: request ID = packet ID (octets 0-1) + packet sequence control (octets 2-3).
    byte[] requestId = Arrays.copyOfRange(tcPacket, 0, 4);
    TcSecondaryHeader sec = tc.secondaryHeader();
    if (sec.pusVersion() != TcSecondaryHeader.PUS_C_VERSION) {
      reject(RejectReason.ILLEGAL_PUS_VERSION, tcPacket, "PUS version " + sec.pusVersion(), nowNanos);
      return List.of(buildTm(1, 2, failureReportAppData(requestId, RejectReason.ILLEGAL_PUS_VERSION), nowNanos));
    }
    // HK-P001 (ICD §9.5): counts every TC with valid CRC and PUS version,
    // independent of the later service/subtype and application data checks.
    tcAcceptedCount = (tcAcceptedCount + 1) & UINT32_MASK;
    boolean st17Ping = sec.serviceType() == 17 && sec.messageSubtype() == 1;
    boolean st3 = sec.serviceType() == 3
        && (sec.messageSubtype() == 1 || sec.messageSubtype() == 5 || sec.messageSubtype() == 7);
    if (!st17Ping && !st3) {
      reject(RejectReason.ILLEGAL_SERVICE_OR_SUBTYPE, tcPacket,
          "TC(" + sec.serviceType() + "," + sec.messageSubtype() + ") not implemented", nowNanos);
      return List.of(
          buildTm(1, 2, failureReportAppData(requestId, RejectReason.ILLEGAL_SERVICE_OR_SUBTYPE), nowNanos));
    }
    if (st3) {
      return handleSt3(tc, tcPacket, requestId, nowNanos);
    }
    List<byte[]> tms = new ArrayList<>(3);
    if (sec.ackAcceptance()) {
      tms.add(buildTm(1, 1, requestId, nowNanos));
    }
    tms.add(buildTm(17, 2, new byte[0], nowNanos));
    if (sec.ackCompletion()) {
      tms.add(buildTm(1, 7, requestId, nowNanos));
    }
    return tms;
  }

  @Override
  public long nextEventNanos() {
    long next = NO_EVENT;
    for (HkStructure structure : hkStructures.values()) {
      if (structure.enabled && structure.nextDueNanos < next) {
        next = structure.nextDueNanos;
      }
    }
    return next;
  }

  @Override
  public List<byte[]> handleTimeEvent(long nowNanos) {
    List<byte[]> tms = new ArrayList<>(1);
    for (HkStructure structure : hkStructures.values()) {
      if (structure.enabled && structure.nextDueNanos == nowNanos) {
        tms.add(buildTm(3, 25, reportAppData(structure, nowNanos), nowNanos));
        structure.nextDueNanos += structure.intervalNanos;
      }
    }
    return tms;
  }

  /**
   * TM(3,25) application data per ICD §9.4: parameter values sampled at
   * report generation, immediately before emission (ICD §9.5, HK-P002).
   */
  private byte[] reportAppData(HkStructure structure, long nowNanos) {
    List<Long> values = new ArrayList<>(structure.parameters.size());
    for (HkParameter parameter : structure.parameters) {
      values.add(switch (parameter) {
        case P001 -> tcAcceptedCount;
        case P002 -> tmEmittedCount;
        case P003 -> batteryMillivolts(nowNanos);
      });
    }
    return new HkReport(structure.sid, values).encode(structure.parameters);
  }

  /**
   * HK-P003 (ICD §9.5): synthetic battery voltage, integer-only triangle wave
   * 3500–4100 mV with 60 s period over simulated time.
   */
  private static long batteryMillivolts(long nowNanos) {
    long timeMs = nowNanos / 1_000_000L;
    long p = timeMs % 60_000L;
    return p < 30_000L ? 3500 + p / 50 : 4100 - (p - 30_000L) / 50;
  }

  /**
   * ST[3] request handling (ICD §9): application data structure check
   * (acceptance, ICD §10.2), then the §9.2/§9.3 semantic checks at execution.
   * Semantic failures yield exactly one TM(1,8) and leave the housekeeping
   * configuration unchanged (§9.1, atomic per §9.3) [SIM-REQ-HK-001,
   * SIM-REQ-HK-004, SIM-REQ-VER-003].
   */
  private List<byte[]> handleSt3(TcPacket tc, byte[] tcPacket, byte[] requestId, long nowNanos) {
    TcSecondaryHeader sec = tc.secondaryHeader();
    int subtype = sec.messageSubtype();
    HkCreateRequest create = null;
    HkSidList sidList = null;
    try {
      if (subtype == 1) {
        create = HkCreateRequest.decode(tc.applicationData());
      } else {
        sidList = HkSidList.decode(tc.applicationData());
      }
    } catch (PacketDecodeException e) {
      reject(RejectReason.ILLEGAL_APPLICATION_DATA, tcPacket, e.getMessage(), nowNanos);
      return List.of(
          buildTm(1, 2, failureReportAppData(requestId, RejectReason.ILLEGAL_APPLICATION_DATA), nowNanos));
    }
    List<byte[]> tms = new ArrayList<>(2);
    if (sec.ackAcceptance()) {
      tms.add(buildTm(1, 1, requestId, nowNanos));
    }
    RejectReason error = switch (subtype) {
      case 1 -> executeCreate(create);
      case 5 -> executeEnableDisable(sidList, true, nowNanos);
      default -> executeEnableDisable(sidList, false, nowNanos);
    };
    if (error != null) {
      reject(error, tcPacket, "TC(3," + subtype + ") semantic error", nowNanos);
      tms.add(buildTm(1, 8, failureReportAppData(requestId, error), nowNanos));
    } else if (sec.ackCompletion()) {
      tms.add(buildTm(1, 7, requestId, nowNanos));
    }
    return tms;
  }

  /** TC(3,1) semantic checks in ICD §9.2 order; creates the structure disabled on success. */
  private RejectReason executeCreate(HkCreateRequest create) {
    if (create.sid() == 0 || create.sid() == DEFAULT_SID) {
      return RejectReason.ILLEGAL_SID;
    }
    if (hkStructures.containsKey(create.sid())) {
      return RejectReason.DUPLICATE_SID;
    }
    if (create.collectionIntervalMs() < MIN_COLLECTION_INTERVAL_MS) {
      return RejectReason.ILLEGAL_COLLECTION_INTERVAL;
    }
    List<HkParameter> parameters = new ArrayList<>(create.parameterIds().size());
    for (int id : create.parameterIds()) {
      var parameter = HkParameter.byId(id);
      if (parameter.isEmpty()) {
        return RejectReason.UNKNOWN_PARAMETER;
      }
      parameters.add(parameter.get());
    }
    hkStructures.put(create.sid(),
        new HkStructure(create.sid(), create.collectionIntervalMs() * 1_000_000L, List.copyOf(parameters)));
    return null;
  }

  /**
   * TC(3,5)/TC(3,7) per ICD §9.3: atomic — an unknown SID fails the whole
   * request with no state change; enabling enabled / disabling disabled
   * structures is a no-op. Enabling at t0 schedules reports at t0 + k·interval.
   */
  private RejectReason executeEnableDisable(HkSidList sidList, boolean enable, long nowNanos) {
    for (int sid : sidList.sids()) {
      if (!hkStructures.containsKey(sid)) {
        return RejectReason.UNKNOWN_SID;
      }
    }
    for (int sid : sidList.sids()) {
      HkStructure structure = hkStructures.get(sid);
      if (enable && !structure.enabled) {
        structure.enabled = true;
        structure.nextDueNanos = nowNanos + structure.intervalNanos;
      } else if (!enable) {
        structure.enabled = false;
      }
    }
    return null;
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

  /**
   * TM(1,2)/TM(1,8) application data per ICD §10.3: request ID followed by
   * the 2-octet failure code per §10.4.
   */
  private static byte[] failureReportAppData(byte[] requestId, RejectReason reason) {
    int failureCode = failureCode(reason);
    byte[] appData = new byte[requestId.length + 2];
    System.arraycopy(requestId, 0, appData, 0, requestId.length);
    appData[requestId.length] = (byte) (failureCode >> 8);
    appData[requestId.length + 1] = (byte) failureCode;
    return appData;
  }

  /** ICD §10.4 failure codes; {@link RejectReason#NOT_A_PACKET} has none (silent discard). */
  private static int failureCode(RejectReason reason) {
    return switch (reason) {
      case ILLEGAL_PUS_VERSION -> 0x0001;
      case ILLEGAL_SERVICE_OR_SUBTYPE -> 0x0002;
      case ILLEGAL_APPLICATION_DATA -> 0x0003;
      case ILLEGAL_SID -> 0x0004;
      case DUPLICATE_SID -> 0x0005;
      case UNKNOWN_SID -> 0x0006;
      case ILLEGAL_COLLECTION_INTERVAL -> 0x0007;
      case UNKNOWN_PARAMETER -> 0x0008;
      case NOT_A_PACKET -> throw new IllegalArgumentException(
          "NOT_A_PACKET has no ICD §10.4 failure code (silent discard, §6.3)");
    };
  }

  private byte[] buildTm(int serviceType, int messageSubtype, byte[] appData, long nowNanos) {
    int key = typeKey(serviceType, messageSubtype);
    int typeCounter = typeCounters.getOrDefault(key, 0);
    typeCounters.put(key, (typeCounter + 1) & 0xFFFF);
    TmSecondaryHeader sec = new TmSecondaryHeader(
        2, 0, serviceType, messageSubtype, typeCounter, 0, CucTime.ofNanos(nowNanos));
    TmPacket tm = TmPacket.of(APID, tmSequenceCount, sec, appData);
    tmSequenceCount = (tmSequenceCount + 1) & 0x3FFF;
    // HK-P002 (ICD §9.5): every emitted TM counts, including this one — a
    // report samples the counter before this increment runs for itself.
    tmEmittedCount = (tmEmittedCount + 1) & UINT32_MASK;
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
