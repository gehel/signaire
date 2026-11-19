package fr.ledcom.signaire.extractor;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import org.junit.jupiter.api.Test;
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

    @TempDir
    Path output;

    private final FakeSource source = new FakeSource();
    private final CategoryExtractor extractor = new CategoryExtractor(
            source, new SignEnumerator(source, 100, 3), new BundleWriter(Clock.systemUTC()));

    @Test
    void writesTheSignsOfAllRequestedLanguagesInABundle() {
        ExtractionReport report = extractor.extract(Category.CITY, EnumSet.of(Language.FRENCH, Language.GERMAN),
                output, false);

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
        ExtractionReport report = extractor.extract(Category.CITY, EnumSet.of(Language.FRENCH), output, false);

        assertThat(source.downloads).isEmpty();
        try (Bundle bundle = new BundleReader().read(report.bundle())) {
            assertThat(bundle.signs().get(0).videos()).containsExactly(new Media(VIDEOS + "LAUSANNE.mp4", null));
        }
    }

    @Test
    void downloadsMediaIntoTheBundle() {
        ExtractionReport report = extractor.extract(Category.CITY, EnumSet.of(Language.FRENCH), output, true);

        assertThat(source.downloads).containsExactlyInAnyOrder(
                URI.create(VIDEOS + "LAUSANNE.mp4"), URI.create(VIDEOS + "LAUSANNE-EXAMPLE.mp4"));
        try (Bundle bundle = new BundleReader().read(report.bundle())) {
            Sign sign = bundle.signs().get(0);
            assertThat(sign.videos()).containsExactly(new Media(VIDEOS + "LAUSANNE.mp4", "media/LAUSANNE.mp4"));
            assertThat(sign.examples().get(0).video().file()).isEqualTo("media/LAUSANNE-EXAMPLE.mp4");
            bundle.verifyMedia();
        }
    }

    @Test
    void writesIntoNewDirectoriesWithTheMediaCacheElsewhere() {
        Path newOutput = output.resolve("new/bundles");
        Path cache = output.resolve("cache/media");

        ExtractionReport report = extractor.extract(Category.CITY, EnumSet.of(Language.FRENCH), newOutput, cache,
                true);

        assertThat(report.bundle()).exists().hasParent(newOutput);
        assertThat(cache).isDirectory();
        assertThat(newOutput.resolve(CategoryExtractor.MEDIA_CACHE)).doesNotExist();
    }

    @Test
    void reusesMediaDownloadedByAPreviousRun() throws IOException {
        Path cached = output.resolve(CategoryExtractor.MEDIA_CACHE).resolve("media/LAUSANNE.mp4");
        Files.createDirectories(cached.getParent());
        Files.writeString(cached, "downloaded before");

        extractor.extract(Category.CITY, EnumSet.of(Language.FRENCH), output, true);

        assertThat(source.downloads).containsExactly(URI.create(VIDEOS + "LAUSANNE-EXAMPLE.mp4"));
    }

    @Test
    void tellsTheMediaToDownloadThenEachDownload() throws IOException {
        Path cached = output.resolve(CategoryExtractor.MEDIA_CACHE).resolve("media/LAUSANNE.mp4");
        Files.createDirectories(cached.getParent());
        Files.writeString(cached, "downloaded before");
        List<String> events = new ArrayList<>();
        CategoryExtractor followed = new CategoryExtractor(source, new SignEnumerator(source, 100, 3),
                new BundleWriter(Clock.systemUTC()), new ExtractionListener() {
                    @Override
                    public void mediaQueued(Sign sign, int count) {
                        events.add("queued " + sign.name() + " " + count);
                    }

                    @Override
                    public void mediaDownloaded(URI uri) {
                        events.add("downloaded " + uri.getPath().substring(uri.getPath().lastIndexOf('/') + 1));
                    }

                    @Override
                    public void signExtracted(Sign sign) {
                        events.add("extracted " + sign.name());
                    }
                });

        followed.extract(Category.CITY, EnumSet.of(Language.FRENCH), output, true);

        assertThat(events).containsExactly("queued LAUSANNE 1", "downloaded LAUSANNE-EXAMPLE.mp4",
                "extracted LAUSANNE");
    }

    @Test
    void reportsSignsThatCannotBeExtractedAndKeepsTheOthers() {
        source.failingUid = "1";

        ExtractionReport report = extractor.extract(Category.CITY, EnumSet.of(Language.FRENCH, Language.GERMAN),
                output, false);

        assertThat(report.signCount()).isEqualTo(1);
        assertThat(report.failures()).extracting(SearchHit::uid).containsExactly("1");
    }

    /** A lexicon with one French and one German sign in the CITY category. */
    private static final class FakeSource implements SignSource {
        private final List<URI> downloads = new ArrayList<>();
        private String failingUid;

        @Override
        public List<SearchHit> search(String term, Language language, Category category) {
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
            if (hit.uid().equals(failingUid)) {
                throw new ExtractionException("Cannot fetch " + hit.url());
            }
            Sign.Builder builder = Sign.builder(hit.uid(), hit.language(), hit.name())
                    .categories(List.of(Category.CITY))
                    .videos(List.of(new Media(VIDEOS + hit.name() + ".mp4", null)));
            if ("1".equals(hit.uid())) {
                builder.examples(List.of(
                        new Example("Exemple", new Media(VIDEOS + hit.name() + "-EXAMPLE.mp4", null))));
            }
            return builder.build();
        }

        @Override
        public void download(URI uri, Path target) {
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
