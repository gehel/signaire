package fr.ledcom.signaire.bundle;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Bundles are read by the Android app too: plain Java, and only the model of Signaire. */
@AnalyzeClasses(packages = "fr.ledcom.signaire.bundle", importOptions = ImportOption.DoNotIncludeTests.class)
final class ArchitectureTest {

    private ArchitectureTest() {
        // rules only
    }

    @ArchTest
    static final ArchRule ANDROID_COMPATIBLE = noClasses().should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "jakarta..", "java.awt..", "javax.swing..", "java.sql..");

    @ArchTest
    static final ArchRule ONLY_THE_MODEL = classes().should().onlyDependOnClassesThat()
            .resideOutsideOfPackage("fr.ledcom.signaire..")
            .orShould().dependOnClassesThat().resideInAnyPackage("fr.ledcom.signaire.bundle..",
                    "fr.ledcom.signaire.model..");
}
