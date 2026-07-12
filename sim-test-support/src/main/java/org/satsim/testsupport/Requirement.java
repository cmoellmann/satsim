package org.satsim.testsupport;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Links a validation test to one or more SRS requirement IDs (docs/srs.md),
 * e.g. {@code @Requirement({"SIM-REQ-PUS-001"})}. Parsed by the CI
 * traceability tooling (SDP §5).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Requirement {
  String[] value();
}
