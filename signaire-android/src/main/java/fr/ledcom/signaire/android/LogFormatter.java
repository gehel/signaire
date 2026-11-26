package fr.ledcom.signaire.android;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * The messages of {@code java.util.logging} with their parameters, as on the JVM: Android's handler, which sends
 * them to logcat, writes them as the code gave them ("Ignoring the invalid bundle {0}: {1}"). An exception follows
 * its message with its stack trace, as Android's own formatter writes it. Nothing more: logcat has the time, the level
 * and the tag.
 */
final class LogFormatter extends Formatter {

    /** Gives the formatter to the handlers of a logger: on Android, those of the root logger send to logcat. */
    static void install(Logger logger) {
        for (Handler handler : logger.getHandlers()) {
            handler.setFormatter(new LogFormatter());
        }
    }

    @Override
    @SuppressFBWarnings(value = "INFORMATION_EXPOSURE_THROUGH_AN_ERROR_MESSAGE",
            justification = "To the log of the phone, as Android's own formatter writes it")
    public String format(LogRecord record) {
        String message = formatMessage(record);
        Throwable thrown = record.getThrown();
        if (thrown == null) {
            return message;
        }
        StringWriter trace = new StringWriter();
        try (PrintWriter out = new PrintWriter(trace)) {
            thrown.printStackTrace(out);
        }
        return message + "\n" + trace;
    }
}
