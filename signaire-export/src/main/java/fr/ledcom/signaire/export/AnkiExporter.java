package fr.ledcom.signaire.export;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import fr.ledcom.signaire.model.Media;
import fr.ledcom.signaire.model.Sign;

/**
 * Anki decks ({@code .apkg}): a note per sign, with a "sign to word" card (the video, for signs that have one)
 * and a "word to sign" card. Notes are identified by the uid of their sign, so that importing a deck again
 * updates its notes. The downloaded videos and images are included; the others are linked.
 *
 * <p>The package holds a collection in the legacy schema (version 11), which all Anki versions import.
 */
public final class AnkiExporter {

    private static final String SEPARATOR = "\u001f";
    private static final int SIGN_TO_WORD = 0;
    private static final int WORD_TO_SIGN = 1;
    /** Field order, see {@link #fields}. */
    private static final List<String> FIELDS = List.of("name", "video", "image", "definition", "periphrase",
            "language", "categories", "link", "uid");
    private static final String CSS = """
            .card { font-family: sans-serif; font-size: 20px; text-align: center; color: black; background: white; }
            .name { font-size: 32px; font-weight: bold; margin: 0.5em 0; }
            .meta { color: #666; font-size: 16px; }
            .definition { margin: 0.5em auto; max-width: 30em; }
            .link { font-size: 14px; margin-top: 1em; }
            img, video { max-width: 100%; max-height: 50vh; }
            """;

    private final MediaSource media;

    public AnkiExporter(MediaSource media) {
        this.media = media;
    }

    public void write(ExportRequest request, OutputStream out) {
        Labels labels = Labels.of(request.locale());
        Path collection = null;
        try {
            collection = Files.createTempFile("signaire-", ".anki2");
            Map<String, byte[]> files = new LinkedHashMap<>();
            writeCollection(collection, request, labels, files);
            try (ZipOutputStream zip = new ZipOutputStream(out)) {
                zip.putNextEntry(new ZipEntry("collection.anki2"));
                Files.copy(collection, zip);
                int index = 0;
                for (byte[] content : files.values()) {
                    zip.putNextEntry(new ZipEntry(String.valueOf(index)));
                    zip.write(content);
                    index++;
                }
                zip.putNextEntry(new ZipEntry("media"));
                zip.write(AnkiSchema.media(List.copyOf(files.keySet())).getBytes(StandardCharsets.UTF_8));
                zip.finish();
            }
        } catch (IOException | SQLException e) {
            throw new ExportException("Cannot write Anki deck " + request.title(), e);
        } finally {
            if (collection != null) {
                try {
                    Files.deleteIfExists(collection);
                } catch (IOException e) {
                    // a temporary file left behind
                }
            }
        }
    }

    @SuppressFBWarnings(value = "SQL_INJECTION_JDBC",
            justification = "The statements of the batch are the constant schema of Anki collections")
    private void writeCollection(Path file, ExportRequest request, Labels labels, Map<String, byte[]> files)
            throws SQLException {
        long now = System.currentTimeMillis();
        long modelId = stableId("model-v1-" + labels.language());
        long deckId = stableId("deck-" + request.title());
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file)) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                for (String table : AnkiSchema.TABLES) {
                    statement.addBatch(table);
                }
                statement.executeBatch();
            }
            try (PreparedStatement col = connection.prepareStatement(
                    "insert into col values (1, ?, ?, ?, 11, 0, 0, 0, ?, ?, ?, ?, '{}')")) {
                col.setLong(1, now / 1000);
                col.setLong(2, now);
                col.setLong(3, now);
                col.setString(4, AnkiSchema.conf(modelId, deckId));
                col.setString(5, model(modelId, deckId, labels, now));
                col.setString(6, AnkiSchema.decks(deckId, request.title(), now));
                col.setString(7, AnkiSchema.DECK_CONFIGURATIONS);
                col.executeUpdate();
            }
            try (PreparedStatement notes = connection.prepareStatement(
                    "insert into notes values (?, ?, ?, ?, -1, ?, ?, ?, ?, 0, '')");
                    PreparedStatement cards = connection.prepareStatement(
                            "insert into cards values (?, ?, ?, ?, ?, -1, 0, 0, ?, 0, 0, 0, 0, 0, 0, 0, 0, '')")) {
                // the same for every note and card
                notes.setLong(3, modelId);
                notes.setLong(4, now / 1000);
                cards.setLong(3, deckId);
                cards.setLong(5, now / 1000);
                List<Sign> signs = request.signs();
                for (int i = 0; i < signs.size(); i++) {
                    Sign sign = signs.get(i);
                    List<String> fields = fields(sign, labels, files);
                    long noteId = now + i;
                    notes.setLong(1, noteId);
                    notes.setString(2, "signaire-" + sign.uid());
                    notes.setString(5, tags(sign, labels));
                    notes.setString(6, String.join(SEPARATOR, fields));
                    notes.setString(7, sign.name());
                    notes.setLong(8, checksum(sign.name()));
                    notes.addBatch();
                    for (int ord : List.of(SIGN_TO_WORD, WORD_TO_SIGN)) {
                        if (ord == SIGN_TO_WORD && fields.get(FIELDS.indexOf("video")).isEmpty()) {
                            continue;
                        }
                        cards.setLong(1, now + 2L * i + ord);
                        cards.setLong(2, noteId);
                        cards.setInt(4, ord);
                        // new cards are shown in the order of the export
                        cards.setInt(6, i + 1);
                        cards.addBatch();
                    }
                }
                notes.executeBatch();
                cards.executeBatch();
            }
            connection.commit();
        }
    }

    /** The fields of the note of a sign, in the order of {@link #FIELDS}. */
    private List<String> fields(Sign sign, Labels labels, Map<String, byte[]> files) {
        String video = MediaFiles.firstVideo(sign).map(videoMedia -> include(videoMedia, files)
                .map(name -> "[sound:" + name + "]")
                .orElseGet(() -> link(videoMedia.sourceUrl(), labels.get("watchVideo")))).orElse("");
        String image = MediaFiles.firstImage(sign).flatMap(imageMedia -> include(imageMedia, files))
                .map(name -> "<img src=\"" + Markup.html(name) + "\">").orElse("");
        return List.of(
                Markup.html(sign.name()),
                video,
                image,
                Markup.html(sign.definition()),
                Markup.html(sign.periphrase()),
                Markup.html(labels.signLanguage(sign.language())),
                Markup.html(sign.categories().stream().map(labels::category).collect(Collectors.joining(", "))),
                sign.sourceUrl() == null ? "" : link(sign.sourceUrl(), "SignSuisse"),
                Markup.html(sign.uid()));
    }

    /** Adds a downloaded media to the package, returning its name there. */
    private Optional<String> include(Media mediaFile, Map<String, byte[]> files) {
        String name = MediaFiles.fileName(mediaFile);
        if (files.containsKey(name)) {
            return Optional.of(name);
        }
        return media.read(mediaFile).map(bytes -> {
            files.put(name, bytes);
            return name;
        });
    }

    private static String model(long modelId, long deckId, Labels labels, long now) {
        List<String> names = FIELDS.stream().map(field -> labels.get("anki.field." + field)).toList();
        String name = "{{" + names.get(FIELDS.indexOf("name")) + "}}";
        String video = "{{" + names.get(FIELDS.indexOf("video")) + "}}";
        String image = "{{" + names.get(FIELDS.indexOf("image")) + "}}";
        String definition = names.get(FIELDS.indexOf("definition"));
        String language = "{{" + names.get(FIELDS.indexOf("language")) + "}}";
        String categories = "{{" + names.get(FIELDS.indexOf("categories")) + "}}";
        String link = "{{" + names.get(FIELDS.indexOf("link")) + "}}";
        String definitionBlock = "{{#" + definition + "}}<div class=\"definition\">{{" + definition + "}}</div>{{/"
                + definition + "}}";
        String word = "<div class=\"name\">" + name + "</div><div class=\"meta\">" + language + " · " + categories
                + "</div>" + definitionBlock;
        String signToWordFront = video;
        String signToWordBack = "{{FrontSide}}<hr id=\"answer\">" + word + image + "<div class=\"link\">" + link
                + "</div>";
        String wordToSignFront = word;
        String wordToSignBack = "{{FrontSide}}<hr id=\"answer\">" + video + image + "<div class=\"link\">" + link
                + "</div>";
        return AnkiSchema.models(modelId, deckId, new AnkiSchema.NoteType(labels.get("anki.model"), names, List.of(
                new AnkiSchema.Template(labels.get("anki.signToWord"), signToWordFront, signToWordBack),
                new AnkiSchema.Template(labels.get("anki.wordToSign"), wordToSignFront, wordToSignBack)),
                FIELDS.indexOf("video"), FIELDS.indexOf("name"), CSS), now);
    }

    private static String link(String url, String text) {
        return "<a href=\"" + Markup.html(url) + "\">" + Markup.html(text) + "</a>";
    }

    /** Tags without spaces: signaire, the sign language and the categories. */
    private static String tags(Sign sign, Labels labels) {
        StringBuilder tags = new StringBuilder(" signaire ").append(labels.signLanguageShort(sign.language()));
        sign.categories().forEach(category -> tags.append(' ').append(category.name().toLowerCase(Locale.ROOT)));
        return tags.append(' ').toString();
    }

    /** Anki's checksum of the sort field: the first 8 hexadecimal digits of its SHA-1. */
    @SuppressFBWarnings(value = "WEAK_MESSAGE_DIGEST_SHA1", justification = "Anki's format, not security")
    private static long checksum(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(text.getBytes(StandardCharsets.UTF_8));
            long checksum = 0;
            for (int i = 0; i < 4; i++) {
                checksum = checksum << 8 | (digest[i] & 0xff);
            }
            return checksum;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("No SHA-1", e);
        }
    }

    /** Identifiers that stay the same from one export to the next, so that imports update models and decks. */
    private static long stableId(String key) {
        return 1_400_000_000_000L + (checksum("signaire-" + key) % 100_000_000_000L);
    }
}
