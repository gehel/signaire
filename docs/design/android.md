# Android app

## Why

Learners look signs up in class, on the way, at a friend's: on a phone, often without a computer nearby and
sometimes without network. The web application needs a server, or the standalone application on a computer. The
Android app keeps the signs on the phone: search, sign pages and word lists, offline, without depending on the web
application.

The licensing of SignSuisse stays as it is: only the code is distributed; each user gets the signs themselves, by
extracting them from SignSuisse on the phone, or by importing bundles they extracted with the command line.

## Design

- **Modules**, all in the Maven build, no other build tool:
  - `signaire-model` and `signaire-bundle`, already Android compatible (Java 17, no framework).
  - `signaire-extractor` becomes Android compatible too: Java 17, `HttpURLConnection` rather than `java.net.http`
    (absent from Android), platform threads rather than virtual ones (Java 21). The phone extracts with the code of
    the command line and of the web application, throttle included.
  - `signaire-offline` (new, Java 17, plain Java, tested on the JVM): what the app does that doesn't need Android.
    The library of the bundles of a directory (their signs in memory, reloaded when the bundles change), the search,
    the word lists (a JSON file), the import of a bundle file (checked, named after its category).
  - `signaire-android` (new): the app. Android's framework only (activities, views, `VideoView`, a foreground
    service), no AndroidX: its libraries are AARs, which Maven doesn't use without a plugin.
- **The build of the app** calls the tools of the Android SDK from Maven: `aapt2` compiles the resources and writes
  `R.java`, `javac` compiles for Java 17 with `android.jar`, `d8` turns the classes and their dependencies into dex,
  `aapt2 link` and the dex make the APK, which `zipalign` aligns and `apksigner` signs (a debug key, made by
  `keytool` on the first build). `d8` fails the build on a class Android doesn't have. The module is in the
  `android` profile, active when `ANDROID_HOME` is set: without the SDK, the rest builds as before.
- **Signs on the phone**: bundles in the files of the app (`files/bundles`), media in them; videos that weren't
  downloaded play from SignSuisse when the phone is online.
- **Getting signs**, from the library screen:
  - download from SignSuisse: categories, sign languages and media to download, as with `signaire extraire`; in a
    foreground service with a notification, since Android stops background work; each bundle is loaded when written;
  - import a bundle file, chosen with the file picker of the system.
- **Search**: in memory, over names, periphrases, definitions and examples; accents and case ignored; names that are
  the word first, then those starting with it, then the texts; filtered by sign language and category; without
  words, every sign by name. A few thousand signs per category fit in memory and are searched in milliseconds.
- **Word lists**: in a JSON file of the app, signs by uid, as everywhere else (signs are transient).
- **Screens**: the search (field, filters, results as entries: gloss, sign language, start of the definition), a sign
  (video, images, texts, examples, add to a list), the lists and a list, the library (bundles, download, import).
- **Languages**: French, German, Italian and English, like the rest: Android's string resources (`values` in French,
  `values-de`, `values-it`, `values-en`), with a test that each translation has the keys of the French. The build
  copies `values` to `values-fr`: Android takes a language of the phone's list that the app names before its default,
  so a phone in French then English would otherwise show English.
- **Android 14 (API 34) and later**: the records and Java 17 APIs of the model and of Jackson run without
  desugaring.
- **Java 17 is not Android**: `System.Logger`, for one, compiles and isn't on the phone, and `d8` doesn't say so.
  `AndroidApiTest` checks that the app and the modules it stands on use only the members of Java classes that
  `android.jar` has; the Android-compatible modules log with `java.util.logging`.
- **`android.jar` is a dependency** like the others (`provided`): the root's `android` profile installs the SDK's into
  the local repository first, rather than the deprecated `system` scope.
- **Tests**: JVM tests for the extractor and `signaire-offline`; `AndroidIT` (`signaire-it`) installs the APK on the
  device adb sees, or on an emulator of Android 14 it starts, pushes the demo bundles, drives the screens with `adb`
  (uiautomator to read them) in French, and writes screenshots, as `BrowserIT` does for the web. It skips without a
  device or the system image to emulate one.
- **CI** builds the APK (the runners have the SDK; `ci/android-sdk.sh` installs its packages) and keeps it as an
  artifact; a job of its own runs `AndroidIT` on an emulator, with KVM.

## Decisions

- **Maven drives the SDK's tools**: one build for the whole project, the same quality gates, a module like the
  others.
  - Gradle with the Android Gradle Plugin, the standard: a second build tool, and the app out of the reactor and of
    its checks.
  - `android-maven-plugin`: abandoned since 2017, before `d8` and `aapt2`.
- **No AndroidX**: Maven can't use AARs without unpacking them by hand; the framework's views are enough for five
  screens.
- **The phone extracts with the extractor** rather than asking a server: no web application needed, and each user
  extracts their own copy, as the licensing requires.
- **`HttpURLConnection` in the extractor, for all**: one code path, tested by the tests that exist; HTTP/2 doesn't
  matter for requests 500 ms apart.
  - `java.net.http` on the JVM and `HttpURLConnection` on Android: two transports under the throttle.
- **Platform threads**: an extraction runs three stages and their downloads, a handful of threads.
- **Search in memory**:
  - Lucene, as in `signaire-core`: Lucene 10 needs Java 21, and core needs Spring.
  - SQLite's full text search, which Android has: not testable on the JVM, and its tokenizers don't fold accents
    as the search of the web does.
- **Word lists in a JSON file** rather than SQLite: a few lists, read and written whole, tested on the JVM.
- **Android 14 and later**: older phones would need the desugaring of records and of Java's newer APIs, which only
  Gradle's plugin sets up.

## Open questions

- Exports on the phone: PDF (`android.graphics.pdf`, not PDFBox, which needs AWT), Anki.
- Synchronizing word lists with the web application (its REST API is still to design).
- A release key and a store (F-Droid, Play) when the app is ready for others.
