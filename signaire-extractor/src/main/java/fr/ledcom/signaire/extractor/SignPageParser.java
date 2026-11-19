package fr.ledcom.signaire.extractor;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.DialectVariant;
import fr.ledcom.signaire.model.Example;
import fr.ledcom.signaire.model.Language;
import fr.ledcom.signaire.model.Media;
import fr.ledcom.signaire.model.Sign;

/**
 * Parses SignSuisse sign pages. The labels of the page are in the language of the sign, so sections are
 * recognized by the beginning of their labels in French, German and Italian.
 */
public class SignPageParser {

    private static final Logger LOG = System.getLogger(SignPageParser.class.getName());

    private static final List<String> PERIPHRASE_LABELS = List.of("périphrase", "umschreibung", "parafrasi");
    private static final List<String> DEFINITION_LABELS = List.of("définition", "definition", "definizione");
    private static final List<String> STATUS_LABELS = List.of("statut", "gebärden-status", "stato");
    private static final List<String> DIALECT_LABELS = List.of("dial");
    private static final Pattern DIALECT = Pattern.compile("\\b[A-Z]{2}\\b");
    private static final Pattern LIST_SEPARATOR = Pattern.compile("[,\\n]");

    /**
     * Parses a sign page.
     *
     * @param html the page
     * @param url the URL of the page, against which relative media URLs are resolved
     * @param uid the SignSuisse uid of the sign
     * @param language the language of the sign
     */
    public Sign parse(String html, String url, String uid, Language language) {
        Document document = Jsoup.parse(html, url);
        Element content = required(document, ".tx-is-signsuisse-lexikon .showaction", url);
        Element left = required(content, ".contentleft", url);
        Element title = required(left, "h1", url);
        return Sign.builder(uid, language, title.ownText().strip())
                .sourceUrl(url)
                .periphrase(section(left, PERIPHRASE_LABELS))
                .definition(section(left, DEFINITION_LABELS))
                .status(status(left))
                .categories(categories(content, url))
                .dialects(dialects(left))
                .videos(content.select(".contentleft video source[src]").stream().map(SignPageParser::media).toList())
                .examples(examples(content))
                .images(content.select(".illustration_doc img[src], .photo_doc img[src]").stream()
                        .map(SignPageParser::media).toList())
                .dialectVariants(dialectVariants(content))
                .build();
    }

    /** Text of the paragraph following the heading whose label starts with one of {@code labels}. */
    private static String section(Element left, Collection<String> labels) {
        for (Element heading : left.select("h2")) {
            Element next = heading.nextElementSibling();
            if (hasLabel(heading, labels) && next != null && "p".equals(next.tagName())) {
                return nonBlank(next.text());
            }
        }
        return null;
    }

    private static String status(Element left) {
        for (Element strong : left.select("strong")) {
            Node next = strong.nextSibling();
            if (hasLabel(strong, STATUS_LABELS) && next instanceof TextNode text) {
                return nonBlank(text.text());
            }
        }
        return null;
    }

    private static List<Category> categories(Element content, String url) {
        Element categories = content.selectFirst(".hiddeninfo.maincategories");
        if (categories == null) {
            return List.of();
        }
        List<Category> result = new ArrayList<>();
        for (String label : LIST_SEPARATOR.split(categories.wholeText())) {
            if (label.isBlank()) {
                continue;
            }
            Optional<Category> category = CategoryLabels.fromLabel(label);
            if (category.isPresent()) {
                result.add(category.get());
            } else {
                LOG.log(Level.WARNING, "Unknown category \"{0}\" in {1}", label.strip(), url);
            }
        }
        return result;
    }

    private static List<String> dialects(Element left) {
        for (Element strong : left.select("strong")) {
            Element list = strong.nextElementSibling();
            if (hasLabel(strong, DIALECT_LABELS) && list != null) {
                return Arrays.stream(list.text().split("[,\\s]+")).filter(s -> !s.isBlank()).toList();
            }
        }
        return List.of();
    }

    private static List<Example> examples(Element content) {
        List<Example> examples = new ArrayList<>();
        for (Element paragraph : content.select(".example_doc > p")) {
            Element next = paragraph.nextElementSibling();
            Element source = next != null && next.hasClass("videocontainer") ? next.selectFirst("source[src]") : null;
            examples.add(new Example(nonBlank(paragraph.text()), source == null ? null : media(source)));
        }
        return examples;
    }

    private static List<DialectVariant> dialectVariants(Element content) {
        List<DialectVariant> variants = new ArrayList<>();
        for (Element video : content.select("video[id^=video-dialekt-]")) {
            Element source = video.selectFirst("source[src]");
            if (source == null) {
                continue;
            }
            Element container = video.parent();
            Element heading = container == null ? null : container.previousElementSibling();
            List<String> dialects = new ArrayList<>();
            if (heading != null && "h2".equals(heading.tagName())) {
                Matcher matcher = DIALECT.matcher(heading.text());
                while (matcher.find()) {
                    dialects.add(matcher.group());
                }
            }
            variants.add(new DialectVariant(dialects, media(source)));
        }
        return variants;
    }

    private static Media media(Element element) {
        String url = element.absUrl("src");
        int fragment = url.indexOf('#');
        return new Media(fragment < 0 ? url : url.substring(0, fragment), null);
    }

    private static Element required(Element parent, String cssQuery, String url) {
        Element element = parent.selectFirst(cssQuery);
        if (element == null) {
            throw new ExtractionException("No sign found in " + url + ": missing " + cssQuery);
        }
        return element;
    }

    private static boolean hasLabel(Element element, Collection<String> labels) {
        String text = element.text().strip().toLowerCase(Locale.ROOT);
        return labels.stream().anyMatch(text::startsWith);
    }

    private static String nonBlank(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
