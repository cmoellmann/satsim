package org.satsim.sim.cag;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.satsim.testsupport.Requirement;
import org.satsim.testsupport.TestCase;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * SIM-TC-060 — the Category B coverage gate on {@code ops-cag}
 * [SIM-REQ-QA-004, SCR-012].
 *
 * <p>The requirement is not "100 %"; it is "100 % of the <em>reachable</em>
 * code, with every uncovered region itemized and justified". A number alone
 * cannot verify that, so this case verifies the part that can silently rot: the
 * <strong>completeness of the itemization</strong>. Every uncovered instruction
 * and branch in the JaCoCo report must fall inside a region listed in
 * {@code docs/safety/cag-safety-analysis.md} §8. Uncovered code that nobody
 * justified fails the build.
 *
 * <p>That coupling is the point. Without it, the itemization is a snapshot that
 * drifts the moment someone adds an untested branch, and the "100 % of
 * reachable" claim quietly becomes false while the ratio still passes its
 * threshold.
 *
 * <p>The remaining half of the requirement — that the threshold is enforced by
 * the build rather than merely reported — is a property of the Maven
 * configuration, demonstrated at the milestone gate by raising the threshold
 * above the achieved ratio and observing the build fail; the evidence is
 * recorded in the M1i report.
 */
class CagCoverageGateSvsTest {

  /** {@code `ClassName:100–103`} or {@code `ClassName:53`} in the §8 table. */
  private static final Pattern ITEMIZED_REGION =
      Pattern.compile("`(\\w+):(\\d+)(?:[–-](\\d+))?`");
  private static final Pattern BASELINE_ROW =
      Pattern.compile("\\|\\s*(Instruction|Branch)\\s*\\|\\s*(\\d+)\\s*\\|\\s*(\\d+)\\s*\\|");

  private static Path repoRoot() {
    // Surefire runs with the module directory as working directory; tolerate a
    // run from the repository root as well.
    Path here = Path.of("").toAbsolutePath();
    return Files.isDirectory(here.resolve("ops-cag")) ? here : here.getParent();
  }

  private record Region(String sourceFile, int firstLine, int lastLine) {
    boolean covers(int line) {
      return line >= firstLine && line <= lastLine;
    }
  }

  @Test
  @TestCase("SIM-TC-060")
  @Requirement("SIM-REQ-QA-004")
  void everyUncoveredRegionOfTheGateIsItemizedAndJustified() throws Exception {
    Path root = repoRoot();
    Path report = root.resolve("ops-cag/target/site/jacoco/jacoco.xml");
    assertTrue(Files.isRegularFile(report),
        () -> "no JaCoCo report at " + report + " — run a full ./mvnw verify, "
            + "since this case verifies the recorded coverage of ops-cag");

    List<Region> justified = itemizedRegions(root.resolve("docs/safety/cag-safety-analysis.md"));
    assertFalse(justified.isEmpty(), "safety analysis §8 lists no regions — parsing broke");

    Map<String, List<Integer>> uncovered = uncoveredLines(report);
    List<String> unjustified = new ArrayList<>();
    for (Map.Entry<String, List<Integer>> entry : uncovered.entrySet()) {
      String sourceFile = entry.getKey().replace(".java", "");
      for (int line : entry.getValue()) {
        boolean listed = justified.stream()
            .anyMatch(r -> r.sourceFile().equals(sourceFile) && r.covers(line));
        if (!listed) {
          unjustified.add(entry.getKey() + ":" + line);
        }
      }
    }

    assertTrue(unjustified.isEmpty(),
        () -> "ops-cag has uncovered code that the safety analysis §8 does not itemize: "
            + unjustified + ". Either cover it, or add it to the itemization with a "
            + "justification for why it is unreachable [SIM-REQ-QA-004].");

    // ...and the measured ratios have not decayed below the recorded baseline.
    Map<String, int[]> baseline =
        recordedBaseline(root.resolve("docs/safety/cag-safety-analysis.md"));
    Map<String, int[]> measured = bundleCounters(report);

    for (String counter : List.of("INSTRUCTION", "BRANCH")) {
      int[] recorded = baseline.get(counter);
      int[] actual = measured.get(counter);
      double recordedRatio = (double) recorded[0] / recorded[1];
      double actualRatio = (double) actual[0] / actual[1];
      assertTrue(actualRatio >= recordedRatio,
          () -> counter + " coverage " + actual[0] + "/" + actual[1]
              + " fell below the baseline recorded in the safety analysis §8 ("
              + recorded[0] + "/" + recorded[1] + ")");
    }
  }

  private static List<Region> itemizedRegions(Path safetyAnalysis) throws IOException {
    List<Region> regions = new ArrayList<>();
    for (String line : Files.readAllLines(safetyAnalysis, StandardCharsets.UTF_8)) {
      if (!line.startsWith("| U-")) {
        continue;
      }
      Matcher m = ITEMIZED_REGION.matcher(line);
      if (m.find()) {
        int first = Integer.parseInt(m.group(1 + 1));
        int last = m.group(3) == null ? first : Integer.parseInt(m.group(3));
        regions.add(new Region(m.group(1), first, last));
      }
    }
    return regions;
  }

  /** The {@code covered}/{@code total} pairs of the §8 baseline table. */
  private static Map<String, int[]> recordedBaseline(Path safetyAnalysis) throws IOException {
    Map<String, int[]> baseline = new LinkedHashMap<>();
    for (String line : Files.readAllLines(safetyAnalysis, StandardCharsets.UTF_8)) {
      Matcher m = BASELINE_ROW.matcher(line);
      if (m.find()) {
        baseline.put(m.group(1).toUpperCase(java.util.Locale.ROOT),
            new int[] {Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3))});
      }
    }
    return baseline;
  }

  private static Document parse(Path report) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    // The JaCoCo report carries a DTD reference; resolving it would reach the
    // network, which no test of this project is permitted to do.
    factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
    return factory.newDocumentBuilder().parse(report.toFile());
  }

  private static Map<String, List<Integer>> uncoveredLines(Path report) throws Exception {
    Map<String, List<Integer>> uncovered = new LinkedHashMap<>();
    NodeList sourceFiles = parse(report).getElementsByTagName("sourcefile");
    for (int i = 0; i < sourceFiles.getLength(); i++) {
      Element sourceFile = (Element) sourceFiles.item(i);
      NodeList lines = sourceFile.getElementsByTagName("line");
      for (int j = 0; j < lines.getLength(); j++) {
        Element line = (Element) lines.item(j);
        int missedInstructions = Integer.parseInt(line.getAttribute("mi"));
        int missedBranches = Integer.parseInt(line.getAttribute("mb"));
        if (missedInstructions > 0 || missedBranches > 0) {
          uncovered.computeIfAbsent(sourceFile.getAttribute("name"), k -> new ArrayList<>())
              .add(Integer.parseInt(line.getAttribute("nr")));
        }
      }
    }
    return uncovered;
  }

  private static Map<String, int[]> bundleCounters(Path report) throws Exception {
    Map<String, int[]> counters = new LinkedHashMap<>();
    Element root = parse(report).getDocumentElement();
    NodeList children = root.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      if (children.item(i) instanceof Element element
          && "counter".equals(element.getTagName())) {
        int missed = Integer.parseInt(element.getAttribute("missed"));
        int covered = Integer.parseInt(element.getAttribute("covered"));
        counters.put(element.getAttribute("type"), new int[] {covered, missed + covered});
      }
    }
    return counters;
  }
}
