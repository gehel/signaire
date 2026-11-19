package fr.ledcom.signaire.cli;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.springframework.core.io.Resource;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import fr.ledcom.signaire.core.media.MediaLibrary;
import fr.ledcom.signaire.export.AnkiExporter;
import fr.ledcom.signaire.export.EpubExporter;
import fr.ledcom.signaire.export.ExportRequest;
import fr.ledcom.signaire.export.MediaSource;
import fr.ledcom.signaire.export.PdfExporter;
import fr.ledcom.signaire.export.PdfLayout;
import fr.ledcom.signaire.model.Media;

/** Writes exports in the chosen format, with the media of a bundle directory. */
final class Exports {

    enum Format {
        PDF, ANKI, EPUB
    }

    private Exports() {
    }

    /**
     * @param layout the layout of PDF documents
     * @param withVideos whether e-books include the videos
     */
    @SuppressFBWarnings(value = "WI_MANUALLY_ALLOCATING_AN_AUTOWIRED_BEAN",
            justification = "The library of the directory given on the command line, not the configured one")
    static void write(Format format, ExportRequest request, PdfLayout layout, boolean withVideos, Path bundles,
            Path output) throws IOException {
        try (MediaLibrary library = new MediaLibrary(bundles); OutputStream out = Files.newOutputStream(output)) {
            MediaSource media = mediaSource(library);
            switch (format) {
                case PDF -> new PdfExporter(media).write(request, layout, out);
                case ANKI -> new AnkiExporter(media).write(request, out);
                case EPUB -> new EpubExporter(media).write(request, withVideos, out);
                default -> throw new IllegalArgumentException("Unknown format " + format);
            }
        }
    }

    /** The media downloaded in the bundles of a library. */
    private static MediaSource mediaSource(MediaLibrary library) {
        return (Media media) -> Optional.ofNullable(media.file()).flatMap(library::find).map(Exports::read);
    }

    private static byte[] read(Resource resource) {
        try {
            return resource.getContentAsByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
