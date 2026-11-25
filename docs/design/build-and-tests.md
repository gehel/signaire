# Build, tests and quality

## Why

A small project maintained by few people needs a build that catches problems early and stays fast enough to run on
every change: quality gates in the build, tests at the right level, and measures of what users feel (speed,
accessibility).

## Design

- **Maven, multi-module**, Java 27, Spring Boot imported as a BOM, the WMF parent POM for quality gates (checkstyle,
  forbiddenapis, spotbugs with findsecbugs and sb-contrib, duplicate-finder, sortpom). The root POM overrides the tool
  versions that can't handle Java 27 or unnamed variables, and turns off what we don't use: the parent's rule against
  package cycles (ArchUnit checks them) and annotation processing (the parent runs Lombok).
- **Checkstyle rules**: the WMF ruleset, from our copy (`checkstyle.xml`, at the root): checkstyle 13.9 removed its
  `JavadocStyle` check. `SummaryJavadoc` replaces it: a summary ends with a period, and as before a Javadoc of block
  tags alone (`@param`, `@throws`) needs none.
- **Architecture rules**: an `ArchitectureTest` (ArchUnit) per module for what Maven dependencies can't say (Android
  compatibility, no Spring in extractor and export, Lucene and JPA internal, packages of core independent), and a
  `PackageCyclesTest`: no cycle between the packages of the module, test classes included.
- **No deprecation warnings**, in the build as in the tests: what is deprecated today breaks with a later version of a
  tool or of Java. The test JVMs load Mockito's agent at startup (the JDK will refuse agents loaded while running),
  enable native access (Lucene, SQLite), and in web allow the change of final fields that Spring Security's test
  support makes; the build cache hashes with SHA-256 (its XX hash uses `sun.misc.Unsafe`).
- **Tests at the lowest level that can show the behavior**:
  - unit tests everywhere, test first, JUnit 5 + AssertJ;
  - accounts services on H2;
  - web controllers in `@WebMvcTest` slices (`@WebLayerTest`) with mocked account services and a real Lucene index;
  - `signaire-it`: the CLI and web jars as processes, PostgreSQL in a container, a fake SignSuisse served locally;
    user journeys, and the check that signs are transient; the pages in Chromium in a container (`BrowserIT`,
    Selenium), with screenshots. Only this module needs Docker.
- **No network in tests**: the root POM sends HTTP(S) to a closed port (localhost allowed), `NoNetworkTest` checks it.
- **Speed**: the Maven build cache extension restores unchanged modules (tests included) locally; builds stopping
  before `package` aren't cached. Test JVMs use the quick compiler and the serial collector. CI builds everything,
  without the cache.
- **Measures** (`quality.yml`): Lighthouse CI (accessibility at 1), sitespeed.io (budget, and comparison with the
  last build of main: no timing, size or request count up by more than 10% and a slack), SonarCloud (quality gate:
  80% coverage of new code by unit tests).
- **CI artifacts**: the CLI jar, the standalone images for Linux, Windows and macOS, the screenshots of the pages.

## Decisions

- **Quality gates in the build, not only in CI**: `./mvnw verify` fails locally on what CI would reject.
- **Mocks above the database layer, containers only end to end**: Docker-free tests for every module but one; the
  test suite went from about 180 s to about 126 s, and contributors without Docker can work on everything else.
- **Coverage from unit tests only** (SonarCloud): code reached only by integration tests needs unit tests too.
- **Comparison with main rather than absolute thresholds alone**: catches regressions early while budgets stay
  loose enough for CI noise.
- **Checkstyle wins over SonarCloud** when they clash; false positives get `// NOSONAR` with their reason.
- **Package cycles checked by ArchUnit**, already in every module, rather than the parent's enforcer rule: its authors
  deprecated it, it relies on JDepend, which reads no class file newer than Java 8, and it uses an Enforcer API to be
  removed.
- **What the JDK will block is allowed explicitly where libraries need it**, as the warnings say: native access in the
  test JVMs (the jars declare it in their manifest, the standalone launcher in its options), the change of final
  fields in web's tests only. Hiding the warnings would leave the build to break with a later Java.
- **Our copy of the WMF checkstyle rules**, to follow checkstyle: an old checkstyle stops reading new Java (the
  parent's 10.16 rejects unnamed variables). The copy differs by its Javadoc check only, so the parent's other rules
  can still be followed by hand.
  - Staying on checkstyle 12.3.1, the last with `JavadocStyle`, until the WMF ruleset changes: it would stop reading
    the Java to come, as 10.16 did.
  - A summary on every Javadoc, as `SummaryJavadoc` wants by default: a Javadoc of tags alone documents a parameter
    or an exception the names don't explain; a summary there would restate the name of the method.

## Open questions

- Integration tests aren't counted in coverage (the parent POM writes the jacoco report before them).
