package fr.ledcom.signaire.extractor;

import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import fr.ledcom.signaire.bundle.Bundle;
import fr.ledcom.signaire.bundle.BundleException;
import fr.ledcom.signaire.bundle.BundleReader;
import fr.ledcom.signaire.bundle.BundleWriter;
import fr.ledcom.signaire.extractor.ExtractionStatus.Stage;
import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Language;
import fr.ledcom.signaire.model.Media;
import fr.ledcom.signaire.model.Sign;
import net.jcip.annotations.ThreadSafe;

/**
 * Extracts the signs of a category into a bundle, in three stages running at the same time: the search lists the
 * signs, each sign found is read (or reused from the existing bundle), and its chosen media are downloaded. All the
 * requests go through the same {@link SignSource}, whose throttle bounds the load on SignSuisse.
 *
 * <p>Media are downloaded into a cache directory shared by all categories ({@value #MEDIA_CACHE} in the output
 * directory by default). Files already in the cache are not downloaded again, so an interrupted extraction can be
 * resumed. See {@code docs/design/extraction.md}.
 */
@ThreadSafe
public class CategoryExtractor {

    /** Name of the media cache, inside the output directory. */
    public static final String MEDIA_CACHE = ".media";
    private static final Logger LOG = System.getLogger(CategoryExtractor.class.getName());
    private static final Duration DEFAULT_PROGRESS_INTERVAL = Duration.ofSeconds(5);
    private static final long POLL_MILLIS = 100;
    private static final long STOP_SECONDS = 10;
    private static final String EXTRACTION_OF = "Extraction of ";

    private final SignSource source;
    private final SignEnumerator enumerator;
    private final BundleWriter writer;
    private final ExtractionListener listener;
    private final Duration progressInterval;
    private final Clock clock;
    private final AtomicReference<ExtractionTracker> lastTracker = new AtomicReference<>();
    private final AtomicReference<Run> running = new AtomicReference<>();
    private volatile boolean cancelled;

    public CategoryExtractor(SignSource source, SignEnumerator enumerator, BundleWriter writer) {
        this(source, enumerator, writer, ExtractionListener.NONE);
    }

    public CategoryExtractor(SignSource source, SignEnumerator enumerator, BundleWriter writer,
            ExtractionListener listener) {
        this(source, enumerator, writer, listener, DEFAULT_PROGRESS_INTERVAL, Clock.systemUTC());
    }

    /**
     * @param progressInterval time between two calls of {@link ExtractionListener#progress} while extracting
     */
    public CategoryExtractor(SignSource source, SignEnumerator enumerator, BundleWriter writer,
            ExtractionListener listener, Duration progressInterval, Clock clock) {
        this.source = source;
        this.enumerator = enumerator;
        this.writer = writer;
        this.listener = listener;
        this.progressInterval = progressInterval;
        this.clock = clock;
    }

    public ExtractionReport extract(Category category, Collection<Language> languages, Path outputDir,
            MediaChoice media) {
        return extract(category, languages, outputDir, outputDir.resolve(MEDIA_CACHE), ExtractionOptions.of(media));
    }

    public ExtractionReport extract(Category category, Collection<Language> languages, Path outputDir, Path cache,
            MediaChoice media) {
        return extract(category, languages, outputDir, cache, ExtractionOptions.of(media));
    }

    /**
     * Extracts a category.
     *
     * @param outputDir where to write the bundle, and where the existing bundle of the category is
     * @param cache where media are downloaded, and found when already downloaded
     * @throws CancellationException when {@link #cancel()} was called: no bundle is written
     */
    public ExtractionReport extract(Category category, Collection<Language> languages, Path outputDir, Path cache,
            ExtractionOptions options) {
        createDirectories(outputDir);
        createDirectories(cache);
        Path target = outputDir.resolve(bundleFileName(category));
        Map<String, Sign> previous = options.refresh() ? Map.of() : previousSigns(target);
        Run run = new Run(category, EnumSet.copyOf(languages), cache, options.media(), previous);
        lastTracker.set(run.tracker);
        running.set(run);
        try {
            run.execute();
        } finally {
            running.set(null);
        }
        run.tracker.stage(Stage.WRITING);
        Path bundle = writer.write(target, category, List.copyOf(run.signs), cache);
        run.tracker.stage(Stage.DONE);
        ExtractionStatus status = run.tracker.snapshot();
        listener.progress(status);
        return new ExtractionReport(category, bundle, run.signs.size(), run.failures, status.searches().complete(),
                status);
    }

    /** Where the current (or last) extraction is, or empty before the first one. */
    public Optional<ExtractionStatus> status() {
        return Optional.ofNullable(lastTracker.get()).map(ExtractionTracker::snapshot);
    }

    /** Stops the current extraction, and those that would start: {@link #extract} throws a CancellationException. */
    public void cancel() {
        cancelled = true;
        Run current = running.get();
        if (current != null) {
            current.stop();
        }
    }

    /** Name of the bundle file of a category, e.g. {@code sport-leisure.zip}. */
    public static String bundleFileName(Category category) {
        return category.name().toLowerCase(Locale.ROOT).replace('_', '-') + ".zip";
    }

    private static Map<String, Sign> previousSigns(Path bundle) {
        if (!Files.isRegularFile(bundle)) {
            return Map.of();
        }
        try (Bundle existing = new BundleReader().read(bundle)) {
            return existing.signs().stream().collect(Collectors.toMap(Sign::uid, Function.identity(), (a, b) -> a));
        } catch (BundleException e) {
            LOG.log(Level.WARNING, "Cannot read the signs of {0}, reading them all again: {1}", bundle,
                    e.getMessage());
            return Map.of();
        }
    }

    /** One extraction: its stages and what they produce. */
    private final class Run {
        private final Category category;
        private final Set<Language> languages;
        private final Path cache;
        private final MediaChoice media;
        private final Map<String, Sign> previous;
        private final ExtractionTracker tracker;
        private final BlockingQueue<Optional<SearchHit>> found = new LinkedBlockingQueue<>();
        private final BlockingQueue<Optional<Sign>> toDownload = new LinkedBlockingQueue<>();
        private final List<Sign> signs = Collections.synchronizedList(new ArrayList<>());
        private final List<SearchHit> failures = Collections.synchronizedList(new ArrayList<>());
        private final AtomicReference<List<Future<Void>>> stages = new AtomicReference<>(List.of());

        Run(Category category, Set<Language> languages, Path cache, MediaChoice media, Map<String, Sign> previous) {
            this.category = category;
            this.languages = languages;
            this.cache = cache;
            this.media = media;
            this.previous = previous;
            this.tracker = new ExtractionTracker(category, clock);
        }

        void execute() {
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            try {
                stages.set(List.of(
                        executor.submit(() -> stage(this::search, found)),
                        executor.submit(() -> stage(this::read, toDownload)),
                        executor.submit(() -> stage(this::download, null))));
                if (cancelled) {
                    stop();
                }
                await();
            } finally {
                executor.shutdownNow();
                awaitTermination(executor);
            }
        }

        /** Lets interrupted stages end (a download being written), without waiting for long. */
        private void awaitTermination(ExecutorService executor) {
            try {
                if (!executor.awaitTermination(STOP_SECONDS, TimeUnit.SECONDS)) {
                    LOG.log(Level.WARNING, "The extraction of {0} did not stop within {1} s", category, STOP_SECONDS);
                }
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
            }
        }

        /** Runs a stage, and tells the next one when it ends, even on failure. */
        private <T> Void stage(Runnable work, BlockingQueue<Optional<T>> next) {
            try {
                work.run();
            } finally {
                if (next != null) {
                    next.add(Optional.empty());
                }
            }
            return null;
        }

        /** Interrupts the stages: they stop at their next request or sign. */
        void stop() {
            stages.get().forEach(stage -> stage.cancel(true));
        }

        private void await() {
            long nextProgress = System.nanoTime() + progressInterval.toNanos();
            for (Future<Void> stage : stages.get()) {
                while (!awaited(stage)) {
                    if (System.nanoTime() >= nextProgress) {
                        listener.progress(tracker.snapshot());
                        nextProgress = System.nanoTime() + progressInterval.toNanos();
                    }
                }
            }
        }

        /** Waits a little for a stage: true when it is done; stops everything on cancellation or failure. */
        @SuppressFBWarnings(value = "LEST_LOST_EXCEPTION_STACK_TRACE",
                justification = "The failure of a stage is thrown as is, the interruption kept as the cause")
        private boolean awaited(Future<Void> stage) {
            if (cancelled) {
                throw stop(new CancellationException(EXTRACTION_OF + category + " cancelled"));
            }
            try {
                stage.get(POLL_MILLIS, TimeUnit.MILLISECONDS);
                return true;
            } catch (TimeoutException _) {
                return false;
            } catch (ExecutionException e) {
                throw stop(e.getCause() instanceof RuntimeException cause ? cause
                        : new ExtractionException(EXTRACTION_OF + category + " failed", e.getCause()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                CancellationException cancellation =
                        new CancellationException(EXTRACTION_OF + category + " interrupted");
                cancellation.initCause(e);
                throw stop(cancellation);
            }
        }

        private RuntimeException stop(RuntimeException cause) {
            stop();
            return cause;
        }

        /** Stage 1: lists the signs, language after language, handing each one over when found. */
        private void search() {
            Set<String> seen = new HashSet<>();
            for (Language language : languages) {
                SignEnumerator.Result result = enumerator.enumerate(language, category, new SignEnumerator.Observer() {
                    @Override
                    public void searching(String term) {
                        tracker.searching(language, term);
                    }

                    @Override
                    public void found(SearchHit hit) {
                        if (seen.add(hit.uid())) {
                            tracker.found(!previous.containsKey(hit.uid()));
                            found.add(Optional.of(hit));
                        }
                    }

                    @Override
                    public void searched(String term, int queries, int pending, int count) {
                        tracker.searched(pending);
                    }

                    @Override
                    public void failed(String term, ExtractionException cause) {
                        tracker.searchFailed();
                    }
                });
                tracker.enumerated(result.complete());
                listener.enumerated(category, language, result);
            }
            tracker.searchDone();
        }

        /** Stage 2: reads (or reuses) each sign found. */
        private void read() {
            for (Optional<SearchHit> next = take(found); next.isPresent(); next = take(found)) {
                checkInterrupted();
                Optional<Sign> sign = sign(next.get());
                if (sign.isEmpty()) {
                    continue;
                }
                if (media == MediaChoice.NONE) {
                    extracted(SignMedia.with(sign.get(), SignMedia::linked, SignMedia::linked));
                } else {
                    tracker.mediaQueued(toFetch(sign.get()).size());
                    toDownload.add(sign);
                }
            }
        }

        private Optional<Sign> sign(SearchHit hit) {
            Sign reused = previous.get(hit.uid());
            if (reused != null) {
                tracker.reused();
                return Optional.of(reused);
            }
            tracker.reading(hit.name());
            try {
                Sign sign = source.fetchSign(hit);
                tracker.read();
                return Optional.of(sign);
            } catch (ExtractionException e) {
                tracker.signFailed();
                failures.add(hit);
                listener.signFailed(hit, e);
                return Optional.empty();
            }
        }

        /** Stage 3: downloads the chosen media of each sign. */
        private void download() {
            for (Optional<Sign> next = take(toDownload); next.isPresent(); next = take(toDownload)) {
                checkInterrupted();
                Sign sign = next.get();
                UnaryOperator<Media> fetched = chosen -> download(sign, chosen);
                extracted(SignMedia.with(sign, media.videos() ? fetched : SignMedia::linked,
                        media.images() ? fetched : SignMedia::linked));
            }
        }

        private void extracted(Sign sign) {
            signs.add(sign);
            listener.signExtracted(sign);
        }

        private void checkInterrupted() {
            if (Thread.currentThread().isInterrupted()) {
                throw new CancellationException(EXTRACTION_OF + category + " interrupted");
            }
        }

        private <T> Optional<T> take(BlockingQueue<Optional<T>> queue) {
            try {
                return queue.take();
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
                throw new CancellationException(EXTRACTION_OF + category + " interrupted");
            }
        }

        /** The chosen media of a sign that are not in the cache yet, once each. */
        private Set<String> toFetch(Sign sign) {
            Set<String> files = new HashSet<>();
            for (Media chosen : SignMedia.chosen(sign, media)) {
                String file = SignMedia.file(chosen);
                if (!Files.isRegularFile(cache.resolve(file))) {
                    files.add(file);
                }
            }
            return files;
        }

        /** The media in the cache, downloaded when missing; linked only when the download fails. */
        private Media download(Sign sign, Media chosen) {
            String file = SignMedia.file(chosen);
            Path target = cache.resolve(file);
            if (!Files.isRegularFile(target)) {
                tracker.downloading(file);
                try {
                    source.download(URI.create(chosen.sourceUrl()), target);
                    tracker.downloaded(size(target));
                } catch (ExtractionException e) {
                    tracker.mediaFailed();
                    listener.mediaFailed(sign, chosen, e);
                    return SignMedia.linked(chosen);
                }
            }
            return new Media(chosen.sourceUrl(), file);
        }
    }

    private static long size(Path file) {
        try {
            return Files.size(file);
        } catch (IOException _) {
            return 0;
        }
    }

    private static void createDirectories(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new ExtractionException("Cannot create " + directory, e);
        }
    }
}
