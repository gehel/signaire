package fr.ledcom.signaire.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.awaitility.core.ConditionTimeoutException;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * An Android device for the tests, through adb: the one adb sees, else an emulator started for them from an Android
 * Virtual Device ({@code signaire.android.avd}), created when missing, and stopped after. Screens are read with
 * uiautomator, and driven with taps and typing.
 */
final class AndroidDevice implements AutoCloseable {

    private static final Duration BOOT = Duration.ofMinutes(5);
    private static final Duration SCREEN = Duration.ofSeconds(15);
    private static final int TYPING_ATTEMPTS = 4;
    /** How far a color of the screen may be from that of a video, on each channel: YCbCr, then RGB again. */
    private static final int VIDEO_ROUNDING = 24;
    private static final int MAX_SWIPES = 10;
    private static final Pattern JAVA_CRASH = Pattern.compile("Process: (\\S+), PID: (\\d+)");
    /** In a line of the runtime's crash ("Fatal signal …"), the process and the end of its name. */
    private static final Pattern RUNTIME_CRASH = Pattern.compile(" pid (\\d++) \\(([^)]++)\\)");
    /** The name of a window, at the end of a line of {@code dumpsys window visible}. */
    private static final Pattern WINDOW = Pattern.compile(" u0 ([^}]++)}:$");
    private static final Pattern BOUNDS = Pattern.compile("\\[(\\d+),(\\d+)]\\[(\\d+),(\\d+)]");

    private final Path adb;
    private final Process emulator;

    private AndroidDevice(Path sdk, Process emulator) {
        this.adb = sdk.resolve("platform-tools/adb");
        this.emulator = emulator;
    }

    /** Whether there is a device for the tests: one adb sees, or the system image to emulate one. */
    static boolean available(Path sdk, String image) throws IOException, InterruptedException {
        return Files.isDirectory(sdk.resolve(image.replace(';', '/')))
                || new AndroidDevice(sdk, null).run("devices").lines().anyMatch(line -> line.endsWith("\tdevice"));
    }

    /** The device adb sees, or an emulator booted for the tests. */
    static AndroidDevice connect(Path sdk, String avd, String image) throws IOException, InterruptedException {
        AndroidDevice device = new AndroidDevice(sdk, null);
        if (device.run("devices").lines().anyMatch(line -> line.endsWith("\tdevice"))) {
            return device;
        }
        // the same place for avdmanager and the emulator, which look in different ones on some machines
        Path avds = Files.createDirectories(Path.of(System.getProperty("user.home"), ".android", "avd"));
        Path log = Path.of(System.getProperty("signaire.it.work"), "emulator.log");
        Files.createDirectories(log.getParent());
        if (!Files.isDirectory(avds.resolve(avd + ".avd"))) {
            ProcessBuilder create = new ProcessBuilder(sdk.resolve("cmdline-tools/latest/bin/avdmanager").toString(),
                    "create", "avd", "--force", "-n", avd, "-k", image, "-d", "pixel_6")
                    .redirectErrorStream(true).redirectOutput(log.toFile());
            create.environment().put("ANDROID_AVD_HOME", avds.toString());
            Process created = create.start();
            created.getOutputStream().write("no\n".getBytes(StandardCharsets.UTF_8));
            created.getOutputStream().close();
            assertThat(created.waitFor()).as("avdmanager create avd %s, see emulator.log", avd).isZero();
        }
        ProcessBuilder start = new ProcessBuilder(sdk.resolve("emulator/emulator").toString(), "-avd", avd,
                "-no-window", "-no-audio", "-no-boot-anim", "-no-snapshot", "-gpu", "swiftshader_indirect")
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()));
        start.environment().put("ANDROID_AVD_HOME", avds.toString());
        Process emulator = start.start();
        AndroidDevice started = new AndroidDevice(sdk, emulator);
        started.awaitBoot();
        started.run("shell", "input", "keyevent", "KEYCODE_WAKEUP");
        started.run("shell", "wm", "dismiss-keyguard");
        // animations only slow an emulator without a GPU down
        for (String scale : List.of("window_animation_scale", "transition_animation_scale", "animator_duration_scale")) {
            started.run("shell", "settings", "put", "global", scale, "0");
        }
        return started;
    }

    private void awaitBoot() {
        await("emulator booted, see emulator.log").atMost(BOOT).pollInterval(Duration.ofSeconds(2))
                .failFast("emulator stopped, see emulator.log", () -> !emulator.isAlive())
                .until(() -> bootCompleted().equals("1"));
    }

    private String bootCompleted() throws IOException, InterruptedException {
        Process booted = new ProcessBuilder(adb.toString(), "shell", "getprop", "sys.boot_completed")
                .redirectErrorStream(true).start();
        String output = new String(booted.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        booted.waitFor();
        return output;
    }

    /** Runs adb, and returns what it writes; fails if adb does. */
    String run(String... arguments) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of(adb.toString()));
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor()).as("%s: %s", command, output).isZero();
        return output;
    }

    /** Copies a file into the files of a debuggable app. */
    void pushToApp(Path file, String app, String target) throws IOException, InterruptedException {
        String temporary = "/data/local/tmp/" + file.getFileName();
        run("push", file.toString(), temporary);
        // the app can't read /data/local/tmp: the shell reads it for the app
        run("shell", "cat " + temporary + " | run-as " + app + " sh -c 'mkdir -p $(dirname " + target + ") && cat > "
                + target + "'");
        run("shell", "rm", temporary);
    }

    /** Copies a file of a debuggable app to the computer. */
    void pullFromApp(String app, String file, Path target) throws IOException, InterruptedException {
        Process cat = new ProcessBuilder(adb.toString(), "exec-out", "run-as", app, "cat", file).start();
        byte[] content = cat.getInputStream().readAllBytes();
        assertThat(cat.waitFor()).as("run-as %s cat %s", app, file).isZero();
        Files.createDirectories(target.getParent());
        Files.write(target, content);
    }

    /**
     * The screen, as uiautomator sees it: it fails while the screen moves, and is asked again; its first reading on a
     * device just booted takes seconds. An emulator just booted may say that the system "isn't responding": the
     * screen is read again after waiting for it.
     */
    String screen() {
        try {
            return await("uiautomator reads the screen").atMost(Duration.ofSeconds(60))
                    .pollInterval(Duration.ofMillis(500)).ignoreExceptions()
                    .until(this::settledScreen, Objects::nonNull);
        } catch (ConditionTimeoutException e) {
            throw new AssertionError("uiautomator cannot read the screen", e);
        }
    }

    /** The screen; null while uiautomator can't read it, or when the system said it wasn't responding. */
    private String settledScreen() throws IOException, InterruptedException {
        String dumped = run("exec-out", "uiautomator", "dump", "/dev/tty");
        if (!dumped.contains("<?xml")) {
            return null;
        }
        String screen = dumped.substring(dumped.indexOf("<?xml"), dumped.lastIndexOf('>') + 1);
        if (texts(screen).stream().noneMatch(text -> text.endsWith("isn't responding"))) {
            return screen;
        }
        Optional<int[]> wait = find(screen, "Wait");
        if (wait.isPresent()) {
            run("shell", "input", "tap", String.valueOf(wait.get()[0]), String.valueOf(wait.get()[1]));
        }
        return null;
    }

    /** Waits for a view showing a text (case ignored) or with an id, and returns its center. */
    int[] waitFor(String textOrId) {
        return waitFor(textOrId, SCREEN);
    }

    /** As {@link #waitFor(String)}, for longer: a download, for one. */
    int[] waitFor(String textOrId, Duration timeout) {
        try {
            return await("view " + textOrId).atMost(timeout).pollInterval(Duration.ofMillis(500))
                    .until(() -> find(screen(), textOrId), Optional::isPresent).orElseThrow();
        } catch (ConditionTimeoutException e) {
            throw new AssertionError("No view \"" + textOrId + "\" on the screen: " + texts(screen()), e);
        }
    }

    /**
     * Waits for a screen showing all these texts, and returns its texts: a screen read once, as it changes, may show
     * none.
     */
    List<String> waitForTexts(String... expected) {
        List<String> wanted = List.of(expected);
        try {
            return await("texts " + wanted).atMost(SCREEN).pollInterval(Duration.ofMillis(500))
                    .until(() -> texts(screen()), texts -> texts.containsAll(wanted));
        } catch (ConditionTimeoutException e) {
            throw new AssertionError("Not all of " + wanted + " on the screen: " + texts(screen()), e);
        }
    }

    /** Scrolls the first view of the screen that scrolls, until a view shows a text (case ignored) or has an id. */
    void scrollTo(String textOrId) throws IOException, InterruptedException {
        for (int swipes = 0; swipes < MAX_SWIPES; swipes++) {
            String screen = screen();
            if (find(screen, textOrId).isPresent()) {
                return;
            }
            int[] list = nodes(screen).stream().filter(node -> node.getAttribute("scrollable").equals("true"))
                    .map(AndroidDevice::bounds).flatMap(Optional::stream).findFirst().orElseThrow(
                            () -> new AssertionError("Nothing scrolls to \"" + textOrId + "\": " + texts(screen)));
            String x = String.valueOf((list[0] + list[2]) / 2);
            int height = list[3] - list[1];
            run("shell", "input", "swipe", x, String.valueOf(list[1] + height * 3 / 4), x,
                    String.valueOf(list[1] + height / 4), "300");
        }
        throw new AssertionError("No view \"" + textOrId + "\" after scrolling: " + texts(screen()));
    }

    void tap(String textOrId) throws IOException, InterruptedException {
        int[] center = waitFor(textOrId);
        run("shell", "input", "tap", String.valueOf(center[0]), String.valueOf(center[1]));
    }

    /** Empties the field that has the focus, of up to 40 characters. */
    private void clear() throws IOException, InterruptedException {
        List<String> keys = new ArrayList<>(List.of("shell", "input", "keyevent", "KEYCODE_MOVE_END"));
        keys.addAll(Collections.nCopies(40, "KEYCODE_DEL"));
        run(keys.toArray(String[]::new));
    }

    /** Types in the view that has the focus: ASCII only, as adb types. */
    void type(String text) throws IOException, InterruptedException {
        run("shell", "input", "text", text.replace(" ", "%s"));
    }

    /**
     * Types a text in a field (its id, or its hint while empty), again if the screen doesn't show it: on an emulator
     * just booted and busy, a key may go to a dialog of the system that doesn't respond, or nowhere. The field is
     * emptied before each try, so that keys that were only late don't stay.
     */
    void typeIn(String field, String text) throws IOException, InterruptedException {
        tap(field);
        for (int attempt = 1;; attempt++) {
            clear();
            type(text);
            try {
                await("\"" + text + "\" typed").atMost(SCREEN).pollInterval(Duration.ofMillis(500))
                        .until(() -> texts(screen()).contains(text));
                return;
            } catch (ConditionTimeoutException e) {
                if (attempt == TYPING_ATTEMPTS) {
                    throw new AssertionError("No \"" + text + "\" typed in \"" + field + "\" (" + windows() + "): "
                            + texts(screen()), e);
                }
            }
        }
    }

    /**
     * The window that has the focus (the keys go there), the app's, and the windows on the screen, top first:
     * uiautomator reads the app's alone.
     */
    private String windows() throws IOException, InterruptedException {
        String focus = run("shell", "dumpsys", "window").lines()
                .filter(line -> line.contains("mCurrentFocus") || line.contains("mFocusedApp")).map(String::strip)
                .distinct().collect(Collectors.joining("; "));
        String windows = run("shell", "dumpsys", "window", "visible").lines().map(String::strip)
                .filter(line -> line.startsWith("Window #")).map(AndroidDevice::windowName)
                .collect(Collectors.joining(", "));
        return focus + "; on the screen: " + windows;
    }

    private static String windowName(String line) {
        Matcher window = WINDOW.matcher(line);
        return window.find() ? window.group(1) : line;
    }

    void key(String key) throws IOException, InterruptedException {
        run("shell", "input", "keyevent", key);
    }

    void screenshot(Path file) throws IOException, InterruptedException {
        byte[] png = capture();
        Files.createDirectories(file.getParent());
        Files.write(file, png);
    }

    /**
     * Waits for a point of the screen to show the color of a video, give or take the rounding of its conversions: a
     * video is black until it plays.
     */
    void waitForColor(int[] point, Color expected) throws IOException, InterruptedException {
        try {
            await("color " + expected).atMost(SCREEN).pollInterval(Duration.ofMillis(500))
                    .until(() -> color(point), color -> close(color, expected));
        } catch (ConditionTimeoutException e) {
            throw new AssertionError("The screen shows " + color(point) + " at " + point[0] + "," + point[1] + ", not "
                    + expected + ": " + texts(screen()), e);
        }
    }

    /** Waits for a part of the screen (left, top, right, bottom) to stay the same for a second: a video that ended. */
    void waitForStill(int[] box) throws IOException, InterruptedException {
        AtomicReference<int[]> previous = new AtomicReference<>(pixels(box));
        await("still at " + Arrays.toString(box)).atMost(SCREEN).pollInterval(Duration.ofSeconds(1))
                .until(() -> Arrays.equals(previous.getAndSet(pixels(box)), previous.get()));
    }

    /** Waits for a part of the screen to change: a video playing. */
    void waitForMotion(int[] box) throws IOException, InterruptedException {
        AtomicReference<int[]> previous = new AtomicReference<>(pixels(box));
        await("moving at " + Arrays.toString(box)).atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofMillis(200))
                .until(() -> !Arrays.equals(previous.getAndSet(pixels(box)), previous.get()));
    }

    private int[] pixels(int[] box) throws IOException, InterruptedException {
        BufferedImage screen = ImageIO.read(new ByteArrayInputStream(capture()));
        int width = box[2] - box[0];
        return screen.getRGB(box[0], box[1], width, box[3] - box[1], null, 0, width);
    }

    private Color color(int[] point) throws IOException, InterruptedException {
        BufferedImage screen = ImageIO.read(new ByteArrayInputStream(capture()));
        return new Color(screen.getRGB(point[0], point[1]));
    }

    private static boolean close(Color color, Color expected) {
        return close(color.getRed(), expected.getRed()) && close(color.getGreen(), expected.getGreen())
                && close(color.getBlue(), expected.getBlue());
    }

    private static boolean close(int channel, int expected) {
        return channel >= expected - VIDEO_ROUNDING && channel <= expected + VIDEO_ROUNDING;
    }

    /** The screen, in PNG. */
    private byte[] capture() throws IOException, InterruptedException {
        Process capture = new ProcessBuilder(adb.toString(), "exec-out", "screencap", "-p").start();
        byte[] png = capture.getInputStream().readAllBytes();
        assertThat(capture.waitFor()).isZero();
        return png;
    }

    /** What a tag (a logger's simple name, for {@code java.util.logging}) wrote since the log was last cleared. */
    List<String> log(String tag) throws IOException, InterruptedException {
        return run("logcat", "-d", "-s", tag + ":V").lines().toList();
    }

    /**
     * The crashes of an app since the log was last cleared, with their stack traces: those of Java ("Process: app, PID:
     * n"), and those of the runtime ("Fatal signal … pid n (end of the app's name)").
     */
    List<String> crashes(String app) throws IOException, InterruptedException {
        List<String> log = run("logcat", "-d", "-b", "crash").lines().toList();
        List<String> processes = new ArrayList<>();
        for (String line : log) {
            Matcher java = JAVA_CRASH.matcher(line);
            Matcher runtime = RUNTIME_CRASH.matcher(line);
            if (java.find() && java.group(1).equals(app)) {
                processes.add(java.group(2));
            } else if (line.contains("Fatal signal") && runtime.find() && app.endsWith(runtime.group(2))) {
                processes.add(runtime.group(1));
            }
        }
        // the lines of a crash carry the process id of the line naming the app
        return log.stream().filter(line -> processes.stream().anyMatch(process -> line.matches(
                "\\S+ \\S+\\s+" + process + "\\s.*"))).toList();
    }

    @Override
    public void close() throws IOException, InterruptedException {
        if (emulator != null) {
            new ProcessBuilder(adb.toString(), "emu", "kill").start().waitFor();
            if (!emulator.waitFor(1, TimeUnit.MINUTES)) {
                emulator.destroyForcibly();
            }
        }
    }

    /** The center of the first view whose text, description or id is this. */
    static Optional<int[]> find(String screen, String textOrId) {
        return bounds(screen, textOrId).map(box -> new int[] {(box[0] + box[2]) / 2, (box[1] + box[3]) / 2});
    }

    /** Left, top, right and bottom of the first view whose text, description or id is this. */
    static Optional<int[]> bounds(String screen, String textOrId) {
        return node(screen, textOrId).flatMap(AndroidDevice::bounds);
    }

    /** Whether the first view whose text, description or id is this is checked: a check box. */
    static boolean checked(String screen, String textOrId) {
        return node(screen, textOrId).map(node -> "true".equals(node.getAttribute("checked"))).orElse(false);
    }

    /** The first view on the screen whose text, description or id is this. */
    private static Optional<Element> node(String screen, String textOrId) {
        String wanted = textOrId.toLowerCase(Locale.ROOT);
        for (Element node : nodes(screen)) {
            for (String attribute : List.of("text", "content-desc", "resource-id")) {
                String value = node.getAttribute(attribute).toLowerCase(Locale.ROOT);
                if ((value.equals(wanted) || value.endsWith(":id/" + wanted)) && bounds(node).isPresent()) {
                    return Optional.of(node);
                }
            }
        }
        return Optional.empty();
    }

    /** Left, top, right and bottom of a view. */
    private static Optional<int[]> bounds(Element node) {
        Matcher bounds = BOUNDS.matcher(node.getAttribute("bounds"));
        return bounds.matches() ? Optional.of(new int[] {Integer.parseInt(bounds.group(1)),
            Integer.parseInt(bounds.group(2)), Integer.parseInt(bounds.group(3)), Integer.parseInt(bounds.group(4))})
                : Optional.empty();
    }

    /** The texts of the screen, to say what was there instead. */
    static List<String> texts(String screen) {
        return nodes(screen).stream().map(node -> node.getAttribute("text")).filter(text -> !text.isEmpty()).toList();
    }

    private static List<Element> nodes(String screen) {
        if (screen.isEmpty()) {
            return List.of();
        }
        try (InputStream in = new ByteArrayInputStream(screen.getBytes(StandardCharsets.UTF_8))) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            NodeList nodes = factory.newDocumentBuilder().parse(in).getElementsByTagName("node");
            List<Element> elements = new ArrayList<>();
            for (int i = 0; i < nodes.getLength(); i++) {
                elements.add((Element) nodes.item(i));
            }
            return elements;
        } catch (IOException | ParserConfigurationException | SAXException e) {
            throw new AssertionError("Cannot read the screen: " + screen, e);
        }
    }
}
