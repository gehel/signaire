package fr.ledcom.signaire.extractor;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import fr.ledcom.signaire.bundle.BundleException;
import fr.ledcom.signaire.bundle.BundleWriter;
import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.DialectVariant;
import fr.ledcom.signaire.model.Example;
import fr.ledcom.signaire.model.Language;
import fr.ledcom.signaire.model.Media;
import fr.ledcom.signaire.model.Sign;

/**
 * Extracts the signs of a category into a bundle.
 *
 * <p>Media are downloaded into a cache directory shared by all categories ({@value #MEDIA_CACHE} in the output
 * directory). Files already in the cache are not downloaded again, so an interrupted extraction can be resumed.
 */
public class CategoryExtractor {

    /** Name of the media cache, inside the output directory. */
    public static final String MEDIA_CACHE = ".media";
    private static final String MEDIA_DIRECTORY = "media/";
    private static final Pattern UNSAFE_FILE_NAME_CHARACTERS = Pattern.compile("[^A-Za-z0-9._-]");

    private final SignSource source;
    private final SignEnumerator enumerator;
    private final BundleWriter writer;
    private final ExtractionListener listener;

    public CategoryExtractor(SignSource source, SignEnumerator enumerator, BundleWriter writer) {
        this(source, enumerator, writer, ExtractionListener.NONE);
    }

    public CategoryExtractor(SignSource source, SignEnumerator enumerator, BundleWriter writer,
            ExtractionListener listener) {
        this.source = source;
        this.enumerator = enumerator;
        this.writer = writer;
        this.listener = listener;
    }

    /**
     * Extracts a category.
     *
     * @param category the category to extract
     * @param languages the sign languages to extract
     * @param outputDir where to write the bundle
     * @param withMedia whether to download the media into the bundle, or only reference their URLs
     */
    public ExtractionReport extract(Category category, Collection<Language> languages, Path outputDir,
            boolean withMedia) {
        return extract(category, languages, outputDir, outputDir.resolve(MEDIA_CACHE), withMedia);
    }

    /**
     * Extracts a category, with the media cache in a directory of its own.
     *
     * @param cache where media are downloaded, and found when already downloaded
     */
    public ExtractionReport extract(Category category, Collection<Language> languages, Path outputDir, Path cache,
            boolean withMedia) {
        createDirectories(cache);
        List<Sign> signs = new ArrayList<>();
        List<SearchHit> failures = new ArrayList<>();
        boolean complete = true;
        for (Language language : EnumSet.copyOf(languages)) {
            SignEnumerator.Result result = enumerator.enumerate(language, category);
            listener.enumerated(category, language, result);
            complete &= result.complete();
            for (SearchHit hit : result.hits()) {
                try {
                    Sign sign = source.fetchSign(hit);
                    if (withMedia) {
                        sign = withDownloadedMedia(sign, cache);
                    }
                    signs.add(sign);
                    listener.signExtracted(sign);
                } catch (ExtractionException | BundleException e) {
                    failures.add(hit);
                    listener.signFailed(hit, e);
                }
            }
        }
        Path bundle = writer.write(outputDir.resolve(bundleFileName(category)), category, signs, cache);
        return new ExtractionReport(category, bundle, signs.size(), failures, complete);
    }

    /** Name of the bundle file of a category, e.g. {@code sport-leisure.zip}. */
    public static String bundleFileName(Category category) {
        return category.name().toLowerCase(Locale.ROOT).replace('_', '-') + ".zip";
    }

    private Sign withDownloadedMedia(Sign sign, Path cache) {
        return sign.toBuilder()
                .videos(sign.videos().stream().map(media -> download(media, cache)).toList())
                .examples(sign.examples().stream()
                        .map(example -> example.video() == null ? example
                                : new Example(example.text(), download(example.video(), cache)))
                        .toList())
                .images(sign.images().stream().map(media -> download(media, cache)).toList())
                .dialectVariants(sign.dialectVariants().stream()
                        .map(variant -> new DialectVariant(variant.dialects(), download(variant.video(), cache)))
                        .toList())
                .build();
    }

    private Media download(Media media, Path cache) {
        URI uri = URI.create(media.sourceUrl());
        String file = MEDIA_DIRECTORY + fileName(uri);
        Path target = cache.resolve(file);
        if (!Files.isRegularFile(target)) {
            source.download(uri, target);
        }
        return new Media(media.sourceUrl(), file);
    }

    private static String fileName(URI uri) {
        String path = uri.getPath();
        String name = path.substring(path.lastIndexOf('/') + 1);
        if (name.isEmpty()) {
            throw new ExtractionException("No file name in media URL " + uri);
        }
        return UNSAFE_FILE_NAME_CHARACTERS.matcher(name).replaceAll("_");
    }

    private static void createDirectories(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new ExtractionException("Cannot create " + directory, e);
        }
    }
}
