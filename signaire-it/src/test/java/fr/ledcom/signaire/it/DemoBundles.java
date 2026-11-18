package fr.ledcom.signaire.it;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.imageio.ImageIO;

import fr.ledcom.signaire.bundle.BundleWriter;
import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Example;
import fr.ledcom.signaire.model.Language;
import fr.ledcom.signaire.model.Media;
import fr.ledcom.signaire.model.Sign;

/**
 * Bundles of invented signs, with images, for the measures of the web application in CI (Lighthouse,
 * sitespeed.io): never SignSuisse data. Run with
 * {@code ./mvnw -pl signaire-it exec:java -Dexec.classpathScope=test
 * -Dexec.mainClass=fr.ledcom.signaire.it.DemoBundles -Dexec.args=<directory>}.
 */
public final class DemoBundles {

    private static final String[] WORDS = {"ARBRE", "BATEAU", "CHEMIN", "DOMAINE", "ÉTOILE", "FORÊT", "GARE",
        "HORLOGE", "ÎLE", "JARDIN", "LAC", "MONTAGNE", "NUAGE", "OISEAU", "PONT", "RIVIÈRE", "SOLEIL", "TOUR",
        "VALLÉE", "VILLAGE"};
    private static final Category[] CATEGORIES = {Category.NATURE, Category.CITY, Category.TRAFFIC};

    private DemoBundles() {
    }

    public static void main(String... args) throws IOException {
        Path directory = Path.of(args.length > 0 ? args[0] : "target/demo-bundles");
        Path media = Files.createDirectories(directory.resolve(".media/media"));
        BundleWriter writer = new BundleWriter(Clock.systemUTC());
        for (int position = 0; position < CATEGORIES.length; position++) {
            Category category = CATEGORIES[position];
            List<Sign> signs = new ArrayList<>();
            for (int i = position; i < WORDS.length; i += CATEGORIES.length) {
                String uid = String.valueOf(90_000 + i);
                String image = "media/demo-" + uid + ".png";
                ImageIO.write(image(i), "png", media.resolve("demo-" + uid + ".png").toFile());
                signs.add(Sign.builder(uid, Language.values()[i % 3], WORDS[i])
                        .sourceUrl("https://example.org/demo/" + uid)
                        .periphrase("Signe inventé pour la démonstration")
                        .definition("Un " + WORDS[i].toLowerCase(Locale.ROOT) + " de démonstration, qui n'existe pas "
                                + "dans SignSuisse.")
                        .categories(List.of(category))
                        .images(List.of(new Media("https://example.org/demo/" + uid + ".png", image)))
                        .examples(List.of(new Example("Une phrase d'exemple avec " + WORDS[i] + ".", null)))
                        .build());
            }
            String name = category.name().toLowerCase(Locale.ROOT).replace('_', '-') + ".zip";
            writer.write(directory.resolve(name), category, signs, directory.resolve(".media"));
        }
    }

    private static BufferedImage image(int index) {
        BufferedImage image = new BufferedImage(320, 240, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.getHSBColor(index / 20f, 0.5f, 0.9f));
        graphics.fillRect(0, 0, 320, 240);
        graphics.dispose();
        return image;
    }
}
