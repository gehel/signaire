package fr.ledcom.signaire.offline;

import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** No package of the module depends on itself through others, test classes included. */
@AnalyzeClasses(packages = "fr.ledcom.signaire.offline")
final class PackageCyclesTest {

    @ArchTest
    static final ArchRule NO_CYCLES = slices().matching("fr.ledcom.signaire.(**)").should().beFreeOfCycles();

    private PackageCyclesTest() {
        // rules only
    }
}
