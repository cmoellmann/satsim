package org.satsim.testsupport.trace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.satsim.testsupport.Requirement;
import org.satsim.testsupport.TestCase;
import org.satsim.testsupport.trace.TraceabilityCheck.Finding;

class TraceabilityCheckTest {

  private static final Path FIXTURES = Path.of("src", "test", "resources", "trace");

  private static List<Finding> run(String set) throws IOException {
    Path base = FIXTURES.resolve(set);
    return TraceabilityCheck.check(
        TraceabilityCheck.parseSrs(base.resolve("srs.md")),
        TraceabilityCheck.parseSvs(base.resolve("svs.md")),
        TraceabilityCheck.scanTests(List.of(base.resolve("tests"))),
        0);
  }

  private static void assertFinding(List<Finding> findings, String code, String subject) {
    assertTrue(
        findings.stream().anyMatch(f -> f.code().equals(code) && f.message().contains(subject)),
        "expected finding " + code + " for " + subject + ", got: " + findings);
  }

  private static void assertNoFinding(List<Finding> findings, String subject) {
    assertTrue(findings.stream().noneMatch(f -> f.message().contains(subject)),
        "expected no finding for " + subject + ", got: " + findings);
  }

  /**
   * SIM-TC-014: the checker detects an SVS case without implementing test, a
   * test with unknown IDs, and an in-scope requirement without SVS coverage —
   * verified with deliberate fixtures.
   */
  @Test
  @TestCase("SIM-TC-014")
  @Requirement({"SIM-REQ-QA-001", "SIM-REQ-QA-002"})
  void detectsPlantedDefectsAndAcceptsCleanSet() throws IOException {
    List<Finding> findings = run("broken");

    // The three detections mandated by the SVS expected result:
    assertFinding(findings, "SVS-NO-TEST", "SIM-TC-902");
    assertFinding(findings, "UNKNOWN-TC", "SIM-TC-999");
    assertFinding(findings, "UNKNOWN-REQ", "SIM-REQ-FIX-999");
    assertFinding(findings, "REQ-NO-SVS", "SIM-REQ-FIX-002");

    // Clean set: no findings at all.
    assertEquals(List.of(), run("clean"));
  }

  // Untraced unit tests (engineering hygiene, SDP §5).

  @Test
  void detectsDuplicateImplementingTests() throws IOException {
    assertFinding(run("broken"), "DUP-TEST", "SIM-TC-903");
  }

  @Test
  void exemptionsStaySilent() throws IOException {
    List<Finding> findings = run("broken");
    assertNoFinding(findings, "SIM-TC-904"); // out of scope (M2 requirement)
    assertNoFinding(findings, "SIM-TC-905"); // manual case
    assertNoFinding(findings, "SIM-REQ-FIX-003"); // review-verified requirement
    assertNoFinding(findings, "SIM-REQ-FIX-004"); // out-of-scope requirement
  }

  @Test
  void errorsSortBeforeWarnings() throws IOException {
    List<Finding> findings = run("broken");
    int lastError = -1;
    int firstWarn = findings.size();
    for (int i = 0; i < findings.size(); i++) {
      if (findings.get(i).error()) {
        lastError = i;
      } else {
        firstWarn = Math.min(firstWarn, i);
      }
    }
    assertTrue(lastError < firstWarn, "errors must precede warnings: " + findings);
  }
}
