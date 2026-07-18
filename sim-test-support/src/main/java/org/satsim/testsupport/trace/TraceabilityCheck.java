package org.satsim.testsupport.trace;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Traceability consistency check (SDP §5, [SIM-REQ-QA-001, SIM-REQ-QA-002]):
 * cross-checks the SRS requirement table, the SVS test case table, and the
 * {@code @TestCase}/{@code @Requirement} annotations in test sources.
 *
 * <p>Detected findings:
 * <ul>
 *   <li>{@code UNKNOWN-TC} — test annotated with a test case ID not in the SVS (error)</li>
 *   <li>{@code UNKNOWN-REQ} — test annotated with a requirement ID not in the SRS (error)</li>
 *   <li>{@code SVS-UNKNOWN-REQ} — SVS case referencing a requirement ID not in the SRS (error)</li>
 *   <li>{@code DUP-TEST} — more than one implementing test for one SVS case (error)</li>
 *   <li>{@code SVS-NO-TEST} — in-scope automated SVS case without implementing test</li>
 *   <li>{@code REQ-NO-SVS} — in-scope test-verified requirement not referenced by any SVS case</li>
 *   <li>{@code REQ-REVIEW-FAIL} — in-scope review-verified requirement with a recorded
 *       reviewed-FAIL verdict (error)</li>
 *   <li>{@code REQ-NO-REVIEW} — in-scope review-verified requirement without a recorded
 *       review verdict in the milestone test reports (ACT-004)</li>
 * </ul>
 *
 * <p>Scope: an SVS case is in scope of milestone N if the highest milestone of
 * its referenced requirements is &le; N. Requirements verified without test
 * (verification column lacking "T") are exempt from {@code REQ-NO-SVS};
 * manual SVS cases (type M) are exempt from {@code SVS-NO-TEST} (their
 * verdicts are recorded in the milestone report, SDP §5).
 *
 * <p>Errors always fail the run; the coverage findings (including
 * {@code REQ-NO-REVIEW}) fail only in {@code --gate} mode (SDP §5: build
 * warning, failure at milestone gate). Review verdicts are read cumulatively
 * from all {@code docs/test-reports/M*-report.md} files whose milestone label
 * is within scope, so a verdict recorded at an earlier gate (e.g. M0) still
 * counts at later gates; later reports override earlier ones per requirement.
 * JDK-only, no third-party dependencies (CLAUDE.md rule 8).
 */
public final class TraceabilityCheck {

  /** SRS requirement: ID, verification methods (e.g. "T", "R+A"), milestone label (e.g. "M1b"). */
  public record Req(String id, String verification, String milestone) {}

  /** SVS test case: ID, referenced requirement IDs, automated (A) vs manual (M). */
  public record SvsCase(String id, List<String> reqIds, boolean automated) {
    public SvsCase {
      reqIds = List.copyOf(reqIds);
    }
  }

  /** A test method carrying traceability annotations. */
  public record TestMethod(String testCaseId, List<String> reqIds, String location) {
    public TestMethod {
      reqIds = List.copyOf(reqIds);
    }
  }

  /** A finding; {@code error} findings fail even outside gate mode. */
  public record Finding(String code, String message, boolean error) {
    @Override
    public String toString() {
      return (error ? "ERROR" : "WARN") + " [" + code + "] " + message;
    }
  }

  private static final Pattern SRS_ROW =
      Pattern.compile("^\\|\\s*(SIM-REQ-[A-Z]+-\\d+)\\s*\\|");
  private static final Pattern SVS_ROW =
      Pattern.compile("^\\|\\s*(SIM-TC-\\d+)\\s*\\|");
  private static final Pattern REQ_ID = Pattern.compile("SIM-REQ-[A-Z]+-\\d+");
  private static final Pattern TESTCASE_ANNOTATION =
      Pattern.compile("@TestCase\\(\"([^\"]+)\"\\)");
  private static final Pattern REQUIREMENT_ANNOTATION =
      Pattern.compile("@Requirement\\(([^)]*)\\)");
  private static final Pattern QUOTED = Pattern.compile("\"([^\"]+)\"");
  private static final Pattern METHOD_DECL =
      Pattern.compile("^\\s*(?:public\\s+|private\\s+|protected\\s+)?\\w[\\w<>\\[\\]]*\\s+\\w+\\s*\\(");

  private static final Pattern MILESTONE_LABEL = Pattern.compile("M(\\d+)([a-i])?");
  private static final Pattern REVIEW_VERDICT = Pattern.compile(
      "(SIM-REQ-[A-Z]+-\\d+).*?reviewed-(PASS|FAIL)", Pattern.CASE_INSENSITIVE);
  private static final Pattern REPORT_FILE = Pattern.compile("(M\\d+[a-i]?)-report\\.md");

  private TraceabilityCheck() {
  }

  /**
   * Ordinal for milestone labels including inserted increments (SCR-001):
   * {@code M<n>} → n·10, {@code M<n><letter>} → n·10 + letter offset, so
   * M0 &lt; M1 &lt; M1b &lt; M2. Letter suffixes a–i keep ordinals below the
   * next numbered milestone.
   */
  public static int milestoneOrdinal(String label) {
    Matcher m = MILESTONE_LABEL.matcher(label);
    if (!m.matches()) {
      throw new IllegalArgumentException("invalid milestone label: " + label);
    }
    int suffix = m.group(2) == null ? 0 : m.group(2).charAt(0) - 'a' + 1;
    return Integer.parseInt(m.group(1)) * 10 + suffix;
  }

  /** Parses SRS requirement rows: {@code | ID | text | category | verification | milestone | refs |}. */
  public static Map<String, Req> parseSrs(Path srsFile) throws IOException {
    Map<String, Req> reqs = new HashMap<>();
    for (String line : Files.readAllLines(srsFile, StandardCharsets.UTF_8)) {
      if (!SRS_ROW.matcher(line).find()) {
        continue;
      }
      String[] cols = line.split("\\|");
      String id = cols[1].trim();
      String verification = cols[4].trim();
      String milestone = cols[5].trim();
      milestoneOrdinal(milestone); // fail fast on malformed labels
      reqs.put(id, new Req(id, verification, milestone));
    }
    return reqs;
  }

  /** Parses SVS case rows: {@code | ID | req refs | title | A/M | expected |}. */
  public static Map<String, SvsCase> parseSvs(Path svsFile) throws IOException {
    Map<String, SvsCase> cases = new HashMap<>();
    for (String line : Files.readAllLines(svsFile, StandardCharsets.UTF_8)) {
      if (!SVS_ROW.matcher(line).find()) {
        continue;
      }
      String[] cols = line.split("\\|");
      String id = cols[1].trim();
      List<String> reqIds = new ArrayList<>();
      Matcher m = REQ_ID.matcher(cols[2]);
      while (m.find()) {
        reqIds.add(m.group());
      }
      boolean automated = cols[4].trim().equals("A");
      cases.put(id, new SvsCase(id, reqIds, automated));
    }
    return cases;
  }

  /**
   * Parses recorded review verdicts from a milestone test report: any line
   * containing a requirement ID followed by {@code reviewed-PASS} or
   * {@code reviewed-FAIL} (case-insensitive; matches both the §5 verdict
   * headings and traceability-matrix rows, see docs/test-reports/). Returns
   * requirement ID → normalized verdict ("PASS"/"FAIL").
   */
  public static Map<String, String> parseReviewVerdicts(Path reportFile) throws IOException {
    Map<String, String> verdicts = new HashMap<>();
    for (String line : Files.readAllLines(reportFile, StandardCharsets.UTF_8)) {
      Matcher m = REVIEW_VERDICT.matcher(line);
      while (m.find()) {
        verdicts.put(m.group(1), m.group(2).toUpperCase(java.util.Locale.ROOT));
      }
    }
    return verdicts;
  }

  /**
   * Collects review verdicts cumulatively from all
   * {@code M<label>-report.md} files in {@code reportsDir} whose milestone
   * ordinal is &le; the scope ordinal, in ascending milestone order (later
   * reports override earlier verdicts per requirement). An absent directory
   * yields no verdicts.
   */
  public static Map<String, String> collectReviewVerdicts(Path reportsDir, String milestone)
      throws IOException {
    Map<String, String> verdicts = new HashMap<>();
    if (!Files.isDirectory(reportsDir)) {
      return verdicts;
    }
    record Report(int ordinal, Path file) {}
    List<Report> reports = new ArrayList<>();
    try (Stream<Path> files = Files.list(reportsDir)) {
      for (Path file : files.toList()) {
        Matcher m = REPORT_FILE.matcher(file.getFileName().toString());
        if (m.matches() && milestoneOrdinal(m.group(1)) <= milestoneOrdinal(milestone)) {
          reports.add(new Report(milestoneOrdinal(m.group(1)), file));
        }
      }
    }
    reports.sort((a, b) -> Integer.compare(a.ordinal(), b.ordinal()));
    for (Report report : reports) {
      verdicts.putAll(parseReviewVerdicts(report.file()));
    }
    return verdicts;
  }

  /**
   * Scans {@code .java} files under the given directories for annotation
   * clusters: a {@code @TestCase} and the {@code @Requirement} annotations
   * immediately preceding the same method declaration.
   */
  public static List<TestMethod> scanTests(List<Path> testSourceDirs) throws IOException {
    List<TestMethod> methods = new ArrayList<>();
    for (Path dir : testSourceDirs) {
      if (!Files.isDirectory(dir)) {
        continue;
      }
      try (Stream<Path> files = Files.walk(dir)) {
        files.filter(p -> p.toString().endsWith(".java"))
            .sorted()
            .forEach(p -> scanFile(p, methods));
      } catch (UncheckedIOException e) {
        throw e.getCause();
      }
    }
    return methods;
  }

  private static void scanFile(Path file, List<TestMethod> out) {
    List<String> lines;
    try {
      lines = Files.readAllLines(file, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    String pendingTc = null;
    List<String> pendingReqs = new ArrayList<>();
    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i);
      Matcher tc = TESTCASE_ANNOTATION.matcher(line);
      if (tc.find()) {
        pendingTc = tc.group(1);
        continue;
      }
      Matcher req = REQUIREMENT_ANNOTATION.matcher(line);
      if (req.find()) {
        Matcher quoted = QUOTED.matcher(req.group(1));
        while (quoted.find()) {
          pendingReqs.add(quoted.group(1));
        }
        continue;
      }
      if (line.trim().startsWith("@")) {
        continue;
      }
      if (METHOD_DECL.matcher(line).find()) {
        if (pendingTc != null) {
          out.add(new TestMethod(pendingTc, List.copyOf(pendingReqs),
              file.getFileName() + ":" + (i + 1)));
        }
        pendingTc = null;
        pendingReqs.clear();
      }
    }
  }

  /**
   * Runs all consistency checks for the given milestone scope (label, e.g.
   * "M1b"). {@code reviewVerdicts} maps requirement IDs to recorded review
   * verdicts ("PASS"/"FAIL"), see {@link #collectReviewVerdicts} (ACT-004).
   */
  public static List<Finding> check(Map<String, Req> reqs, Map<String, SvsCase> cases,
      List<TestMethod> tests, String milestone, Map<String, String> reviewVerdicts) {
    int scopeOrdinal = milestoneOrdinal(milestone);
    List<Finding> findings = new ArrayList<>();

    Map<String, List<TestMethod>> testsByCase = new HashMap<>();
    for (TestMethod t : tests) {
      testsByCase.computeIfAbsent(t.testCaseId(), k -> new ArrayList<>()).add(t);
      if (!cases.containsKey(t.testCaseId())) {
        findings.add(new Finding("UNKNOWN-TC",
            t.location() + " @TestCase(\"" + t.testCaseId() + "\") not defined in SVS", true));
      }
      for (String reqId : t.reqIds()) {
        if (!reqs.containsKey(reqId)) {
          findings.add(new Finding("UNKNOWN-REQ",
              t.location() + " @Requirement(\"" + reqId + "\") not defined in SRS", true));
        }
      }
    }

    Set<String> coveredReqs = new HashSet<>();
    for (SvsCase c : cases.values()) {
      int caseOrdinal = 0;
      String caseMilestone = "M0";
      for (String reqId : c.reqIds()) {
        Req req = reqs.get(reqId);
        if (req == null) {
          findings.add(new Finding("SVS-UNKNOWN-REQ",
              c.id() + " references " + reqId + " which is not defined in SRS", true));
        } else if (milestoneOrdinal(req.milestone()) > caseOrdinal) {
          caseOrdinal = milestoneOrdinal(req.milestone());
          caseMilestone = req.milestone();
        }
        coveredReqs.add(reqId);
      }
      List<TestMethod> impls = testsByCase.getOrDefault(c.id(), List.of());
      if (impls.size() > 1) {
        findings.add(new Finding("DUP-TEST",
            c.id() + " has " + impls.size() + " implementing tests (exactly one allowed)", true));
      }
      if (c.automated() && caseOrdinal <= scopeOrdinal && impls.isEmpty()) {
        findings.add(new Finding("SVS-NO-TEST",
            c.id() + " (scope " + caseMilestone + ") has no implementing test", false));
      }
    }

    for (Req req : reqs.values()) {
      if (milestoneOrdinal(req.milestone()) <= scopeOrdinal && req.verification().contains("T")
          && !coveredReqs.contains(req.id())) {
        findings.add(new Finding("REQ-NO-SVS",
            req.id() + " (" + req.milestone() + ", verification " + req.verification()
                + ") is not referenced by any SVS case", false));
      }
      if (milestoneOrdinal(req.milestone()) <= scopeOrdinal && req.verification().contains("R")) {
        String verdict = reviewVerdicts.get(req.id());
        if (verdict == null) {
          findings.add(new Finding("REQ-NO-REVIEW",
              req.id() + " (" + req.milestone() + ", verification " + req.verification()
                  + ") has no recorded review verdict in the milestone test reports", false));
        } else if (verdict.equals("FAIL")) {
          findings.add(new Finding("REQ-REVIEW-FAIL",
              req.id() + " has a recorded reviewed-FAIL verdict", true));
        }
      }
    }

    findings.sort((a, b) -> Boolean.compare(b.error(), a.error()));
    return findings;
  }

  /**
   * CLI: {@code --root <dir> --milestone M<n> [--gate]}. Scans all
   * {@code *}{@code /src/test/java} directories below the root. Exit code 1 on
   * any error finding, or on any finding at all in gate mode.
   */
  public static void main(String[] args) throws IOException {
    Path root = Path.of(".");
    String milestone = "M0";
    boolean gate = false;
    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "--root" -> root = Path.of(args[++i]);
        case "--milestone" -> milestone = args[++i];
        case "--gate" -> gate = true;
        default -> throw new IllegalArgumentException("unknown argument: " + args[i]);
      }
    }
    milestoneOrdinal(milestone); // fail fast on malformed labels

    List<Path> testDirs;
    try (Stream<Path> walk = Files.walk(root, 4)) {
      testDirs = walk.filter(p -> p.endsWith(Path.of("src", "test", "java"))).toList();
    }
    List<Finding> findings = check(
        parseSrs(root.resolve("docs/srs.md")),
        parseSvs(root.resolve("docs/svs.md")),
        scanTests(testDirs),
        milestone,
        collectReviewVerdicts(root.resolve("docs/test-reports"), milestone));

    findings.forEach(f -> System.out.println(f.toString()));
    boolean fail = findings.stream().anyMatch(Finding::error) || (gate && !findings.isEmpty());
    System.out.println("Traceability check " + milestone + (gate ? " (gate)" : "")
        + ": " + findings.size() + " finding(s) -> " + (fail ? "FAIL" : "OK"));
    if (fail) {
      System.exit(1);
    }
  }
}
