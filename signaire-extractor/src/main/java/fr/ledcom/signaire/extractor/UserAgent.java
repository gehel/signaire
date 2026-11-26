package fr.ledcom.signaire.extractor;

import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/**
 * The User-Agent header of the requests to SignSuisse. It names the project, so that SignSuisse can find out what
 * sends them, and the contact of the user, so that SignSuisse can reach them (if the load is too high, for example).
 *
 * @param value the header
 * @param withContact whether the header says how to reach the user (a contact, or a header of their own)
 */
public record UserAgent(String value, boolean withContact) {

    public static final String PROJECT = "https://github.com/gehel/signaire";

    private static final Pattern CONTROL_CHARACTERS = Pattern.compile("\\p{Cntrl}");

    /**
     * @param contact an email address or a URL where SignSuisse can reach the user, or null
     * @param override a header replacing the one of Signaire, or null
     */
    public static UserAgent of(@Nullable String contact, @Nullable String override) {
        String header = clean(override);
        if (!header.isEmpty()) {
            return new UserAgent(header, true);
        }
        String reachable = clean(contact);
        return reachable.isEmpty() ? new UserAgent("Signaire (+" + PROJECT + ")", false)
                : new UserAgent("Signaire (+" + PROJECT + "; " + reachable + ")", true);
    }

    /** Header values are a single line. */
    private static String clean(@Nullable String text) {
        return text == null ? "" : CONTROL_CHARACTERS.matcher(text).replaceAll("").strip();
    }
}
