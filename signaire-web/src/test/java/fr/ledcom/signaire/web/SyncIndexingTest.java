package fr.ledcom.signaire.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import fr.ledcom.signaire.core.search.SearchException;
import fr.ledcom.signaire.model.Language;
import fr.ledcom.signaire.model.Sign;

class SyncIndexingTest {

    private final BundleLoading loading = mock(BundleLoading.class);
    private final MutableClock clock = new MutableClock();
    private final SyncIndexing indexing = new SyncIndexing(loading, clock, 2, Duration.ofSeconds(2));

    private final Sign lausanne = Sign.builder("1", Language.FRENCH, "LAUSANNE").build();
    private final Sign berne = Sign.builder("2", Language.FRENCH, "BERNE").build();

    @Test
    void indexesTheSignsByBatches() {
        indexing.add(lausanne);
        verify(loading, never()).indexExtracted(anyList());

        indexing.add(berne);

        verify(loading).indexExtracted(List.of(lausanne, berne));
        assertThat(indexing.indexed()).isEqualTo(2);
    }

    @Test
    void indexesTheSignsWaitingForTooLong() {
        indexing.add(lausanne);
        clock.now = clock.now.plusSeconds(3);

        indexing.flushIfDue();

        verify(loading).indexExtracted(List.of(lausanne));
    }

    @Test
    void forgetsTheSignsWaitingWhenTheBundleReplacesThem() {
        indexing.add(lausanne);

        indexing.discard();
        clock.now = clock.now.plusSeconds(3);
        indexing.flushIfDue();

        verify(loading, never()).indexExtracted(anyList());
    }

    @Test
    void anIndexFailureDoesNotStopTheExtraction() {
        doThrow(new SearchException("Cannot index signs")).when(loading).indexExtracted(anyList());

        indexing.add(lausanne);
        indexing.add(berne);

        assertThat(indexing.indexed()).isZero();
    }

    /** A clock that tests move forward. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-11-19T12:00:00Z");

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
