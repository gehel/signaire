package fr.ledcom.signaire.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.Keys;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.Rectangle;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.testcontainers.Testcontainers;
import org.testcontainers.selenium.BrowserWebDriverContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * The pages of the web application in a real browser (Chromium, in a container), in the light and dark themes, on
 * a computer and on a phone: what the stylesheets must get right, and screenshots of every page in
 * {@code target/screenshots}, for people to look at (CI keeps them). The application runs on invented signs
 * ({@link DemoBundles}) and an in-memory H2 database: the look doesn't depend on PostgreSQL.
 */
class BrowserIT {

    /** Keep in line with the Selenium version of the Spring Boot BOM. */
    private static final String CHROMIUM = "selenium/standalone-chromium:4.43.0-20260404";
    private static final String PASSWORD = "correct horse";

    private static Path work;
    private static Path screenshots;
    private static JavaProcess web;
    private static BrowserWebDriverContainer browser;
    private static String base;
    private static String list;

    private RemoteWebDriver driver;

    @BeforeAll
    static void startTheApplicationAndTheBrowser() throws Exception {
        work = Path.of(System.getProperty("signaire.it.work")).resolveSibling("it-browser");
        screenshots = work.resolveSibling("screenshots");
        JavaProcess.deleteRecursively(work);
        JavaProcess.deleteRecursively(screenshots);
        Files.createDirectories(work);
        DemoBundles.write(work.resolve("bundles"));
        int port = JavaProcess.freePort();
        web = JavaProcess.start(JavaProcess.jar("signaire.web.jar"), work, "web", List.of(
                "--server.port=" + port,
                "--signaire.bundles.directory=" + work.resolve("bundles"),
                "--spring.datasource.url=jdbc:h2:mem:signaire;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;"
                        + "DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
                "--spring.datasource.username=sa",
                "--spring.datasource.password=",
                "--signaire.accounts.admins=alice"));
        URI local = URI.create("http://127.0.0.1:" + port);
        Session anybody = new Session(local);
        await("signs loaded, see " + web.log())
                .atMost(Duration.ofMinutes(2))
                .pollInterval(Duration.ofMillis(500))
                .failFast("web application stopped, see " + web.log(), () -> !web.isAlive())
                .ignoreExceptionsInstanceOf(IOException.class)
                .until(() -> anybody.get("/?q=arbre").body().contains("href=\"/signs/90000\""));

        // an administrator, with a list and a request
        Session alice = Session.registered(local, "alice");
        HttpResponse<String> created = alice.post("/lists", Map.of("name", "Nature"));
        list = created.headers().firstValue("Location").orElseThrow().replace(local.toString(), "");
        String id = list.substring(list.lastIndexOf('/') + 1);
        for (String uid : List.of("90000", "90003")) {
            alice.post("/signs/" + uid + "/lists/" + id, Map.of());
        }
        alice.post("/requests", Map.of("word", "Zoug", "language", "fr", "comment", "La ville et le canton"),
                "/requests");

        // the browser reaches the application through a tunnel to this machine
        Testcontainers.exposeHostPorts(port);
        // Chromium rather than Chrome: its image is also built for ARM
        browser = new BrowserWebDriverContainer(DockerImageName.parse(CHROMIUM)
                .asCompatibleSubstituteFor("selenium/standalone-chrome"))
                .withRecordingMode(BrowserWebDriverContainer.VncRecordingMode.SKIP, null);
        browser.start();
        base = "http://host.testcontainers.internal:" + port;
    }

    @AfterAll
    static void stopThem() throws InterruptedException {
        if (browser != null) {
            browser.stop();
        }
        if (web != null) {
            web.close();
        }
    }

    @AfterEach
    void closeTheBrowser() {
        if (driver != null) {
            driver.quit();
        }
    }

    @ParameterizedTest
    @EnumSource(Theme.class)
    void everyPageFitsComputersAndPhones(Theme theme) throws IOException {
        driver = open(theme);
        List<Page> pages = new ArrayList<>(List.of(
                new Page("home", "/"),
                new Page("results", "/?category=NATURE"),
                new Page("sign", "/signs/90000"),
                new Page("tour", "/tour/1"),
                new Page("help", "/help/search"),
                new Page("login", "/login"),
                new Page("register", "/register")));
        for (int i = 0; i < pages.size(); i++) {
            look(theme, i, pages.get(i));
        }

        logIn();
        pages = List.of(
                new Page("results-member", "/?category=NATURE"),
                new Page("sign-member", "/signs/90000"),
                new Page("lists", "/lists"),
                new Page("list", list),
                new Page("requests", "/requests"),
                new Page("admin", "/admin"),
                new Page("admin-requests", "/admin/requests"),
                new Page("admin-sync", "/admin/sync"),
                new Page("admin-bundles", "/admin/bundles"),
                new Page("admin-new-sign", "/admin/signs/new"));
        for (int i = 0; i < pages.size(); i++) {
            look(theme, 20 + i, pages.get(i));
        }
    }

    @Test
    void entriesStandInTwoColumnsOnComputersAndOneOnPhones() {
        driver = open(Theme.LIGHT);
        resize(Device.COMPUTER);
        // found by a word: by relevance, without initials
        driver.get(base + "/?q=demonstration&category=NATURE");
        List<WebElement> entries = driver.findElements(By.cssSelector("ul.signs > li"));
        assertThat(entries).hasSize(7);
        // down the first column, then the second
        assertThat(entries.get(1).getRect().getX()).isEqualTo(entries.get(0).getRect().getX());
        assertThat(entries.getLast().getRect().getX()).isGreaterThan(entries.get(0).getRect().getX());

        resize(Device.PHONE);
        assertThat(entries).extracting(entry -> entry.getRect().getX()).containsOnly(entries.get(0).getRect().getX());

        // the whole entry opens the sign
        String sign = entries.get(0).findElement(By.cssSelector("a.name")).getDomAttribute("href");
        entries.get(0).click();
        await().atMost(Duration.ofSeconds(10)).until(() -> driver.getCurrentUrl().endsWith(sign));
    }

    @Test
    void signsListedByNameStandUnderTheirInitials() {
        driver = open(Theme.LIGHT);
        resize(Device.COMPUTER);
        driver.get(base + "/?category=NATURE");

        assertThat(driver.findElements(By.cssSelector("h2.initial"))).extracting(WebElement::getText)
                .containsExactly("A", "D", "G", "J", "N", "R", "V");
    }

    @Test
    void theTourIsACardNextToWhatItPresents() {
        driver = open(Theme.LIGHT);
        resize(Device.COMPUTER);
        driver.get(base + "/tour/1");
        WebElement dialog = driver.findElement(By.id("tour"));
        WebElement search = driver.findElement(By.cssSelector("[data-tour=search]"));
        assertThat(search.getDomAttribute("class")).contains("tour-target");

        // Pico makes a dialog a modal that covers the window
        Rectangle box = dialog.getRect();
        assertThat(box.getWidth()).isLessThan(Device.COMPUTER.width / 2);
        assertThat(box.getHeight()).isLessThan(Device.COMPUTER.height / 2);
        assertThat(dialog.isDisplayed()).isTrue();

        dialog.sendKeys(Keys.ESCAPE);
        assertThat(dialog.isDisplayed()).isFalse();
    }

    @Test
    void aChosenThemeLooksLikeTheSystemsAndIsRemembered() {
        driver = open(Theme.LIGHT);
        resize(Device.COMPUTER);
        driver.get(base + "/");
        Map<String, Object> light = colors();

        choose("dark");
        assertThat(script("return document.documentElement.dataset.theme")).isEqualTo("dark");
        Map<String, Object> dark = colors();
        assertThat(dark).isNotEqualTo(light);
        driver.get(base + "/help");
        assertThat(colors()).as("remembered").isEqualTo(dark);

        choose("auto");
        assertThat(script("return document.documentElement.dataset.theme")).isNull();
        assertThat(colors()).isEqualTo(light);
        driver.quit();

        // the dark theme of the system is the one chosen, and light can be chosen over it
        driver = open(Theme.DARK);
        resize(Device.COMPUTER);
        driver.get(base + "/");
        assertThat(colors()).isEqualTo(dark);
        choose("light");
        assertThat(colors()).isEqualTo(light);
    }

    private void choose(String theme) {
        driver.findElement(By.cssSelector("nav.themes a[data-choice=" + theme + "]")).click();
        await("theme " + theme).atMost(Duration.ofSeconds(10))
                .until(() -> driver.getCurrentUrl().contains("theme=" + theme));
    }

    /** The colors of a page: ours, Pico's, and some of their uses. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> colors() {
        return (Map<String, Object>) script("""
                const root = getComputedStyle(document.documentElement);
                const colors = {};
                for (const name of ['--page', '--surface', '--tint', '--tint-border', '--ink', '--success', '--error',
                        '--shadow', '--shadow-raised', '--fr', '--fr-background', '--de', '--de-background', '--it',
                        '--it-background', '--pico-primary', '--pico-primary-background', '--pico-primary-hover',
                        '--pico-primary-hover-background', '--pico-primary-focus', '--pico-primary-underline',
                        '--pico-text-selection-color', '--pico-background-color', '--pico-color',
                        '--pico-muted-color']) {
                    colors[name] = root.getPropertyValue(name).trim();
                }
                colors.body = getComputedStyle(document.body).backgroundColor;
                colors.header = getComputedStyle(document.querySelector('header.site')).borderTopColor;
                colors.scheme = root.colorScheme;
                return colors;""");
    }

    private RemoteWebDriver open(Theme theme) {
        ChromeOptions options = new ChromeOptions()
                .addArguments("--headless=new", "--hide-scrollbars", "--force-device-scale-factor=1")
                .setExperimentalOption("prefs", Map.of("intl.accept_languages", "fr"));
        if (theme == Theme.DARK) {
            // the system's theme, which the pages follow (prefers-color-scheme)
            options.addArguments("--force-dark-mode");
        }
        return new RemoteWebDriver(browser.getSeleniumAddress(), options);
    }

    private void logIn() {
        driver.get(base + "/login");
        driver.findElement(By.id("username")).sendKeys("alice");
        driver.findElement(By.id("password")).sendKeys(PASSWORD);
        driver.findElement(By.cssSelector("main form button[type=submit]")).click();
        await("logged in").atMost(Duration.ofSeconds(10)).until(() -> !driver.getCurrentUrl().contains("/login"));
    }

    /** Checks a page on each device, and takes its screenshots. */
    private void look(Theme theme, int number, Page page) throws IOException {
        for (Device device : Device.values()) {
            resize(device);
            driver.get(base + page.path());
            String where = page.path() + " on a " + device + " in the " + theme + " theme";

            assertThat(script("return matchMedia('(prefers-color-scheme: dark)').matches"))
                    .as("dark theme").isEqualTo(theme == Theme.DARK);
            // Pico, from its webjar, and our stylesheet, with the brand and the font
            assertThat(script("return getComputedStyle(document.documentElement).fontFamily"))
                    .as("font of %s", where).asString().startsWith("\"Atkinson Hyperlegible Next\"");
            assertThat(script("""
                    return document.fonts.ready.then(() => [...document.fonts]
                        .some(font => font.family.includes('Atkinson') && font.status === 'loaded'))"""))
                    .as("font loaded on %s", where).isEqualTo(true);
            assertThat(script("return getComputedStyle(document.querySelector('header.site')).borderTopColor"))
                    .as("brand of %s", where).isEqualTo(theme.brand);
            assertThat(script("return innerWidth")).as("width of the window").isEqualTo((long) device.width);
            assertThat(script("""
                    return [...document.querySelectorAll('body *')]
                        .filter(e => e.getBoundingClientRect().right > innerWidth + 1)
                        .map(e => e.tagName.toLowerCase() + (e.className ? '.' + e.className : ''))""")
            ).as("elements wider than the window: %s scrolls sideways", where).asList().isEmpty();

            screenshot(theme, device, number, page);
        }
    }

    /** The whole page: the window is made as high as the page first. */
    private void screenshot(Theme theme, Device device, int number, Page page) throws IOException {
        long height = (Long) script("return document.documentElement.scrollHeight");
        resize(device.width, (int) Math.min(height, 6000));
        Path file = screenshots.resolve(theme.name().toLowerCase(Locale.ROOT) + "-"
                + device.name().toLowerCase(Locale.ROOT)).resolve(String.format(Locale.ROOT, "%02d-%s.png", number,
                page.name()));
        Files.createDirectories(file.getParent());
        Files.write(file, driver.getScreenshotAs(OutputType.BYTES));
        resize(device);
    }

    private void resize(Device device) {
        resize(device.width, device.height);
    }

    /** Sizes the page, inside the frame of the window. */
    private void resize(int width, int height) {
        driver.manage().window().setSize(new Dimension(width, height));
        long frame = (Long) script("return outerHeight - innerHeight");
        if (frame > 0) {
            driver.manage().window().setSize(new Dimension(width, height + (int) frame));
        }
    }

    private Object script(String script) {
        return driver.executeScript(script);
    }

    private record Page(String name, String path) {
    }

    private enum Theme {
        LIGHT("rgb(181, 18, 27)"),
        DARK("rgb(197, 47, 33)");

        /** The color of the brand, at the top of every page. */
        private final String brand;

        Theme(String brand) {
            this.brand = brand;
        }
    }

    private enum Device {
        COMPUTER(1280, 800),
        PHONE(390, 844);

        private final int width;
        private final int height;

        Device(int width, int height) {
            this.width = width;
            this.height = height;
        }
    }
}
