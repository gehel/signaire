package fr.ledcom.signaire.export;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** A plain Java library: used by the command line and the web application, which bring Spring. */
@AnalyzeClasses(packages = "fr.ledcom.signaire.export", importOptions = ImportOption.DoNotIncludeTests.class)
final class ArchitectureTest {

    private ArchitectureTest() {
        // rules only
    }

    @ArchTest
    static final ArchRule NO_SPRING = noClasses().should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework..");

    @ArchTest
    static final ArchRule NO_APPLICATION_MODULE = noClasses().should().dependOnClassesThat()
            .resideInAnyPackage("fr.ledcom.signaire.core..", "fr.ledcom.signaire.accounts..",
                    "fr.ledcom.signaire.cli..", "fr.ledcom.signaire.web..");
}
