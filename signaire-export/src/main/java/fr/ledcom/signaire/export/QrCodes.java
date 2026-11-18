package fr.ledcom.signaire.export;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import org.apache.pdfbox.pdmodel.PDPageContentStream;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

/** QR codes, drawn as vector squares so that they print sharp at any size. */
final class QrCodes {

    private static final Map<EncodeHintType, Object> HINTS = Map.of(
            EncodeHintType.MARGIN, 0,
            EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
            EncodeHintType.CHARACTER_SET, "UTF-8");

    private QrCodes() {
    }

    /** The QR code of a URL, or empty when the URL can't be encoded. */
    static Optional<BitMatrix> encode(String url) {
        try {
            return Optional.of(new QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 0, 0, HINTS));
        } catch (WriterException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** Draws a QR code in a square. */
    static void draw(PDPageContentStream content, BitMatrix matrix, float x, float y, float size)
            throws IOException {
        float module = size / matrix.getWidth();
        content.setNonStrokingColor(0f);
        for (int row = 0; row < matrix.getHeight(); row++) {
            for (int column = 0; column < matrix.getWidth(); column++) {
                if (matrix.get(column, row)) {
                    content.addRect(x + column * module, y + size - (row + 1) * module, module, module);
                }
            }
        }
        content.fill();
    }
}
