package fr.ledcom.signaire.web;

import java.lang.System.Logger.Level;
import java.lang.System.Logger;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import fr.ledcom.signaire.core.search.SearchException;
import fr.ledcom.signaire.model.Sign;
import net.jcip.annotations.GuardedBy;
import net.jcip.annotations.ThreadSafe;

/**
 * Indexes the signs of a synchronization as they are extracted, by batches (a commit of the index per sign would
 * cost more than the sign). Best effort: the bundle, loaded at the end of the category, holds all the signs anyway.
 */
@ThreadSafe
final class SyncIndexing {

    private static final Logger LOG = System.getLogger(SyncIndexing.class.getName());

    private final BundleLoading loading;
    private final Clock clock;
    private final int batchSize;
    private final Duration maxDelay;
    @GuardedBy("this")
    private final List<Sign> pending = new ArrayList<>();
    @GuardedBy("this")
    private @Nullable Instant oldest;
    @GuardedBy("this")
    private int indexed;

    SyncIndexing(BundleLoading loading, Clock clock, int batchSize, Duration maxDelay) {
        this.loading = loading;
        this.clock = clock;
        this.batchSize = batchSize;
        this.maxDelay = maxDelay;
    }

    synchronized void add(Sign sign) {
        if (pending.isEmpty()) {
            oldest = clock.instant();
        }
        pending.add(sign);
        if (pending.size() >= batchSize) {
            flush();
        } else {
            flushIfDue();
        }
    }

    /** Indexes the signs waiting since longer than the delay. */
    synchronized void flushIfDue() {
        // oldest is set as soon as a sign is pending
        if (oldest != null && !pending.isEmpty() && !clock.instant().isBefore(oldest.plus(maxDelay))) {
            flush();
        }
    }

    /** Forgets the signs waiting: the bundle being loaded has them, with their media. */
    synchronized void discard() {
        pending.clear();
    }

    /** Signs of the current category indexed so far. */
    synchronized int indexed() {
        return indexed;
    }

    /** Starts a new category. */
    synchronized void reset() {
        pending.clear();
        indexed = 0;
    }

    private void flush() {
        List<Sign> batch = List.copyOf(pending);
        pending.clear();
        try {
            loading.indexExtracted(batch);
            indexed += batch.size();
        } catch (SearchException e) {
            LOG.log(Level.WARNING, "Cannot index {0} signs being extracted, they will be with their bundle: {1}",
                    batch.size(), e.getMessage());
        }
    }
}
