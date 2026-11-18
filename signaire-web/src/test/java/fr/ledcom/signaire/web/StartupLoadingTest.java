package fr.ledcom.signaire.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import fr.ledcom.signaire.core.bundles.BundleLoader;
import fr.ledcom.signaire.core.bundles.LoadReport;
import fr.ledcom.signaire.core.media.MediaLibrary;
import fr.ledcom.signaire.core.search.SearchException;
import fr.ledcom.signaire.model.Category;

class StartupLoadingTest {

    @TempDir
    Path bundles;

    private final BundleLoader loader = mock(BundleLoader.class);
    private final MediaLibrary media = mock(MediaLibrary.class);
    private final List<Duration> waits = new ArrayList<>();

    private StartupLoading startupLoading(int attempts) {
        return new StartupLoading(loader, media, bundles, true, attempts, Duration.ofSeconds(5), waits::add);
    }

    @Test
    void waitsForElasticsearchToStart() {
        LoadReport loaded = new LoadReport(bundles.resolve("city.zip"), Category.CITY, 2, LoadReport.Status.LOADED,
                null);
        when(loader.loadDirectory(bundles))
                .thenThrow(new SearchException("Cannot check index"))
                .thenThrow(new SearchException("Cannot check index"))
                .thenReturn(List.of(loaded));

        List<LoadReport> reports = startupLoading(5).loadWhenReady();

        assertThat(reports).containsExactly(loaded);
        assertThat(waits).containsExactly(Duration.ofSeconds(5), Duration.ofSeconds(5));
        verify(media).refresh();
    }

    @Test
    void givesUpAfterSomeAttempts() {
        when(loader.loadDirectory(bundles)).thenThrow(new SearchException("Cannot check index"));

        List<LoadReport> reports = startupLoading(3).loadWhenReady();

        assertThat(reports).isEmpty();
        verify(loader, times(3)).loadDirectory(bundles);
        assertThat(waits).hasSize(2);
    }
}
