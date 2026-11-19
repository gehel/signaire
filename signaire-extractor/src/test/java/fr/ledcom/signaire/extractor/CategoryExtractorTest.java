package fr.ledcom.signaire.extractor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import fr.ledcom.signaire.bundle.Bundle;
import fr.ledcom.signaire.bundle.BundleReader;
import fr.ledcom.signaire.bundle.BundleWriter;
import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Example;
import fr.ledcom.signaire.model.Language;
import fr.ledcom.signaire.model.Media;
import fr.ledcom.signaire.model.Sign;

class CategoryExtractorTest {

    private static final String VIDEOS = "https://www.sgb-fss.ch/signsuisse/fileadmin/signsuisse_ressources/videos/";
    private static final String IMAGES = "https://www.sgb-fss.ch/signsuisse/fileadmin/signsuisse_ressources/images/";

    @TempDir
    Path output;

    private final FakeSource source = new FakeSource();
    private final CategoryExtractor extractor = new CategoryExtractor(
            source, new SignEnumerator(source, 100, 3), new BundleWriter(Clock.systemUTC()));

    @Test
    void writesTheSignsOfAllRequestedLanguagesInABundle() {
        ExtractionReport report = extractor.extract(Category.CITY, EnumSet.of(Language.FRENCH, Language.GERMAN),
                output, MediaChoice.NONE);

        assertThat(report.bundle()).isEqualTo(output.resolve("city.zip"));
        assertThat(report.signCount()).isEqualTo(2);
        assertThat(report.failures()).isEmpty();
        assertThat(report.enumerationComplete()).isTrue();
        try (Bundle bundle = new BundleReader().read(report.bundle())) {
            assertThat(bundle.manifest().category()).isEqualTo(Category.CITY);
            assertThat(bundle.signs()).extracting(Sign::name).containsExactlyInAnyOrder("LAUSANNE", "ZÜRICH");
        }
    }

    @Test
    void withoutMediaOnlyReferencesTheSignSuisseUrls() {
        ExtractionReport report = extractor.extract(Category.CITY, EnumSet.of(Language.FRENCH), output,
                MediaChoice.NONE);

        assertThat(source.downloads).isEmpty();
        try (Bundle bundle = new BundleReader().read(report.bundle())) {
            assertThat(bundle.signs().get(0).videos()).containsExactly(new Media(VIDEOS + "LAUSANNE.mp4", null));
        }
    }

    @Test
    void downloadsMediaIntoTheBundle() {
        ExtractionReport report = extractor.extract(Category.CITY, EnumSet.of(Language.FRENCH), output,
                MediaChoice.ALL);

        assertThat(source.downloads).containsExactlyInAnyOrder(URI.create(VIDEOS + "LAUSANNE.mp4"),
                URI.create(VIDEOS + "LAUSANNE-EXAMPLE.mp4"), URI.create(IMAGES + "LAUSANNE.jpg"));
        try (Bundle bundle = new BundleReader().read(report.bundle())) {
            Sign sign = bundle.signs().get(0);
            assertThat(sign.videos()).containsExactly(new Media(VIDEOS + "LAUSANNE.mp4", "media/LAUSANNE.mp4"));
            assertThat(sign.examples().get(0).video().file()).isEqualTo("media/LAUSANNE-EXAMPLE.mp4");
            bundle.verifyMedia();
        }
    }

    @Test
    void downloadsOnlyTheVideos() {
        ExtractionReport report = extractor.extract(Category.CITY, EnumSet.of(Language.FRENCH), output,
                MediaChoice.VIDEOS);

        assertThat(source.downloads).containsExactlyInAnyOrder(URI.create(VIDEOS + "LAUSANNE.mp4"),
                URI.create(VIDEOS + "LAUSANNE-EXAMPLE.mp4"));
        try (Bundle bundle = new BundleReader().read(report.bundle())) {
            Sign sign = bundle.signs().get(0);
            assertThat(sign.videos()).containsExactly(new Media(VIDEOS + "LAUSANNE.mp4", "media/LAUSANNE.mp4"));
            assertThat(sign.images()).containsExactly(new Media(IMAGES + "LAUSANNE.jpg", null));
            bundle.verifyMedia();
        }
    }

    @Test
    void downloadsOnlyTheImages() {
        ExtractionReport report = extractor.extract(Category.CITY, EnumSet.of(Language.FRENCH), output,
                MediaChoice.IMAGES);

        assertThat(source.downloads).containsExactly(URI.create(IMAGES + "LAUSANNE.jpg"));
        try (Bundle bundle = new BundleReader().read(report.bundle())) {
            Sign sign = bundle.signs().get(0);
            assertThat(sign.videos()).containsExactly(new Media(VIDEOS + "LAUSANNE.mp4", null));
            assertThat(sign.examples().get(0).video().file()).isNull();
            assertThat(sign.images()).containsExactly(new Media(IMAGES + "LAUSANNE.jpg", "media/LAUSANNE.jpg"));
        }
    }

    @Test
    void writesIntoNewDirectoriesWithTheMediaCacheElsewhere() {
        Path newOutput = output.resolve("new/bundles");
        Path cache = output.resolve("cache/media");

        ExtractionReport report = extractor.extract(Category.CITY, EnumSet.of(Language.FRENCH), newOutput, cache,
                MediaChoice.ALL);

        assertThat(report.bundle()).exists().hasParent(newOutput);
        assertThat(cache).isDirectory();
        assertThat(newOutput.resolve(CategoryExtractor.MEDIA_CACHE)).doesNotExist();
    }

    @Test
    void reusesMediaDownloadedByAPreviousRun() throws IOException {
        Path cached = output.resolve(CategoryExtractor.MEDIA_CACHE).resolve("media/LAUSANNE.mp4");
        Files.createDirectories(cached.getParent());
        Files.writeString(cached, "downloaded before");

        extractor.extract(Category.CITY, EnumSet.of(Language.FRENCH), output, MediaChoice.ALL);

        assertThat(source.downloads).containsExactly(URI.create(VIDEOS + "LAUSANNE-EXAMPLE.mp4"),
                URI.create(IMAGES + "LAUSANNE.jpg"));
    }

    @Test
    void theStatusTellsWhatWasDoneOnceDone() throws IOException {
        Path cached = output.resolve(CategoryExtractor.MEDIA_CACHE).resolve("media/LAUSANNE.mp4");
        Files.createDirectories(cached.getParent());
        Files.writeString(cached, "downloaded before");
        List<ExtractionStatus> progress = new ArrayList<>();
        CategoryExtractor followed = new CategoryExtractor(source, new SignEnumerator(source, 100, 3),
                new BundleWriter(Clock.systemUTC()), new ExtractionListener() {
                    @Override
                    public void progress(ExtractionStatus status) {
                        progress.add(status);
                    }
                });

        ExtractionStatus status = followed.extract(Category.CITY, EnumSet.of(Language.FRENCH), output,
                MediaChoice.ALL).status();

        assertThat(status.stage()).isEqualTo(ExtractionStatus.Stage.DONE);
        assertThat(status.searches()).isEqualTo(new ExtractionStatus.Searches(35, 0, 0, true, null));
        assertThat(status.signs()).isEqualTo(new ExtractionStatus.Signs(1, 1, 0, 0, null));
        assertThat(status.signs().pending()).isZero();
        // the video was in the cache already
        assertThat(status.media().queued()).isEqualTo(2);
        assertThat(status.media().downloaded()).isEqualTo(2);
        assertThat(status.media().bytes()).isPositive();
        assertThat(status.requestsDone()).isEqualTo(35 + 1 + 2);
        assertThat(status.requestsLeft()).isZero();
        assertThat(progress).last().isEqualTo(status);
        assertThat(followed.status()).contains(status);
    }

    @Test
    void reusesTheSignsOfTheExistingBundle() {
        extractor.extract(Category.CITY, EnumSet.of(Language.FRENCH), output, MediaChoice.NONE);
        source.fetches.clear();

        ExtractionReport report = extractor.extract(Category.CITY, EnumSet.of(Language.FRENCH), output,
                MediaChoice.NONE);

        assertThat(source.fetches).isEmpty();
        assertThat(report.signCount()).isEqualTo(1);
        assertThat(report.status().signs().reused()).isEqualTo(1);
        assertThat(report.status().requestsDone()).isEqualTo(35);
    }

    @Test
    void readsAllTheSignsAgainWhenAskedToRefresh() {
        extractor.extract(Category.CITY, EnumSet.of(Language.FRENCH), output, MediaChoice.NONE);
        source.fetches.clear();

        extractor.extract(Category.CITY, EnumSet.of(Language.FRENCH), output, output.resolve(".media"),
                new ExtractionOptions(MediaChoice.NONE, true));

        assertThat(source.fetches).containsExactly("1");
    }

    @Test
    void reusedSignsGetTheChosenMedia() {
        extractor.extract(Category.CITY, EnumSet.of(Language.FRENCH), output, MediaChoice.NONE);

        ExtractionReport report = extractor.extract(Category.CITY, EnumSet.of(Language.FRENCH), output,
                MediaChoice.VIDEOS);

        try (Bundle bundle = new BundleReader().read(report.bundle())) {
            assertThat(bundle.signs().get(0).videos())
                    .containsExactly(new Media(VIDEOS + "LAUSANNE.mp4", "media/LAUSANNE.mp4"));
            bundle.verifyMedia();
        }
    }

    @Test
    void keepsTheSignsWhoseMediaFailLinkedToSignSuisse() {
        source.failingDownload = URI.create(IMAGES + "LAUSANNE.jpg");
        List<String> failed = new ArrayList<>();
        CategoryExtractor followed = new CategoryExtractor(source, new SignEnumerator(source, 100, 3),
                new BundleWriter(Clock.systemUTC()), new ExtractionListener() {
                    @Override
                    public void mediaFailed(Sign sign, Media media, RuntimeException cause) {
                        failed.add(sign.name() + " " + media.sourceUrl());
                    }
                });

        ExtractionReport report = followed.extract(Category.CITY, EnumSet.of(Language.FRENCH), output,
                MediaChoice.ALL);

        assertThat(failed).containsExactly("LAUSANNE " + IMAGES + "LAUSANNE.jpg");
        assertThat(report.status().media().failed()).isEqualTo(1);
        try (Bundle bundle = new BundleReader().read(report.bundle())) {
            Sign sign = bundle.signs().get(0);
            assertThat(sign.images()).containsExactly(new Media(IMAGES + "LAUSANNE.jpg", null));
            assertThat(sign.videos()).containsExactly(new Media(VIDEOS + "LAUSANNE.mp4", "media/LAUSANNE.mp4"));
        }
    }

    @Test
    @Timeout(10)
    void readsSignsWhileTheSearchGoesOn() throws InterruptedException {
        // the search of "z" waits until LAUSANNE, found by "a", is read: it would never end without parallel stages
        source.searchWaitsForAFetch = new CountDownLatch(1);

        ExtractionReport report = extractor.extract(Category.CITY, EnumSet.of(Language.FRENCH), output,
                MediaChoice.NONE);

        assertThat(source.searchWaitsForAFetch.await(0, TimeUnit.SECONDS)).isTrue();
        assertThat(report.signCount()).isEqualTo(1);
    }

    @Test
    @Timeout(10)
    void cancellingStopsTheExtractionWithoutWritingTheBundle() {
        CategoryExtractor cancelling = new CategoryExtractor(source, new SignEnumerator(source, 100, 3),
                new BundleWriter(Clock.systemUTC()));
        source.onFetch = cancelling::cancel;

        assertThatThrownBy(() -> cancelling.extract(Category.CITY, EnumSet.of(Language.FRENCH), output,
                MediaChoice.NONE)).isInstanceOf(CancellationException.class);
        assertThat(output.resolve("city.zip")).doesNotExist();
    }

    @Test
    void reportsSignsThatCannotBeExtractedAndKeepsTheOthers() {
        source.failingUid = "1";

        ExtractionReport report = extractor.extract(Category.CITY, EnumSet.of(Language.FRENCH, Language.GERMAN),
                output, MediaChoice.NONE);

        assertThat(report.signCount()).isEqualTo(1);
        assertThat(report.failures()).extracting(SearchHit::uid).containsExactly("1");
    }

    /** A lexicon with one French and one German sign in the CITY category. */
    private static final class FakeSource implements SignSource {
        private final List<URI> downloads = Collections.synchronizedList(new ArrayList<>());
        private final List<String> fetches = Collections.synchronizedList(new ArrayList<>());
        private String failingUid;
        private URI failingDownload;
        private CountDownLatch searchWaitsForAFetch;
        private Runnable onFetch = () -> { };

        @Override
        public List<SearchHit> search(String term, Language language, Category category) {
            if (searchWaitsForAFetch != null && "z".equals(term)) {
                try {
                    searchWaitsForAFetch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new ExtractionException("Interrupted", e);
                }
            }
            if (category != Category.CITY) {
                return List.of();
            }
            return switch (language) {
                case FRENCH -> List.of(new SearchHit("1", "LAUSANNE", language, "https://example.org/1"));
                case GERMAN -> List.of(new SearchHit("2", "ZÜRICH", language, "https://example.org/2"));
                default -> List.of();
            };
        }

        @Override
        public Sign fetchSign(SearchHit hit) {
            fetches.add(hit.uid());
            onFetch.run();
            if (searchWaitsForAFetch != null) {
                searchWaitsForAFetch.countDown();
            }
            if (hit.uid().equals(failingUid)) {
                throw new ExtractionException("Cannot fetch " + hit.url());
            }
            Sign.Builder builder = Sign.builder(hit.uid(), hit.language(), hit.name())
                    .categories(List.of(Category.CITY))
                    .videos(List.of(new Media(VIDEOS + hit.name() + ".mp4", null)));
            if ("1".equals(hit.uid())) {
                builder.examples(List.of(
                        new Example("Exemple", new Media(VIDEOS + hit.name() + "-EXAMPLE.mp4", null))))
                        .images(List.of(new Media(IMAGES + hit.name() + ".jpg", null)));
            }
            return builder.build();
        }

        @Override
        public void download(URI uri, Path target) {
            if (uri.equals(failingDownload)) {
                throw new ExtractionException("HTTP status 404");
            }
            downloads.add(uri);
            try {
                Files.createDirectories(target.getParent());
                Files.writeString(target, "content of " + uri);
            } catch (IOException e) {
                throw new ExtractionException("Cannot write " + target, e);
            }
        }
    }
}
