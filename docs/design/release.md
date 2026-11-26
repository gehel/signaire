# Releases and app stores

## Why

Nothing is released yet. CI keeps the artifacts of each build of main for 90 days. Its APK is signed with the debug
key of the runner, a new key on every run, so one build doesn't install over another. Learners need versions they can
install and update:
- the command line jar, the web application's jar, and the standalone application for their system;
- the Android app from where people get apps:
  - **Google Play**, on most phones;
  - **F-Droid**, for people who want free software only. The app fits there: Apache-2.0, no Google library.
  - **the APK of each release on GitHub**, for the others (and Obtainium, which follows GitHub releases).

The constraints, as of November 2026:
- **Licensing**: only the code is distributed, and that doesn't change. The stores list an app that downloads
  SignSuisse's signs for its user. A listing shows no SignSuisse content and says the app is not SGB-FSS's.
- **F-Droid builds the app itself**, from the source of a tag, with a recipe in its own repository (`fdroiddata`). Its
  build servers have only Debian's JDKs (21 and 25 on trixie), and the binaries a build uses come from Debian or from
  trusted Maven repositories (Central, Google). Its scanner rejects other binaries.
- **F-Droid signs with its own key**, unless the build is reproducible: F-Droid then rebuilds the app, checks that it
  matches the developer's signed APK, and publishes that APK
  ([Reproducible builds](https://f-droid.org/docs/Reproducible_Builds/)).
- **Google Play**:
  - an Android App Bundle (AAB) rather than an APK, for new apps;
  - Play App Signing, so Google holds the app signing key;
  - a target of API 36 for new apps and updates since 31 August 2026 (the app targets 34);
  - a privacy policy and a Data safety form, even for an app that collects nothing;
  - for a personal developer account, a closed test with 12 testers for 14 days before production.
- **Android developer verification**: certified phones install only apps of registered developers, that is a
  package name and its signing key. It started on 30 September 2026 in Brazil, Indonesia, Singapore and Thailand, and
  goes global in 2027. Other apps need ADB or the "advanced flow" (24 hours). F-Droid refuses to enroll, so an app
  that F-Droid signs with its own key is unregistered unless its developer registers F-Droid's key.

The automation stays in Maven where it can: one build, the same reviewable POMs. GitHub Actions only runs the steps on
the machines they need.

## Design

### Versions

- **`MAJOR.MINOR.PATCH`**, `-SNAPSHOT` between releases. The first release is 1.0.0, so main becomes
  `1.0.0-SNAPSHOT`. Tags are `v1.2.3`.
- **Android `versionCode` = major × 10000 + minor × 100 + patch** (1.2.3 → 10203; minor and patch stay below
  100). It is a literal, `android.versionCode` in `signaire-android/pom.xml`, so that F-Droid's update check can read
  it in a tag. The release writes it (below), and a release build fails when it doesn't match the version.
- **`project.build.outputTimestamp`** in the root POM: the release plugin sets it to the time of the release, and
  every jar and the APK take their dates from it.

### Release builds of the app

`signaire-android` builds a release when `android.release` is set. Without it, the build is the debug build of today,
which `AndroidIT` tests.
- **Not debuggable** (no `--debug-mode`): it ignores the test server in `files/signsuisse-server`, as now.
- **Three outputs** in `target/android/`:
  - `signaire-unsigned.apk`: aligned, unsigned, the output that F-Droid's recipe names;
  - `signaire-<version>.apk`: the same APK, signed by `apksigner` (v2 and v3 schemes) with the app signing key;
  - `signaire-<version>.aab`: for Play, built by `bundletool` and signed by `jarsigner` with the upload key. It needs:
    - a second `aapt2 link` with `--proto-format`;
    - the base module laid out as `bundletool` wants it (`manifest/`, `dex/`, `res/`, `resources.pb`, `root/` for
      the Java resources);
    - `com.android.tools.build:bundletool`, a dependency from Google's Maven repository, declared in the `android`
      profile only.
- **Keys from the environment**: the paths of the keystores and their passwords. `apksigner` reads
  `--ks-pass env:…`. No key, password or path to one goes in the repository.
- **Reproducible**: the unsigned APK depends only on the sources, the JDK and the build tools. The zip of the APK gets
  a fixed entry order and its dates from `project.build.outputTimestamp` (Ant's `zip` writes the files' times today).
  No path or time of the build goes in.
- **One script, `ci/build-apk.sh`**, for every release APK: the Maven command of the release build of the app, which
  builds the app and the modules it needs, without their tests (`-Dmaven.test.skip`, since their tests compile for
  Java 27) or the quality gates (CI ran them). The release workflow and F-Droid's recipe both call it.
- **Debian's JDK 25 for release APKs**, in a `debian:trixie` container (`openjdk-25-jdk-headless`): javac's output
  differs from one JDK to another, and F-Droid builds with Debian's. The app's modules are Java 17 and build there. The
  rest of the project stays on Java 27.
- **Debug builds are `fr.ledcom.signaire.debug`** (`aapt2 --rename-manifest-package`), "Signaire (debug)": they
  install beside the released app, without its user uninstalling it (and losing their lists). `AndroidIT` uses that
  package.
- **Checked on every pull request**: a job builds the release APK twice in the trixie container, in two directories,
  with a key made for the run, and fails unless the unsigned APKs are identical. The release path doesn't rot between
  releases, and F-Droid will reproduce what we publish.

### Texts and images of the stores

- **`fastlane/metadata/android/<locale>/`**, at the root, in four locales: `fr-FR`, `de-DE`, `it-IT` and `en-US`
  (Play's codes, which F-Droid accepts). Each holds:
  - `title.txt`, `short_description.txt`, `full_description.txt`;
  - `changelogs/<versionCode>.txt`;
  - `images/icon.png` (512 × 512, from the launcher icon), `images/featureGraphic.png` (1024 × 500), and
    `images/phoneScreenshots/`.
- **One source for both stores**: F-Droid reads this directory in the tag. The Play publisher uploads it.
- **Translated together**, like everything else: a JVM test of `signaire-android` checks that the four locales have the
  same files, within Play's limits (stricter than F-Droid's: title 30, short description 80, description 4000,
  changelog 500 characters). The release build checks that each locale has the changelog of the `versionCode`.
- **Content**: an unofficial app, not affiliated with the SGB-FSS. Its signs come from SignSuisse, downloaded by the
  user. Screenshots show the test signs (`AndroidIT`'s), never SignSuisse's videos or images.

### The release: `release:prepare`

In the root POM:
- **`<scm>` of the project**: the parent's points at WMF's GitLab, with its own tag.
- **maven-release-plugin** (the parent's, 3.3.1):
  - `tagNameFormat` is `v@{project.version}`;
  - the preparation goals build without tests or gates (`clean verify -DskipTests -PnoVerify`): main's CI tested the
    commit, and the release changes only versions;
  - the preparation build runs with the `release` profile (the plugin's `arguments`), which writes
    `android.versionCode` into the app's POM and checks the changelogs.

The maintainer, on an up-to-date main, after merging the pull request that adds the changelogs:

```bash
./mvnw release:prepare -Pandroid    # asks the versions (1.2.0, then 1.3.0-SNAPSHOT)
./mvnw release:clean
```

`release:prepare` makes two commits (the release, the next snapshot) and the tag, and pushes them. The `android`
profile matters: the app's POM is in the reactor only with it, and its version must change with the others. Without
the SDK, the profile fails rather than releasing a POM at the old version.

There is no `release:perform`: the tag starts the release workflow, which builds and publishes.

### The release workflow (`.github/workflows/release.yml`)

It starts on a pushed tag `v*`. `workflow_dispatch`, given a tag, runs it again (for a failed upload). Its jobs:

| Job | Where | What |
|---|---|---|
| `build` | Temurin 27 | `./mvnw -B verify`, as CI: every test and gate; the CLI, web and standalone jars |
| `android` | emulator | `AndroidIT`, as CI |
| `apk` | `debian:trixie`, JDK 25 | `ci/build-apk.sh`, then again in another directory, compared; the APK and AAB signed |
| `standalone` | Linux, Windows, macOS | `ci/package-standalone.sh` with the version; an archive per system |
| `github` | after all | the GitHub release, by JReleaser |
| `play` | after `github` | the AAB to Play's internal track, by `signaire-play` |

- **Secrets in a `release` environment**, which only `v*` tags can use: the keystores (base64) and their passwords.
  Play's API is reached through Workload Identity Federation: GitHub's OIDC token for a Google service account, so no
  Google key is stored.
- **GitHub release** (`jreleaser-maven-plugin`, `./mvnw -N jreleaser:release`):
  - the release of the existing tag (`skipTag`), with notes generated by GitHub from the merged pull requests;
  - assets:
    - `signaire-<version>.apk`;
    - `signaire-cli-<version>.jar`;
    - `signaire-web-<version>.jar` and `signaire-web-<version>-standalone.jar`;
    - `signaire-<version>-linux-x64.tar.gz`, `-windows-x64.zip` and `-macos-aarch64.zip`;
    - `checksums_sha256.txt`.
  - The AAB is for Play only.
- **Play** (`./mvnw -pl signaire-play -Pplay exec:java`): the AAB to the `internal` track, with the changelogs as
  release notes and the listing from the metadata. Promotion to production stays a decision, taken in the Play
  Console.
- **F-Droid: nothing in the workflow.** Its bot sees the tag, reads its version, adds a build to the recipe, builds it
  within a few days, fetches `signaire-<version>.apk` from the GitHub release, and publishes it if the two match.
- **Order**: nothing is published before every build and check passed. The GitHub release comes before Play: F-Droid
  needs its APK, and an upload that fails can be run again.

### `signaire-play`, the Play publisher

- **A module of its own**: plain Java 27, Google's client for the Play Developer API
  (`com.google.apis:google-api-services-androidpublisher`, on Maven Central) and Google's credentials library, which
  finds the credentials that the workflow's identity federation leaves.
- **One edit**:
  - insert it;
  - upload the bundle;
  - set the track: the release of that `versionCode`, with its notes in four languages;
  - update the listings and images when they changed;
  - commit.
  A failure before the commit leaves Play as it was.
- **Tested against a fake Play API** on 127.0.0.1 (the client takes a root URL), like the fake SignSuisse: tests never
  reach the network.
- **Run by `exec:java`** in the module's `play` profile; its properties are the bundle, the track and the metadata
  directory.

### F-Droid

Once: a merge request to `fdroiddata`, with `metadata/fr.ledcom.signaire.yml`. This repository keeps a copy
(`ci/fdroid/`), tried with `fdroid build` before it's submitted. A sketch:

```yaml
License: Apache-2.0
SourceCode: https://github.com/gehel/signaire
IssueTracker: https://github.com/gehel/signaire/issues
AntiFeatures:
  NonFreeNet:
    en-US: The signs come from SignSuisse, the lexicon of the SGB-FSS, whose content isn't free.
RepoType: git
Repo: https://github.com/gehel/signaire.git
Binaries: https://github.com/gehel/signaire/releases/download/v%v/signaire-%v.apk
Builds:
  - versionName: 1.0.0
    versionCode: 10000
    commit: <commit of v1.0.0>
    sudo:
      - apt-get update
      - apt-get install -y openjdk-25-jdk-headless maven
      - update-java-alternatives --set java-1.25.0-openjdk-amd64
    output: signaire-android/target/android/signaire-unsigned.apk
    build: ci/build-apk.sh
AllowedAPKSigningKeys: <SHA-256 of the app signing certificate>
AutoUpdateMode: Version
UpdateCheckMode: Tags ^v[0-9.]+$
UpdateCheckData: signaire-android/pom.xml|<android.versionCode>(\d+)</android.versionCode>||v(.+)
```

- `NonFreeNet`: F-Droid's label for an app that depends on a service that isn't free, and SignSuisse is one.
- The SDK packages of the POM (platform 36, build tools 36.1.0) come from F-Droid's `sdkmanager`. Debian's Maven
  (3.9) is recent enough for the parent's enforcer (3.9.6).

### Signing keys

- **The app signing key**:
  - RSA 4096, valid 30 years;
  - the same for GitHub, F-Droid (reproducible) and Play: Play App Signing is set up with it (uploaded once, encrypted
    by Google's PEPK tool);
  - an offline copy in the maintainer's password manager, and the CI copy in the `release` environment.
- **The upload key**, separate, signs the AAB. If it leaks, Play resets it.
- **Registered** for developer verification: the package `fr.ledcom.signaire` and the certificate. Play registers
  most of the apps it distributes, and the others are claimed in the Play Console. APKs from GitHub and F-Droid carry
  the same key, so they're of a registered app too.
- **If the app signing key leaks**: the app's Android versions (14 and later) accept a rotated key (APK Signature
  Scheme v3, `apksigner rotate`), and Play offers a key upgrade.

### What Maven does

| Step | How |
|---|---|
| Versions, tag, `versionCode` | maven-release-plugin, the `release` profile |
| APK and AAB, signed | the SDK's tools from antrun, as now; `bundletool` as a dependency |
| Store texts checked | a JVM test |
| GitHub release | jreleaser-maven-plugin |
| Play upload | `signaire-play` by exec-maven-plugin |
| Jobs on three systems, a container with Debian's JDK, secrets | GitHub Actions |
| F-Droid's builds | F-Droid's bot and build servers, from the recipe |

## Decisions

- **One signing key for GitHub, F-Droid and Play**: a phone moves from one source to another without uninstalling,
  and the app is registered once. Uninstalling deletes the downloaded signs and the word lists. Rather than:
  - Google's key for Play (Play App Signing's default) and ours elsewhere: two apps for Android, which won't update
    each other;
  - F-Droid's key for F-Droid (its default): the same, and its builds unregistered unless we register F-Droid's key
    too.
- **Reproducible builds for F-Droid** rather than:
  - F-Droid's signature: no reproducibility to keep, but a key of its own (above). A later switch would break
    F-Droid's users' updates, so it's decided before the first F-Droid release.
  - IzzyOnDroid alone, a repository that takes the APK of the GitHub release: quick, but not F-Droid's main
    repository. It can come first while F-Droid reviews.
- **The release APK built with Debian's JDK 25**, the JDK F-Droid uses:
  - with Temurin 27, as the rest: F-Droid has no JDK 27, so its build wouldn't match ours;
  - a JDK downloaded by F-Droid's recipe: against its inclusion policy.
- **`release:prepare` by the maintainer, publishing by the workflow of the tag**, rather than:
  - `release:perform`: it checks the tag out again and deploys to a Maven repository, which this project, not being a
    library, doesn't have. The standalone images need three systems, and the keys would be on a laptop.
  - `release:prepare` in a `workflow_dispatch` job: the same environment as CI, but a tag pushed with the workflow's
    token starts no workflow. It would need a personal token or an App allowed to write to main.
- **`versionCode` from the version, as a literal written by the release**, rather than:
  - computed by the build: F-Droid's bot couldn't read it in the tag, so each release would need a merge request to
    `fdroiddata`;
  - a counter raised by hand: forgotten, and nothing ties it to the version.
- **JReleaser for the GitHub release** rather than `gh release create` in the workflow: the same result from a script,
  with the renaming and checksums by hand, outside Maven.
- **A Play publisher of our own** (a module, Google's Java client), rather than:
  - fastlane `supply`: Ruby, a second toolchain for one upload;
  - Gradle Play Publisher: Gradle;
  - an Action (`upload-google-play`): outside Maven, untested here;
  - `android-maven-plugin`'s publishing goals: abandoned since 2017 ([android.md](android.md)).
- **fastlane's layout for the store texts**: F-Droid reads it from the repository, Play's listing comes from the same
  files, and both stay in step with the app's languages.
- **Identity federation for Play's API** rather than a service account's JSON key in the secrets: nothing to leak or
  rotate.
- **Debug builds under their own package**: a developer's phone keeps its released app, and `AndroidIT` never touches
  it.
- **Play's internal track by the workflow, production by hand**: the first releases go through the closed test (12
  testers, 14 days), and a release to everyone is a decision.
- **No Maven repository**: the artifacts are for users. The parent's `deploy` goal stays unused.

## Open questions

- **SignSuisse's permission** before a store listing: Play reviews intellectual property, and the app's purpose is
  to download someone else's content. Ask the SGB-FSS for their agreement before Play, and tell them before F-Droid.
- **API 36 before Play**: edge-to-edge screens (Android 15) and Android 16's changes, a change of its own. F-Droid and
  GitHub don't need it.
- **The Play account**:
  - personal (identity check, closed test with 12 testers) or organization (D-U-N-S number);
  - who the testers are.
- **The privacy policy** Play requires: nothing collected, and requests go to SignSuisse only. Where it lives (the
  web application's help, a GitHub page).
- **Play declarations**:
  - the foreground service of the downloads (`dataSync`);
  - the content rating and the target audience. Learners include children, and an audience under 13 brings Play's
    Families policy.
- **Standalone images**:
  - unsigned, so macOS Gatekeeper and Windows SmartScreen warn;
  - macOS on Apple silicon only (`macos-latest`);
  - installers (dmg, msi, deb) later.
- **A smoke test of the release APK**: `AndroidIT` needs a debuggable build (`run-as`). Install the release APK and
  check that it starts?
- **To try before relying on it**:
  - that the release plugin commits the `versionCode` written during the preparation (if not, it's written by hand,
    and the release build checks it);
  - the F-Droid recipe, with `fdroid build`.
- **When Debian moves on**, F-Droid's JDK changes and the release APK's with it. Mismatches show as F-Droid skipping a
  version.
- **A container image of the web application** (`spring-boot:build-image`, on ghcr.io): another channel, later.
