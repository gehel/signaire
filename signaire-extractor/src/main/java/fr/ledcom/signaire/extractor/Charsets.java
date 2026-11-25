package fr.ledcom.signaire.extractor;

import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/** The charset of a response, from its {@code Content-Type}. */
final class Charsets {

    private static final Pattern CHARSET = Pattern.compile("charset=\"?([^\";\\s]+)", Pattern.CASE_INSENSITIVE);

    private Charsets() {
    }

    /** UTF-8 when the response doesn't say, or names a charset Java doesn't know. */
    static Charset of(@Nullable String contentType) {
        if (contentType != null) {
            Matcher matcher = CHARSET.matcher(contentType);
            if (matcher.find()) {
                try {
                    return Charset.forName(matcher.group(1).toUpperCase(Locale.ROOT));
                } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
                    return StandardCharsets.UTF_8;
                }
            }
        }
        return StandardCharsets.UTF_8;
    }
}
