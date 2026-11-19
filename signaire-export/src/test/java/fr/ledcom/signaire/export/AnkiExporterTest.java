package fr.ledcom.signaire.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class AnkiExporterTest {

    @TempDir
    Path directory;

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final AnkiExporter exporter = new AnkiExporter(ExportTestData.MEDIA);

    @Test
    void packagesTheCollectionAndTheDownloadedMedia() throws IOException {
        Map<String, byte[]> entries = export(Locale.FRENCH);

        assertThat(entries).containsKeys("collection.anki2", "media", "0", "1");
        assertThat(new String(entries.get("media"), StandardCharsets.UTF_8))
                .isEqualTo("{\"0\":\"lausanne.mp4\",\"1\":\"lausanne.png\"}");
        assertThat(entries).containsEntry("0", ExportTestData.VIDEO).containsEntry("1", ExportTestData.IMAGE);
    }

    @Test
    void hasANoteForEachSign() throws Exception {
        try (Connection collection = collection(Locale.FRENCH)) {
            List<String> notes = strings(collection, "select flds from notes order by id");

            assertThat(notes).hasSize(3);
            assertThat(notes.get(0).split("\u001f"))
                    .startsWith("LAUSANNE", "[sound:lausanne.mp4]", "<img src=\"lausanne.png\">",
                            "Capitale du canton de Vaud, au bord du Léman.")
                    .contains("Langue des signes française (LSF)", "Ville");
            assertThat(notes.get(1)).contains("KATZE", "<a href=\"https://example.org/videos/katze.mp4\">");
            assertThat(strings(collection, "select guid from notes order by id"))
                    .containsExactly("signaire-1", "signaire-4", "signaire-5");
            assertThat(strings(collection, "select sfld from notes order by id"))
                    .containsExactly("LAUSANNE", "KATZE", "GATTO");
        }
    }

    @Test
    void hasCardsBothWaysWhenTheSignHasAVideo() throws Exception {
        try (Connection collection = collection(Locale.FRENCH)) {
            // GATTO has no video: no "sign to word" card
            assertThat(strings(collection, "select n.sfld || ':' || c.ord from cards c join notes n on c.nid = n.id "
                    + "order by n.id, c.ord"))
                    .containsExactly("LAUSANNE:0", "LAUSANNE:1", "KATZE:0", "KATZE:1", "GATTO:1");
        }
    }

    @Test
    void theDeckIsNamedAfterTheExport() throws Exception {
        try (Connection collection = collection(Locale.FRENCH)) {
            assertThat(json(collection, "decks").valueStream().map(deck -> deck.get("name").asString()))
                    .containsExactlyInAnyOrder("Default", "Famille");
            JsonNode model = json(collection, "models").values().iterator().next();
            assertThat(model.get("name").asString()).isEqualTo("Signaire (signes)");
            assertThat(model.get("tmpls").valueStream().map(template -> template.get("name").asString()))
                    .containsExactly("Signe → mot", "Mot → signe");
            assertThat(model.get("tmpls").get(0).get("qfmt").asString()).isEqualTo("{{Vidéo}}");
        }
    }

    @Test
    void fieldsAndCardsAreNamedInTheLanguageOfTheRequest() throws Exception {
        try (Connection collection = collection(Locale.GERMAN)) {
            JsonNode model = json(collection, "models").values().iterator().next();
            assertThat(model.get("tmpls").get(0).get("name").asString()).isEqualTo("Gebärde → Wort");
            assertThat(model.get("flds").valueStream().map(field -> field.get("name").asString())).contains("Wort");
        }
    }

    @Test
    void titlesWithQuotesAndBackslashesStayValidJson() throws Exception {
        Path file = directory.resolve("quoted.anki2");
        Files.write(file, export(Locale.FRENCH, "Le \"lac\" \\ ici").get("collection.anki2"));
        try (Connection collection = DriverManager.getConnection("jdbc:sqlite:" + file)) {
            assertThat(json(collection, "decks").valueStream().map(deck -> deck.get("name").asString()))
                    .contains("Le \"lac\" \\ ici");
        }
    }

    private Map<String, byte[]> export(Locale locale) throws IOException {
        return export(locale, "Famille");
    }

    private Map<String, byte[]> export(Locale locale, String title) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exporter.write(new ExportRequest(title, ExportTestData.signs(3), locale), out);
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(out.toByteArray()))) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                entries.put(entry.getName(), zip.readAllBytes());
            }
        }
        return entries;
    }

    private Connection collection(Locale locale) throws IOException, SQLException {
        Path file = directory.resolve("collection-" + locale + ".anki2");
        Files.write(file, export(locale).get("collection.anki2"));
        return DriverManager.getConnection("jdbc:sqlite:" + file);
    }

    private static JsonNode json(Connection collection, String column) throws SQLException {
        return JSON.readTree(strings(collection, "select " + column + " from col").getFirst());
    }

    private static List<String> strings(Connection connection, String query) throws SQLException {
        List<String> values = new ArrayList<>();
        try (Statement statement = connection.createStatement(); ResultSet results = statement.executeQuery(query)) {
            while (results.next()) {
                values.add(results.getString(1));
            }
        }
        return values;
    }
}
