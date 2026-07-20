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
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * JSONL ops log per ICD §8.4 [SIM-REQ-MCP-006]: one record per tool
 * invocation — including denied ones — with tool, parameters, outcome and
 * OBT. Timestamps are on-board time only; no wall clock is read.
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

  synchronized void record(String tool, Map<String, Object> params, String outcome,
      Map<String, Object> obt) {
    Map<String, Object> line = new LinkedHashMap<>();
    line.put("tool", tool);
    line.put("params", params);
    line.put("outcome", outcome);
    line.put("obt", obt);
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
