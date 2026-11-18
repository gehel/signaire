package fr.ledcom.signaire.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import fr.ledcom.signaire.accounts.AccountService;

/** With an OpenID Connect provider configured; the provider's endpoints are given, to avoid discovery. */
@WebMvcTest(value = AccountController.class, properties = {
    "spring.security.oauth2.client.registration.idp.client-id=signaire",
    "spring.security.oauth2.client.registration.idp.client-secret=secret",
    "spring.security.oauth2.client.registration.idp.client-name=Example IdP",
    "spring.security.oauth2.client.registration.idp.scope=openid,profile,email",
    "spring.security.oauth2.client.registration.idp.authorization-grant-type=authorization_code",
    "spring.security.oauth2.client.registration.idp.redirect-uri={baseUrl}/login/oauth2/code/{registrationId}",
    "spring.security.oauth2.client.provider.idp.authorization-uri=https://idp.example.org/authorize",
    "spring.security.oauth2.client.provider.idp.token-uri=https://idp.example.org/token",
    "spring.security.oauth2.client.provider.idp.jwk-set-uri=https://idp.example.org/jwks",
    "spring.security.oauth2.client.provider.idp.user-info-uri=https://idp.example.org/userinfo",
    "spring.security.oauth2.client.provider.idp.user-name-attribute=sub",
})
@Import({SecurityConfiguration.class, AdminUsers.class})
@ImportAutoConfiguration(OAuth2ClientAutoConfiguration.class)
class OidcLoginTest {

    @MockitoBean
    private AccountService accounts;

    @Autowired
    private MockMvcTester mvc;

    @Test
    void loginPageOffersTheConfiguredProviders() {
        MvcTestResult result = mvc.get().uri("/login").exchange();

        assertThat(result).bodyText().contains("href=\"/oauth2/authorization/idp\"", "Se connecter avec Example IdP");
    }

    @Test
    void redirectsToTheProvider() {
        MvcTestResult result = mvc.get().uri("/oauth2/authorization/idp").exchange();

        assertThat(result).hasStatus(HttpStatus.FOUND);
        assertThat(result.getResponse().getRedirectedUrl()).startsWith("https://idp.example.org/authorize?");
    }
}
