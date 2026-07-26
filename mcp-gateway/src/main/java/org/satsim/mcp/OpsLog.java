package org.satsim.mcp;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import org.satsim.cag.GateDecision;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * JSONL ops log per ICD §8.4 [SIM-REQ-MCP-006, SIM-REQ-CAG-006]: one record per
 * tool invocation — forwarded, rejected and held alike — with tool, parameters,
 * outcome and OBT, and for every authorized injection the gate's decision, its
 * reason and the assigned authority tier. Timestamps are on-board time only; no
 * wall clock is read.
 *
 * <p>For the Category B argument this file is not a convenience: it is the
 * evidence that the gate decided what it was specified to decide (ADR-0007 C6).
 */
final class OpsLog implements AutoCloseable {

  private final ObjectMapper json;
  private final BufferedWriter writer;

  OpsLog(Path path, ObjectMapper json) throws IOException {
    this.json = json;
    Path parent = path.getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    this.writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
  }

  /**
   * Appends one record.
   *
   * @param decision the gate's verdict, or {@code null} for a tool that
   *     authorized no injection ({@code preview_tc}, the read-only tools)
   */
  synchronized void record(String tool, Map<String, Object> params, String outcome,
      Map<String, Object> obt, GateDecision decision) {
    Map<String, Object> line = new LinkedHashMap<>();
    line.put("tool", tool);
    line.put("params", params);
    line.put("outcome", outcome);
    line.put("obt", obt);
    if (decision != null) {
      line.put("gateDecision", decision.outcomeName());
      line.put("gateReason", decision.reason());
      // Absent where classification did not complete — undecodable octets have
      // no tier, and saying so is more honest than inventing one.
      if (decision.tier() != null) {
        line.put("authorityTier", decision.tier().name());
      }
      if (decision.token() != null) {
        line.put("confirmationToken", decision.token());
      }
      if (!decision.ignoredConfirmations().isEmpty()) {
        line.put("ignoredConfirmations", decision.ignoredConfirmations());
        line.put("ignoredConfirmationsReason",
            "recorded tokens matching no pending hold; discarded unhonoured");
      }
    }
    try {
      writer.write(json.writeValueAsString(line));
      writer.newLine();
      writer.flush();
    } catch (IOException e) {
      throw new UncheckedIOException("ops log write failed", e);
    }
  }

  @Override
  public synchronized void close() throws IOException {
    writer.close();
  }
}
