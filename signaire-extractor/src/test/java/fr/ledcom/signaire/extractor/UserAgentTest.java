package fr.ledcom.signaire.extractor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UserAgentTest {

    @Test
    void namesTheProjectSoThatSignSuisseCanFindIt() {
        UserAgent agent = UserAgent.of(null, null);

        assertThat(agent.value()).isEqualTo("Signaire (+https://github.com/gehel/signaire)");
        assertThat(agent.withContact()).isFalse();
        assertThat(UserAgent.of("  ", "")).isEqualTo(agent);
    }

    @Test
    void addsTheContactOfTheUser() {
        UserAgent agent = UserAgent.of(" alice@example.org ", null);

        assertThat(agent.value()).isEqualTo("Signaire (+https://github.com/gehel/signaire; alice@example.org)");
        assertThat(agent.withContact()).isTrue();
    }

    @Test
    void canBeReplacedEntirely() {
        UserAgent agent = UserAgent.of("alice@example.org", "MyLexicon/2.0 (alice@example.org)");

        assertThat(agent.value()).isEqualTo("MyLexicon/2.0 (alice@example.org)");
        assertThat(agent.withContact()).isTrue();
    }

    @Test
    void keepsToASingleHeaderLine() {
        assertThat(UserAgent.of("alice@example.org\r\nX-Evil: 1", null).value())
                .isEqualTo("Signaire (+https://github.com/gehel/signaire; alice@example.orgX-Evil: 1)");
    }
}
