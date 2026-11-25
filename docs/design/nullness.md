# Nullness

## Why

A `NullPointerException` is the most common failure of Java code, and nothing in a signature says whether `null` is
allowed: the absent definition of a sign, the language of a request, a field opened lazily. Javadoc said it here and
there; tests catch the cases they think of. The compiler can check it, if the code says where `null` may be.

## Design

- **JSpecify annotations** (`org.jspecify:jspecify`, the version of the Spring Boot BOM): every package is
  `@NullMarked` (its `package-info.java`), so types are non-null unless written `@Nullable`. Spring Boot 4 annotates
  its APIs the same way.
- **NullAway** (Uber), a plugin of **Error Prone** (Google), checks the main code of `@NullMarked` packages when it
  compiles, in JSpecify mode (generics and arrays included): passing, returning or storing `null` where a type isn't
  `@Nullable`, dereferencing a `@Nullable` without a check, a non-null field left unset by the constructor. The build
  fails like it does on a checkstyle error. Only NullAway runs: the other checks of Error Prone are off.
- **The same annotations feed the other checkers**: SpotBugs reads `@NullMarked` and `@Nullable`, SonarCloud's Java
  analyzer as well, and IDEs show them.
- **Every package is null-marked**: the `ArchitectureTest` of each module checks it, so a new package can't escape
  the check.
- **Where frameworks set fields** (JPA entities, picocli options), NullAway is told so rather than the fields declared
  `@Nullable`: their annotations (`@Id`, `@Option`, …) mark them as initialized outside the constructor.
- **What the web gets from a request may be absent**: parameters and cookies `required = false`, the principal of a
  public page (anonymous visitors), the fields of a form are `@Nullable`, so that NullAway follows them into the code.
  Spring creates them: nothing else would tell NullAway.
- **Tests aren't checked**: they pass `null` on purpose to see what the code does with it, and their packages are the
  same as the main code's.
- Error Prone works inside javac: Maven's JVM opens the internals of the compiler to it (`.mvn/jvm.config`).
- `@Nullable` is no coupling: checkstyle doesn't count the annotation packages in the fan-out of a class.

## Decisions

- **NullAway**: checks at compile time, fast, with few annotations beyond `@Nullable`; it runs on Java 27 with
  annotation processing off (`-proc:none`): Error Prone is a plugin of javac, not a processor.
  - The annotations alone, checked by SpotBugs and SonarCloud: SpotBugs reasons within a method, SonarCloud only
    reports on pull requests.
  - The Checker Framework: thorough, but more annotations (initialization, monotonic fields), slower compiles, and
    its support of Java 27 unconfirmed.
  - SpotBugs' own `@Nullable`: no default for a package without JSR-305, abandoned.
- **JSpecify**: the standard that Spring, SpotBugs, SonarCloud, NullAway and IDEs share; a tiny jar for Java 8 and
  later, fine on Android for model and bundle.
- **`@NullMarked` packages rather than NullAway's list of packages**: one place says it, for every tool.
- **JSpecify mode**: the nullness of type arguments too (`List<@Nullable String>`), as Spring is checked.

## Open questions

- Error Prone's other checks (its `GuardedBy` is stricter than SpotBugs', see [thread safety](thread-safety.md)).
- Error Prone has to support each new version of Java: when it lags, the version of Java waits, or NullAway is off
  until it catches up.
