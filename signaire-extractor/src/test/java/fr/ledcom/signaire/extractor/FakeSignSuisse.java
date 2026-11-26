package fr.ledcom.signaire.extractor;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import org.jspecify.annotations.Nullable;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.DialectVariant;
import fr.ledcom.signaire.model.Example;
import fr.ledcom.signaire.model.Language;
import fr.ledcom.signaire.model.Media;
import fr.ledcom.signaire.model.Sign;
import fr.ledcom.signaire.model.TestSigns;
import net.jcip.annotations.ThreadSafe;

/**
 * SignSuisse over HTTP on {@code 127.0.0.1}, serving {@link TestSigns}, for what runs the real client (the CLI and web
 * jars, the Android app): its search (JSON, the search of {@link FakeSignSource}), the pages of the signs (the structure
 * of SignSuisse's, as the parser's fixtures have it, with the labels of the sign's language) and their media (invented
 * bytes, a PNG for images). {@code FakeSignSuisseTest} reads every page back with the parser.
 */
@ThreadSafe
@SuppressForbidden(reason = "the JDK HTTP server is fine for a test server")
public final class FakeSignSuisse implements AutoCloseable {

    private static final byte[] IMAGE = image();
    private static final Map<Language, Labels> LABELS = Map.of(
            Language.FRENCH, new Labels("Périphrase", "Définition", "Classification", "Statut des signes:",
                    "Catégorie:", "Dialectes:", "Exemple", "Illustration", "Photo Signe", "Variante dialectale"),
            Language.GERMAN, new Labels("Umschreibung", "Definition", "Klassifizierung", "Gebärden-Status:",
                    "Kategorien:", "Dialekte:", "Beispiel", "Illustration", "Gebärden Foto", "Dialektvariante"),
            Language.ITALIAN, new Labels("Parafrasi", "Definizione", "Classificazione", "Stato:", "Categoria:",
                    "Dialetti:", "Esempio", "Illustrazione", "Foto segno", "Variante dialettale"));

    /** The labels of a page, in the language of its sign. */
    private record Labels(String periphrase, String definition, String classification, String status,
            String categories, String dialects, String example, String illustration, String photo, String variant) {
    }

    private final HttpServer server;
    private final List<String> requests = new CopyOnWriteArrayList<>();

    public FakeSignSuisse() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    public URI uri() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    /** The address of the page of a sign. */
    public String link(Sign sign) {
        return uri() + FakeSearch.page(sign);
    }

    /** A sign as the client reads it here: as SignSuisse has it, at the address of its page, its media here. */
    public Sign served(Sign sign) {
        Sign online = TestSigns.atSignSuisse(sign);
        return online.toBuilder()
                .sourceUrl(link(sign))
                .videos(online.videos().stream().map(this::here).toList())
                .images(online.images().stream().map(this::here).toList())
                .examples(online.examples().stream().map(example -> {
                    Media video = example.video();
                    return new Example(example.text(), video == null ? null : here(video));
                }).toList())
                .dialectVariants(online.dialectVariants().stream()
                        .map(variant -> new DialectVariant(variant.dialects(), here(variant.video())))
                        .toList())
                .build();
    }

    /** The paths (and queries) it was asked for, in order. */
    public List<String> requests() {
        return List.copyOf(requests);
    }

    private Media here(Media media) {
        return new Media(uri() + URI.create(media.sourceUrl()).getPath(), null);
    }

    private void handle(HttpExchange exchange) {
        URI request = exchange.getRequestURI();
        String path = request.getPath();
        requests.add(request.getRawQuery() == null ? path : path + "?" + request.getRawQuery());
        if (path.equals("/signsuisse/index.php")) {
            search(exchange, query(request.getRawQuery()));
        } else if (path.contains("/lexikon/")) {
            String uid = path.substring(path.indexOf("/lexikon/") + "/lexikon/".length()).split("/")[0];
            Optional<Sign> sign = TestSigns.LEXICON.stream()
                    .filter(candidate -> candidate.uid().equals(uid) && FakeSearch.page(candidate).equals(path))
                    .findFirst();
            sign.ifPresentOrElse(found -> respond(exchange, "text/html", page(found).getBytes(StandardCharsets.UTF_8)),
                    () -> respond(exchange, 404));
        } else if (path.endsWith(".mp4")) {
            respond(exchange, "video/mp4", ("fake video " + path).repeat(500).getBytes(StandardCharsets.UTF_8));
        } else if (path.endsWith(".jpg") || path.endsWith(".png")) {
            respond(exchange, "image/png", IMAGE);
        } else {
            respond(exchange, 404);
        }
    }

    /** As SignSuisse: HTML for an empty term, an error for "0". */
    private void search(HttpExchange exchange, Map<String, String> query) {
        String term = query.getOrDefault("sword", "");
        if (term.isEmpty()) {
            respond(exchange, "text/html", "<html><body>Lexikon</body></html>".getBytes(StandardCharsets.UTF_8));
            return;
        }
        if (term.equals("0")) {
            respond(exchange, 500);
            return;
        }
        String language = query.getOrDefault("lang", "all");
        String category = query.getOrDefault("category", "");
        List<Sign> found = FakeSearch.search(term, language.equals("all") ? null : Language.fromCode(language),
                category.isEmpty() ? null : Category.fromSignSuisseId(Integer.parseInt(category)));
        String items = found.stream()
                .map(sign -> String.format(Locale.ROOT, "{\"uid\":\"%s\",\"name\":\"%s\",\"kategorie\":\"%s\",\"sprache\":\"%s\","
                        + "\"link\":\"%s\"}", sign.uid(), json(sign.name()),
                        json(FakeSearch.category(sign.categories().getFirst(), Language.FRENCH)),
                        sign.language().code(), json(link(sign))))
                .collect(Collectors.joining(","));
        respond(exchange, "application/json",
                ("{\"items\":[" + items + "],\"count\":" + found.size() + "}").getBytes(StandardCharsets.UTF_8));
    }

    private static String page(Sign sign) {
        Labels labels = LABELS.get(sign.language());
        return String.format(Locale.ROOT, """
                <!DOCTYPE html>
                <!-- The structure of a SignSuisse page, with the invented signs of the tests. -->
                <html lang="%s"><head><title>Lexikon</title></head><body>
                <div class="tx-is-signsuisse-lexikon"><div class="showaction">
                <div class="column w-col w-col-8 w-col-medium-8"><div class="contentleft">
                %s</div></div>
                <div class="column-2 w-col w-col-4 w-col-medium-4"><div class="contentright">
                %s%s%s</div></div>
                </div></div></body></html>
                """, sign.language().code(), left(sign, labels), examples(sign, labels), images(sign, labels),
                variants(sign, labels));
    }

    /** The name, the videos, the texts and the classification. */
    private static String left(Sign sign, Labels labels) {
        StringBuilder left = new StringBuilder();
        left.append("<h1 class=\"heading-1 hyphenate\"><a class=\"backarrow\" href=\"/signsuisse/\">|</a>")
                .append(html(sign.name())).append("</h1>\n");
        for (Media video : sign.videos()) {
            left.append(video("video-main", video));
        }
        section(left, labels.periphrase(), sign.periphrase());
        section(left, labels.definition(), sign.definition());
        left.append("<h2 class=\"heading-2\">").append(labels.classification()).append("</h2>\n<p>");
        String status = sign.status();
        if (status != null) {
            left.append("<strong>").append(labels.status()).append("</strong> ").append(html(status)).append("<br/>");
        }
        String categories = sign.categories().stream().map(category -> FakeSearch.category(category, sign.language()))
                .collect(Collectors.joining("\n"));
        left.append("<strong>").append(labels.categories()).append("</strong> <span>").append(html(categories))
                .append("</span></p>\n<div class=\"hiddeninfo maincategories\">").append(html(categories))
                .append("</div>\n");
        if (!sign.dialects().isEmpty()) {
            left.append("<p><strong>").append(labels.dialects()).append("</strong> <span>")
                    .append(String.join(",", sign.dialects())).append("</span></p>\n");
        }
        return left.toString();
    }

    private static String examples(Sign sign, Labels labels) {
        if (sign.examples().isEmpty()) {
            return "";
        }
        StringBuilder examples = new StringBuilder("<div class=\"example_doc document\"><h2 class=\"heading-2\">")
                .append(labels.example()).append("</h2>\n");
        for (Example example : sign.examples()) {
            String text = example.text();
            examples.append("<p>").append(text == null ? "" : html(text)).append("</p>\n");
            Media video = example.video();
            if (video != null) {
                examples.append(video("video-example", video));
            }
        }
        return examples.append("</div>\n").toString();
    }

    /** Photos and illustrations, told apart by their address. */
    private static String images(Sign sign, Labels labels) {
        StringBuilder images = new StringBuilder();
        for (Media image : sign.images()) {
            boolean illustration = image.sourceUrl().contains("/illustrations/");
            images.append("<div class=\"").append(illustration ? "illustration_doc" : "photo_doc")
                    .append(" document\"><h2 class=\"heading-2\">")
                    .append(illustration ? labels.illustration() : labels.photo()).append("</h2>\n<img alt=\"")
                    .append(html(sign.name())).append("\" src=\"").append(path(image))
                    .append("\" width=\"239\" height=\"204\" /></div>\n");
        }
        return images.toString();
    }

    private static String variants(Sign sign, Labels labels) {
        StringBuilder variants = new StringBuilder();
        int number = 0;
        for (DialectVariant variant : sign.dialectVariants()) {
            variants.append("<h2 class=\"heading-2\">").append(labels.variant()).append(' ')
                    .append(String.join(",", variant.dialects())).append("</h2>\n")
                    .append(video("video-dialekt-" + ++number, variant.video()));
        }
        return variants.toString();
    }

    private static void section(StringBuilder page, String label, @Nullable String text) {
        if (text != null) {
            page.append("<h2 class=\"heading-2\">").append(label).append("</h2>\n<p>").append(html(text))
                    .append("</p>\n");
        }
    }

    private static String video(String id, Media video) {
        return "<div class=\"videocontainer\"><video id=\"" + id + "\" class=\"video-js\" controls>"
                + "<source src=\"" + path(video) + "#t=0.1\" type='video/mp4'></video></div>\n";
    }

    /** Media are served by this server, at the path they have at SignSuisse. */
    private static String path(Media media) {
        return URI.create(media.sourceUrl()).getPath();
    }

    private static String html(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String json(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static Map<String, String> query(@Nullable String rawQuery) {
        return rawQuery == null ? Map.of() : Arrays.stream(rawQuery.split("&"))
                .map(parameter -> parameter.split("=", 2))
                .collect(Collectors.toMap(parts -> parts[0],
                        parts -> parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "",
                        (a, b) -> b));
    }

    private static void respond(HttpExchange exchange, String type, byte[] body) {
        try (exchange; OutputStream out = exchange.getResponseBody()) {
            exchange.getResponseHeaders().add("Content-Type", type);
            exchange.sendResponseHeaders(200, body.length);
            out.write(body);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void respond(HttpExchange exchange, int status) {
        try (exchange) {
            exchange.sendResponseHeaders(status, -1);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] image() {
        BufferedImage image = new BufferedImage(60, 40, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.ORANGE);
        graphics.fillRect(0, 0, 60, 40);
        graphics.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
