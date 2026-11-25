package fr.ledcom.signaire.extractor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import fr.ledcom.signaire.extractor.ExtractionStatus.Stage;
import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Language;
import net.jcip.annotations.GuardedBy;
import net.jcip.annotations.ThreadSafe;

/** Counts what the stages of an extraction do, for {@link ExtractionStatus} snapshots. */
@ThreadSafe
final class ExtractionTracker {

    private final Category category;
    private final Clock clock;
    private final Instant start;
    @GuardedBy("this")
    private Instant end;
    @GuardedBy("this")
    private Stage stage = Stage.SEARCHING;
    @GuardedBy("this")
    private Language language;
    @GuardedBy("this")
    private int searches;
    @GuardedBy("this")
    private int searchesPending;
    @GuardedBy("this")
    private int searchesFailed;
    @GuardedBy("this")
    private boolean complete = true;
    @GuardedBy("this")
    private String currentSearch;
    @GuardedBy("this")
    private int signsFound;
    @GuardedBy("this")
    private int signsToRead;
    @GuardedBy("this")
    private int signsRead;
    @GuardedBy("this")
    private int signsReused;
    @GuardedBy("this")
    private int signsFailed;
    @GuardedBy("this")
    private String currentSign;
    @GuardedBy("this")
    private int mediaQueued;
    @GuardedBy("this")
    private int mediaDownloaded;
    @GuardedBy("this")
    private int mediaFailed;
    @GuardedBy("this")
    private long bytes;
    @GuardedBy("this")
    private String currentMedia;

    ExtractionTracker(Category category, Clock clock) {
        this.category = category;
        this.clock = clock;
        this.start = clock.instant();
    }

    synchronized void stage(Stage newStage) {
        stage = newStage;
        if (newStage == Stage.DONE) {
            end = clock.instant();
        }
    }

    synchronized void searching(Language newLanguage, String term) {
        language = newLanguage;
        currentSearch = term;
    }

    synchronized void searched(int pending) {
        searches++;
        searchesPending = pending;
        currentSearch = null;
    }

    synchronized void searchFailed() {
        searchesFailed++;
    }

    synchronized void enumerated(boolean languageComplete) {
        complete &= languageComplete;
        searchesPending = 0;
    }

    @SuppressFBWarnings(value = "ENMI_NULL_ENUM_VALUE",
            justification = "No language is being searched any more: the status documents null")
    synchronized void searchDone() {
        language = null;
        currentSearch = null;
        if (stage == Stage.SEARCHING) {
            stage = Stage.EXTRACTING;
        }
    }

    /**
     * A sign found by the search.
     *
     * @param toRead whether its page will be read, rather than the sign reused
     */
    synchronized void found(boolean toRead) {
        signsFound++;
        if (toRead) {
            signsToRead++;
        }
    }

    synchronized void reading(String name) {
        currentSign = name;
    }

    synchronized void read() {
        signsRead++;
        currentSign = null;
    }

    synchronized void reused() {
        signsReused++;
    }

    synchronized void signFailed() {
        signsFailed++;
        currentSign = null;
    }

    synchronized void mediaQueued(int count) {
        mediaQueued += count;
    }

    synchronized void downloading(String file) {
        currentMedia = file;
    }

    synchronized void downloaded(long size) {
        mediaDownloaded++;
        bytes += size;
        currentMedia = null;
    }

    synchronized void mediaFailed() {
        mediaFailed++;
        currentMedia = null;
    }

    synchronized ExtractionStatus snapshot() {
        ExtractionStatus.Searches searchCounts = new ExtractionStatus.Searches(searches, searchesPending,
                searchesFailed, complete, currentSearch);
        ExtractionStatus.Signs signCounts = new ExtractionStatus.Signs(signsFound, signsRead, signsReused, signsFailed,
                currentSign);
        ExtractionStatus.Media mediaCounts = new ExtractionStatus.Media(mediaQueued, mediaDownloaded, mediaFailed,
                bytes, currentMedia);
        int done = searches + signsRead + signsFailed + mediaDownloaded + mediaFailed;
        int left = searchesPending + Math.max(0, signsToRead - signsRead - signsFailed) + mediaCounts.pending();
        Duration elapsed = Duration.between(start, end == null ? clock.instant() : end);
        Duration remaining = done == 0 ? null : elapsed.multipliedBy(left).dividedBy(done);
        return new ExtractionStatus(category, stage, language, searchCounts, signCounts, mediaCounts, done, left,
                elapsed, remaining);
    }
}
