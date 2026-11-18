package fr.ledcom.signaire.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import fr.ledcom.signaire.bundle.BundleWriter;
import fr.ledcom.signaire.core.TestSigns;
import fr.ledcom.signaire.core.bundles.LoadReport;
import fr.ledcom.signaire.core.search.SignIndex;
import fr.ledcom.signaire.core.search.SignSearchService;
import fr.ledcom.signaire.model.Category;

@WebIntegrationTest
class StartupLoadingIT {

    @Autowired
    private StartupLoading startupLoading;

    @Autowired
    private SignIndex index;

    @Autowired
    private SignSearchService search;

    @Value("${signaire.bundles.directory}")
    private Path bundles;

    @Test
    void loadsTheBundlesOfTheConfiguredDirectory() throws IOException {
        index.recreate();
        Files.createDirectories(bundles);
        new BundleWriter(Clock.systemUTC()).write(bundles.resolve("animals.zip"), Category.ANIMALS,
                List.of(TestSigns.CHAT, TestSigns.KATZE), bundles.resolve(".media"));

        List<LoadReport> reports = startupLoading.load();

        assertThat(reports).extracting(LoadReport::status).contains(LoadReport.Status.LOADED);
        assertThat(search.findByUid(TestSigns.KATZE.uid())).isPresent();
    }
}
