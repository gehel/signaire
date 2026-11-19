package fr.ledcom.signaire.export;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import com.google.zxing.common.BitMatrix;

import fr.ledcom.signaire.model.Media;
import fr.ledcom.signaire.model.Sign;

/** Draws signs in the cells of PDF pages: whole signs, or the two sides of flashcards. */
final class SignCells {

    private static final float GRAY = 0.4f;
    private static final float LINE_SPACING = 1.25f;

    private final PDDocument document;
    private final MediaSource media;
    private final Labels labels;
    private final Pdf pdf = new Pdf();
    /** Images by media, embedded once per document. */
    private final Map<String, Optional<PDImageXObject>> images = new HashMap<>();

    SignCells(PDDocument document, MediaSource media, Labels labels) {
        this.document = document;
        this.media = media;
        this.labels = labels;
    }

    Pdf pdf() {
        return pdf;
    }

    /** A sign with its name, image, QR code and definition. */
    void drawSign(PDPageContentStream content, Box cell, Sign sign) throws IOException {
        drawCutLines(content, cell);
        float size = Math.min(cell.width(), cell.height());
        float padding = Math.max(3, size * 0.04f);
        Box inner = cell.inset(padding);
        float nameSize = Math.clamp(size * 0.075f, 7, 20);
        float smallSize = Math.max(5, nameSize * 0.55f);
        float textSize = Math.max(5, nameSize * 0.5f);

        float baseline = inner.top() - nameSize;
        content.setNonStrokingColor(0f);
        Pdf.text(content, Pdf.BOLD, nameSize, inner.x(), baseline, pdf.fit(Pdf.BOLD, nameSize, sign.name(),
                inner.width()));
        baseline -= smallSize * 1.6f;
        content.setNonStrokingColor(GRAY);
        Pdf.text(content, Pdf.REGULAR, smallSize, inner.x(), baseline, pdf.fit(Pdf.REGULAR, smallSize,
                subtitle(sign), inner.width()));
        float contentTop = baseline - smallSize;

        Optional<String> qrUrl = qrUrl(sign);
        float qrSize = qrUrl.isPresent() ? Math.min(inner.width() * 0.3f, inner.height() * 0.3f) : 0;
        float captionSize = Math.max(4, qrSize * 0.07f);
        float bottomHeight = qrUrl.isPresent() ? qrSize + captionSize * 2.5f : inner.height() * 0.3f;
        Box bottom = new Box(inner.x(), inner.y(), inner.width(), bottomHeight);
        Box middle = new Box(inner.x(), bottom.top() + padding, inner.width(),
                Math.max(0, contentTop - bottom.top() - 2 * padding));

        Optional<PDImageXObject> image = image(sign);
        String definition = sign.definition() != null ? sign.definition() : sign.periphrase();
        if (image.isPresent()) {
            drawImage(content, image.get(), middle);
            float textWidth = inner.width() - (qrSize > 0 ? qrSize + padding : 0);
            drawParagraph(content, Pdf.REGULAR, textSize, definition, new Box(bottom.x(), bottom.y(), textWidth,
                    bottom.height()));
        } else {
            drawParagraph(content, Pdf.REGULAR, textSize, definition, middle);
        }
        if (qrUrl.isPresent()) {
            drawQrCode(content, qrUrl.get(), bottom.right() - qrSize, bottom.y() + captionSize * 2.5f, qrSize,
                    captionSize);
        }
    }

    /** The front of a flashcard: the image of the sign and the QR code of its video. */
    void drawFront(PDPageContentStream content, Box cell, Sign sign) throws IOException {
        drawCutLines(content, cell);
        float size = Math.min(cell.width(), cell.height());
        float padding = Math.max(3, size * 0.05f);
        Box inner = cell.inset(padding);
        Optional<String> qrUrl = qrUrl(sign);
        float qrSize = qrUrl.isPresent() ? Math.min(inner.width(), inner.height()) * 0.28f : 0;
        float captionSize = Math.max(4, qrSize * 0.07f);
        float qrZone = qrUrl.isPresent() ? qrSize + captionSize * 2.5f + padding : 0;
        Optional<PDImageXObject> image = image(sign);
        if (image.isPresent()) {
            drawImage(content, image.get(), new Box(inner.x(), inner.y() + qrZone, inner.width(),
                    inner.height() - qrZone));
        }
        if (qrUrl.isPresent()) {
            float x = image.isPresent() ? inner.right() - qrSize : inner.x() + (inner.width() - qrSize) / 2;
            float y = image.isPresent() ? inner.y() + captionSize * 2.5f
                    : inner.y() + (inner.height() - qrSize) / 2;
            drawQrCode(content, qrUrl.get(), x, y, qrSize, captionSize);
        }
    }

    /** The back of a flashcard: the word, its language and categories, its definition. */
    void drawBack(PDPageContentStream content, Box cell, Sign sign) throws IOException {
        drawCutLines(content, cell);
        float size = Math.min(cell.width(), cell.height());
        float padding = Math.max(3, size * 0.06f);
        Box inner = cell.inset(padding);
        float nameSize = Math.clamp(size * 0.1f, 8, 28);
        float smallSize = Math.max(5, nameSize * 0.45f);
        float textSize = Math.max(5, nameSize * 0.42f);
        float baseline = inner.y() + inner.height() * 0.62f;
        String name = pdf.fit(Pdf.BOLD, nameSize, sign.name(), inner.width());
        content.setNonStrokingColor(0f);
        Pdf.text(content, Pdf.BOLD, nameSize, centered(inner, Pdf.BOLD, nameSize, name), baseline, name);
        baseline -= smallSize * 1.8f;
        String subtitle = pdf.fit(Pdf.REGULAR, smallSize, subtitle(sign), inner.width());
        content.setNonStrokingColor(GRAY);
        Pdf.text(content, Pdf.REGULAR, smallSize, centered(inner, Pdf.REGULAR, smallSize, subtitle), baseline,
                subtitle);
        String definition = sign.definition() != null ? sign.definition() : sign.periphrase();
        drawParagraph(content, Pdf.REGULAR, textSize, definition, new Box(inner.x(), inner.y(), inner.width(),
                baseline - smallSize - inner.y()));
    }

    /** Language and categories of a sign: "LSF · Ville, Nature". */
    String subtitle(Sign sign) {
        String categories = sign.categories().stream().map(labels::category).collect(Collectors.joining(", "));
        String language = labels.signLanguageShort(sign.language());
        return categories.isEmpty() ? language : language + " · " + categories;
    }

    private void drawQrCode(PDPageContentStream content, String url, float x, float y, float size,
            float captionSize) throws IOException {
        Optional<BitMatrix> qrCode = QrCodes.encode(url);
        if (qrCode.isEmpty()) {
            return;
        }
        QrCodes.draw(content, qrCode.get(), x, y, size);
        content.setNonStrokingColor(GRAY);
        List<String> caption = pdf.wrap(Pdf.REGULAR, captionSize, labels.get("scanToWatch"), size + 20, 2);
        float baseline = y - captionSize * LINE_SPACING;
        for (String line : caption) {
            float width = Pdf.width(Pdf.REGULAR, captionSize, line);
            Pdf.text(content, Pdf.REGULAR, captionSize, x + size - width, baseline, line);
            baseline -= captionSize * LINE_SPACING;
        }
    }

    private void drawParagraph(PDPageContentStream content, PDFont font, float size, String text, Box box)
            throws IOException {
        if (text == null || text.isBlank() || box.height() < size) {
            return;
        }
        int maxLines = (int) (box.height() / (size * LINE_SPACING));
        content.setNonStrokingColor(0f);
        float baseline = box.top() - size;
        for (String line : pdf.wrap(font, size, text, box.width(), maxLines)) {
            Pdf.text(content, font, size, box.x(), baseline, line);
            baseline -= size * LINE_SPACING;
        }
    }

    private static void drawImage(PDPageContentStream content, PDImageXObject image, Box box) throws IOException {
        if (box.width() <= 0 || box.height() <= 0) {
            return;
        }
        float scale = Math.min(box.width() / image.getWidth(), box.height() / image.getHeight());
        float width = image.getWidth() * scale;
        float height = image.getHeight() * scale;
        content.drawImage(image, box.x() + (box.width() - width) / 2, box.y() + (box.height() - height) / 2,
                width, height);
    }

    /** Light lines around the cell, to cut cards apart. */
    private static void drawCutLines(PDPageContentStream content, Box cell) throws IOException {
        content.setStrokingColor(0.85f);
        content.setLineWidth(0.3f);
        content.addRect(cell.x(), cell.y(), cell.width(), cell.height());
        content.stroke();
    }

    private Optional<PDImageXObject> image(Sign sign) {
        return MediaFiles.firstImage(sign).flatMap(image -> images.computeIfAbsent(MediaFiles.fileName(image),
                name -> embed(image, name)));
    }

    private Optional<PDImageXObject> embed(Media image, String name) {
        return media.read(image).flatMap(bytes -> {
            try {
                return Optional.of(PDImageXObject.createFromByteArray(document, bytes, name));
            } catch (IOException | IllegalArgumentException _) {
                return Optional.empty();
            }
        });
    }

    /** The QR codes link to the video of the sign on SignSuisse, else to its page. */
    private static Optional<String> qrUrl(Sign sign) {
        return MediaFiles.firstVideo(sign).map(Media::sourceUrl).or(() -> Optional.ofNullable(sign.sourceUrl()));
    }

    private static float centered(Box box, PDFont font, float size, String text) {
        return box.x() + (box.width() - Pdf.width(font, size, text)) / 2;
    }
}
