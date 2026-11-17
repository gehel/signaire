package fr.ledcom.signaire.core;

import java.util.List;

import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Example;
import fr.ledcom.signaire.model.Language;
import fr.ledcom.signaire.model.Media;
import fr.ledcom.signaire.model.Sign;

/** Signs used by the tests. */
public final class TestSigns {

    public static final Sign LAUSANNE = Sign.builder("1", Language.FRENCH, "LAUSANNE")
            .periphrase("Ville au bord du lac")
            .definition("Capitale du canton de Vaud")
            .categories(List.of(Category.CITY))
            .videos(List.of(new Media("https://example.org/lausanne.mp4", "media/lausanne.mp4")))
            .examples(List.of(new Example("Je travaille à Lausanne.", null)))
            .build();
    public static final Sign GENEVE = Sign.builder("2", Language.FRENCH, "GENÈVE")
            .periphrase("Ville du jet d'eau")
            .categories(List.of(Category.CITY))
            .build();
    public static final Sign CHAT = Sign.builder("3", Language.FRENCH, "CHAT")
            .definition("Petit félin domestique qui chasse les souris")
            .categories(List.of(Category.ANIMALS))
            .build();
    public static final Sign KATZE = Sign.builder("4", Language.GERMAN, "KATZE")
            .definition("Ein kleines Haustier, das Mäuse jagt")
            .categories(List.of(Category.ANIMALS))
            .dialects(List.of("BE", "ZH"))
            .build();
    public static final Sign GATTO = Sign.builder("5", Language.ITALIAN, "GATTO")
            .definition("Piccolo felino domestico")
            .categories(List.of(Category.ANIMALS))
            .build();

    public static final List<Sign> ALL = List.of(LAUSANNE, GENEVE, CHAT, KATZE, GATTO);

    private TestSigns() {
    }
}
