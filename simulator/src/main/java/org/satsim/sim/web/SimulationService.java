package org.satsim.sim.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.HexFormat;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;
import org.satsim.pus.PacketDecodeException;
import org.satsim.pus.tc.TcPacket;
import org.satsim.pus.tc.TcSecondaryHeader;
import org.satsim.pus.tm.TmPacket;
import org.satsim.sim.obsw.PusSimulatedObsw;
import org.satsim.sim.time.SimulationScheduler;
import org.springframework.stereotype.Service;

/**
 * Bridge between the web API and the simulation: serializes all access to the
 * (single-threaded, ADR-0006) {@link SimulationScheduler} onto one dedicated
 * simulation thread. TCs submitted via REST are encoded (structured compose)
 * or passed verbatim (raw hex, so negative vectors can be injected) and enter
 * the simulation at the current simulated time; TM, time and rejection frames
 * (ICD §8.2) are serialized to JSON and pushed to the WebSocket broadcaster
 * [SIM-REQ-UI-003, SIM-REQ-UI-005, SIM-REQ-UI-007].
 *
 * <p>Time advances only via {@link #advanceBy(long)}, called by the pacing
 * policy; this class never reads the wall clock [SIM-REQ-TIME-001]. Time
 * frames are published at the ICD §8.2 quantum of 100 ms <em>simulated</em>
 * time: advances are chunked at quantum boundaries, so the cadence is
 * deterministic regardless of how callers slice their advances. Ground TC
 * sequence counts are counted here per APID (ICD §2: TC counted by ground)
 * and wrap at 16383.
 */
@Service
public final class SimulationService {

  private static final Logger LOG = Logger.getLogger(SimulationService.class.getName());
  private static final HexFormat HEX = HexFormat.of();
  private static final long SUBMIT_TIMEOUT_SECONDS = 5;

  /** Time-frame publication quantum in simulated nanoseconds (ICD §8.2). */
  private static final long TIME_FRAME_QUANTUM_NANOS = 100_000_000L;

  private final SimulationScheduler scheduler;
  private final PusSimulatedObsw obsw;
  private final TmWebSocketHandler tmBroadcaster;
  /** Own instance: TM frames are plain records, no context-wide customization needed. */
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final ExecutorService simThread =
      Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "sim-master");
        thread.setDaemon(true);
        return thread;
      });

  private int groundSequenceCount;
  /** Simulated time of the next due time frame; touched only on the sim thread. */
  private long nextTimeFrameNanos;

  public SimulationService(
      SimulationScheduler scheduler, PusSimulatedObsw obsw, TmWebSocketHandler tmBroadcaster) {
    this.scheduler = scheduler;
    this.obsw = obsw;
    this.tmBroadcaster = tmBroadcaster;
  }

  @PostConstruct
  void start() {
    tmBroadcaster.onSessionConnect(session ->
        simThread.execute(() ->
            tmBroadcaster.sendTo(session, toJson(TimeFrame.ofNanos(scheduler.clock().nanos())))));
    onSimThread(() -> {
      scheduler.onTm(this::publishTm);
      obsw.onRejection(rejection -> broadcast(RejectionFrame.of(rejection)));
      scheduler.start();
      nextTimeFrameNanos = scheduler.clock().nanos() + TIME_FRAME_QUANTUM_NANOS;
      return null;
    });
  }

  @PreDestroy
  void shutdown() {
    simThread.shutdown();
    try {
      if (!simThread.awaitTermination(SUBMIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        simThread.shutdownNow();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      simThread.shutdownNow();
    }
  }

  /**
   * Encodes (structured) or parses (raw hex) the submission, injects the
   * packet at the current simulated time, and returns the ICD §8.1 response:
   * hex, injection OBT, sequence count and decoded fields (or the first
   * failed §6.3 check for undecodable raw injections) [SIM-REQ-UI-006]. Raw
   * hex is injected verbatim — deliberately without validation — so negative
   * vectors exercise the spacecraft-side rejection path.
   */
  public TcSendResponse sendTc(TcSubmission submission) {
    return onSimThread(() -> {
      byte[] packet = toPacket(submission, true);
      scheduler.injectTc(packet);
      Integer sequenceCount = null;
      TcSendResponse.Decoded decoded = null;
      String decodeError = null;
      try {
        TcPacket tc = TcPacket.decode(packet);
        decoded = TcSendResponse.Decoded.of(tc);
        sequenceCount = tc.primaryHeader().sequenceCount();
      } catch (PacketDecodeException e) {
        decodeError = e.reason().name();
      }
      return TcSendResponse.of(
          HEX.formatHex(packet), scheduler.clock().nanos(), sequenceCount, decoded, decodeError);
    });
  }

  /**
   * Returns the hex the submission would produce, without injecting and
   * without consuming a ground sequence count (frontend preview,
   * [SIM-REQ-UI-004]).
   */
  public String previewTc(TcSubmission submission) {
    return onSimThread(() -> HEX.formatHex(toPacket(submission, false)));
  }

  /**
   * Advances simulated time by {@code deltaNanos} on the simulation thread,
   * chunked at the time-frame quantum boundaries so a time frame is published
   * for every completed 100 ms of simulated time (ICD §8.2,
   * [SIM-REQ-UI-005]). Called by the interactive pacing policy.
   */
  public void advanceBy(long deltaNanos) {
    simThread.execute(() -> {
      long remaining = deltaNanos;
      while (remaining > 0) {
        long step = Math.min(remaining, nextTimeFrameNanos - scheduler.clock().nanos());
        scheduler.advanceBy(step);
        remaining -= step;
        if (scheduler.clock().nanos() >= nextTimeFrameNanos) {
          broadcast(TimeFrame.ofNanos(scheduler.clock().nanos()));
          nextTimeFrameNanos += TIME_FRAME_QUANTUM_NANOS;
        }
      }
    });
  }

  private byte[] toPacket(TcSubmission submission, boolean consumeSequenceCount) {
    if (submission.hex() != null) {
      return HEX.parseHex(submission.hex().replaceAll("\\s", "").toLowerCase());
    }
    if (submission.service() == null || submission.subtype() == null) {
      throw new IllegalArgumentException("either hex or service+subtype must be given");
    }
    int ackFlags = submission.ackFlags() == null ? 0 : submission.ackFlags();
    byte[] appData = submission.appDataHex() == null
        ? new byte[0]
        : HEX.parseHex(submission.appDataHex().replaceAll("\\s", "").toLowerCase());
    TcSecondaryHeader sec = new TcSecondaryHeader(
        TcSecondaryHeader.PUS_C_VERSION, ackFlags, submission.service(), submission.subtype(), 0);
    int sequenceCount = groundSequenceCount;
    if (consumeSequenceCount) {
      groundSequenceCount = (groundSequenceCount + 1) & 0x3FFF;
    }
    return TcPacket.of(PusSimulatedObsw.APID, sequenceCount, sec, appData).encode();
  }

  private void publishTm(byte[] tmPacket) {
    TmFrame.Decoded decoded = null;
    try {
      TmPacket tm = TmPacket.decode(tmPacket);
      decoded = TmFrame.Decoded.of(tm);
    } catch (PacketDecodeException e) {
      LOG.severe(() -> "emitted TM failed to decode: " + e.getMessage());
    }
    broadcast(new TmFrame(HEX.formatHex(tmPacket), decoded));
  }

  private void broadcast(Object frame) {
    tmBroadcaster.broadcast(toJson(frame));
  }

  private String toJson(Object frame) {
    try {
      return objectMapper.writeValueAsString(frame);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("frame JSON serialization failed", e);
    }
  }

  private <T> T onSimThread(java.util.concurrent.Callable<T> action) {
    try {
      return simThread.submit(action).get(SUBMIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrupted while waiting for simulation thread", e);
    } catch (TimeoutException e) {
      throw new IllegalStateException("simulation thread did not respond in time", e);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof RuntimeException runtime) {
        throw runtime;
      }
      throw new IllegalStateException("simulation task failed", e.getCause());
    }
  }
}
