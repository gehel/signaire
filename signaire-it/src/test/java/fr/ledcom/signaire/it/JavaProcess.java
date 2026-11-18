package fr.ledcom.signaire.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * A jar run in its own JVM, the way users run it, with its output in a log file. The XDG directories of the
 * process are under the work directory, never in the home of the user, and it can only reach this machine.
 */
final class JavaProcess implements AutoCloseable {

    private final Process process;
    private final Path log;

    private JavaProcess(Process process, Path log) {
        this.process = process;
        this.log = log;
    }

    static JavaProcess start(Path jar, Path work, String name, List<String> arguments) throws IOException {
        List<String> command = new ArrayList<>(List.of(
                ProcessHandle.current().info().command().orElse("java"),
                "-Duser.language=fr", "-Duser.country=CH",
                "-Dhttp.proxyHost=127.0.0.1", "-Dhttp.proxyPort=9", "-Dhttps.proxyHost=127.0.0.1",
                "-Dhttps.proxyPort=9", "-Dhttp.nonProxyHosts=localhost|127.0.0.1|[::1]",
                "-jar", jar.toString()));
        command.addAll(arguments);
        Path log = work.resolve(name + ".log");
        ProcessBuilder builder = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(log.toFile());
        Map<String, String> environment = builder.environment();
        environment.put("XDG_DATA_HOME", work.resolve("data").toString());
        environment.put("XDG_CONFIG_HOME", work.resolve("config").toString());
        environment.put("XDG_CACHE_HOME", work.resolve("cache").toString());
        environment.put("XDG_STATE_HOME", work.resolve("state").toString());
        return new JavaProcess(builder.start(), log);
    }

    /** A jar built by another module, named by a system property of the build. */
    static Path jar(String property) {
        Path jar = Path.of(System.getProperty(property));
        assertThat(jar).as("built by the modules this one depends on").exists();
        return jar;
    }

    /** A free port of this machine. */
    static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    static void deleteRecursively(Path directory) throws IOException {
        if (Files.exists(directory)) {
            try (Stream<Path> files = Files.walk(directory)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(file);
                }
            }
        }
    }

    /** Waits for the end of the process; returns its exit code. */
    int await(Duration timeout) throws InterruptedException {
        if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            throw new IllegalStateException("Still running after " + timeout + ", see " + log);
        }
        return process.exitValue();
    }

    boolean isAlive() {
        return process.isAlive();
    }

    Path log() {
        return log;
    }

    @Override
    public void close() throws InterruptedException {
        process.destroy();
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
        }
    }
}
