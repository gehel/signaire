package fr.ledcom.signaire.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
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
    private final SignEditor editor = mock(SignEditor.class);

    @Test
    void loadsTheBundlesThenTheChangesOfAdministrators() {
        LoadReport loaded = new LoadReport(bundles.resolve("city.zip"), Category.CITY, 2, LoadReport.Status.LOADED,
                null);
        when(loader.loadDirectory(bundles)).thenReturn(List.of(loaded));

        List<LoadReport> reports = new StartupLoading(loader, media, editor, bundles, true).load();

        assertThat(reports).containsExactly(loaded);
        verify(media).refresh();
        verify(editor).reapply();
    }

    @Test
    void errorsOfTheIndexAreLogged() {
        when(loader.loadDirectory(bundles)).thenThrow(new SearchException("Cannot index signs"));

        List<LoadReport> reports = new StartupLoading(loader, media, editor, bundles, true).load();

        assertThat(reports).isEmpty();
        verify(editor, never()).reapply();
    }

    @Test
    void appliesTheChangesOfAdministratorsWithoutBundles() {
        Path missing = bundles.resolve("missing");

        List<LoadReport> reports = new StartupLoading(loader, media, editor, missing, true).load();

        assertThat(reports).isEmpty();
        verify(loader, never()).loadDirectory(missing);
        verify(editor).reapply();
    }
}
