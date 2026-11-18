package fr.ledcom.signaire.cli;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CommandArgumentsTest {

    @Test
    void springPropertiesAreLeftToSpring() {
        assertThat(SignaireCliApplication.commandArguments("--spring.elasticsearch.uris=http://es:9200",
                "chercher", "--langue", "fr", "--signaire.bundles.directory=/tmp/b", "--nombre=5", "lac"))
                .containsExactly("chercher", "--langue", "fr", "--nombre=5", "lac");
    }
}
