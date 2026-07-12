package org.satsim.testsupport;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Links a validation test to its SVS test case ID (docs/svs.md),
 * e.g. {@code @TestCase("SIM-TC-003")}. Exactly one implementing test method
 * per automated SVS case; consistency enforced by CI (SDP §5).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface TestCase {
  String value();
}
