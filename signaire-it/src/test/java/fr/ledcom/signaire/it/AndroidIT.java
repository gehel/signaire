package fr.ledcom.signaire.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import fr.ledcom.signaire.bundle.Bundle;
import fr.ledcom.signaire.bundle.BundleReader;
import fr.ledcom.signaire.extractor.FakeSignSuisse;
import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Language;
import fr.ledcom.signaire.model.Media;
import fr.ledcom.signaire.model.Sign;
import fr.ledcom.signaire.model.TestSigns;

/**
 * The Android app on a phone (an emulator of Android 14, the oldest it supports), as its users go: signs made on a
 * computer, searched, a sign put in a list, the signs of the phone, a category downloaded from SignSuisse. In French,
 * the base language. Writes screenshots to {@code target/screenshots/android}. Only where the app is built: the
 * Android SDK ({@code ANDROID_HOME}). Downloads are from a fake SignSuisse, never from the real one.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AndroidIT {

    private static final String APP = "fr.ledcom.signaire";
    private static final String SEARCH = APP + "/fr.ledcom.signaire.android.SearchActivity";
    private static final Path SCREENSHOTS = Path.of(System.getProperty("signaire.it.work"))
            .resolveSibling("screenshots").resolve("android");
    private static final Path WORK = Path.of(System.getProperty("signaire.it.work")).resolveSibling("it-android");
    /** About 40 requests, 500 ms apart. */
    private static final Duration DOWNLOAD = Duration.ofMinutes(2);
    private static final String CITY = "city.zip";
    /** A file among the bundles that isn't one. */
    private static final String BROKEN = "broken.zip";

    private static AndroidDevice device;
    private static FakeSignSuisse signSuisse;
    /** The port of the fake SignSuisse, the same on the phone (adb reverse). */
    private static String port;
    private static int screenshots;

    @BeforeAll
    static void installTheApp() throws IOException, InterruptedException {
        String sdk = System.getenv("ANDROID_HOME");
        Path apk = Path.of(System.getProperty("signaire.android.apk", "missing"));
        assumeTrue(sdk != null && Files.isRegularFile(apk), "the app is built with the Android SDK only");
        String image = System.getProperty("signaire.android.image", "system-images;android-34;google_apis;x86_64");
        assumeTrue(AndroidDevice.available(Path.of(sdk), image), "no device, nor " + image + " to emulate one");
        device = AndroidDevice.connect(Path.of(sdk), System.getProperty("signaire.android.avd", "signaire-it"), image);
        device.run("install", "-r", apk.toString());
        device.run("shell", "pm", "clear", APP);
        device.run("shell", "cmd", "locale", "set-app-locales", APP, "--locales", "fr");
        // the bundles made on a computer, in the files of the app: not the city, downloaded by a test
        Path bundles = WORK.resolve("bundles");
        JavaProcess.deleteRecursively(bundles);
        DemoBundles.write(bundles);
        try (Stream<Path> files = Files.list(bundles)) {
            for (Path bundle : files.filter(file -> file.toString().endsWith(".zip"))
                    .filter(file -> !file.getFileName().toString().equals(CITY)).toList()) {
                device.pushToApp(bundle, APP, "files/bundles/" + bundle.getFileName());
            }
        }
        device.pushToApp(Files.writeString(WORK.resolve(BROKEN), "not a bundle"), APP, "files/bundles/" + BROKEN);
        // downloads from a fake SignSuisse, at the same address on the phone as on the computer
        signSuisse = new FakeSignSuisse();
        port = "tcp:" + signSuisse.uri().getPort();
        device.run("reverse", port, port);
        Path server = Files.writeString(WORK.resolve("signsuisse-server"), signSuisse.uri().toString());
        device.pushToApp(server, APP, "files/signsuisse-server");
        // the notification of downloads, without the question
        device.run("shell", "pm", "grant", APP, "android.permission.POST_NOTIFICATIONS");
        device.run("logcat", "-c");
        device.run("shell", "am", "start", "-W", "-n", SEARCH);
    }

    @AfterAll
    static void close() throws IOException, InterruptedException {
        if (signSuisse != null) {
            signSuisse.close();
        }
        if (device != null) {
            try {
                if (port != null) {
                    device.run("reverse", "--remove", port);
                }
            } finally {
                device.close();
            }
        }
    }

    @AfterEach
    void nothingCrashed() throws IOException, InterruptedException {
        assertThat(device.crashes(APP)).as("crashes of the app, see adb logcat -b crash").isEmpty();
    }

    @Test
    @Order(1)
    void theSignsOfThePhoneAreListedThenFoundAccentsIgnored() throws IOException, InterruptedException {
        // every sign of the bundles, by name
        device.waitFor("ALBERO");
        long onThePhone = TestSigns.LEXICON.stream().filter(sign -> !sign.categories().contains(Category.CITY)).count();
        assertThat(AndroidDevice.texts(device.screen())).contains(onThePhone + " signes", "ARBRE", "LIS");
        screenshot("search");

        device.tap("search");
        device.type("foret");
        device.waitFor("1 signe");
        assertThat(AndroidDevice.texts(device.screen())).contains("FORÊT").doesNotContain("ARBRE");
        device.key("KEYCODE_BACK");
        screenshot("results");
    }

    @Test
    @Order(2)
    void aSignShowsItsTextsAndGoesInAList() throws IOException, InterruptedException {
        device.tap("FORÊT");
        device.waitFor("add_to_list");
        // the definition is one text with its label
        assertThat(String.join("\n", AndroidDevice.texts(device.screen()))).contains("Langue des signes française (LSF)",
                "Définition\n" + TestSigns.FORET.definition());
        screenshot("sign");

        device.tap("add_to_list");
        device.tap("Nouvelle liste");
        // the name asked once the dialog is there: its field, empty, shows its hint
        device.tap("Nom de la liste");
        device.type("Nature");
        device.tap("OK");
        device.waitFor("Dans : Nature");
        screenshot("sign-in-list");
        device.key("KEYCODE_BACK");
    }

    @Test
    @Order(3)
    void theListsKeepTheirSignsWhenTheAppRestarts() throws IOException, InterruptedException {
        device.tap("Mes listes");
        device.tap("Nature (1)");
        device.waitFor("FORÊT");
        assertThat(AndroidDevice.texts(device.screen())).contains("Nature", "FORÊT", "LSF");
        screenshot("list");
        device.key("KEYCODE_BACK");
        device.key("KEYCODE_BACK");

        // the lists are in a file of the app, which a new process reads
        device.run("shell", "am", "force-stop", APP);
        device.run("shell", "am", "start", "-W", "-n", SEARCH);
        device.tap("Mes listes");
        device.waitFor("Nature (1)");
        device.key("KEYCODE_BACK");
    }

    @Test
    @Order(4)
    void theSignsOfThePhoneAreThoseOfItsValidBundles() throws IOException, InterruptedException {
        device.key("KEYCODE_MENU");
        device.tap("Signes du téléphone");
        device.waitFor("Télécharger depuis SignSuisse");
        List<String> texts = AndroidDevice.texts(device.screen());
        assertThat(texts).contains("Animaux", "Nature").doesNotContain("Ville");
        assertThat(texts).filteredOn(text -> text.startsWith("7 signes, extraits le")).hasSize(2);
        screenshot("library");
        device.key("KEYCODE_BACK");

        // the file that isn't a bundle is left out, and the log says which and why
        assertThat(device.log("SignLibrary")).anySatisfy(line -> assertThat(line)
                .containsPattern("Ignoring the invalid bundle /\\S+/files/bundles/" + BROKEN + ": [^{]+$"));
    }

    @Test
    @Order(5)
    void aCategoryIsDownloadedFromSignSuisseWithItsMedia() throws IOException, InterruptedException {
        device.key("KEYCODE_MENU");
        device.tap("Signes du téléphone");
        // the fake SignSuisse, before any request: never the real one
        device.waitFor("Téléchargements depuis un serveur de test : " + signSuisse.uri());
        device.tap("Télécharger depuis SignSuisse");
        device.scrollTo("Ville");
        device.tap("Ville");
        device.tap("OK");
        // the sign language of the phone is chosen
        device.waitFor("Langues des signes");
        device.tap("OK");
        device.tap("Vidéos et images");
        // the French signs of the city: GARE, GENÈVE and LAUSANNE
        List<Sign> french = TestSigns.of(Category.CITY, Language.FRENCH);
        device.waitFor("Téléchargement terminé : " + french.size() + " signes", DOWNLOAD);
        // the bundle of the city added to the others
        List<String> texts = AndroidDevice.texts(device.screen());
        assertThat(texts).filteredOn(text -> text.startsWith(french.size() + " signes, extraits le")).hasSize(1);
        assertThat(texts).filteredOn(text -> text.startsWith("7 signes, extraits le")).hasSize(2);
        screenshot("downloaded");
        device.key("KEYCODE_BACK");

        Path city = WORK.resolve("downloaded").resolve(CITY);
        device.pullFromApp(APP, "files/bundles/" + CITY, city);
        try (Bundle bundle = new BundleReader().read(city)) {
            bundle.verifyMedia();
            assertThat(bundle.signs()).extracting(Sign::uid)
                    .containsExactlyInAnyOrderElementsOf(french.stream().map(Sign::uid).toList());
            Sign gare = bundle.signs().stream().filter(sign -> sign.uid().equals(TestSigns.GARE.uid())).findFirst()
                    .orElseThrow();
            assertThat(gare.videos()).singleElement().extracting(Media::file).isNotNull();
            assertThat(gare.images()).singleElement().extracting(Media::file).isNotNull();
            assertThat(gare.examples()).singleElement()
                    .satisfies(example -> assertThat(Objects.requireNonNull(example.video()).file()).isNotNull());
        }
    }

    @Test
    @Order(6)
    void theDownloadedSignsAreFound() throws IOException, InterruptedException {
        device.tap("search");
        device.clear();
        device.type("gare");
        device.waitFor("1 signe");
        assertThat(AndroidDevice.texts(device.screen())).contains("GARE", "LSF");
        device.key("KEYCODE_BACK");
        screenshot("downloaded-found");
    }

    private static void screenshot(String name) throws IOException, InterruptedException {
        device.screenshot(SCREENSHOTS.resolve(String.format(Locale.ROOT, "%02d-%s.png", ++screenshots, name)));
    }
}
