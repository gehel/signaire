package fr.ledcom.signaire.core;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * The packages of core: {@code search} (the index) knows nothing of the others; {@code bundles} loads bundles
 * into the index; {@code media} reads bundles by itself.
 */
@AnalyzeClasses(packages = "fr.ledcom.signaire.core", importOptions = ImportOption.DoNotIncludeTests.class)
final class ArchitectureTest {

    private ArchitectureTest() {
        // rules only
    }

    @ArchTest
    static final ArchRule SEARCH_STANDS_ALONE = noClasses().that().resideInAPackage("..core.search..")
            .should().dependOnClassesThat().resideInAnyPackage("..core.bundles..", "..core.media..");

    @ArchTest
    static final ArchRule MEDIA_STANDS_ALONE = noClasses().that().resideInAPackage("..core.media..")
            .should().dependOnClassesThat().resideInAnyPackage("..core.search..", "..core.bundles..");

    @ArchTest
    static final ArchRule NO_USER_DATA = noClasses().should().dependOnClassesThat()
            .resideInAnyPackage("fr.ledcom.signaire.accounts..", "jakarta.persistence..");
}
