package fr.ledcom.signaire.export;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.imageio.ImageIO;

import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Example;
import fr.ledcom.signaire.model.Language;
import fr.ledcom.signaire.model.Media;
import fr.ledcom.signaire.model.Sign;

/** Signs and media for the export tests: invented content, with the structure of SignSuisse data. */
final class ExportTestData {

    static final byte[] IMAGE = png();
    static final byte[] VIDEO = "not really a video".getBytes(StandardCharsets.UTF_8);

    static final Sign LAUSANNE = Sign.builder("1", Language.FRENCH, "LAUSANNE")
            .sourceUrl("https://example.org/lexikon/1/lausanne")
            .periphrase("Ville au bord du lac")
            .definition("Capitale du canton de Vaud, au bord du Léman.")
            .categories(List.of(Category.CITY))
            .videos(List.of(new Media("https://example.org/videos/lausanne.mp4", "media/lausanne.mp4")))
            .images(List.of(new Media("https://example.org/images/lausanne.png", "media/lausanne.png")))
            .examples(List.of(new Example("Je travaille à Lausanne.", null)))
            .build();
    /** Media not downloaded. */
    static final Sign KATZE = Sign.builder("4", Language.GERMAN, "KATZE")
            .definition("Ein kleines Haustier, das Mäuse jagt.")
            .categories(List.of(Category.ANIMALS))
            .dialects(List.of("BE", "ZH"))
            .videos(List.of(new Media("https://example.org/videos/katze.mp4", null)))
            .build();
    /** No media at all. */
    static final Sign GATTO = Sign.builder("5", Language.ITALIAN, "GATTO")
            .definition("Piccolo felino domestico.")
            .categories(List.of(Category.ANIMALS))
            .build();

    static final MediaSource MEDIA = media -> switch (String.valueOf(media.file())) {
        case "media/lausanne.png" -> Optional.of(IMAGE);
        case "media/lausanne.mp4" -> Optional.of(VIDEO);
        default -> Optional.empty();
    };

    private ExportTestData() {
    }

    /** Some signs: the three above, then copies of LAUSANNE with other names. */
    static List<Sign> signs(int count) {
        List<Sign> signs = new ArrayList<>(List.of(LAUSANNE, KATZE, GATTO));
        for (int i = signs.size(); i < count; i++) {
            Sign l = LAUSANNE;
            signs.add(new Sign(String.valueOf(100 + i), l.language(), "SIGNE " + i, l.sourceUrl(), l.periphrase(),
                    l.definition(), l.status(), l.categories(), l.dialects(), l.videos(), l.examples(), l.images(),
                    l.dialectVariants()));
        }
        return List.copyOf(signs.subList(0, count));
    }

    private static byte[] png() {
        BufferedImage image = new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.ORANGE);
        graphics.fillRect(0, 0, 40, 30);
        graphics.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }
}
