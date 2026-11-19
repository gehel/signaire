package fr.ledcom.signaire.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import fr.ledcom.signaire.extractor.ExtractionOptions;
import fr.ledcom.signaire.extractor.ExtractionStatus;
import fr.ledcom.signaire.extractor.MediaChoice;
import fr.ledcom.signaire.extractor.SearchHit;
import fr.ledcom.signaire.extractor.SignSource;
import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Language;
import fr.ledcom.signaire.model.Media;
import fr.ledcom.signaire.model.Sign;
import fr.ledcom.signaire.web.SignSuisseSync.CategoryResult;
import fr.ledcom.signaire.web.SignSuisseSync.Progress;
import fr.ledcom.signaire.web.SignSuisseSync.State;

/** An extraction takes minutes to hours: the synchronization says where it is and what is left. */
class SignSuisseSyncTest {

    @TempDir
    Path directory;

    @Test
    void showsWhereTheExtractionIsAndCanBeStopped() throws InterruptedException {
        SlowSignSuisse signSuisse = new SlowSignSuisse(3);
        SignSuisseSync sync = new SignSuisseSync(signSuisse, mock(BundleLoading.class),
                directory.resolve("bundles"), directory.resolve("media"));

        sync.start(List.of(Category.CITY, Category.NATURE), List.of(Language.FRENCH),
                ExtractionOptions.of(MediaChoice.NONE));
        assertThat(signSuisse.blocked.await(10, TimeUnit.SECONDS)).isTrue();

        Progress progress = sync.progress();
        ExtractionStatus status = progress.current();
        assertThat(status.category()).isEqualTo(Category.CITY);
        assertThat(status.stage()).isEqualTo(ExtractionStatus.Stage.SEARCHING);
        assertThat(status.language()).isEqualTo(Language.FRENCH);
        assertThat(status.searches().sent()).isEqualTo(2);
        assertThat(status.searches().pending()).isEqualTo(33);
        assertThat(status.searches().current()).isEqualTo("c");
        assertThat(status.signs().found()).isEqualTo(1);
        assertThat(progress.left()).containsExactly(Category.NATURE);

        sync.cancel();
        signSuisse.release.countDown();
        assertThat(sync.awaitCompletion(Duration.ofSeconds(10))).isTrue();
        assertThat(sync.progress().state()).isEqualTo(State.CANCELLED);
        assertThat(signSuisse.searches.get()).isEqualTo(3);
    }

    @Test
    void tellsWhatEachCategoryTook() throws InterruptedException {
        SlowSignSuisse signSuisse = new SlowSignSuisse(0);
        BundleLoading loading = mock(BundleLoading.class);
        SignSuisseSync sync = new SignSuisseSync(signSuisse, loading,
                directory.resolve("bundles"), directory.resolve("media"));

        sync.start(List.of(Category.CITY), List.of(Language.FRENCH), ExtractionOptions.of(MediaChoice.ALL));
        assertThat(sync.awaitCompletion(Duration.ofSeconds(10))).isTrue();

        Progress progress = sync.progress();
        assertThat(progress.state()).isEqualTo(State.DONE);
        assertThat(progress.current()).isNull();
        // 35 searches, 1 page, 2 videos
        assertThat(progress.done()).containsExactly(new CategoryResult(Category.CITY, 1, 0, true, 38));
        verify(loading).load(directory.resolve("bundles/city.zip"));
    }

    /** Finds one sign with "a", with two videos; blocks on a search until released (none when 0). */
    private static final class SlowSignSuisse implements SignSource {
        private final int blockingSearch;
        private final AtomicInteger searches = new AtomicInteger();
        private final CountDownLatch blocked = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        SlowSignSuisse(int blockingSearch) {
            this.blockingSearch = blockingSearch;
        }

        @Override
        public List<SearchHit> search(String term, Language language, Category category) {
            if (searches.incrementAndGet() == blockingSearch) {
                blocked.countDown();
                try {
                    release.await();
                } catch (InterruptedException _) {
                    Thread.currentThread().interrupt();
                }
            }
            return "a".equals(term) ? List.of(new SearchHit("1", "AARAU", language, "https://example.org/1"))
                    : List.of();
        }

        @Override
        public Sign fetchSign(SearchHit hit) {
            return Sign.builder(hit.uid(), hit.language(), hit.name()).sourceUrl(hit.url())
                    .videos(List.of(new Media("https://example.org/aarau.mp4", null),
                            new Media("https://example.org/aarau-2.mp4", null)))
                    .build();
        }

        @Override
        public void download(URI uri, Path target) {
            try {
                Files.createDirectories(target.getParent());
                Files.writeString(target, "video");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
