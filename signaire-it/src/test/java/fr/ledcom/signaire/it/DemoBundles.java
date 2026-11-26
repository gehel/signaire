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
import fr.ledcom.signaire.model.Media;
import fr.ledcom.signaire.model.Sign;
import fr.ledcom.signaire.model.TestSigns;

/**
 * Bundles of the test signs ({@link TestSigns}), for the measures of the web application in CI (Lighthouse,
 * sitespeed.io) and its pages in a browser ({@link BrowserIT}) or on a phone ({@link AndroidIT}): never SignSuisse
 * data. Each sign has an image of its own; no video, which would be read from {@code example.org}. Run with
 * {@code ./mvnw -pl signaire-it exec:java -Dexec.classpathScope=test
 * -Dexec.mainClass=fr.ledcom.signaire.it.DemoBundles -Dexec.args=<directory>}.
 */
public final class DemoBundles {

    private static final List<Category> CATEGORIES = List.of(Category.CITY, Category.ANIMALS, Category.NATURE);

    private DemoBundles() {
    }

    public static void main(String... args) throws IOException {
        write(Path.of(args.length > 0 ? args[0] : "target/demo-bundles"));
    }

    /** Writes the bundles of the demonstration (city, animals and nature) into a directory. */
    static void write(Path directory) throws IOException {
        Path media = Files.createDirectories(directory.resolve(".media/media"));
        BundleWriter writer = new BundleWriter(Clock.systemUTC());
        for (Category category : CATEGORIES) {
            List<Sign> signs = new ArrayList<>();
            for (Sign sign : TestSigns.LEXICON) {
                if (!sign.categories().contains(category)) {
                    continue;
                }
                String image = "media/demo-" + sign.uid() + ".png";
                ImageIO.write(image(TestSigns.LEXICON.indexOf(sign)), "png", directory.resolve(".media")
                        .resolve(image).toFile());
                signs.add(shown(sign, image));
            }
            String name = category.name().toLowerCase(Locale.ROOT).replace('_', '-') + ".zip";
            writer.write(directory.resolve(name), category, signs, media.getParent());
        }
    }

    /** A sign as the demonstration shows it: its texts, its image, and no video. */
    private static Sign shown(Sign sign, String image) {
        String address = sign.images().isEmpty() ? "https://example.org/demo/" + sign.uid() + ".png"
                : sign.images().getFirst().sourceUrl();
        return sign.toBuilder()
                .videos(List.of())
                .examples(sign.examples().stream().map(example -> new Example(example.text(), null)).toList())
                .dialectVariants(List.of())
                .images(List.of(new Media(address, image)))
                .build();
    }

    private static BufferedImage image(int index) {
        BufferedImage image = new BufferedImage(320, 240, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.getHSBColor(index / (float) TestSigns.LEXICON.size(), 0.5f, 0.9f));
        graphics.fillRect(0, 0, 320, 240);
        graphics.dispose();
        return image;
    }
}
