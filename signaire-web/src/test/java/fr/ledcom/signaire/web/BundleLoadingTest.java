package fr.ledcom.signaire.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;

import fr.ledcom.signaire.core.bundles.BundleLoader;
import fr.ledcom.signaire.core.bundles.LoadReport;
import fr.ledcom.signaire.core.media.MediaLibrary;
import fr.ledcom.signaire.core.search.SearchException;
import fr.ledcom.signaire.core.search.SignIndex;
import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Example;
import fr.ledcom.signaire.model.Language;
import fr.ledcom.signaire.model.Media;
import fr.ledcom.signaire.model.Sign;

class BundleLoadingTest {

    @TempDir
    Path bundles;

    private final BundleLoader loader = mock(BundleLoader.class);
    private final MediaLibrary media = mock(MediaLibrary.class);
    private final SignEditor editor = mock(SignEditor.class);
    private final SignIndex index = mock(SignIndex.class);
    private BundleLoading loading;

    @BeforeEach
    void createLoading() {
        loading = new BundleLoading(loader, index, media, editor, bundles, true);
    }

    @Test
    void loadsTheBundlesThenTheChangesOfAdministrators() {
        LoadReport loaded = new LoadReport(bundles.resolve("city.zip"), Category.CITY, 2, LoadReport.Status.LOADED,
                null);
        when(loader.loadDirectory(bundles)).thenReturn(List.of(loaded));

        List<LoadReport> reports = loading.load();

        assertThat(reports).containsExactly(loaded);
        verify(media).refresh();
        verify(editor).reapply();
    }

    @Test
    void errorsOfTheIndexAreLogged() {
        when(loader.loadDirectory(bundles)).thenThrow(new SearchException("Cannot index signs"));

        List<LoadReport> reports = loading.load();

        assertThat(reports).isEmpty();
        verify(editor, never()).reapply();
    }

    @Test
    void appliesTheChangesOfAdministratorsWithoutBundles() {
        Path missing = bundles.resolve("missing");

        List<LoadReport> reports = new BundleLoading(loader, index, media, editor, missing, true).load();

        assertThat(reports).isEmpty();
        verify(loader, never()).loadDirectory(missing);
        verify(editor).reapply();
    }

    @Test
    void loadsOneBundleThenTheChangesOfAdministrators() {
        Path bundle = bundles.resolve("city.zip");
        LoadReport loaded = new LoadReport(bundle, Category.CITY, 2, LoadReport.Status.LOADED, null);
        when(loader.load(bundle)).thenReturn(loaded);

        assertThat(loading.load(bundle)).isEqualTo(loaded);

        InOrder order = inOrder(loader, media, editor);
        order.verify(loader).load(bundle);
        order.verify(media).refresh();
        order.verify(editor).reapply();
    }

    @Test
    void reindexingEmptiesTheIndexThenLoadsEverything() {
        when(loader.loadDirectory(bundles)).thenReturn(List.of());

        loading.reindex();

        InOrder order = inOrder(index, loader, editor);
        order.verify(index).recreate();
        order.verify(loader).loadDirectory(bundles);
        order.verify(editor).reapply();
    }

    @Test
    void bundlesLoadedElsewhereGetTheChangesOfAdministrators() {
        loading.loaded();

        verify(media).refresh();
        verify(editor).reapply();
        verify(loader, never()).loadDirectory(bundles);
    }

    @Test
    void indexesSignsBeingExtractedWithTheirMediaOnSignSuisse() {
        Sign downloaded = Sign.builder("1", Language.FRENCH, "LAUSANNE")
                .videos(List.of(new Media("https://example.org/lausanne.mp4", "media/lausanne.mp4")))
                .examples(List.of(new Example("Exemple", new Media("https://example.org/ex.mp4", "media/ex.mp4"))))
                .images(List.of(new Media("https://example.org/lausanne.jpg", "media/lausanne.jpg")))
                .build();

        loading.indexExtracted(List.of(downloaded));

        Sign linked = downloaded.toBuilder()
                .videos(List.of(new Media("https://example.org/lausanne.mp4", null)))
                .examples(List.of(new Example("Exemple", new Media("https://example.org/ex.mp4", null))))
                .images(List.of(new Media("https://example.org/lausanne.jpg", null)))
                .build();
        verify(editor).indexWithChanges(List.of(linked));
        verify(media, never()).refresh();
    }
}
