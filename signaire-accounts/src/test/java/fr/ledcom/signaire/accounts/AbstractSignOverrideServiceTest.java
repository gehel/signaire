package fr.ledcom.signaire.accounts;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import fr.ledcom.signaire.model.Category;
import fr.ledcom.signaire.model.Example;
import fr.ledcom.signaire.model.Language;
import fr.ledcom.signaire.model.Media;
import fr.ledcom.signaire.model.Sign;

// Hibernate checks that the entities match the schema made by Flyway
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
abstract class AbstractSignOverrideServiceTest {

    private static final Sign LAUSANNE = Sign.builder("119158", Language.FRENCH, "LAUSANNE")
            .definition("Capitale du canton de Vaud")
            .categories(List.of(Category.CITY))
            .videos(List.of(new Media("https://example.org/lausanne.mp4", "media/lausanne.mp4")))
            .examples(List.of(new Example("Je travaille à Lausanne.", null)))
            .build();

    @Autowired
    private AccountService accounts;

    @Autowired
    private SignOverrideService overrides;

    @Autowired
    private AccountsCleaner cleaner;

    private long admin;

    @BeforeEach
    void clean() {
        cleaner.deleteAll();
        admin = accounts.register("admin", "correct horse", "Admin").id();
    }

    @Test
    void savesEditedSigns() {
        overrides.save(LAUSANNE, admin);

        SignOverride saved = overrides.find("119158").orElseThrow();
        assertThat(saved.sign()).isEqualTo(LAUSANNE);
        assertThat(saved.deleted()).isFalse();
        assertThat(saved.updatedBy()).isEqualTo("admin");
        assertThat(saved.updatedAt()).isNotNull();
    }

    @Test
    void replacesThePreviousChange() {
        overrides.save(LAUSANNE, admin);
        Sign edited = LAUSANNE.toBuilder().definition("Ville olympique").build();

        overrides.save(edited, admin);

        assertThat(overrides.all()).extracting(SignOverride::sign).containsExactly(edited);
    }

    @Test
    void recordsDeletions() {
        overrides.save(LAUSANNE, admin);

        overrides.delete("119158", admin);

        SignOverride deleted = overrides.find("119158").orElseThrow();
        assertThat(deleted.deleted()).isTrue();
        assertThat(deleted.sign()).isNull();
    }

    @Test
    void revertsChanges() {
        overrides.delete("119158", admin);

        overrides.revert("119158");

        assertThat(overrides.find("119158")).isEmpty();
        assertThat(overrides.all()).isEmpty();
    }

    @Test
    void givesNewSignsTheirOwnUids() {
        String uid = overrides.newUid();

        assertThat(uid).startsWith(SignOverrideService.LOCAL_UID_PREFIX).isNotEqualTo(overrides.newUid());
    }

    @Test
    void listsTheMostRecentChangesFirst() {
        overrides.save(LAUSANNE, admin);
        overrides.delete("4", admin);

        assertThat(overrides.all()).extracting(SignOverride::uid).containsExactly("4", "119158");
    }
}
