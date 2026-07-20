package org.satsim.mcp;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Ordered ring buffer of received ICD §8.2 {@code tm} and {@code rejection}
 * frames with monotonic cursors [SIM-REQ-MCP-003], plus the current OBT per
 * the latest {@code time} frame [SIM-REQ-MCP-004]. Blocking waits use
 * relative timeouts only — the gateway reads no wall clock (CLAUDE.md
 * rule 2 stays trivially satisfied on the ground side too).
 */
final class TmLog {

  /** One buffered frame; {@code cursor} is monotonic and never reused. */
  record Entry(long cursor, String kind, Map<String, Object> frame) {}

  /** Optional match criteria on kind and decoded service/subtype. */
  record Filter(String kind, Integer service, Integer subtype) {

    boolean matches(Entry entry) {
      if (kind != null && !kind.equals(entry.kind())) {
        return false;
      }
      if (service == null && subtype == null) {
        return true;
      }
      Object decoded = entry.frame().get("decoded");
      if (!(decoded instanceof Map<?, ?> fields)) {
        return false;
      }
      return (service == null || service.equals(intOf(fields.get("service"))))
          && (subtype == null || subtype.equals(intOf(fields.get("subtype"))));
    }

    private static Integer intOf(Object value) {
      return value instanceof Number n ? n.intValue() : null;
    }
  }

  private final int capacity;
  private final ArrayDeque<Entry> entries = new ArrayDeque<>();
  private final ReentrantLock lock = new ReentrantLock();
  private final Condition appended = lock.newCondition();
  private long nextCursor = 1;
  private volatile Map<String, Object> lastTimeFrame;

  TmLog(int capacity) {
    this.capacity = capacity;
  }

  /** Accepts any ICD §8.2 frame; buffers tm/rejection, tracks time frames. */
  void accept(Map<String, Object> frame) {
    Object kind = frame.get("kind");
    if ("time".equals(kind)) {
      lastTimeFrame = frame;
      return;
    }
    if (!"tm".equals(kind) && !"rejection".equals(kind)) {
      return;
    }
    lock.lock();
    try {
      if (entries.size() == capacity) {
        entries.removeFirst();
      }
      entries.addLast(new Entry(nextCursor++, (String) kind, frame));
      appended.signalAll();
    } finally {
      lock.unlock();
    }
  }

  /** All buffered entries with cursor &gt; {@code afterCursor} matching {@code filter}. */
  List<Entry> after(long afterCursor, Filter filter) {
    lock.lock();
    try {
      List<Entry> result = new ArrayList<>();
      for (Entry entry : entries) {
        if (entry.cursor() > afterCursor && filter.matches(entry)) {
          result.add(entry);
        }
      }
      return result;
    } finally {
      lock.unlock();
    }
  }

  /**
   * Blocks until a TM entry matching {@code filter} with cursor &gt;
   * {@code afterCursor} exists, returning it — or {@code null} on timeout.
   */
  Entry await(long afterCursor, Filter filter, long timeoutMillis)
      throws InterruptedException {
    long remainingNanos = timeoutMillis * 1_000_000L;
    lock.lock();
    try {
      while (true) {
        List<Entry> matches = after(afterCursor, filter);
        if (!matches.isEmpty()) {
          return matches.get(0);
        }
        if (remainingNanos <= 0) {
          return null;
        }
        remainingNanos = appended.awaitNanos(remainingNanos);
      }
    } finally {
      lock.unlock();
    }
  }

  /** Cursor of the most recently buffered entry (0 if none yet). */
  long latestCursor() {
    lock.lock();
    try {
      return nextCursor - 1;
    } finally {
      lock.unlock();
    }
  }

  /** Cursor of the oldest still-buffered entry (0 if none yet). */
  long firstCursor() {
    lock.lock();
    try {
      return entries.isEmpty() ? 0 : entries.peekFirst().cursor();
    } finally {
      lock.unlock();
    }
  }

  /** Current OBT fields per the latest ICD §8.2 time frame (zeros before one arrived). */
  Map<String, Object> obt() {
    Map<String, Object> frame = lastTimeFrame;
    if (frame == null) {
      return Map.of("timeCoarse", 0, "timeFine", 0, "timeSeconds", 0.0);
    }
    return Map.of(
        "timeCoarse", frame.get("timeCoarse"),
        "timeFine", frame.get("timeFine"),
        "timeSeconds", frame.get("timeSeconds"));
  }
}
