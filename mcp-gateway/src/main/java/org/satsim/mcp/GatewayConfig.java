package org.satsim.mcp;

import java.net.URI;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Gateway configuration per ICD §8.4: simulator web-API base URL, TC
 * allowlist, session TC budget, ops-log and ICD file locations. Parsed from
 * command-line arguments by {@link GatewayMain}.
 *
 * <p>Allowlist entries are either a bare service type ({@code "3"} = all
 * subtypes of ST[3]) or {@code service/subtype} ({@code "17/1"}). The
 * default is the tailored TC set of the ICD (ST[3], ST[17]).
 */
record GatewayConfig(
    URI baseUrl, Set<String> allowlist, int budget, Path opsLogPath, Path icdPath) {

  static final String DEFAULT_ALLOWLIST = "3,17";
  static final int DEFAULT_BUDGET = 100;

  static GatewayConfig parse(String[] args) {
    URI url = null;
    String allow = DEFAULT_ALLOWLIST;
    int budget = DEFAULT_BUDGET;
    Path opsLog = Path.of("ops-log.jsonl");
    Path icd = Path.of("docs", "icd.md");
    for (int i = 0; i + 1 < args.length; i += 2) {
      switch (args[i]) {
        case "--url" -> url = URI.create(args[i + 1]);
        case "--allow" -> allow = args[i + 1];
        case "--budget" -> budget = Integer.parseInt(args[i + 1]);
        case "--ops-log" -> opsLog = Path.of(args[i + 1]);
        case "--icd" -> icd = Path.of(args[i + 1]);
        default -> throw new IllegalArgumentException("unknown option: " + args[i]);
      }
    }
    if (url == null) {
      throw new IllegalArgumentException(
          "usage: --url http://host:port [--allow 3,17] [--budget 100]"
              + " [--ops-log ops-log.jsonl] [--icd docs/icd.md]");
    }
    Set<String> entries = new LinkedHashSet<>();
    for (String entry : allow.split(",")) {
      if (!entry.isBlank()) {
        entries.add(entry.trim());
      }
    }
    return new GatewayConfig(url, Set.copyOf(entries), budget, opsLog, icd);
  }

  /** Allowlist check on a decoded (service, subtype) pair. */
  boolean allows(int service, int subtype) {
    return allowlist.contains(String.valueOf(service))
        || allowlist.contains(service + "/" + subtype);
  }
}
