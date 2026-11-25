package fr.ledcom.signaire.export;

import org.jspecify.annotations.Nullable;

/** Escaping for the HTML (Anki, EPUB) written by the exporters. */
final class Markup {

    private Markup() {
    }

    /** Text for HTML or XHTML content and attribute values. */
    static String html(@Nullable String text) {
        if (text == null) {
            return "";
        }
        StringBuilder escaped = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            switch (c) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&#39;");
                default -> escaped.append(c);
            }
        }
        return escaped.toString();
    }
}
