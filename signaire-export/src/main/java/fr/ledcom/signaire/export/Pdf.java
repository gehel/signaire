package fr.ledcom.signaire.export;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.encoding.GlyphList;
import org.apache.pdfbox.pdmodel.font.encoding.WinAnsiEncoding;

/**
 * Text in PDF pages, with the standard Helvetica fonts: they need no embedding and cover the French, German
 * and Italian alphabets (WinAnsi encoding). Other characters are replaced.
 */
final class Pdf {

    static final PDFont REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    static final PDFont BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    static final PDFont ITALIC = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);
    private static final float POINTS_PER_MILLIMETER = 72 / 25.4f;
    private static final String ELLIPSIS = "…";

    private final Map<Integer, Boolean> encodable = new HashMap<>();

    static float millimeters(float millimeters) {
        return millimeters * POINTS_PER_MILLIMETER;
    }

    /** The text, with the characters the fonts can't show replaced, on one line. */
    String clean(String text) {
        StringBuilder clean = new StringBuilder(text.length());
        text.codePoints().forEach(codePoint -> {
            if (Character.isWhitespace(codePoint)) {
                clean.append(' ');
            } else if (Boolean.TRUE.equals(encodable.computeIfAbsent(codePoint, Pdf::canEncode))) {
                clean.appendCodePoint(codePoint);
            } else {
                clean.append('?');
            }
        });
        return clean.toString();
    }

    private static boolean canEncode(int codePoint) {
        return WinAnsiEncoding.INSTANCE.contains(GlyphList.getAdobeGlyphList().codePointToName(codePoint));
    }

    static float width(PDFont font, float size, String text) {
        try {
            return font.getStringWidth(text) / 1000 * size;
        } catch (IOException | IllegalArgumentException e) {
            return text.length() * size / 2;
        }
    }

    /** Cuts text to fit a width, ending it with an ellipsis when cut. */
    String fit(PDFont font, float size, String text, float maxWidth) {
        String clean = clean(text);
        if (width(font, size, clean) <= maxWidth) {
            return clean;
        }
        String cut = clean;
        while (!cut.isEmpty() && width(font, size, cut + ELLIPSIS) > maxWidth) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut.strip() + ELLIPSIS;
    }

    /** Splits text into lines that fit a width; the last allowed line ends with an ellipsis when text remains. */
    List<String> wrap(PDFont font, float size, String text, float maxWidth, int maxLines) {
        List<String> lines = new ArrayList<>();
        if (maxLines <= 0) {
            return lines;
        }
        String[] words = clean(text).strip().split(" +");
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String candidate = line.isEmpty() ? words[i] : line + " " + words[i];
            if (width(font, size, candidate) <= maxWidth || line.isEmpty()) {
                line.setLength(0);
                line.append(candidate);
                continue;
            }
            if (lines.size() == maxLines - 1) {
                lines.add(fit(font, size, line + " " + String.join(" ", List.of(words).subList(i, words.length)),
                        maxWidth));
                return lines;
            }
            lines.add(fit(font, size, line.toString(), maxWidth));
            line.setLength(0);
            line.append(words[i]);
        }
        if (!line.isEmpty() && !line.toString().isBlank()) {
            lines.add(fit(font, size, line.toString(), maxWidth));
        }
        return lines;
    }

    /** Shows text that was already {@link #clean(String) cleaned}, from its baseline. */
    static void text(PDPageContentStream content, PDFont font, float size, float x, float y, String text)
            throws IOException {
        if (text.isEmpty()) {
            return;
        }
        content.beginText();
        content.setFont(font, size);
        content.newLineAtOffset(x, y);
        content.showText(text);
        content.endText();
    }
}
