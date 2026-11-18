package fr.ledcom.signaire.web;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** The application goes through the services of core and accounts, never to the stores directly. */
@AnalyzeClasses(packages = "fr.ledcom.signaire.web", importOptions = ImportOption.DoNotIncludeTests.class)
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
}
