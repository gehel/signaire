package fr.ledcom.signaire.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * The Android app on a phone (an emulator of Android 14, the oldest it supports), as its users go: signs made on a
 * computer, searched, a sign put in a list, the signs of the phone. In French, the base language. Writes screenshots
 * to {@code target/screenshots/android}. Only where the app is built: the Android SDK ({@code ANDROID_HOME}).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AndroidIT {

    private static final String APP = "fr.ledcom.signaire";
    private static final String SEARCH = APP + "/fr.ledcom.signaire.android.SearchActivity";
    private static final Path SCREENSHOTS = Path.of(System.getProperty("signaire.it.work"))
            .resolveSibling("screenshots").resolve("android");

    private static AndroidDevice device;
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
        // the bundles made on a computer, in the files of the app
        Path bundles = Path.of(System.getProperty("signaire.it.work")).resolveSibling("it-android/bundles");
        DemoBundles.write(bundles);
        try (Stream<Path> files = Files.list(bundles)) {
            for (Path bundle : files.filter(file -> file.toString().endsWith(".zip")).toList()) {
                device.pushToApp(bundle, APP, "files/bundles/" + bundle.getFileName());
            }
        }
        device.run("logcat", "-c");
        device.run("shell", "am", "start", "-W", "-n", SEARCH);
    }

    @AfterAll
    static void close() throws IOException, InterruptedException {
        if (device != null) {
            device.close();
        }
    }

    @AfterEach
    void nothingCrashed() throws IOException, InterruptedException {
        assertThat(device.crashes(APP)).as("crashes of the app, see adb logcat -b crash").isEmpty();
    }

    @Test
    @Order(1)
    void theSignsOfThePhoneAreListedThenFoundAccentsIgnored() throws IOException, InterruptedException {
        device.waitFor("ARBRE");
        assertThat(AndroidDevice.texts(device.screen())).contains("20 signes", "BATEAU", "DSGS");
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
        // the periphrase and the definition are one text, under their labels
        assertThat(String.join("\n", AndroidDevice.texts(device.screen()))).contains("Langue des signes italienne (LIS)",
                "Définition\nUn forêt de démonstration, qui n'existe pas dans SignSuisse.");
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
    void theListsHaveTheirSigns() throws IOException, InterruptedException {
        device.tap("Mes listes");
        device.tap("Nature (1)");
        device.waitFor("FORÊT");
        assertThat(AndroidDevice.texts(device.screen())).contains("Nature", "FORÊT", "LIS");
        screenshot("list");
        device.key("KEYCODE_BACK");
        device.key("KEYCODE_BACK");
    }

    @Test
    @Order(4)
    void theSignsOfThePhoneAreThoseOfTheirBundles() throws IOException, InterruptedException {
        device.key("KEYCODE_MENU");
        device.tap("Signes du téléphone");
        device.waitFor("Télécharger depuis SignSuisse");
        List<String> texts = AndroidDevice.texts(device.screen());
        assertThat(texts).contains("Ville", "Nature", "Circulation");
        assertThat(texts).filteredOn(text -> text.startsWith("7 signes, extraits le")).hasSize(2);
        screenshot("library");
        device.key("KEYCODE_BACK");
    }

    private static void screenshot(String name) throws IOException, InterruptedException {
        device.screenshot(SCREENSHOTS.resolve(String.format(Locale.ROOT, "%02d-%s.png", ++screenshots, name)));
    }
}
