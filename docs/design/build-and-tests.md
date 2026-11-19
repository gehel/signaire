# Build, tests and quality

## Why

A small project maintained by few people needs a build that catches problems early and stays fast enough to run on
every change: quality gates in the build, tests at the right level, and measures of what users feel (speed,
accessibility).

## Design

- **Maven, multi-module**, Java 27, Spring Boot imported as a BOM, the WMF parent POM for quality gates (checkstyle,
  forbiddenapis, spotbugs with findsecbugs and sb-contrib, no package cycles, duplicate-finder, sortpom). The root POM
  overrides the tool versions that can't handle Java 27 or unnamed variables.
- **Architecture rules**: an `ArchitectureTest` (ArchUnit) per module for what Maven dependencies can't say (Android
  compatibility, no Spring in extractor and export, Lucene and JPA internal, packages of core independent).
- **Tests at the lowest level that can show the behavior**:
  - unit tests everywhere, test first, JUnit 5 + AssertJ;
  - accounts services on H2;
  - web controllers in `@WebMvcTest` slices (`@WebLayerTest`) with mocked account services and a real Lucene index;
  - `signaire-it`: the CLI and web jars as processes, PostgreSQL in a container, a fake SignSuisse served locally;
    user journeys, and the check that signs are transient. Only this module needs Docker.
- **No network in tests**: the root POM sends HTTP(S) to a closed port (localhost allowed), `NoNetworkTest` checks it.
- **Speed**: the Maven build cache extension restores unchanged modules (tests included) locally; builds stopping
  before `package` aren't cached. Test JVMs use the quick compiler and the serial collector. CI builds everything,
  without the cache.
- **Measures** (`quality.yml`): Lighthouse CI (accessibility at 1), sitespeed.io (budget, and comparison with the
  last build of main: no timing, size or request count up by more than 10% and a slack), SonarCloud (quality gate:
  80% coverage of new code by unit tests).
- **CI artifacts**: the CLI jar, the standalone images for Linux, Windows and macOS.

## Decisions

- **Quality gates in the build, not only in CI**: `./mvnw verify` fails locally on what CI would reject.
- **Mocks above the database layer, containers only end to end**: Docker-free tests for every module but one; the
  test suite went from about 180 s to about 126 s, and contributors without Docker can work on everything else.
- **Coverage from unit tests only** (SonarCloud): code reached only by integration tests needs unit tests too.
- **Comparison with main rather than absolute thresholds alone**: catches regressions early while budgets stay
  loose enough for CI noise.
- **Checkstyle wins over SonarCloud** when they clash; false positives get `// NOSONAR` with their reason.

## Open questions

- Integration tests aren't counted in coverage (the parent POM writes the jacoco report before them).
