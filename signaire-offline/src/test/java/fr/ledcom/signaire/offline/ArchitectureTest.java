package fr.ledcom.signaire.offline;

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

/** The Android app is built on this module: plain Java, what Android has. */
@AnalyzeClasses(packages = "fr.ledcom.signaire.offline", importOptions = ImportOption.DoNotIncludeTests.class)
final class ArchitectureTest {

    private ArchitectureTest() {
        // rules only
    }

    @ArchTest
    static final ArchRule ANDROID_COMPATIBLE = noClasses().should().dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "jakarta..", "java.net.http..", "java.awt..", "javax.swing..",
                    "java.sql..", "java.lang.management..", "javax.management..");

    @ArchTest
    static final ArchRule NO_APPLICATION_MODULE = noClasses().should().dependOnClassesThat()
            .resideInAnyPackage("fr.ledcom.signaire.core..", "fr.ledcom.signaire.accounts..",
                    "fr.ledcom.signaire.export..", "fr.ledcom.signaire.cli..", "fr.ledcom.signaire.web..");

    /** Nothing is null unless written {@code @Nullable}, which NullAway checks (docs/design/nullness.md). */
    @ArchTest
    static final ArchRule NULL_MARKED = classes().should(have(describe("a @NullMarked package",
            (JavaClass type) -> type.getPackage().isAnnotatedWith(NullMarked.class))));
}
