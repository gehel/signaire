# Supply chain security (OpenSSF Scorecard)

## Why

People run what Signaire's build makes:
- the jars;
- the standalone application with its Java runtime;
- the Android app;
- soon, releases ([release.md](release.md)).

The build pulls hundreds of Maven artifacts, GitHub Actions, an npm package and Docker images, and a compromised one
would reach those users. [OpenSSF Scorecard](https://scorecard.dev) checks the practices that limit this: dependencies
pinned, tokens restricted, code analyzed, branch protected, a way to report vulnerabilities.

Scorecard runs on each push to main and every week. Its findings go to GitHub's code scanning, and the README shows
its score, so users can judge the project before running it.

## Design

- **The Scorecard workflow** (`scorecard.yml`), as OpenSSF's example:
  - triggers: pushes to main, once a week, and changes to branch protection;
  - its job runs `ossf/scorecard-action` only (Scorecard refuses `run` steps and `env` in a workflow whose results it
    publishes);
  - it uploads the SARIF to code scanning, and publishes the results (OIDC token) for the badge.
- **The README badge** links to the results on scorecard.dev.

What each check finds, and what the project does about it:

| Check | Today | Change |
|---|---|---|
| Pinned-Dependencies | actions by tag; Lighthouse CI by `npm install -g` | actions by commit SHA; Lighthouse CI from a lockfile; sitespeed.io by digest; the Maven distribution's SHA-256 |
| Token-Permissions | read-only at the top of both workflows | the same in the new ones, write only in the job that needs it |
| SAST | SonarCloud from Maven, which Scorecard doesn't see | CodeQL: Java, JavaScript, the workflows |
| Security-Policy | none | `SECURITY.md`: private vulnerability reporting, the delays |
| Branch-Protection | none | a ruleset: no force push, no deletion of main (setting) |
| Dependency-Update-Tool | Dependabot: Maven, actions | and npm, for Lighthouse CI |
| Signed-Releases, Packaging | no release | with the releases ([release.md](release.md)) |
| CII-Best-Practices | none | the OpenSSF Best Practices questionnaire (setting) |
| Code-Review, Contributors | one maintainer | none: accepted |
| Fuzzing | none | none: Scorecard recognizes no Java fuzzing |
| Maintained | repository under 90 days old | none: counts from February 2027 |
| Binary-Artifacts, Dangerous-Workflow, License, CI-Tests, Vulnerabilities | fine | keep them so |

- **Actions pinned by commit SHA**, with the version as a comment (`actions/checkout@3d3c42e… # v7.0.1`). Dependabot
  updates the SHA and the comment together.
- **Lighthouse CI from a lockfile**: `ci/lighthouse/package.json` names `@lhci/cli`, and `package-lock.json` holds the
  whole tree with the hash of each package. CI installs it with `npm ci --ignore-scripts`, and Dependabot proposes the
  updates.
- **The sitespeed.io image by digest**, the tag beside it for people.
- **The Maven wrapper checks Maven**: `distributionSha256Sum` in `maven-wrapper.properties`. The wrapper refuses a
  download that doesn't match.
- **CodeQL** (`codeql.yml`), on pull requests, on main and once a week:
  - three languages: Java (`build-mode: none`, from the sources: no build with our JDK and plugins), JavaScript (the
    tour's script) and the workflows (`actions`: script injection, tokens);
  - its alerts go to code scanning and fail nothing; SonarCloud stays the gate.
- **`SECURITY.md`**:
  - report privately, through GitHub's "Report a vulnerability";
  - an answer within 7 days, a fix or a disclosure within 90;
  - the supported version (main, then the latest release).
- **Settings, by the maintainer**: Scorecard reads them, and the repository can't hold them.
  - private vulnerability reporting, which `SECURITY.md` points to;
  - the ruleset of main;
  - the Best Practices questionnaire (bestpractices.dev).

## Decisions

- **Commit SHAs rather than tags**: whoever controls an action's repository can move a tag, so the next build runs
  other code. A SHA can't move. Dependabot keeps them current, with the version in the comment.
- **CodeQL besides SonarCloud**, rather than:
  - SonarCloud alone: Scorecard sees only SonarCloud's GitHub App, not an analysis run from Maven. CodeQL also covers
    what SonarCloud doesn't analyze here: the workflows, and dataflow queries of its own.
  - SonarCloud's GitHub App: it would show Scorecard an analysis it already runs, but a third-party app with write
    access to checks, for the score only.
- **Branch protection without required pull requests or reviews**: a single maintainer can't approve their own pull
  request, and `release:prepare` pushes to main ([release.md](release.md)). Required status checks would refuse those
  pushes too. Blocking force pushes and deletion protects the history, and earns Scorecard's first tier.
- **A lockfile for one npm tool** rather than `npm install -g @lhci/cli@0.16.1`: the version is fixed, but its
  dependencies float within their ranges.
- **Maven dependencies stay unpinned by hash**: Maven 3 has no lockfile; Central's artifacts are immutable and carry
  checksums, which Maven checks. Scorecard doesn't check Maven.

## Open questions

- Signed release assets and SLSA provenance (`*.intoto.jsonl`), which Signed-Releases counts: with the release workflow.
- Maven dependencies verified beyond checksums: PGP signatures, or a lockfile (Maven 4, `maven-lockfile`).
- Fuzzing the parsers of untrusted input (SignSuisse's pages, bundle files) with Jazzer: useful, though Scorecard
  doesn't count it.
- Code review: a second maintainer would make Code-Review and the next tiers of Branch-Protection possible.
