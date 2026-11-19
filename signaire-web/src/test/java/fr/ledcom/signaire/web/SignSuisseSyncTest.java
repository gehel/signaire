package fr.ledcom.signaire.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import fr.ledcom.signaire.core.bundles.BundleLoader;
import fr.ledcom.signaire.core.media.MediaLibrary;
import fr.ledcom.signaire.extractor.SearchHit;
import fr.ledcom.signaire.extractor.SignSource;
import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Language;
import fr.ledcom.signaire.model.Sign;
import fr.ledcom.signaire.web.SignSuisseSync.Progress;
import fr.ledcom.signaire.web.SignSuisseSync.State;

/** Finding the signs of a category takes hundreds of searches: the synchronization says where it is. */
class SignSuisseSyncTest {

    @TempDir
    Path directory;

    @Test
    void showsTheSearchesWhileFindingTheSignsAndCanBeStoppedThen() throws InterruptedException {
        SlowSignSuisse signSuisse = new SlowSignSuisse(3);
        SignSuisseSync sync = new SignSuisseSync(signSuisse, mock(BundleLoader.class), mock(MediaLibrary.class),
                mock(SignEditor.class), directory.resolve("bundles"), directory.resolve("media"));

        sync.start(List.of(Category.CITY), List.of(Language.FRENCH), false);
        assertThat(signSuisse.blocked.await(10, TimeUnit.SECONDS)).isTrue();

        Progress progress = sync.progress();
        assertThat(progress.listing()).isTrue();
        assertThat(progress.category()).isEqualTo(Category.CITY);
        assertThat(progress.language()).isEqualTo(Language.FRENCH);
        assertThat(progress.searches()).isEqualTo(2);
        assertThat(progress.found()).isEqualTo(1);

        sync.cancel();
        signSuisse.release.countDown();
        assertThat(sync.awaitCompletion(Duration.ofSeconds(10))).isTrue();
        assertThat(sync.progress().state()).isEqualTo(State.CANCELLED);
        assertThat(signSuisse.searches.get()).isEqualTo(3);
    }

    /** Finds one sign with "a", then blocks on a search until released. */
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
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return "a".equals(term) ? List.of(new SearchHit("1", "AARAU", language, "https://example.org/1"))
                    : List.of();
        }

        @Override
        public Sign fetchSign(SearchHit hit) {
            throw new UnsupportedOperationException("not expected while finding the signs");
        }

        @Override
        public void download(URI uri, Path target) {
            throw new UnsupportedOperationException("not expected while finding the signs");
        }
    }
}
