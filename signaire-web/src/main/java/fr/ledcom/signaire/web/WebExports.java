package fr.ledcom.signaire.web;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.Optional;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import fr.ledcom.signaire.core.media.MediaLibrary;
import fr.ledcom.signaire.export.AnkiExporter;
import fr.ledcom.signaire.export.EpubExporter;
import fr.ledcom.signaire.export.ExportRequest;
import fr.ledcom.signaire.export.MediaSource;
import fr.ledcom.signaire.export.PdfExporter;
import fr.ledcom.signaire.export.PdfLayout;

/** Writes exports with the media of the configured bundles. */
@Component
public class WebExports {

    /** The export formats, with their media type and file extension. */
    public enum Format {
        PDF("application/pdf", "pdf"),
        ANKI("application/octet-stream", "apkg"),
        EPUB("application/epub+zip", "epub");

        private final String mediaType;
        private final String extension;

        Format(String mediaType, String extension) {
            this.mediaType = mediaType;
            this.extension = extension;
        }

        String mediaType() {
            return mediaType;
        }

        String extension() {
            return extension;
        }
    }

    private final MediaSource media;

    public WebExports(MediaLibrary library) {
        this.media = sign -> Optional.ofNullable(sign.file()).flatMap(library::find).map(WebExports::read);
    }

    /**
     * @param layout the layout of PDF documents
     * @param withVideos whether e-books include the videos
     */
    void write(Format format, ExportRequest request, PdfLayout layout, boolean withVideos, OutputStream out) {
        switch (format) {
            case PDF -> new PdfExporter(media).write(request, layout, out);
            case ANKI -> new AnkiExporter(media).write(request, out);
            case EPUB -> new EpubExporter(media).write(request, withVideos, out);
            default -> throw new IllegalArgumentException("Unknown format " + format);
        }
    }

    private static byte[] read(Resource resource) {
        try {
            return resource.getContentAsByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
