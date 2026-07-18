# SatSim Software Reuse File (SRF)

- Configuration item: SATSIM-SRF, Issue 1 (draft)
- Purpose: register of all reused third-party software (runtime, test, and
  build dependencies) with license and distribution impact, per tailored
  ECSS-Q-ST-80C. Maintained under SDP §6 control 6: no dependency is added,
  removed, or upgraded without prior human approval; every change to the
  dependency set updates this file in the same PR.
- Scope legend: **runtime** = linked into distributed artifacts;
  **test** = test classpath only, not distributed; **build** = build/CI
  tooling only, not distributed.

## 1. Approved dependencies

| Component | Version | Scope | License (SPDX) | Distribution impact | Approved |
|---|---|---|---|---|---|
| JUnit 5 (junit-bom, junit-jupiter) | 5.10.2 | test | EPL-2.0 | None (test scope, not distributed) | 2026-07-12, C. Möllmann (bootstrap) |
| Apache Maven | 3.9.11 (pinned via wrapper) | build | Apache-2.0 | None (build tool) | 2026-07-12, C. Möllmann (bootstrap) |
| Maven Wrapper (mvnw scripts, only-script) | 3.3.4 | build | Apache-2.0 | Scripts committed to repo under their Apache-2.0 header | 2026-07-12, C. Möllmann (merge of PR #3) |
| maven-surefire-plugin | 3.2.5 | build | Apache-2.0 | None (build plugin) | 2026-07-12, C. Möllmann (bootstrap) |
| maven-compiler-plugin | 3.13.0 | build | Apache-2.0 | None (build plugin) | 2026-07-12, C. Möllmann (merge of PR #3) |
| actions/checkout | v4 | build (CI) | MIT | None (CI infrastructure) | 2026-07-12, C. Möllmann (merge of PR #3) |
| actions/setup-java | v4 | build (CI) | MIT | None (CI infrastructure) | 2026-07-12, C. Möllmann (merge of PR #3) |
| Eclipse Temurin JDK (CI runner) | 21 | build (CI) | GPL-2.0-only WITH Classpath-exception-2.0 | None (CI toolchain, not redistributed) | 2026-07-12, C. Möllmann (merge of PR #3) |
| JDK (Java SE) | 21 | runtime platform | per distribution (e.g. OpenJDK: GPL-2.0-only WITH Classpath-exception-2.0) | None with Classpath Exception; JDK itself is not redistributed | 2026-07-12, C. Möllmann (bootstrap) |
| maven-checkstyle-plugin | 3.6.0 | build | Apache-2.0 | None (build plugin) | 2026-07-18, C. Möllmann (merge of PR #11) |
| Checkstyle (tool, plugin dependency) | 10.21.1 | build | LGPL-2.1-or-later | None — executes at build time only; not linked into or distributed with SatSim | 2026-07-18, C. Möllmann (merge of PR #11) |
| spotbugs-maven-plugin | 4.8.6.6 | build | Apache-2.0 | None (build plugin) | 2026-07-18, C. Möllmann (merge of PR #11) |
| SpotBugs (tool, resolved by plugin) | 4.8.6 | build | LGPL-2.1-only | None — executes at build time only; not linked into or distributed with SatSim | 2026-07-18, C. Möllmann (merge of PR #11) |

Default Maven core plugins (compiler, resources, jar, install, deploy) are
Apache-2.0 and covered by the Apache Maven entry; versions are inherited from
the Maven distribution unless pinned in `pom.xml`.

## 2. Planned dependencies (approval pending — do not add before approval)

| Component | Anticipated scope | License (SPDX) | Notes | Milestone |
|---|---|---|---|---|
| Spring Boot (web, websocket) | runtime (`simulator` only) | Apache-2.0 | Permissive; no copyleft obligations beyond notice retention | M0/M1 |

## 3. Open licensing items

| ID | Item | Status |
|---|---|---|
| SRF-OPEN-1 | SatSim itself has no `LICENSE` file yet. An outbound license must be chosen before public distribution (planned per README). All current and planned dependencies above are compatible with any common outbound choice (Apache-2.0, MIT, EPL, proprietary) since copyleft components are test/build scope only. | open |

## 4. Change log

| Issue | Date | Change |
|---|---|---|
| 1 (draft) | 2026-07-12 | Initial register of bootstrap dependencies; planned M0/M1 entries. |
| 1 (draft) | 2026-07-12 | CI toolchain added (GitHub Actions checkout/setup-java, Temurin 21); maven-compiler-plugin 3.13.0 pinned (build fix). |
| 1 (draft) | 2026-07-12 | Maven Wrapper 3.3.4 added; Maven pinned to 3.9.11 project-locally. |
