package fr.ledcom.signaire.extractor;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Language;
import fr.ledcom.signaire.model.Sign;
import fr.ledcom.signaire.model.TestSigns;
import net.jcip.annotations.ThreadSafe;

/**
 * SignSuisse in the process, serving {@link TestSigns}: its search, the signs as SignSuisse has them (media without
 * files), and invented bytes for media. Pages or media can fail; hooks run before each search and each page, to hold
 * the extraction where a test wants it. Records what it served. Its links are those of SignSuisse, on
 * {@code example.org}: nothing reads them.
 */
@ThreadSafe
public class FakeSignSource implements SignSource {

    private static final String SITE = "https://example.org";

    /** A search it answered. */
    public record Search(String term, Language language, @Nullable Category category) {
    }

    private final List<Search> searches = new CopyOnWriteArrayList<>();
    private final List<String> pages = new CopyOnWriteArrayList<>();
    private final List<URI> downloads = new CopyOnWriteArrayList<>();
    private final Set<String> failingPages = new CopyOnWriteArraySet<>();
    private final Set<URI> failingMedia = new CopyOnWriteArraySet<>();
    private volatile Consumer<String> beforeSearch = term -> { };
    private volatile Consumer<SearchHit> beforePage = hit -> { };

    /** The hit of a sign in the search. */
    public static SearchHit hit(Sign sign) {
        return new SearchHit(sign.uid(), sign.name(), sign.language(), SITE + FakeSearch.page(sign));
    }

    /** A sign as this fake gives it: as SignSuisse has it, at the address of its page. */
    public static Sign served(Sign sign) {
        return TestSigns.atSignSuisse(sign).toBuilder().sourceUrl(hit(sign).url()).build();
    }

    /** The page of this sign fails, as a page SignSuisse doesn't answer. */
    public FakeSignSource failingPage(Sign sign) {
        failingPages.add(sign.uid());
        return this;
    }

    /** The download of this medium fails. */
    public FakeSignSource failingMedia(URI media) {
        failingMedia.add(media);
        return this;
    }

    /** Runs before each search, with its term: to count them, or to hold one. */
    public FakeSignSource beforeSearch(Consumer<String> hook) {
        beforeSearch = hook;
        return this;
    }

    /** Runs before each page is read. */
    public FakeSignSource beforePage(Consumer<SearchHit> hook) {
        beforePage = hook;
        return this;
    }

    @Override
    public List<SearchHit> search(String term, Language language, @Nullable Category category) {
        beforeSearch.accept(term);
        searches.add(new Search(term, language, category));
        return FakeSearch.search(term, language, category).stream().map(FakeSignSource::hit).toList();
    }

    @Override
    public Sign fetchSign(SearchHit hit) {
        beforePage.accept(hit);
        pages.add(hit.uid());
        if (failingPages.contains(hit.uid())) {
            throw new ExtractionException("Cannot read " + hit.url());
        }
        return TestSigns.atSignSuisse(FakeSearch.sign(hit.uid())).toBuilder().sourceUrl(hit.url()).build();
    }

    @Override
    public void download(URI uri, Path target) {
        if (failingMedia.contains(uri)) {
            throw new ExtractionException("HTTP status 404 for " + uri);
        }
        downloads.add(uri);
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, "content of " + uri, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ExtractionException("Cannot write " + target, e);
        }
    }

    /** The searches it answered, in order. */
    public List<Search> searches() {
        return List.copyOf(searches);
    }

    /** The uids of the pages it served, in order. */
    public List<String> pages() {
        return List.copyOf(pages);
    }

    /** The media it served, in order. */
    public List<URI> downloads() {
        return List.copyOf(downloads);
    }
}
