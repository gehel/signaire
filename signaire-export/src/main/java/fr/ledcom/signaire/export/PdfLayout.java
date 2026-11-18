package fr.ledcom.signaire.export;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * How signs are laid out in a PDF.
 *
 * @param paper the paper the document is printed on
 * @param signsPerPage signs on each page, one of {@link #SIGNS_PER_PAGE}
 * @param mode sheets, a booklet, or flashcards
 */
public record PdfLayout(PaperSize paper, int signsPerPage, Mode mode) {

    /** Supported numbers of signs per page: they make regular grids. */
    public static final SortedSet<Integer> SIGNS_PER_PAGE = Collections.unmodifiableSortedSet(
            new TreeSet<>(List.of(1, 2, 4, 6, 8, 9, 12)));

    public PdfLayout {
        Objects.requireNonNull(paper, "paper");
        Objects.requireNonNull(mode, "mode");
        if (!SIGNS_PER_PAGE.contains(signsPerPage)) {
            throw new IllegalArgumentException("Unsupported number of signs per page: " + signsPerPage);
        }
    }

    /** ISO paper sizes, in millimeters, portrait. */
    public enum PaperSize {
        A4(210, 297),
        A5(148, 210),
        A6(105, 148);

        private final float widthMillimeters;
        private final float heightMillimeters;

        PaperSize(float widthMillimeters, float heightMillimeters) {
            this.widthMillimeters = widthMillimeters;
            this.heightMillimeters = heightMillimeters;
        }

        float width() {
            return Pdf.millimeters(widthMillimeters);
        }

        float height() {
            return Pdf.millimeters(heightMillimeters);
        }
    }

    /** Ways of printing the signs. */
    public enum Mode {
        /** Pages of the paper size, each with a grid of signs. */
        SHEETS,
        /**
         * A booklet: pages of half the paper size, printed two per side of landscape sheets, in the order that
         * makes a booklet once the sheets are printed on both sides (flipped on the short edge), stacked and
         * folded.
         */
        BOOKLET,
        /**
         * Cards to cut: pages with the images and QR codes of the signs alternate with pages with their words,
         * placed so that each word is behind its image when printed on both sides (flipped on the long edge).
         */
        FLASHCARDS,
    }
}
