package com.civsetu;

import static org.assertj.core.api.Assertions.assertThat;
import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import jakarta.persistence.Entity;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

/**
 * Keeps the package structure described in backend/README.md true as the code grows.
 *
 * <pre>
 * com.civsetu.&lt;feature&gt;.controller | dto | service | repository | entity | config | security
 * com.civsetu.common, com.civsetu.config  (shared, feature-independent)
 * </pre>
 */
class ArchitectureTest {

    private static final String ROOT = "com.civsetu";
    private static final Set<String> SHARED_PACKAGES = Set.of("common", "config");

    private static JavaClasses classes;
    private static Set<String> features;

    @BeforeAll
    static void importProductionClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);
        features = new TreeSet<>();
        for (JavaClass javaClass : classes) {
            String pkg = javaClass.getPackageName();
            if (pkg.startsWith(ROOT + ".")) {
                String top = pkg.substring(ROOT.length() + 1).split("\\.")[0];
                if (!SHARED_PACKAGES.contains(top)) {
                    features.add(top);
                }
            }
        }
    }

    @Test
    void classesLiveInTheFolderForTheirRole() {
        classes().that().areAnnotatedWith(RestController.class)
                .should().resideInAPackage("..controller..").check(classes);
        classes().that().areAnnotatedWith(Entity.class)
                .should().resideInAPackage("..entity..").check(classes);
        classes().that().areInterfaces().and().areAssignableTo(Repository.class)
                .should().resideInAPackage("..repository..").check(classes);
        classes().that().areAnnotatedWith(Service.class)
                .should().resideInAPackage("..service..").check(classes);
    }

    @Test
    void controllersGoThroughServicesNotRepositories() {
        noClasses().that().resideInAPackage("..controller..")
                .should().dependOnClassesThat().resideInAPackage("..repository..").check(classes);
    }

    @Test
    void repositoriesAreOnlyUsedByServices() {
        classes().that().resideInAPackage("..repository..")
                .should().onlyHaveDependentClassesThat().resideInAnyPackage("..service..", "..repository..")
                .check(classes);
    }

    @Test
    void controllersNeverReturnEntities() {
        methods().that().areDeclaredInClassesThat().resideInAPackage("..controller..")
                .should().notHaveRawReturnType(resideInAPackage("..entity..")).check(classes);
    }

    @Test
    void sharedCodeDoesNotDependOnFeatures() {
        String[] featurePackages = features.stream().map(f -> ROOT + "." + f + "..").toArray(String[]::new);
        noClasses().that().resideInAPackage(ROOT + ".common..")
                .should().dependOnClassesThat().resideInAnyPackage(featurePackages).check(classes);
    }

    /**
     * A feature may use another feature's service and dto packages (and its enums), but nothing
     * else: not its controllers, repositories, entities, storage or configuration.
     */
    @Test
    void featuresOnlyUseEachOthersServicesAndDtos() {
        for (String feature : features) {
            String base = ROOT + "." + feature;
            DescribedPredicate<JavaClass> internals = resideInAPackage(base + "..")
                    .and(not(resideInAnyPackage(base + ".service..", base + ".dto..")))
                    .and(not(describe("enums", JavaClass::isEnum)));
            for (String other : features) {
                if (!other.equals(feature)) {
                    noClasses().that().resideInAPackage(ROOT + "." + other + "..")
                            .should().dependOnClassesThat(internals)
                            .because(other + " must use " + feature + " only through its service and dto packages")
                            .check(classes);
                }
            }
        }
    }

    /**
     * The OpenAPI document (and the frontend types generated from it) names schemas by simple class
     * name, so two DTOs called e.g. "Question" would silently merge into one wrong type.
     */
    @Test
    void dtoNamesAreUniqueBecauseTheyBecomeApiSchemaNames() {
        Map<String, List<String>> byName = new TreeMap<>();
        for (JavaClass javaClass : classes) {
            if (javaClass.getPackageName().contains(".dto") && !javaClass.isAnonymousClass()) {
                byName.computeIfAbsent(javaClass.getSimpleName(), name -> new ArrayList<>())
                        .add(javaClass.getName());
            }
        }
        Map<String, List<String>> duplicates = new TreeMap<>();
        byName.forEach((name, owners) -> {
            if (owners.size() > 1) {
                duplicates.put(name, owners);
            }
        });
        assertThat(duplicates).as("DTO classes sharing a simple name").isEmpty();
    }

    @Test
    void noCyclesBetweenFeatures() {
        slices().matching(ROOT + ".(*)..").should().beFreeOfCycles().check(classes);
    }
}
