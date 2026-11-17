package fr.ledcom.signaire.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@SpringBootTest
@AutoConfigureMockMvc
class HomeControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @Test
    void homePageIsInFrenchByDefault() {
        MvcTestResult result = mvc.get().uri("/").exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyText()
                .contains("<html lang=\"fr\">")
                .contains("<title>Signaire</title>")
                .contains("Lexique de la langue des signes suisse");
    }

    @Test
    void unsupportedLanguagesFallBackToFrench() {
        MvcTestResult result = mvc.get().uri("/").header(HttpHeaders.ACCEPT_LANGUAGE, "de").exchange();

        assertThat(result).hasStatus(HttpStatus.OK);
        assertThat(result).bodyText().contains("<html lang=\"fr\">");
    }
}
