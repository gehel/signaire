package fr.ledcom.signaire.model;

import java.util.Comparator;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * The invented signs of every test, as a bundle has them: the fake SignSuisse serves them, the demonstration shows
 * them, the tests of the search index them. Nothing comes from SignSuisse. Between them, they have everything a page of
 * SignSuisse can have; their media have addresses shaped as SignSuisse's, on {@code example.org}.
 */
public final class TestSigns {

    private static final String MEDIA = "https://example.org/signsuisse/fileadmin/signsuisse_ressources/";

    // Ville
    public static final Sign LAUSANNE = Sign.builder("1", Language.FRENCH, "LAUSANNE")
            .periphrase("Ville au bord du lac")
            .definition("Capitale du canton de Vaud")
            .categories(List.of(Category.CITY))
            .videos(List.of(new Media(MEDIA + "videos/lausanne.mp4", "media/lausanne.mp4")))
            .examples(List.of(new Example("Je travaille à Lausanne.", null)))
            .build();
    public static final Sign GENEVE = Sign.builder("2", Language.FRENCH, "GENÈVE")
            .periphrase("Ville du jet d'eau")
            .categories(List.of(Category.CITY))
            .build();
    public static final Sign GARE = Sign.builder("6", Language.FRENCH, "GARE")
            .definition("Lieu où les trains s'arrêtent pour prendre et laisser des voyageurs.")
            .status("Reconnu")
            .categories(List.of(Category.CITY))
            .videos(List.of(video("gare")))
            .examples(List.of(new Example("Le train part de la gare à midi.", video("gare-exemple"))))
            .images(List.of(photo("gare")))
            .build();
    public static final Sign ZUERICH = Sign.builder("7", Language.GERMAN, "ZÜRICH")
            .periphrase("Stadt an der Limmat")
            .definition("Die grösste Stadt der Schweiz.")
            .categories(List.of(Category.CITY))
            .dialects(List.of("BE", "ZH"))
            .videos(List.of(video("zuerich")))
            .dialectVariants(List.of(new DialectVariant(List.of("BS"), video("zuerich-bs"))))
            .build();
    public static final Sign BERN = Sign.builder("8", Language.GERMAN, "BERN")
            .definition("Die Bundesstadt der Schweiz.")
            .categories(List.of(Category.CITY))
            .videos(List.of(video("bern")))
            .images(List.of(illustration("bern")))
            .build();
    public static final Sign LUGANO = Sign.builder("9", Language.ITALIAN, "LUGANO")
            .definition("Città sul lago Ceresio.")
            .categories(List.of(Category.CITY))
            .videos(List.of(video("lugano")))
            .images(List.of(photo("lugano")))
            .build();
    public static final Sign BELLINZONA = Sign.builder("10", Language.ITALIAN, "BELLINZONA")
            .definition("Capitale del Canton Ticino, con tre castelli.")
            .categories(List.of(Category.CITY))
            .videos(List.of(video("bellinzona")))
            .build();

    // Animaux
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
    public static final Sign CHIEN = Sign.builder("11", Language.FRENCH, "CHIEN")
            .definition("Animal domestique qui aboie et garde la maison.")
            .categories(List.of(Category.ANIMALS))
            .videos(List.of(video("chien")))
            .images(List.of(photo("chien")))
            .build();
    public static final Sign OISEAU = Sign.builder("12", Language.FRENCH, "OISEAU")
            .definition("Animal couvert de plumes, qui vole.")
            .categories(List.of(Category.ANIMALS))
            .videos(List.of(video("oiseau")))
            .images(List.of(illustration("oiseau")))
            .build();
    public static final Sign HUND = Sign.builder("13", Language.GERMAN, "HUND")
            .definition("Ein Haustier, das bellt.")
            .categories(List.of(Category.ANIMALS))
            .dialects(List.of("ZH"))
            .videos(List.of(video("hund")))
            .build();
    public static final Sign CANE = Sign.builder("14", Language.ITALIAN, "CANE")
            .definition("Animale domestico che abbaia.")
            .categories(List.of(Category.ANIMALS))
            .videos(List.of(video("cane")))
            .build();

    // Nature
    public static final Sign ARBRE = Sign.builder("15", Language.FRENCH, "ARBRE")
            .definition("Grande plante avec un tronc de bois.")
            .categories(List.of(Category.NATURE))
            .videos(List.of(video("arbre")))
            .examples(List.of(new Example("L'arbre perd ses feuilles en automne.", null)))
            .images(List.of(photo("arbre")))
            .build();
    public static final Sign FORET = Sign.builder("16", Language.FRENCH, "FORÊT")
            .definition("Grand espace couvert d'arbres.")
            .categories(List.of(Category.NATURE))
            .videos(List.of(video("foret")))
            .build();
    public static final Sign MONTAGNE = Sign.builder("17", Language.FRENCH, "MONTAGNE")
            .definition("Grande élévation du terrain.")
            .categories(List.of(Category.NATURE))
            .videos(List.of(video("montagne")))
            .images(List.of(photo("montagne")))
            .build();
    public static final Sign BAUM = Sign.builder("18", Language.GERMAN, "BAUM")
            .definition("Eine grosse Pflanze mit einem Stamm aus Holz.")
            .categories(List.of(Category.NATURE))
            .videos(List.of(video("baum")))
            .build();
    public static final Sign WALD = Sign.builder("19", Language.GERMAN, "WALD")
            .definition("Eine grosse Fläche voller Bäume.")
            .categories(List.of(Category.NATURE))
            .videos(List.of(video("wald")))
            .build();
    public static final Sign ALBERO = Sign.builder("20", Language.ITALIAN, "ALBERO")
            .definition("Grande pianta con un tronco di legno.")
            .categories(List.of(Category.NATURE))
            .videos(List.of(video("albero")))
            .build();
    public static final Sign BOSCO = Sign.builder("21", Language.ITALIAN, "BOSCO")
            .definition("Terreno coperto di alberi.")
            .categories(List.of(Category.NATURE))
            .videos(List.of(video("bosco")))
            .build();

    /** Every sign: what the fake SignSuisse serves, and the demonstration shows. */
    public static final List<Sign> LEXICON = List.of(LAUSANNE, GENEVE, GARE, ZUERICH, BERN, LUGANO, BELLINZONA,
            CHAT, KATZE, GATTO, CHIEN, OISEAU, HUND, CANE,
            ARBRE, FORET, MONTAGNE, BAUM, WALD, ALBERO, BOSCO);

    /** The five signs the tests of the search are written on: two French cities, and a cat in each language. */
    public static final List<Sign> FIVE = List.of(LAUSANNE, GENEVE, CHAT, KATZE, GATTO);

    private TestSigns() {
    }

    /** The signs of a category in a sign language, as SignSuisse lists them: by name. */
    public static List<Sign> of(Category category, Language language) {
        return LEXICON.stream()
                .filter(sign -> sign.language() == language && sign.categories().contains(category))
                .sorted(Comparator.comparing(Sign::name))
                .toList();
    }

    /** A sign as SignSuisse has it: its media have their address, but no file. */
    public static Sign atSignSuisse(Sign sign) {
        return sign.toBuilder()
                .videos(sign.videos().stream().map(TestSigns::online).toList())
                .images(sign.images().stream().map(TestSigns::online).toList())
                .examples(sign.examples().stream()
                        .map(example -> new Example(example.text(), onlineIfAny(example.video())))
                        .toList())
                .dialectVariants(sign.dialectVariants().stream()
                        .map(variant -> new DialectVariant(variant.dialects(), online(variant.video())))
                        .toList())
                .build();
    }

    private static Media video(String name) {
        return new Media(MEDIA + "videos/" + name + ".mp4", null);
    }

    private static Media photo(String name) {
        return new Media(MEDIA + "images/" + name + ".jpg", null);
    }

    private static Media illustration(String name) {
        return new Media(MEDIA + "illustrations/" + name + ".png", null);
    }

    private static Media online(Media media) {
        return new Media(media.sourceUrl(), null);
    }

    private static @Nullable Media onlineIfAny(@Nullable Media media) {
        return media == null ? null : online(media);
    }
}
