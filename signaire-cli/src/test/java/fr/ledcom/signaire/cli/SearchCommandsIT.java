package fr.ledcom.signaire.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import fr.ledcom.signaire.bundle.BundleWriter;
import fr.ledcom.signaire.core.ElasticsearchTestConfiguration;
import fr.ledcom.signaire.core.TestSigns;
import fr.ledcom.signaire.core.search.SignIndex;
import fr.ledcom.signaire.model.Category;
import picocli.CommandLine;
import picocli.CommandLine.IFactory;

@SpringBootTest
@Import(ElasticsearchTestConfiguration.class)
class SearchCommandsIT {

    @TempDir
    Path bundles;

    @Autowired
    private IFactory factory;

    @Autowired
    private SignaireCommand command;

    @Autowired
    private SignIndex index;

    private StringWriter out;
    private StringWriter err;

    @BeforeEach
    void writeBundles() throws IOException {
        index.recreate();
        Path media = Files.createDirectories(bundles.resolve(".media/media"));
        Files.writeString(media.resolve("lausanne.mp4"), "video");
        BundleWriter writer = new BundleWriter(Clock.systemUTC());
        writer.write(bundles.resolve("city.zip"), Category.CITY, List.of(TestSigns.LAUSANNE, TestSigns.GENEVE),
                bundles.resolve(".media"));
        writer.write(bundles.resolve("animals.zip"), Category.ANIMALS,
                List.of(TestSigns.CHAT, TestSigns.KATZE, TestSigns.GATTO), bundles.resolve(".media"));
    }

    @Test
    void loadsBundles() {
        int exitCode = execute("charger", "--bundles", bundles.toString());

        assertThat(exitCode).isZero();
        assertThat(out.toString())
                .contains("city.zip : 2 signes chargés (ville)")
                .contains("animals.zip : 3 signes chargés (animaux)");

        execute("charger", "--bundles", bundles.toString());

        assertThat(out.toString()).contains("city.zip : inchangé");
    }

    @Test
    void reportsBundlesThatCannotBeLoaded() throws IOException {
        Files.writeString(bundles.resolve("broken.zip"), "not a zip");

        int exitCode = execute("charger", "--bundles", bundles.toString());

        assertThat(exitCode).isEqualTo(1);
        assertThat(err.toString()).contains("broken.zip : échec");
    }

    @Test
    void searchesSigns() {
        execute("charger", "--bundles", bundles.toString());

        int exitCode = execute("chercher", "canton", "de", "vaud");

        assertThat(exitCode).isZero();
        assertThat(out.toString())
                .contains("1 signe trouvé")
                .contains("LAUSANNE (fr) — ville")
                .contains("Capitale du canton de Vaud");
    }

    @Test
    void filtersByLanguageAndCategory() {
        execute("charger", "--bundles", bundles.toString());

        int exitCode = execute("chercher", "--langue", "de,it", "--categorie", "animaux");

        assertThat(exitCode).isZero();
        assertThat(out.toString())
                .contains("2 signes trouvés")
                .contains("GATTO (it)")
                .contains("KATZE (de) — animaux")
                .doesNotContain("CHAT (fr)");
    }

    @Test
    void saysWhenNothingIsFound() {
        execute("charger", "--bundles", bundles.toString());

        int exitCode = execute("chercher", "xylophone");

        assertThat(exitCode).isZero();
        assertThat(out.toString()).contains("Aucun signe trouvé.");
    }

    private int execute(String... args) {
        out = new StringWriter();
        err = new StringWriter();
        CommandLine commandLine = SignaireCommand.commandLine(command, factory);
        commandLine.setOut(new PrintWriter(out, true));
        commandLine.setErr(new PrintWriter(err, true));
        return commandLine.execute(args);
    }
}
