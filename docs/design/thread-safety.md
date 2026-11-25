# Thread safety

## Why

Signaire runs code on several threads at once: the web application serves requests concurrently with singleton
beans, loads the bundles in the background at startup, synchronizes with SignSuisse on a worker thread, and an
extraction runs its three stages (search, reading, media) at the same time, all through one `SignSource`. Which
classes are shared between threads, and what protects their state, was said in prose here and there, or not at all;
a lock forgotten on one access to a field is a bug that tests rarely show.

## Design

- **The annotations of *Java Concurrency in Practice*** (`net.jcip.annotations`, from
  `com.github.stephenc.jcip:jcip-annotations`, `provided` scope like `spotbugs-annotations`) say it in the code:
  - `@ThreadSafe` on the classes whose instances are shared between threads and that have mutable state or coordinate
    threads (`SignIndex`, `MediaLibrary`, `CategoryExtractor`, `ExtractionTracker`, `ThrottledHttpFetcher`,
    `SignSuisseSync`, `SyncIndexing`, `SyncListener`, `Standalone`, `AdminUsers`, `HelpPages`, `SingleUserFilter`,
    `Browser`), and on the interfaces whose implementations are called from several threads at once (`SignSource`,
    `HttpFetcher`: the stages of an extraction share them).
  - `@GuardedBy("this")` on every field a lock protects; `volatile` and atomic fields need none.
  - `@Immutable` on the classes other than records whose state never changes and that threads share
    (`IndexWriters`, `UserDirectories`).
  - `@NotThreadSafe` on the commands of the command line: picocli sets their options, one run each (they are
    prototype beans for that reason).
- **Checked by SpotBugs**, which reads these annotations: a field `@GuardedBy` read or written without its lock
  (`IS_FIELD_NOT_GUARDED`), a field that isn't final in an `@Immutable` class
  (`JCIP_FIELD_ISNT_FINAL_IN_IMMUTABLE_CLASS`).
- **Not annotated**: records (immutable by construction), stateless beans (singletons with final collaborators, the
  convention of Spring), JPA entities (one persistence context each, as JPA says), test code.

## Decisions

- **JCIP annotations**: what SpotBugs, already in the build, checks; small and without dependencies, they suit the
  Android-compatible modules too.
  - `javax.annotation.concurrent` (JSR-305, which SpotBugs reads as well): a JSR abandoned in 2012, whose package
    splits with others named `javax.annotation`.
  - Error Prone's annotations (`com.google.errorprone.annotations.concurrent`): checked by Error Prone only, which the
    build doesn't run.
  - Prose in the Javadoc, as before: nothing checks it.
- **The Apache-licensed `com.github.stephenc.jcip` artifact**, a clean-room copy of the annotations of the book: the
  original (`net.jcip:jcip-annotations`) is under Creative Commons Attribution 2.5, a license for documents rather
  than code. Same package, same annotations.
- **Where it says something**: on the classes that threads share and on the contracts of interfaces. An annotation on
  every class would bury the few that matter.

## Open questions

- Error Prone, now in the build for [nullness](nullness.md), also checks `@GuardedBy`, more strictly than SpotBugs.
