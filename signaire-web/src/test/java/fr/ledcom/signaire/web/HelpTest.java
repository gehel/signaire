package fr.ledcom.signaire.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import fr.ledcom.signaire.accounts.AccountService;

@WebMvcTest(HelpController.class)
@Import({SecurityConfiguration.class, AdminUsers.class, HelpPages.class})
class HelpTest {

    @MockitoBean
    private AccountService accounts;

    @Autowired
    private MockMvcTester mvc;

    static String[][] topicsAndLanguages() {
        return HelpPages.TOPICS.stream()
                .flatMap(topic -> Stream.of("fr", "de", "it", "en")
                        .map(language -> new String[] {topic, language}))
                .toArray(String[][]::new);
    }

    @ParameterizedTest
    @MethodSource("topicsAndLanguages")
    void everyTopicIsWrittenInEveryLanguage(String topic, String language) throws IOException {
        try (InputStream page = HelpTest.class.getResourceAsStream("/help/" + language + "/" + topic + ".md")) {
            assertThat(page).as(topic + " in " + language).isNotNull();
            assertThat(new String(page.readAllBytes(), StandardCharsets.UTF_8)).startsWith("# ");
        }
    }

    @Test
    void theManualListsAllTheTopics() {
        MvcTestResult result = mvc.get().uri("/help").exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyText()
                .contains("<h1>Aide</h1>")
                .contains("href=\"/help/search\"", "href=\"/help/exports\"", "href=\"/help/admin\"",
                        "href=\"/help/cli\"");
    }

    @Test
    void topicsAreRenderedInTheUserLanguage() {
        MvcTestResult french = mvc.get().uri("/help/exports").exchange();
        MvcTestResult german = mvc.get().uri("/help/exports").header(HttpHeaders.ACCEPT_LANGUAGE, "de").exchange();

        assertThat(french).bodyText().contains("<h1>Exporter", "<html lang=\"fr\">");
        assertThat(german).bodyText().contains("<h1>Export", "<html lang=\"de\">");
    }

    @Test
    void unknownTopicsAreNotFound() {
        assertThat(mvc.get().uri("/help/nothing").exchange()).hasStatus(HttpStatus.NOT_FOUND);
    }
}
