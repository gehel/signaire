package fr.ledcom.signaire.accounts;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.springframework.data.repository.Repository;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import jakarta.persistence.Entity;

/** Entities and repositories are internal: the services expose immutable records. */
@AnalyzeClasses(packages = "fr.ledcom.signaire.accounts", importOptions = ImportOption.DoNotIncludeTests.class)
final class ArchitectureTest {

    private ArchitectureTest() {
        // rules only
    }

    @ArchTest
    static final ArchRule ENTITIES_ARE_INTERNAL = classes().that().areAnnotatedWith(Entity.class)
            .should().notBePublic();

    @ArchTest
    static final ArchRule REPOSITORIES_ARE_INTERNAL = classes().that().areAssignableTo(Repository.class)
            .should().notBePublic();

    @ArchTest
    static final ArchRule SERVICES_RETURN_NO_ENTITY = methods().that().arePublic()
            .and().areDeclaredInClassesThat().arePublic()
            .should().notHaveRawReturnType(DescribedPredicate.describe("an entity",
                    (JavaClass type) -> type.isAnnotatedWith(Entity.class)));

    @ArchTest
    static final ArchRule NO_SEARCH_INDEX = noClasses().should().dependOnClassesThat()
            .resideInAnyPackage("fr.ledcom.signaire.core..", "co.elastic..");
}
