package fixture;

/** Fixture test source (not compiled): scanned by TraceabilityCheckTest. */
class FixtureTest {

  @Test
  @TestCase("SIM-TC-901")
  @Requirement("SIM-REQ-FIX-001")
  void implemented() {
  }

  @Test
  @TestCase("SIM-TC-999")
  @Requirement("SIM-REQ-FIX-999")
  void unknownIds() {
  }

  @Test
  @TestCase("SIM-TC-903")
  @Requirement("SIM-REQ-FIX-001")
  void duplicateA() {
  }

  @Test
  @TestCase("SIM-TC-903")
  @Requirement("SIM-REQ-FIX-001")
  void duplicateB() {
  }
}
