package fr.ledcom.signaire.web;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedSet;
import java.util.concurrent.ConcurrentHashMap;

import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.stereotype.Component;

/**
 * The manual: a Markdown page per topic and language, in {@code help/<language>/<topic>.md}, rendered once.
 * French pages stand in for missing translations.
 */
@Component
public class HelpPages {

    /** The topics, in the order of the manual. */
    static final SequencedSet<String> TOPICS = Collections.unmodifiableSequencedSet(new LinkedHashSet<>(List.of(
            "search", "signs", "lists", "exports", "requests", "account", "admin", "cli")));
    private static final String DEFAULT_LANGUAGE = "fr";

    private final Parser parser = Parser.builder().extensions(List.of(TablesExtension.create())).build();
    private final HtmlRenderer renderer = HtmlRenderer.builder().extensions(List.of(TablesExtension.create()))
            .build();
    private final Map<String, Page> pages = new ConcurrentHashMap<>();

    /**
     * A page of the manual.
     *
     * @param title the first heading of the page
     * @param html the page, without its first heading
     */
    public record Page(String topic, String title, String html) {
    }

    /** A topic in a language, or empty when there is no such topic. */
    public Optional<Page> page(String topic, Locale locale) {
        if (!TOPICS.contains(topic)) {
            return Optional.empty();
        }
        String language = locale.getLanguage();
        return Optional.of(pages.computeIfAbsent(language + "/" + topic, key -> load(topic, language)));
    }

    /** All the topics, in a language. */
    public List<Page> all(Locale locale) {
        return TOPICS.stream().map(topic -> page(topic, locale).orElseThrow()).toList();
    }

    private Page load(String topic, String language) {
        String markdown = read("help/" + language + "/" + topic + ".md")
                .or(() -> read("help/" + DEFAULT_LANGUAGE + "/" + topic + ".md"))
                .orElseThrow(() -> new IllegalStateException("No help for " + topic));
        int endOfTitle = markdown.indexOf('\n');
        String title = markdown.substring(2, endOfTitle).strip();
        return new Page(topic, title, renderer.render(parser.parse(markdown.substring(endOfTitle + 1))));
    }

    private static Optional<String> read(String resource) {
        try (InputStream in = HelpPages.class.getClassLoader().getResourceAsStream(resource)) {
            return in == null ? Optional.empty() : Optional.of(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
