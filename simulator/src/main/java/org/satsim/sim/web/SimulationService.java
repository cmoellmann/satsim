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
import org.satsim.sim.obsw.PusSpacecraftApplication;
import org.satsim.sim.time.SimulationScheduler;
import org.springframework.stereotype.Service;

/**
 * Bridge between the web API and the simulation: serializes all access to the
 * (single-threaded, ADR-0006) {@link SimulationScheduler} onto one dedicated
 * simulation thread. TCs submitted via REST are encoded (structured compose)
 * or passed verbatim (raw hex, so negative vectors can be injected) and enter
 * the simulation at the current simulated time; TM is decoded, serialized to
 * JSON and pushed to the WebSocket broadcaster [SIM-REQ-UI-003].
 *
 * <p>Time advances only via {@link #advanceBy(long)}, called by the pacing
 * policy; this class never reads the wall clock [SIM-REQ-TIME-001]. Ground TC
 * sequence counts are counted here per APID (ICD §2: TC counted by ground)
 * and wrap at 16383.
 */
@Service
public final class SimulationService {

  private static final Logger LOG = Logger.getLogger(SimulationService.class.getName());
  private static final HexFormat HEX = HexFormat.of();
  private static final long SUBMIT_TIMEOUT_SECONDS = 5;

  private final SimulationScheduler scheduler;
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

  public SimulationService(SimulationScheduler scheduler, TmWebSocketHandler tmBroadcaster) {
    this.scheduler = scheduler;
    this.tmBroadcaster = tmBroadcaster;
  }

  @PostConstruct
  void start() {
    onSimThread(() -> {
      scheduler.onTm(this::publishTm);
      scheduler.start();
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
   * packet at the current simulated time, and returns the injected packet's
   * hex. Raw hex is injected verbatim — deliberately without validation — so
   * negative vectors exercise the spacecraft-side rejection path.
   */
  public String sendTc(TcSubmission submission) {
    return onSimThread(() -> {
      byte[] packet = toPacket(submission, true);
      scheduler.injectTc(packet);
      return HEX.formatHex(packet);
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
   * Advances simulated time by {@code deltaNanos} on the simulation thread.
   * Called by the interactive pacing policy.
   */
  public void advanceBy(long deltaNanos) {
    simThread.execute(() -> scheduler.advanceBy(deltaNanos));
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
    return TcPacket.of(PusSpacecraftApplication.APID, sequenceCount, sec, appData).encode();
  }

  private void publishTm(byte[] tmPacket) {
    TmFrame.Decoded decoded = null;
    try {
      TmPacket tm = TmPacket.decode(tmPacket);
      decoded = TmFrame.Decoded.of(tm);
    } catch (PacketDecodeException e) {
      LOG.severe(() -> "emitted TM failed to decode: " + e.getMessage());
    }
    try {
      tmBroadcaster.broadcast(
          objectMapper.writeValueAsString(new TmFrame(HEX.formatHex(tmPacket), decoded)));
    } catch (JsonProcessingException e) {
      LOG.severe(() -> "TM frame JSON serialization failed: " + e.getMessage());
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
