package fr.ledcom.signaire.web;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.util.Arrays;
import java.util.Map;

import org.springframework.boot.SpringApplication;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Starts the standalone application: Signaire for one computer, started with a double click. The {@code standalone}
 * profile ({@code application-standalone.properties}) keeps the accounts in an H2 database in the state directory of
 * the user, listens to this computer only, and opens the browser once started. When Signaire already runs, this
 * opens the browser only.
 */
public final class StandaloneApplication {

    /** The port of the standalone application; {@code --server.port=…} changes it. */
    static final int DEFAULT_PORT = 8180;
    private static final String PORT_OPTION = "--server.port=";
    private static final int CONNECT_TIMEOUT_MILLIS = 500;

    private StandaloneApplication() {
        // main only
    }

    public static void main(String... args) {
        int port = port(args);
        if (inUse(port)) {
            System.getLogger(StandaloneApplication.class.getName()).log(System.Logger.Level.INFO,
                    "Signaire already runs on port {0,number,#}: opening it", port);
            Browser.open(URI.create("http://localhost:" + port + "/"));
            return;
        }
        SpringApplication application = new SpringApplication(SignaireWebApplication.class);
        application.setAdditionalProfiles("standalone");
        // to open the browser
        application.setHeadless(false);
        application.setDefaultProperties(Map.of("server.port", Integer.toString(port)));
        application.run(args);
    }

    /** The port given on the command line, or the default one. */
    static int port(String... args) {
        return Arrays.stream(args)
                .filter(arg -> arg.startsWith(PORT_OPTION))
                .mapToInt(arg -> Integer.parseInt(arg.substring(PORT_OPTION.length())))
                .reduce((first, second) -> second)
                .orElse(DEFAULT_PORT);
    }

    /** Whether something, Signaire most likely, listens to the port on this computer. */
    @SuppressFBWarnings(value = {"UNENCRYPTED_SOCKET", "EXS_EXCEPTION_SOFTENING_RETURN_FALSE"},
            justification = "only connects to this computer, to know whether the port is taken: no data, no error")
    static boolean inUse(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), CONNECT_TIMEOUT_MILLIS);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
