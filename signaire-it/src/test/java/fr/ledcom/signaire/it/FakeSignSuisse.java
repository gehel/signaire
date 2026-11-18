package fr.ledcom.signaire.it;

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
import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * A local SignSuisse, with the structure of its search and pages and invented content: tests never reach the
 * real site. Two French signs in the "Ville" category.
 */
@SuppressForbidden(reason = "the JDK HTTP server is fine for a test server")
final class FakeSignSuisse implements AutoCloseable {

    /** An invented sign. */
    record FakeSign(String uid, String name, String slug, String definition) {
    }

    static final List<FakeSign> SIGNS = List.of(
            new FakeSign("9001", "LAUSANNE", "lausanne", "Une ville au bord du lac."),
            new FakeSign("9002", "GENÈVE", "geneve", "Une ville au bout du lac."));
    private static final int CITY = 6809;
    private static final byte[] IMAGE = image();

    private final HttpServer server;

    FakeSignSuisse() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    URI uri() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    private void handle(HttpExchange exchange) {
        String path = exchange.getRequestURI().getPath();
        if (path.equals("/signsuisse/index.php")) {
            search(exchange, query(exchange.getRequestURI().getRawQuery()));
        } else if (path.startsWith("/signsuisse/fr/lexikon/")) {
            String uid = path.split("/")[4];
            SIGNS.stream().filter(sign -> sign.uid().equals(uid)).findFirst().ifPresentOrElse(
                    sign -> respond(exchange, "text/html", page(sign).getBytes(StandardCharsets.UTF_8)),
                    () -> respond(exchange, 404));
        } else if (path.endsWith(".mp4")) {
            respond(exchange, "video/mp4", ("fake video " + path).repeat(500).getBytes(StandardCharsets.UTF_8));
        } else if (path.endsWith(".jpg")) {
            respond(exchange, "image/png", IMAGE);
        } else {
            respond(exchange, 404);
        }
    }

    private void search(HttpExchange exchange, Map<String, String> query) {
        String term = fold(query.getOrDefault("sword", ""));
        String category = query.getOrDefault("category", "");
        boolean matches = "fr".equals(query.get("lang"))
                && (category.isEmpty() || category.equals(String.valueOf(CITY)));
        String items = SIGNS.stream()
                .filter(sign -> matches && !term.isBlank() && fold(sign.name()).contains(term.strip()))
                .map(sign -> "{\"uid\":\"" + sign.uid() + "\",\"name\":\"" + sign.name()
                        + "\",\"kategorie\":\"Ville\",\"sprache\":\"fr\",\"link\":\"" + uri() + "/signsuisse/fr/lexikon/"
                        + sign.uid() + "/" + sign.slug() + "\"}")
                .collect(Collectors.joining(","));
        respond(exchange, "application/json", ("{\"items\":[" + items + "],\"count\":0}")
                .getBytes(StandardCharsets.UTF_8));
    }

    private static String page(FakeSign sign) {
        String media = "/signsuisse/fileadmin/signsuisse_ressources/";
        return String.format(Locale.ROOT, """
                <!DOCTYPE html>
                <html lang="fr-CH"><head><title>Lexique</title></head><body>
                <div class="tx-is-signsuisse-lexikon"><div class="showaction">
                <div class="column w-col w-col-8 w-col-medium-8"><div class="contentleft">
                <h1 class="heading-1 hyphenate"><a class="backarrow" href="/signsuisse/fr/">|</a>%s</h1>
                <div class="videocontainer"><video id="video-main" class="video-js" controls>
                <source src="%svideos/MAIN-%s.mp4#t=0.1" type='video/mp4'></video></div>
                <h2 class="heading-2">Définition</h2>
                <p>%s</p>
                <h2 class="heading-2">Classification</h2>
                <p><strong>Statut des signes:</strong> Reconnu <br/><strong>Catégorie:</strong> <span>Ville</span>
                <div class="hiddeninfo maincategories">Ville</div>
                </div></div>
                <div class="column-2 w-col w-col-4 w-col-medium-4"><div class="contentright">
                <div class="photo_doc document"><h2 class="heading-2">Photo Signe</h2>
                <img alt="Photo du signe" src="%simages/PHOTO-%s.jpg" width="239" height="204" /></div>
                </div></div>
                </div></div></body></html>
                """, sign.name(), media, sign.uid(), sign.definition(), media, sign.uid());
    }

    private static Map<String, String> query(String rawQuery) {
        return rawQuery == null ? Map.of() : Arrays.stream(rawQuery.split("&"))
                .map(parameter -> parameter.split("=", 2))
                .collect(Collectors.toMap(parts -> parts[0],
                        parts -> parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "",
                        (a, b) -> b));
    }

    private static String fold(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
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
