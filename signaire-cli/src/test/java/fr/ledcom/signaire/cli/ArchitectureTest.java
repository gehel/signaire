package fr.ledcom.signaire.cli;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.lang.conditions.ArchConditions.have;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.jspecify.annotations.NullMarked;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** The application goes through the services of core and accounts, never to the stores directly. */
@AnalyzeClasses(packages = "fr.ledcom.signaire.cli", importOptions = ImportOption.DoNotIncludeTests.class)
final class ArchitectureTest {

    private ArchitectureTest() {
        // rules only
    }

    @ArchTest
    static final ArchRule NO_STORE = noClasses().should().dependOnClassesThat()
            .resideInAnyPackage("org.apache.lucene..", "jakarta.persistence..", "org.springframework.data..",
                    "org.springframework.jdbc..");

    @ArchTest
    static final ArchRule SYSTEM_LOGGER = noClasses().should().dependOnClassesThat()
            .resideInAnyPackage("java.util.logging..", "org.slf4j..", "org.apache.logging..");

    /** Nothing is null unless written {@code @Nullable}, which NullAway checks (docs/design/nullness.md). */
    @ArchTest
    static final ArchRule NULL_MARKED = classes().should(have(describe("a @NullMarked package",
            (JavaClass type) -> type.getPackage().isAnnotatedWith(NullMarked.class))));
}
