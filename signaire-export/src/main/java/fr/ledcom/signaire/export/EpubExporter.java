package fr.ledcom.signaire.export;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.UUID;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.jspecify.annotations.Nullable;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import fr.ledcom.signaire.model.Media;
import fr.ledcom.signaire.model.Sign;

/**
 * EPUB 3 e-books: a title page, then a page per sign with its image, texts and examples. Videos are linked to
 * SignSuisse, or included in the book when asked (e-readers rarely play them, and they make books big).
 */
public final class EpubExporter {

    private static final String MEDIA_TYPE = "application/epub+zip";
    private static final String XHTML_TYPE = "application/xhtml+xml";
    private static final String CSS = """
            body { font-family: sans-serif; }
            h1 { margin-bottom: 0.2em; }
            .meta { color: #555; margin-top: 0; }
            img, video { max-width: 100%; }
            .credit { color: #555; font-size: 0.8em; }
            """;

    private final MediaSource media;

    public EpubExporter(MediaSource media) {
        this.media = media;
    }

    /**
     * @param withVideos whether to include the downloaded videos in the book, rather than linking to them
     */
    public void write(ExportRequest request, boolean withVideos, OutputStream out) {
        Labels labels = Labels.of(request.locale());
        Book book = new Book(labels, withVideos);
        List<Sign> signs = request.signs();
        book.add("title", "title.xhtml", XHTML_TYPE, titlePage(request, labels), true);
        for (int i = 0; i < signs.size(); i++) {
            book.add("sign-" + (i + 1), pageName(i), XHTML_TYPE, book.signPage(signs.get(i)), true);
        }
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            writeMediaType(zip);
            writeEntry(zip, "META-INF/container.xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                      <rootfiles>
                        <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                      </rootfiles>
                    </container>
                    """.getBytes(StandardCharsets.UTF_8));
            writeEntry(zip, "OEBPS/content.opf", book.packageDocument(request).getBytes(StandardCharsets.UTF_8));
            writeEntry(zip, "OEBPS/nav.xhtml", navigation(request, labels).getBytes(StandardCharsets.UTF_8));
            writeEntry(zip, "OEBPS/style.css", CSS.getBytes(StandardCharsets.UTF_8));
            for (Item item : book.items.values()) {
                writeEntry(zip, "OEBPS/" + item.href(), item.content());
            }
            zip.finish();
        } catch (IOException e) {
            throw new ExportException("Cannot write EPUB " + request.title(), e);
        }
    }

    /** A text and its video: an example sentence, a regional variant. */
    private record Captioned(@Nullable String text, @Nullable Media video) {
    }

    /** A file of the book, listed in its manifest. Compared by the bytes of its content. */
    record Item(String id, String href, String mediaType, byte[] content, boolean inSpine) {

        @Override
        public boolean equals(Object other) {
            return other instanceof Item item && id.equals(item.id) && href.equals(item.href)
                    && mediaType.equals(item.mediaType) && Arrays.equals(content, item.content)
                    && inSpine == item.inSpine;
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, href, mediaType, Arrays.hashCode(content), inSpine);
        }

        /** With the size of the content: a media can be megabytes. */
        @Override
        public String toString() {
            return "Item[id=" + id + ", href=" + href + ", mediaType=" + mediaType + ", content=" + content.length
                    + " bytes, inSpine=" + inSpine + "]";
        }
    }

    /** The files of a book, with the media they include. */
    @SuppressFBWarnings(value = "POTENTIAL_XML_INJECTION", justification = "All texts are escaped by Markup.html")
    private final class Book {
        private final Labels labels;
        private final boolean withVideos;
        private final Map<String, Item> items = new LinkedHashMap<>();

        Book(Labels labels, boolean withVideos) {
            this.labels = labels;
            this.withVideos = withVideos;
        }

        void add(String id, String href, String mediaType, String content, boolean inSpine) {
            items.put(href, new Item(id, href, mediaType, content.getBytes(StandardCharsets.UTF_8), inSpine));
        }

        /** Includes a downloaded media in the book, returning its path. */
        Optional<String> include(Media mediaFile, String directory) {
            String href = directory + "/" + MediaFiles.fileName(mediaFile);
            if (items.containsKey(href)) {
                return Optional.of(href);
            }
            return media.read(mediaFile).map(bytes -> {
                items.put(href, new Item("media-" + items.size(), href, MediaFiles.mediaType(mediaFile), bytes,
                        false));
                return href;
            });
        }

        String signPage(Sign sign) {
            StringBuilder body = new StringBuilder();
            body.append("<h1>").append(Markup.html(sign.name())).append("</h1>\n<p class=\"meta\">")
                    .append(Markup.html(labels.signLanguage(sign.language())));
            if (!sign.categories().isEmpty()) {
                body.append(" · ").append(Markup.html(sign.categories().stream().map(labels::category)
                        .collect(Collectors.joining(", "))));
            }
            body.append("</p>\n");
            for (Media video : sign.videos()) {
                body.append(video(video)).append('\n');
            }
            for (Media image : sign.images()) {
                include(image, "images").ifPresent(href -> body.append("<p><img src=\"").append(Markup.html(href))
                        .append("\" alt=\"").append(Markup.html(sign.name())).append("\"/></p>\n"));
            }
            section(body, "periphrase", sign.periphrase());
            section(body, "definition", sign.definition());
            if (!sign.dialects().isEmpty()) {
                section(body, "dialects", String.join(", ", sign.dialects()));
            }
            videoList(body, "examples", sign.examples().stream()
                    .map(example -> new Captioned(example.text(), example.video())).toList());
            videoList(body, "dialectVariants", sign.dialectVariants().stream()
                    .map(variant -> new Captioned(String.join(", ", variant.dialects()), variant.video())).toList());
            return xhtml(labels, sign.name(), body.toString());
        }

        /** A section listing texts with their videos: examples, regional variants. */
        private void videoList(StringBuilder body, String label, Collection<Captioned> entries) {
            if (entries.isEmpty()) {
                return;
            }
            body.append("<h2>").append(Markup.html(labels.get(label))).append("</h2>\n<ul>\n");
            for (Captioned entry : entries) {
                body.append("<li>").append(Markup.html(entry.text()));
                if (entry.video() != null) {
                    body.append(' ').append(video(entry.video()));
                }
                body.append("</li>\n");
            }
            body.append("</ul>\n");
        }

        /** An included video, or a link to it. */
        private String video(Media video) {
            Optional<String> included = withVideos ? include(video, "videos") : Optional.empty();
            return included.map(href -> "<video src=\"" + Markup.html(href) + "\" controls=\"controls\">"
                    + link(video.sourceUrl(), labels.get("watchVideo")) + "</video>")
                    .orElseGet(() -> "<p>" + link(video.sourceUrl(), labels.get("watchVideo")) + "</p>");
        }

        private void section(StringBuilder body, String label, @Nullable String text) {
            if (text != null && !text.isBlank()) {
                body.append("<h2>").append(Markup.html(labels.get(label))).append("</h2>\n<p>")
                        .append(Markup.html(text)).append("</p>\n");
            }
        }

        String packageDocument(ExportRequest request) {
            StringBuilder manifest = new StringBuilder();
            StringBuilder spine = new StringBuilder();
            manifest.append("    <item id=\"nav\" href=\"nav.xhtml\" media-type=\"").append(XHTML_TYPE)
                    .append("\" properties=\"nav\"/>\n")
                    .append("    <item id=\"style\" href=\"style.css\" media-type=\"text/css\"/>\n");
            for (Item item : items.values()) {
                manifest.append("    <item id=\"").append(item.id()).append("\" href=\"")
                        .append(Markup.html(item.href())).append("\" media-type=\"").append(item.mediaType())
                        .append("\"/>\n");
                if (item.inSpine()) {
                    spine.append("    <itemref idref=\"").append(item.id()).append("\"/>\n");
                }
            }
            String modified = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString();
            return String.format(Locale.ROOT, """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="book-id">
                      <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                        <dc:identifier id="book-id">urn:uuid:%s</dc:identifier>
                        <dc:title>%s</dc:title>
                        <dc:language>%s</dc:language>
                        <dc:creator>Signaire</dc:creator>
                        <dc:rights>%s</dc:rights>
                        <meta property="dcterms:modified">%s</meta>
                      </metadata>
                      <manifest>
                    %s  </manifest>
                      <spine>
                    %s  </spine>
                    </package>
                    """, UUID.randomUUID(), Markup.html(request.title()), labels.language(),
                    Markup.html(labels.get("credit")), modified, manifest, spine);
        }
    }

    private static String titlePage(ExportRequest request, Labels labels) {
        return xhtml(labels, request.title(), "<h1>" + Markup.html(request.title()) + "</h1>\n<p class=\"meta\">"
                + Markup.html(labels.get("signCount", request.signs().size())) + "</p>\n<p class=\"credit\">"
                + Markup.html(labels.get("credit")) + "</p>\n");
    }

    private static String navigation(ExportRequest request, Labels labels) {
        List<String> entries = new ArrayList<>();
        entries.add("<li><a href=\"title.xhtml\">" + Markup.html(request.title()) + "</a></li>");
        List<Sign> signs = request.signs();
        for (int i = 0; i < signs.size(); i++) {
            entries.add("<li><a href=\"" + pageName(i) + "\">" + Markup.html(signs.get(i).name()) + "</a></li>");
        }
        return xhtml(labels, labels.get("contents"), "<nav epub:type=\"toc\" id=\"toc\">\n<h1>"
                + Markup.html(labels.get("contents")) + "</h1>\n<ol>\n" + String.join("\n", entries)
                + "\n</ol>\n</nav>\n");
    }

    private static String pageName(int index) {
        return "sign-" + (index + 1) + ".xhtml";
    }

    private static String xhtml(Labels labels, String title, String body) {
        return String.format(Locale.ROOT, """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE html>
                <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" \
                xml:lang="%1$s" lang="%1$s">
                <head>
                <meta charset="UTF-8"/>
                <title>%2$s</title>
                <link rel="stylesheet" type="text/css" href="style.css"/>
                </head>
                <body>
                %3$s</body>
                </html>
                """, labels.language(), Markup.html(title), body);
    }

    private static String link(String url, String text) {
        return "<a href=\"" + Markup.html(url) + "\">" + Markup.html(text) + "</a>";
    }

    /** The first entry: "mimetype", uncompressed, so that the type of the file can be recognized. */
    private static void writeMediaType(ZipOutputStream zip) throws IOException {
        byte[] type = MEDIA_TYPE.getBytes(StandardCharsets.US_ASCII);
        ZipEntry entry = new ZipEntry("mimetype");
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(type.length);
        CRC32 crc = new CRC32();
        crc.update(type);
        entry.setCrc(crc.getValue());
        zip.putNextEntry(entry);
        zip.write(type);
        zip.closeEntry();
    }

    private static void writeEntry(ZipOutputStream zip, String name, byte[] content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content);
        zip.closeEntry();
    }
}
