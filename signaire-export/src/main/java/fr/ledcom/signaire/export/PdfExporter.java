package fr.ledcom.signaire.export;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import org.apache.pdfbox.multipdf.LayerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.util.Matrix;

import fr.ledcom.signaire.model.Sign;

/** Printable PDF documents of signs, in the layouts of {@link PdfLayout}. */
public final class PdfExporter {

    private static final float GRAY = 0.4f;

    private final MediaSource media;

    public PdfExporter(MediaSource media) {
        this.media = media;
    }

    public void write(ExportRequest request, PdfLayout layout, OutputStream out) {
        Labels labels = Labels.of(request.locale());
        PdfLayout.PaperSize paper = layout.paper();
        try (PDDocument document = new PDDocument()) {
            document.getDocumentInformation().setTitle(request.title());
            document.getDocumentInformation().setCreator("Signaire");
            switch (layout.mode()) {
                case SHEETS -> new Pages(document, request, labels, layout.signsPerPage())
                        .writeSheets(new PDRectangle(paper.width(), paper.height()));
                case FLASHCARDS -> new Pages(document, request, labels, layout.signsPerPage())
                        .writeFlashcards(new PDRectangle(paper.width(), paper.height()));
                case BOOKLET -> writeBooklet(document, request, labels, layout);
                default -> throw new IllegalArgumentException("Unknown mode " + layout.mode());
            }
            document.save(out);
        } catch (IOException e) {
            throw new ExportException("Cannot write PDF " + request.title(), e);
        }
    }

    /** Pages of half the paper size, imposed two per side of landscape sheets. */
    private void writeBooklet(PDDocument document, ExportRequest request, Labels labels, PdfLayout layout)
            throws IOException {
        PdfLayout.PaperSize paper = layout.paper();
        PDRectangle sheet = new PDRectangle(paper.height(), paper.width());
        PDRectangle half = new PDRectangle(sheet.getWidth() / 2, sheet.getHeight());
        try (PDDocument pages = new PDDocument()) {
            new Pages(pages, request, labels, layout.signsPerPage()).writeSheets(half);
            int count = pages.getNumberOfPages();
            int total = (count + 3) / 4 * 4;
            LayerUtility layers = new LayerUtility(document);
            for (int sheetIndex = 0; sheetIndex < total / 4; sheetIndex++) {
                // outer side, then inner side, of each folded sheet
                imposeSide(document, layers, pages, sheet, total - 1 - 2 * sheetIndex, 2 * sheetIndex);
                imposeSide(document, layers, pages, sheet, 2 * sheetIndex + 1, total - 2 - 2 * sheetIndex);
            }
            // the forms are copied when saving: the pages must still be open
            document.save(OutputStream.nullOutputStream());
        }
    }

    private static void imposeSide(PDDocument document, LayerUtility layers, PDDocument pages, PDRectangle sheet,
            int left, int right) throws IOException {
        PDPage side = new PDPage(sheet);
        document.addPage(side);
        try (PDPageContentStream content = new PDPageContentStream(document, side)) {
            int[] indices = {left, right};
            for (int i = 0; i < indices.length; i++) {
                if (indices[i] < pages.getNumberOfPages()) {
                    PDFormXObject form = layers.importPageAsForm(pages, indices[i]);
                    content.saveGraphicsState();
                    content.transform(Matrix.getTranslateInstance(i * sheet.getWidth() / 2, 0));
                    content.drawForm(form);
                    content.restoreGraphicsState();
                }
            }
        }
    }

    /** Writes the pages of a document. */
    private final class Pages {
        private final PDDocument document;
        private final ExportRequest request;
        private final Labels labels;
        private final int signsPerPage;
        private final SignCells cells;

        Pages(PDDocument document, ExportRequest request, Labels labels, int signsPerPage) {
            this.document = document;
            this.request = request;
            this.labels = labels;
            this.signsPerPage = signsPerPage;
            this.cells = new SignCells(document, media, labels);
        }

        /** Pages with a grid of signs, a header on the first page and a footer on every page. */
        void writeSheets(PDRectangle size) throws IOException {
            List<Sign> signs = request.signs();
            int pageCount = Math.max(1, (signs.size() + signsPerPage - 1) / signsPerPage);
            float margin = margin(size);
            float footerSize = Math.clamp(size.getWidth() / 70, 5f, 7f);
            Grid grid = Grid.of(signsPerPage, size.getWidth() > size.getHeight());
            for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
                PDPage page = new PDPage(size);
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    float top = size.getHeight() - margin;
                    if (pageIndex == 0) {
                        top = writeHeader(content, size, margin, top);
                    }
                    float bottom = margin + footerSize * 2.5f;
                    writeFooter(content, size, margin, footerSize, pageIndex + 1, pageCount);
                    Box area = new Box(margin, bottom, size.getWidth() - 2 * margin, top - bottom);
                    for (int i = 0; i < grid.cells(); i++) {
                        int signIndex = pageIndex * signsPerPage + i;
                        if (signIndex < signs.size()) {
                            cells.drawSign(content, area.cell(grid, i), signs.get(signIndex));
                        }
                    }
                }
            }
        }

        /** Fronts and backs of cards on alternate pages; the backs are mirrored to match the fronts. */
        void writeFlashcards(PDRectangle size) throws IOException {
            List<Sign> signs = request.signs();
            int sheetCount = Math.max(1, (signs.size() + signsPerPage - 1) / signsPerPage);
            float margin = margin(size);
            Grid grid = Grid.of(signsPerPage, false);
            Box area = new Box(margin, margin, size.getWidth() - 2 * margin, size.getHeight() - 2 * margin);
            for (int sheetIndex = 0; sheetIndex < sheetCount; sheetIndex++) {
                PDPage front = new PDPage(size);
                PDPage back = new PDPage(size);
                document.addPage(front);
                document.addPage(back);
                try (PDPageContentStream frontContent = new PDPageContentStream(document, front);
                        PDPageContentStream backContent = new PDPageContentStream(document, back)) {
                    for (int i = 0; i < grid.cells(); i++) {
                        int signIndex = sheetIndex * signsPerPage + i;
                        if (signIndex >= signs.size()) {
                            break;
                        }
                        int column = i % grid.columns();
                        int mirrored = i - column + grid.columns() - 1 - column;
                        cells.drawFront(frontContent, area.cell(grid, i), signs.get(signIndex));
                        cells.drawBack(backContent, area.cell(grid, mirrored), signs.get(signIndex));
                    }
                }
            }
        }

        private float writeHeader(PDPageContentStream content, PDRectangle size, float margin, float top)
                throws IOException {
            float titleSize = Math.clamp(size.getWidth() / 36, 10f, 16f);
            float subtitleSize = titleSize * 0.65f;
            float width = size.getWidth() - 2 * margin;
            Pdf pdf = cells.pdf();
            float baseline = top - titleSize;
            content.setNonStrokingColor(0f);
            Pdf.text(content, Pdf.BOLD, titleSize, margin, baseline, pdf.fit(Pdf.BOLD, titleSize, request.title(),
                    width));
            baseline -= subtitleSize * 1.6f;
            content.setNonStrokingColor(GRAY);
            Pdf.text(content, Pdf.REGULAR, subtitleSize, margin, baseline, pdf.clean(labels.get("signCount",
                    request.signs().size())));
            return baseline - subtitleSize * 1.5f;
        }

        private void writeFooter(PDPageContentStream content, PDRectangle size, float margin, float fontSize,
                int page, int pageCount) throws IOException {
            Pdf pdf = cells.pdf();
            String number = pdf.clean(labels.get("page", page, pageCount));
            float numberWidth = Pdf.width(Pdf.REGULAR, fontSize, number);
            float baseline = margin;
            content.setNonStrokingColor(GRAY);
            Pdf.text(content, Pdf.REGULAR, fontSize, size.getWidth() - margin - numberWidth, baseline, number);
            Pdf.text(content, Pdf.REGULAR, fontSize * 0.85f, margin, baseline, pdf.fit(Pdf.REGULAR,
                    fontSize * 0.85f, labels.get("credit"), size.getWidth() - 2 * margin - numberWidth - 10));
        }

        private float margin(PDRectangle size) {
            return Pdf.millimeters(Math.min(size.getWidth(), size.getHeight()) < Pdf.millimeters(120) ? 6f : 10f);
        }
    }
}
