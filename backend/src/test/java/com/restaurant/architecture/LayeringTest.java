package com.restaurant.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * NFR-05 as executable rules rather than a claim (M8 D4).
 *
 * <p>"The system shall follow a layered architecture (presentation, service, repository) with
 * clearly separated concerns" is the kind of requirement that is true on the day it is written and
 * quietly false a few commits later. These tests make the layering a property of the build: the
 * moment somebody injects a repository into a controller to save a hop, this goes red.
 *
 * <p>Rules are here because a violation would be a genuine defect, not to enumerate every
 * convention. An over-strict rule that people learn to suppress is worse than no rule at all.
 */
class LayeringTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.restaurant");
    }

    // ---- The layering NFR-05 names -----------------------------------------

    @Test
    @DisplayName("controllers never reach past the service layer into repositories")
    void controllersDoNotUseRepositories() {
        noClasses().that().resideInAPackage("..controller..")
                .should().dependOnClassesThat().resideInAPackage("..repository..")
                .because("controllers handle HTTP and DTO mapping only; business logic and "
                        + "persistence belong to the service and repository layers (NFR-05). "
                        + "Reaching straight for a repository is how the layering starts to rot.")
                .check(classes);
    }

    @Test
    @DisplayName("the service layer never calls back up into controllers")
    void servicesDoNotUseControllers() {
        noClasses().that().resideInAPackage("..service..")
                .should().dependOnClassesThat().resideInAPackage("com.restaurant.controller")
                .because("dependencies point inward. A service that knows about a controller is a "
                        + "cycle waiting to happen (NFR-05).")
                .check(classes);
    }

    @Test
    @DisplayName("the domain depends on neither controllers nor services")
    void domainIsIndependent() {
        noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("com.restaurant.controller", "..service..", "..repository..")
                .because("the domain is the innermost layer: entities and value objects own the "
                        + "rules, and nothing about how they are stored or exposed (NFR-05).")
                .check(classes);
    }

    @Test
    @DisplayName("the domain stays free of Spring")
    void domainDoesNotDependOnSpring() {
        noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAPackage("org.springframework..")
                .because("domain rules must be unit-testable with a constructor and no context — "
                        + "which is why OrderTest, DateRangeTest and BillCalculatorTest run in "
                        + "milliseconds. JPA annotations are allowed; Spring is not.")
                .check(classes);
    }

    // ---- The boundary ------------------------------------------------------

    @Test
    @DisplayName("controllers never return domain entities — DTOs at the boundary")
    void controllersReturnDtosNotEntities() {
        methods().that().areDeclaredInClassesThat().resideInAPackage("..controller..")
                .and().arePublic()
                .should().notHaveRawReturnType(
                        com.tngtech.archunit.base.DescribedPredicate.describe(
                                "a domain entity",
                                javaClass -> javaClass.getPackageName()
                                        .equals("com.restaurant.domain")
                                        && javaClass.isAnnotatedWith(jakarta.persistence.Entity.class)))
                .because("entities must not cross the REST boundary (NFR-05): persistence "
                        + "structure and wire format have to evolve separately, and a lazy "
                        + "association serialized outside its transaction is a "
                        + "LazyInitializationException in production.")
                .check(classes);
    }

    // ---- Conventions that carry weight -------------------------------------

    @Test
    @DisplayName("no field injection anywhere — constructors only")
    void noFieldInjection() {
        noClasses().should().beAnnotatedWith("org.springframework.beans.factory.annotation.Autowired")
                .because("constructor injection everywhere: it makes dependencies explicit, keeps "
                        + "them final, and lets every collaborator be passed in by a plain unit "
                        + "test.")
                .check(classes);

        fields().should().notBeAnnotatedWith(
                        "org.springframework.beans.factory.annotation.Autowired")
                .because("field injection hides dependencies and cannot be set by a unit test.")
                .check(classes);
    }

    @Test
    @DisplayName("repository interfaces live in the repository package and are named for it")
    void repositoriesAreNamedConsistently() {
        com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes()
                .that().areAssignableTo(org.springframework.data.repository.Repository.class)
                .and().areInterfaces()
                .should().resideInAPackage("..repository..")
                .andShould().haveSimpleNameEndingWith("Repository")
                .because("persistence access is found in one place, under one name (NFR-05).")
                .check(classes);
    }
}
