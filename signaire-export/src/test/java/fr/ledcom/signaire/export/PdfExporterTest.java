package fr.ledcom.signaire.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import fr.ledcom.signaire.export.PdfLayout.Mode;
import fr.ledcom.signaire.export.PdfLayout.PaperSize;

class PdfExporterTest {

    private final PdfExporter exporter = new PdfExporter(ExportTestData.MEDIA);

    @Test
    void sheetsHaveTheChosenPaperSizeAndNumberOfSignsPerPage() throws IOException {
        try (PDDocument pdf = export(9, new PdfLayout(PaperSize.A5, 4, Mode.SHEETS), Locale.FRENCH)) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(3);
            assertSize(pdf.getPage(0), PDRectangle.A5.getWidth(), PDRectangle.A5.getHeight());
        }
    }

    @Test
    void sheetsShowTheSigns() throws IOException {
        try (PDDocument pdf = export(3, new PdfLayout(PaperSize.A4, 6, Mode.SHEETS), Locale.FRENCH)) {
            String text = text(pdf, 1, 1);
            assertThat(text)
                    .contains("Famille", "3 signes")
                    .contains("LAUSANNE", "LSF", "Ville", "Capitale du canton de Vaud")
                    .contains("KATZE", "DSGS", "GATTO", "Piccolo felino domestico.")
                    .contains("Scanner pour voir le signe")
                    .contains("SignSuisse", "1 / 1");
            assertThat(images(pdf.getPage(0))).isEqualTo(1);
        }
    }

    @Test
    void labelsAreInTheLanguageOfTheRequest() throws IOException {
        try (PDDocument pdf = export(3, new PdfLayout(PaperSize.A4, 6, Mode.SHEETS), Locale.GERMAN)) {
            assertThat(text(pdf, 1, 1)).contains("3 Gebärden", "Scannen, um die Gebärde zu sehen", "Stadt");
        }
    }

    @Test
    void smallPagesHoldOneSignEach() throws IOException {
        try (PDDocument pdf = export(3, new PdfLayout(PaperSize.A6, 1, Mode.SHEETS), Locale.FRENCH)) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(3);
            assertSize(pdf.getPage(0), PDRectangle.A6.getWidth(), PDRectangle.A6.getHeight());
            assertThat(text(pdf, 2, 2)).contains("KATZE").doesNotContain("LAUSANNE");
        }
    }

    @Test
    void bookletsArePrintedTwoPagesPerSideOfLandscapeSheets() throws IOException {
        // 9 signs, 2 per page: 5 pages, completed to 8 pages, on 2 sheets printed on both sides
        try (PDDocument pdf = export(9, new PdfLayout(PaperSize.A4, 2, Mode.BOOKLET), Locale.FRENCH)) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(4);
            assertSize(pdf.getPage(0), PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
            // the outer side of the first sheet holds the last (blank) page and the first one
            assertThat(text(pdf, 1, 1)).contains("Famille", "LAUSANNE", "KATZE", "1 / 5").doesNotContain("GATTO");
            // the inner side of the second sheet holds the middle pages: 4 and 5
            assertThat(text(pdf, 4, 4)).contains("4 / 5", "5 / 5", "SIGNE 6", "SIGNE 8");
        }
    }

    @Test
    void flashcardsHaveTheImagesOnTheFrontAndTheWordsOnTheBack() throws IOException {
        try (PDDocument pdf = export(9, new PdfLayout(PaperSize.A4, 8, Mode.FLASHCARDS), Locale.FRENCH)) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(4);
            assertThat(text(pdf, 1, 1)).doesNotContain("LAUSANNE", "KATZE").contains("Scanner pour voir le signe");
            assertThat(images(pdf.getPage(0))).isEqualTo(1);
            assertThat(text(pdf, 2, 2)).contains("LAUSANNE", "KATZE", "Capitale du canton de Vaud")
                    .doesNotContain("SIGNE 8");
            assertThat(text(pdf, 4, 4)).contains("SIGNE 8");
        }
    }

    @Test
    void rejectsUnsupportedNumbersOfSignsPerPage() {
        assertThatThrownBy(() -> new PdfLayout(PaperSize.A4, 5, Mode.SHEETS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anEmptyExportHasOnePage() throws IOException {
        try (PDDocument pdf = export(0, new PdfLayout(PaperSize.A4, 4, Mode.SHEETS), Locale.FRENCH)) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(1);
            assertThat(text(pdf, 1, 1)).contains("Famille", "aucun signe");
        }
    }

    private PDDocument export(int signs, PdfLayout layout, Locale locale) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exporter.write(new ExportRequest("Famille", ExportTestData.signs(signs), locale), layout, out);
        return Loader.loadPDF(out.toByteArray());
    }

    private static String text(PDDocument pdf, int startPage, int endPage) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setStartPage(startPage);
        stripper.setEndPage(endPage);
        return stripper.getText(pdf);
    }

    /** Images drawn on a page, directly or in the forms of a booklet. */
    private static long images(PDPage page) {
        long count = 0;
        for (COSName name : page.getResources().getXObjectNames()) {
            if (page.getResources().isImageXObject(name)) {
                count++;
            }
        }
        return count;
    }

    private static void assertSize(PDPage page, float width, float height) {
        assertThat(page.getMediaBox().getWidth()).isCloseTo(width, within(1f));
        assertThat(page.getMediaBox().getHeight()).isCloseTo(height, within(1f));
    }
}
