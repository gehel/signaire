package fr.ledcom.signaire.extractor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import fr.ledcom.signaire.bundle.Bundle;
import fr.ledcom.signaire.bundle.BundleReader;
import fr.ledcom.signaire.bundle.BundleWriter;
import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Language;
import fr.ledcom.signaire.model.Media;
import fr.ledcom.signaire.model.Sign;
import fr.ledcom.signaire.model.TestSigns;

/** Extraction of the test signs from the fake SignSuisse: in French, the city has GARE, GENÈVE and LAUSANNE. */
class CategoryExtractorTest {

    private static final Media GARE_VIDEO = TestSigns.GARE.videos().getFirst();
    private static final Media GARE_EXAMPLE = Objects.requireNonNull(TestSigns.GARE.examples().getFirst().video());
    private static final Media GARE_PHOTO = TestSigns.GARE.images().getFirst();
    private static final Media LAUSANNE_VIDEO = TestSigns.atSignSuisse(TestSigns.LAUSANNE).videos().getFirst();
    private static final Set<Language> FRENCH = EnumSet.of(Language.FRENCH);

    @TempDir
    Path output;

    private final FakeSignSource source = new FakeSignSource();
    private final CategoryExtractor extractor = new CategoryExtractor(
            source, new SignEnumerator(source, 100, 3), new BundleWriter(Clock.systemUTC()));

    @Test
    void writesTheSignsOfAllRequestedLanguagesInABundle() {
        ExtractionReport report = extractor.extract(Category.CITY, EnumSet.of(Language.FRENCH, Language.GERMAN),
                output, MediaChoice.NONE);

        assertThat(report.bundle()).isEqualTo(output.resolve("city.zip"));
        assertThat(report.signCount()).isEqualTo(5);
        assertThat(report.failures()).isEmpty();
        assertThat(report.enumerationComplete()).isTrue();
        try (Bundle bundle = new BundleReader().read(report.bundle())) {
            assertThat(bundle.manifest().category()).isEqualTo(Category.CITY);
            assertThat(bundle.signs()).extracting(Sign::name)
                    .containsExactlyInAnyOrder("GARE", "GENÈVE", "LAUSANNE", "BERN", "ZÜRICH");
        }
    }

    @Test
    void withoutMediaOnlyReferencesTheSignSuisseUrls() {
        ExtractionReport report = extractor.extract(Category.CITY, FRENCH, output, MediaChoice.NONE);

        assertThat(source.downloads()).isEmpty();
        try (Bundle bundle = new BundleReader().read(report.bundle())) {
            assertThat(gare(bundle).videos()).containsExactly(GARE_VIDEO);
        }
    }

    @Test
    void downloadsMediaIntoTheBundle() {
        ExtractionReport report = extractor.extract(Category.CITY, FRENCH, output, MediaChoice.ALL);

        assertThat(source.downloads()).containsExactlyInAnyOrder(uri(LAUSANNE_VIDEO), uri(GARE_VIDEO),
                uri(GARE_EXAMPLE), uri(GARE_PHOTO));
        try (Bundle bundle = new BundleReader().read(report.bundle())) {
            Sign gare = gare(bundle);
            assertThat(gare.videos()).containsExactly(downloaded(GARE_VIDEO, "media/gare.mp4"));
            assertThat(gare.examples().getFirst().video()).isEqualTo(
                    downloaded(GARE_EXAMPLE, "media/gare-exemple.mp4"));
            assertThat(gare.images()).containsExactly(downloaded(GARE_PHOTO, "media/gare.jpg"));
            bundle.verifyMedia();
        }
    }

    @Test
    void downloadsOnlyTheVideos() {
        ExtractionReport report = extractor.extract(Category.CITY, FRENCH, output, MediaChoice.VIDEOS);

        assertThat(source.downloads()).containsExactlyInAnyOrder(uri(LAUSANNE_VIDEO), uri(GARE_VIDEO),
                uri(GARE_EXAMPLE));
        try (Bundle bundle = new BundleReader().read(report.bundle())) {
            Sign gare = gare(bundle);
            assertThat(gare.videos()).containsExactly(downloaded(GARE_VIDEO, "media/gare.mp4"));
            assertThat(gare.images()).containsExactly(GARE_PHOTO);
            bundle.verifyMedia();
        }
    }

    @Test
    void downloadsOnlyTheImages() {
        ExtractionReport report = extractor.extract(Category.CITY, FRENCH, output, MediaChoice.IMAGES);

        assertThat(source.downloads()).containsExactly(uri(GARE_PHOTO));
        try (Bundle bundle = new BundleReader().read(report.bundle())) {
            Sign gare = gare(bundle);
            assertThat(gare.videos()).containsExactly(GARE_VIDEO);
            assertThat(gare.examples().getFirst().video()).isEqualTo(GARE_EXAMPLE);
            assertThat(gare.images()).containsExactly(downloaded(GARE_PHOTO, "media/gare.jpg"));
        }
    }

    @Test
    void writesIntoNewDirectoriesWithTheMediaCacheElsewhere() {
        Path newOutput = output.resolve("new/bundles");
        Path cache = output.resolve("cache/media");

        ExtractionReport report = extractor.extract(Category.CITY, FRENCH, newOutput, cache, MediaChoice.ALL);

        assertThat(report.bundle()).exists().hasParent(newOutput);
        assertThat(cache).isDirectory();
        assertThat(newOutput.resolve(CategoryExtractor.MEDIA_CACHE)).doesNotExist();
    }

    @Test
    void reusesMediaDownloadedByAPreviousRun() throws IOException {
        cache("media/gare.mp4");

        extractor.extract(Category.CITY, FRENCH, output, MediaChoice.ALL);

        assertThat(source.downloads()).containsExactlyInAnyOrder(uri(LAUSANNE_VIDEO), uri(GARE_EXAMPLE),
                uri(GARE_PHOTO));
    }

    @Test
    void theStatusTellsWhatWasDoneOnceDone() throws IOException {
        cache("media/gare.mp4");
        List<ExtractionStatus> progress = new ArrayList<>();
        CategoryExtractor followed = new CategoryExtractor(source, new SignEnumerator(source, 100, 3),
                new BundleWriter(Clock.systemUTC()), new ExtractionListener() {
                    @Override
                    public void progress(ExtractionStatus status) {
                        progress.add(status);
                    }
                });

        ExtractionStatus status = followed.extract(Category.CITY, FRENCH, output, MediaChoice.ALL).status();

        assertThat(status.stage()).isEqualTo(ExtractionStatus.Stage.DONE);
        assertThat(status.searches()).isEqualTo(new ExtractionStatus.Searches(35, 0, 0, true, null));
        assertThat(status.signs()).isEqualTo(new ExtractionStatus.Signs(3, 3, 0, 0, null));
        assertThat(status.signs().pending()).isZero();
        // the video of GARE was in the cache already
        assertThat(status.media().queued()).isEqualTo(3);
        assertThat(status.media().downloaded()).isEqualTo(3);
        assertThat(status.media().bytes()).isPositive();
        assertThat(status.requestsDone()).isEqualTo(35 + 3 + 3);
        assertThat(status.requestsLeft()).isZero();
        assertThat(progress).last().isEqualTo(status);
        assertThat(followed.status()).contains(status);
    }

    @Test
    void handsEachSignOverOnceItsMediaAreDownloaded() {
        List<Sign> extracted = new CopyOnWriteArrayList<>();
        CategoryExtractor followed = new CategoryExtractor(source, new SignEnumerator(source, 100, 3),
                new BundleWriter(Clock.systemUTC()), new ExtractionListener() {
                    @Override
                    public void signExtracted(Sign sign) {
                        extracted.add(sign);
                    }
                });

        followed.extract(Category.CITY, EnumSet.of(Language.FRENCH, Language.GERMAN), output, MediaChoice.VIDEOS);

        assertThat(extracted).extracting(Sign::name)
                .containsExactlyInAnyOrder("GARE", "GENÈVE", "LAUSANNE", "BERN", "ZÜRICH");
        assertThat(extracted).filteredOn(sign -> sign.name().equals("GARE")).singleElement()
                .satisfies(sign -> assertThat(sign.videos()).containsExactly(downloaded(GARE_VIDEO, "media/gare.mp4")));
    }

    @Test
    void reusesTheSignsOfTheExistingBundle() {
        extractor.extract(Category.CITY, FRENCH, output, MediaChoice.NONE);
        int pages = source.pages().size();

        ExtractionReport report = extractor.extract(Category.CITY, FRENCH, output, MediaChoice.NONE);

        assertThat(source.pages()).hasSize(pages);
        assertThat(report.signCount()).isEqualTo(3);
        assertThat(report.status().signs().reused()).isEqualTo(3);
        assertThat(report.status().requestsDone()).isEqualTo(35);
    }

    @Test
    void readsAllTheSignsAgainWhenAskedToRefresh() {
        extractor.extract(Category.CITY, FRENCH, output, MediaChoice.NONE);
        int pages = source.pages().size();

        extractor.extract(Category.CITY, FRENCH, output, output.resolve(".media"),
                new ExtractionOptions(MediaChoice.NONE, true));

        assertThat(source.pages().subList(pages, source.pages().size())).containsExactlyInAnyOrder(
                TestSigns.GARE.uid(), TestSigns.GENEVE.uid(), TestSigns.LAUSANNE.uid());
    }

    @Test
    void reusedSignsGetTheChosenMedia() {
        extractor.extract(Category.CITY, FRENCH, output, MediaChoice.NONE);

        ExtractionReport report = extractor.extract(Category.CITY, FRENCH, output, MediaChoice.VIDEOS);

        try (Bundle bundle = new BundleReader().read(report.bundle())) {
            assertThat(gare(bundle).videos()).containsExactly(downloaded(GARE_VIDEO, "media/gare.mp4"));
            bundle.verifyMedia();
        }
    }

    @Test
    void keepsTheSignsWhoseMediaFailLinkedToSignSuisse() {
        source.failingMedia(uri(GARE_PHOTO));
        List<String> failed = new ArrayList<>();
        CategoryExtractor followed = new CategoryExtractor(source, new SignEnumerator(source, 100, 3),
                new BundleWriter(Clock.systemUTC()), new ExtractionListener() {
                    @Override
                    public void mediaFailed(Sign sign, Media media, RuntimeException cause) {
                        failed.add(sign.name() + " " + media.sourceUrl());
                    }
                });

        ExtractionReport report = followed.extract(Category.CITY, FRENCH, output, MediaChoice.ALL);

        assertThat(failed).containsExactly("GARE " + GARE_PHOTO.sourceUrl());
        assertThat(report.status().media().failed()).isEqualTo(1);
        try (Bundle bundle = new BundleReader().read(report.bundle())) {
            Sign gare = gare(bundle);
            assertThat(gare.images()).containsExactly(GARE_PHOTO);
            assertThat(gare.videos()).containsExactly(downloaded(GARE_VIDEO, "media/gare.mp4"));
        }
    }

    @Test
    @Timeout(10)
    void readsSignsWhileTheSearchGoesOn() throws InterruptedException {
        // the search of "z" waits until a sign found before is read: it would never end without parallel stages
        CountDownLatch read = new CountDownLatch(1);
        source.beforePage(hit -> read.countDown()).beforeSearch(term -> {
            if (term.equals("z")) {
                awaitUninterruptibly(read);
            }
        });

        ExtractionReport report = extractor.extract(Category.CITY, FRENCH, output, MediaChoice.NONE);

        assertThat(read.getCount()).isZero();
        assertThat(report.signCount()).isEqualTo(3);
    }

    @Test
    @Timeout(10)
    void cancellingStopsTheExtractionWithoutWritingTheBundle() {
        CategoryExtractor cancelling = new CategoryExtractor(source, new SignEnumerator(source, 100, 3),
                new BundleWriter(Clock.systemUTC()));
        source.beforePage(hit -> cancelling.cancel());

        assertThatThrownBy(() -> cancelling.extract(Category.CITY, FRENCH, output, MediaChoice.NONE))
                .isInstanceOf(CancellationException.class);
        assertThat(output.resolve("city.zip")).doesNotExist();
    }

    @Test
    void reportsSignsThatCannotBeExtractedAndKeepsTheOthers() {
        source.failingPage(TestSigns.LAUSANNE);

        ExtractionReport report = extractor.extract(Category.CITY, EnumSet.of(Language.FRENCH, Language.GERMAN),
                output, MediaChoice.NONE);

        assertThat(report.signCount()).isEqualTo(4);
        assertThat(report.failures()).extracting(SearchHit::uid).containsExactly(TestSigns.LAUSANNE.uid());
    }

    private static Sign gare(Bundle bundle) {
        return bundle.signs().stream().filter(sign -> sign.uid().equals(TestSigns.GARE.uid())).findFirst()
                .orElseThrow();
    }

    private static Media downloaded(Media media, String file) {
        return new Media(media.sourceUrl(), file);
    }

    private static URI uri(Media media) {
        return URI.create(media.sourceUrl());
    }

    private void cache(String media) throws IOException {
        Path cached = output.resolve(CategoryExtractor.MEDIA_CACHE).resolve(media);
        Files.createDirectories(cached.getParent());
        Files.writeString(cached, "downloaded before");
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExtractionException("Interrupted", e);
        }
    }
}
