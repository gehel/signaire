package fr.ledcom.signaire.web;

import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import fr.ledcom.signaire.accounts.SignOverride;
import fr.ledcom.signaire.accounts.SignOverrideService;
import fr.ledcom.signaire.core.bundles.BundleLoader;
import fr.ledcom.signaire.core.search.SignIndex;
import fr.ledcom.signaire.model.Language;
import fr.ledcom.signaire.model.Sign;

class SignEditorTest {

    private final SignOverrideService overrides = mock(SignOverrideService.class);
    private final SignIndex index = mock(SignIndex.class);
    private final SignEditor editor = new SignEditor(overrides, index, mock(BundleLoader.class), Path.of("bundles"));

    private final Sign lausanne = Sign.builder("1", Language.FRENCH, "LAUSANNE").build();
    private final Sign berne = Sign.builder("2", Language.FRENCH, "BERNE").build();
    private final Sign sion = Sign.builder("3", Language.FRENCH, "SION").build();

    @Test
    void indexesNewSignsWithTheChangesOfAdministrators() {
        Sign edited = berne.toBuilder().definition("Capitale fédérale").build();
        when(overrides.all()).thenReturn(List.of(
                new SignOverride("2", edited, false, Instant.EPOCH, "admin"),
                new SignOverride("3", null, true, Instant.EPOCH, "admin")));

        editor.indexWithChanges(List.of(lausanne, berne, sion));

        verify(index).index(List.of(lausanne, edited));
        verify(index).refresh();
    }

    @Test
    void indexesNothingWhenAllTheSignsWereDeleted() {
        when(overrides.all()).thenReturn(List.of(new SignOverride("3", null, true, Instant.EPOCH, "admin")));

        editor.indexWithChanges(List.of(sion));

        verify(index, never()).index(anyCollection());
    }
}
