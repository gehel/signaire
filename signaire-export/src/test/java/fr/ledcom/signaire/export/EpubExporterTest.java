package fr.ledcom.signaire.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;

class EpubExporterTest {

    private final EpubExporter exporter = new EpubExporter(ExportTestData.MEDIA);

    @Test
    void startsWithTheUncompressedMediaType() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exporter.write(new ExportRequest("Famille", ExportTestData.signs(3), Locale.FRENCH), false, out);

        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(out.toByteArray()))) {
            ZipEntry first = zip.getNextEntry();
            assertThat(first.getName()).isEqualTo("mimetype");
            assertThat(first.getMethod()).isEqualTo(ZipEntry.STORED);
            assertThat(new String(zip.readAllBytes(), StandardCharsets.US_ASCII)).isEqualTo("application/epub+zip");
        }
    }

    @Test
    void describesThePublication() throws Exception {
        Map<String, String> book = export(Locale.FRENCH, false);

        assertThat(book.get("META-INF/container.xml")).contains("full-path=\"OEBPS/content.opf\"");
        assertThat(book.get("OEBPS/content.opf"))
                .contains("<dc:title>Famille</dc:title>", "<dc:language>fr</dc:language>", "dcterms:modified")
                .contains("href=\"nav.xhtml\"", "properties=\"nav\"", "href=\"images/lausanne.png\"")
                .contains("<itemref idref=\"title\"/>", "<itemref idref=\"sign-1\"/>", "<itemref idref=\"sign-3\"/>");
        assertThat(book.get("OEBPS/nav.xhtml")).contains("Sommaire", "href=\"sign-1.xhtml\">LAUSANNE</a>",
                "href=\"sign-3.xhtml\">GATTO</a>");
        assertThat(book.get("OEBPS/title.xhtml")).contains("<h1>Famille</h1>", "3 signes", "SignSuisse");
        for (Map.Entry<String, String> file : book.entrySet()) {
            if (file.getKey().endsWith(".xhtml") || file.getKey().endsWith(".xml") || file.getKey().endsWith(".opf")) {
                assertWellFormed(file.getValue());
            }
        }
    }

    @Test
    void hasAPagePerSign() throws IOException {
        Map<String, String> book = export(Locale.FRENCH, false);

        assertThat(book.get("OEBPS/sign-1.xhtml"))
                .contains("<h1>LAUSANNE</h1>", "Langue des signes française (LSF)", "Ville")
                .contains("<img src=\"images/lausanne.png\" alt=\"LAUSANNE\"/>")
                .contains("Périphrase", "Ville au bord du lac", "Définition", "Capitale du canton de Vaud")
                .contains("Exemples", "Je travaille à Lausanne.")
                .contains("href=\"https://example.org/videos/lausanne.mp4\"")
                .doesNotContain("<video");
        assertThat(book.get("OEBPS/sign-2.xhtml")).contains("KATZE", "Dialectes", "BE, ZH");
    }

    @Test
    void includesTheVideosWhenAsked() throws IOException {
        Map<String, String> book = export(Locale.GERMAN, true);

        assertThat(book).containsKey("OEBPS/videos/lausanne.mp4");
        assertThat(book.get("OEBPS/sign-1.xhtml")).contains("<video src=\"videos/lausanne.mp4\" controls=\"controls\"",
                "Definition");
        assertThat(book.get("OEBPS/content.opf")).contains("href=\"videos/lausanne.mp4\" media-type=\"video/mp4\"");
        assertThat(book.get("OEBPS/nav.xhtml")).contains("Inhalt");
    }

    private Map<String, String> export(Locale locale, boolean withVideos) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exporter.write(new ExportRequest("Famille", ExportTestData.signs(3), locale), withVideos, out);
        Map<String, String> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(out.toByteArray()))) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                entries.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return entries;
    }

    private static void assertWellFormed(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }
}
