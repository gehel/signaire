package fr.ledcom.signaire.model;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** The model is shared with the Android app: plain Java, no framework. */
@AnalyzeClasses(packages = "fr.ledcom.signaire.model", importOptions = ImportOption.DoNotIncludeTests.class)
final class ArchitectureTest {

    private ArchitectureTest() {
        // rules only
    }

    @ArchTest
    static final ArchRule ANDROID_COMPATIBLE = noClasses().should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "jakarta..", "java.awt..", "javax.swing..", "java.sql..",
                    "tools.jackson..");

    @ArchTest
    static final ArchRule NO_OTHER_MODULE = noClasses().should().dependOnClassesThat()
            .resideInAnyPackage("fr.ledcom.signaire.bundle..", "fr.ledcom.signaire.core..",
                    "fr.ledcom.signaire.extractor..", "fr.ledcom.signaire.export..", "fr.ledcom.signaire.accounts..",
                    "fr.ledcom.signaire.cli..", "fr.ledcom.signaire.web..");
}
