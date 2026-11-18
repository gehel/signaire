package fr.ledcom.signaire.core;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Locale;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import co.elastic.clients.elasticsearch.ElasticsearchClient;

/**
 * An Elasticsearch that answers every request with an error, to test how errors are reported without a
 * container. Indices exist or not, as asked ({@code HEAD} requests).
 */
@SuppressForbidden(reason = "jdk.httpserver is a supported JDK module, flagged because of its com.sun package")
public final class FakeElasticsearch implements AutoCloseable {

    private final HttpServer server;
    private final ElasticsearchClient client;

    private FakeElasticsearch(boolean indicesExist, int status, String errorType) {
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/", exchange -> {
            if ("HEAD".equals(exchange.getRequestMethod())) {
                respond(exchange, indicesExist ? 200 : 404, null);
            } else {
                respond(exchange, status, String.format(Locale.ROOT,
                        "{\"error\":{\"type\":\"%s\",\"reason\":\"fake\"},\"status\":%d}", errorType, status));
            }
        });
        server.start();
        String url = "http://localhost:" + server.getAddress().getPort();
        client = ElasticsearchClient.of(builder -> builder.host(url));
    }

    /** Fails every request but {@code HEAD}, with an error of the given type. */
    public static FakeElasticsearch failing(boolean indicesExist, int status, String errorType) {
        return new FakeElasticsearch(indicesExist, status, errorType);
    }

    /** A client of this Elasticsearch. */
    public ElasticsearchClient client() {
        return client;
    }

    @Override
    public void close() throws IOException {
        client.close();
        server.stop(0);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        exchange.getResponseHeaders().add("X-Elastic-Product", "Elasticsearch");
        if (body == null) {
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
            return;
        }
        byte[] bytes = body.getBytes(UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
