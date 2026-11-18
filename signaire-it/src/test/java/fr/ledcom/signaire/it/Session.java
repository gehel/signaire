package fr.ledcom.signaire.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** A browser of a Signaire web application: cookies, CSRF tokens, no automatic redirects. */
final class Session {

    private static final Pattern CSRF = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"");

    final HttpClient client = HttpClient.newBuilder()
            .cookieHandler(new CookieManager())
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private final URI base;

    Session(URI base) {
        this.base = base;
    }

    /** A new account, logged in. */
    static Session registered(URI base, String username) throws Exception {
        Session session = new Session(base);
        Map<String, String> account = Map.of("username", username, "displayName", username,
                "password", "correct horse", "confirmation", "correct horse");
        assertThat(session.post("/register", account, "/register").statusCode()).isEqualTo(302);
        HttpResponse<String> login = session.post("/login", Map.of("username", username,
                "password", "correct horse"), "/login");
        assertThat(login.headers().firstValue("Location")).hasValueSatisfying(
                location -> assertThat(location).doesNotContain("error"));
        return session;
    }

    HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return client.send(HttpRequest.newBuilder(base.resolve(path)).build(), HttpResponse.BodyHandlers.ofString());
    }

    byte[] download(String path) throws IOException, InterruptedException {
        HttpResponse<byte[]> response = client.send(HttpRequest.newBuilder(base.resolve(path)).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.statusCode()).as(path).isEqualTo(200);
        return response.body();
    }

    /** Posts a form, with the CSRF token of the home page. */
    HttpResponse<String> post(String path, Map<String, String> form) throws IOException, InterruptedException {
        return post(path, form, "/");
    }

    HttpResponse<String> post(String path, Map<String, String> form, String tokenPage)
            throws IOException, InterruptedException {
        Matcher token = CSRF.matcher(get(tokenPage).body());
        assertThat(token.find()).as("CSRF token on " + tokenPage).isTrue();
        String body = form.entrySet().stream()
                .map(field -> encode(field.getKey()) + "=" + encode(field.getValue()))
                .collect(Collectors.joining("&", "_csrf=" + encode(token.group(1)) + "&", ""));
        return client.send(HttpRequest.newBuilder(base.resolve(path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
