package fr.ledcom.signaire.android;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.StreamHandler;

import org.junit.jupiter.api.Test;

/** On the phone, the logs of the shared modules say what they are about: Android's handler writes "{0}". */
class LogFormatterTest {

    private final LogFormatter formatter = new LogFormatter();

    @Test
    void messagesHaveTheirParameters() {
        LogRecord record = new LogRecord(Level.WARNING, "Ignoring the invalid bundle {0}: {1}");
        record.setParameters(new Object[] {"files/bundles/broken.zip", "not a zip file"});
        assertThat(formatter.format(record))
                .isEqualTo("Ignoring the invalid bundle files/bundles/broken.zip: not a zip file");
    }

    @Test
    void messagesWithoutParametersAreAsGiven() {
        assertThat(formatter.format(new LogRecord(Level.INFO, "Downloads from {0}, once given"))).isEqualTo(
                "Downloads from {0}, once given");
    }

    @Test
    void anExceptionFollowsItsMessageWithItsStackTrace() {
        LogRecord record = new LogRecord(Level.WARNING, "Cannot read the signs of {0}");
        record.setParameters(new Object[] {"city.zip"});
        record.setThrown(new IOException("truncated"));
        assertThat(formatter.format(record)).startsWith("Cannot read the signs of city.zip\n"
                + "java.io.IOException: truncated\n\tat fr.ledcom.signaire.android.LogFormatterTest.");
    }

    @Test
    void theHandlersOfALoggerGetIt() {
        Logger root = Logger.getAnonymousLogger();
        Handler android = new StreamHandler();
        root.addHandler(android);
        LogFormatter.install(root);
        assertThat(android.getFormatter()).isInstanceOf(LogFormatter.class);
    }
}
