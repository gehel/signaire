package fr.ledcom.signaire.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class EpubItemTest {

    @Test
    void itemsWithTheSameContentAreEqual() {
        EpubExporter.Item item = item("same".getBytes(StandardCharsets.UTF_8));
        EpubExporter.Item copy = item("same".getBytes(StandardCharsets.UTF_8));

        assertThat(item).isEqualTo(copy).hasSameHashCodeAs(copy);
    }

    @Test
    void itemsWithAnotherContentDiffer() {
        assertThat(item("one".getBytes(StandardCharsets.UTF_8)))
                .isNotEqualTo(item("two".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void theDescriptionGivesTheSizeOfTheContentRatherThanTheBytes() {
        assertThat(item(new byte[2048])).hasToString(
                "Item[id=sign-1, href=sign-1.xhtml, mediaType=application/xhtml+xml, content=2048 bytes, inSpine=true]");
    }

    private static EpubExporter.Item item(byte[] content) {
        return new EpubExporter.Item("sign-1", "sign-1.xhtml", "application/xhtml+xml", content, true);
    }
}
