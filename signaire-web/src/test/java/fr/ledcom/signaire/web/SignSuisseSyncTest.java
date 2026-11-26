package fr.ledcom.signaire.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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
import fr.ledcom.signaire.extractor.FakeSignSource;
import fr.ledcom.signaire.extractor.MediaChoice;
import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Language;
import fr.ledcom.signaire.web.SignSuisseSync.CategoryResult;
import fr.ledcom.signaire.web.SignSuisseSync.Progress;
import fr.ledcom.signaire.web.SignSuisseSync.State;

/** An extraction takes minutes to hours: the synchronization says where it is and what is left. */
class SignSuisseSyncTest {

    @TempDir
    Path directory;

    @Test
    void showsWhereTheExtractionIsAndCanBeStopped() throws InterruptedException {
        // the third search waits: "a" found ARBRE and MONTAGNE, "b" nothing
        AtomicInteger searches = new AtomicInteger();
        CountDownLatch blocked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        FakeSignSource signSuisse = new FakeSignSource().beforeSearch(term -> {
            if (searches.incrementAndGet() == 3) {
                blocked.countDown();
                awaitUninterruptibly(release);
            }
        });
        SignSuisseSync sync = new SignSuisseSync(signSuisse, mock(BundleLoading.class),
                directory.resolve("bundles"), directory.resolve("media"));

        // categories are extracted in their order: Nature, then Religion
        sync.start(List.of(Category.RELIGION, Category.NATURE), List.of(Language.FRENCH),
                ExtractionOptions.of(MediaChoice.NONE));
        assertThat(blocked.await(10, TimeUnit.SECONDS)).isTrue();

        Progress progress = sync.progress();
        ExtractionStatus status = progress.current();
        assertThat(status.category()).isEqualTo(Category.NATURE);
        assertThat(status.stage()).isEqualTo(ExtractionStatus.Stage.SEARCHING);
        assertThat(status.language()).isEqualTo(Language.FRENCH);
        assertThat(status.searches().sent()).isEqualTo(2);
        assertThat(status.searches().pending()).isEqualTo(33);
        assertThat(status.searches().current()).isEqualTo("c");
        assertThat(status.signs().found()).isEqualTo(2);
        assertThat(progress.left()).containsExactly(Category.RELIGION);

        sync.cancel();
        release.countDown();
        assertThat(sync.awaitCompletion(Duration.ofSeconds(10))).isTrue();
        assertThat(sync.progress().state()).isEqualTo(State.CANCELLED);
        assertThat(signSuisse.searches()).hasSize(3);
    }

    @Test
    void tellsWhatEachCategoryTook() throws InterruptedException {
        BundleLoading loading = mock(BundleLoading.class);
        SignSuisseSync sync = new SignSuisseSync(new FakeSignSource(), loading,
                directory.resolve("bundles"), directory.resolve("media"));

        sync.start(List.of(Category.CITY), List.of(Language.FRENCH), ExtractionOptions.of(MediaChoice.ALL));
        assertThat(sync.awaitCompletion(Duration.ofSeconds(10))).isTrue();

        Progress progress = sync.progress();
        assertThat(progress.state()).isEqualTo(State.DONE);
        assertThat(progress.current()).isNull();
        // 35 searches, 3 pages (GARE, GENÈVE, LAUSANNE), 4 media (the video of LAUSANNE, the video, example and
        // photo of GARE)
        assertThat(progress.done()).containsExactly(new CategoryResult(Category.CITY, 3, 0, true, 35 + 3 + 4));
        verify(loading).load(directory.resolve("bundles/city.zip"));
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        }
    }
}
