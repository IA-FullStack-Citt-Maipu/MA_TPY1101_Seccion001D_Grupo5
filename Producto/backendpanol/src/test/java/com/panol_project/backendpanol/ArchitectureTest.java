package com.panol_project.backendpanol;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ArchitectureTest {

    private static final String MODULES_PREFIX = "com.panol_project.backendpanol.modules.";
    private static final String AUTH_AUDIT_LOG_PORT = "com.panol_project.backendpanol.modules.auth.domain.AuditLogPort";

    private final JavaClasses importedClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.panol_project.backendpanol");

    @Test
    void modulesMustNotDependOnBootstrapLayer() {
        noClasses()
                .that().resideInAPackage("..modules..")
                .should().dependOnClassesThat().resideInAPackage("..bootstrap..")
                .check(importedClasses);
    }

    @Test
    void sharedMustNotDependOnModules() {
        noClasses()
                .that().resideInAPackage("..shared..")
                .should().dependOnClassesThat().resideInAPackage("..modules..")
                .check(importedClasses);
    }

    @Test
    void apiMustNotDependOnJooq() {
        noClasses()
                .that().resideInAPackage("..modules..api..")
                .should().dependOnClassesThat().resideInAPackage("org.jooq..")
                .check(importedClasses);
    }

    @Test
    void applicationMustNotDependOnJooq() {
        noClasses()
                .that().resideInAPackage("..modules..application..")
                .should().dependOnClassesThat().resideInAPackage("org.jooq..")
                .check(importedClasses);
    }

    @Test
    void applicationMustNotDependOnInfrastructure() {
        noClasses()
                .that().resideInAPackage("..modules..application..")
                .should().dependOnClassesThat().resideInAPackage("..modules..infrastructure..")
                .check(importedClasses);
    }

    @Test
    void domainMustNotDependOnFrameworks() {
        noClasses()
                .that().resideInAPackage("..modules..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..",
                        "org.jooq..",
                        "jakarta.."
                )
                .check(importedClasses);
    }

    @Test
    void domainMustNotDependOnApiApplicationInfrastructurePackages() {
        for (JavaClass source : importedClasses) {
            String sourcePackage = source.getPackageName();
            if (!isDomainPackage(sourcePackage)) {
                continue;
            }

            for (Dependency dependency : source.getDirectDependenciesFromSelf()) {
                String targetPackage = dependency.getTargetClass().getPackageName();
                assertTrue(
                        !isApiPackage(targetPackage)
                                && !isApplicationPackage(targetPackage)
                                && !isInfrastructurePackage(targetPackage),
                        () -> "Domain class " + source.getFullName()
                                + " must not depend on api/application/infrastructure package " + targetPackage
                );
            }
        }
    }

    @Test
    void domainMustNotDependOnForeignModuleDomain() {
        for (JavaClass source : importedClasses) {
            String sourcePackage = source.getPackageName();
            if (!isDomainPackage(sourcePackage)) {
                continue;
            }
            String sourceModule = moduleId(sourcePackage);

            for (Dependency dependency : source.getDirectDependenciesFromSelf()) {
                String targetPackage = dependency.getTargetClass().getPackageName();
                if (!isDomainPackage(targetPackage)) {
                    continue;
                }
                String targetModule = moduleId(targetPackage);
                assertTrue(
                        sourceModule.equals(targetModule),
                        () -> "Domain class " + source.getFullName()
                                + " depends on foreign domain package " + targetPackage
                );
            }
        }
    }

    @Test
    void apiMustNotDependOnForeignModuleApplicationExceptContracts() {
        for (JavaClass source : importedClasses) {
            String sourcePackage = source.getPackageName();
            if (!isApiPackage(sourcePackage)) {
                continue;
            }
            String sourceModule = moduleId(sourcePackage);

            for (Dependency dependency : source.getDirectDependenciesFromSelf()) {
                String targetPackage = dependency.getTargetClass().getPackageName();
                if (!isApplicationPackage(targetPackage) || isApplicationContractPackage(targetPackage)) {
                    continue;
                }
                String targetModule = moduleId(targetPackage);
                assertTrue(
                        sourceModule.equals(targetModule),
                        () -> "API class " + source.getFullName()
                                + " depends on foreign application package " + targetPackage
                );
            }
        }
    }

    @Test
    void apiMustNotDependOnForeignModuleApi() {
        for (JavaClass source : importedClasses) {
            String sourcePackage = source.getPackageName();
            if (!isApiPackage(sourcePackage)) {
                continue;
            }
            String sourceModule = moduleId(sourcePackage);

            for (Dependency dependency : source.getDirectDependenciesFromSelf()) {
                String targetPackage = dependency.getTargetClass().getPackageName();
                if (!isApiPackage(targetPackage)) {
                    continue;
                }
                String targetModule = moduleId(targetPackage);
                assertTrue(
                        sourceModule.equals(targetModule),
                        () -> "API class " + source.getFullName()
                                + " depends on foreign API package " + targetPackage
                );
            }
        }
    }

    @Test
    void applicationMustNotDependOnApiPackages() {
        for (JavaClass source : importedClasses) {
            String sourcePackage = source.getPackageName();
            if (!isApplicationPackage(sourcePackage)) {
                continue;
            }

            for (Dependency dependency : source.getDirectDependenciesFromSelf()) {
                String targetPackage = dependency.getTargetClass().getPackageName();
                assertTrue(
                        !isApiPackage(targetPackage),
                        () -> "Application class " + source.getFullName()
                                + " must not depend on API package " + targetPackage
                );
            }
        }
    }

    @Test
    void applicationMustNotDependOnForeignModuleApplicationExceptContracts() {
        for (JavaClass source : importedClasses) {
            String sourcePackage = source.getPackageName();
            if (!isApplicationPackage(sourcePackage) || isApplicationContractPackage(sourcePackage)) {
                continue;
            }
            String sourceModule = moduleId(sourcePackage);

            for (Dependency dependency : source.getDirectDependenciesFromSelf()) {
                String targetPackage = dependency.getTargetClass().getPackageName();
                if (!isApplicationPackage(targetPackage) || isApplicationContractPackage(targetPackage)) {
                    continue;
                }
                String targetModule = moduleId(targetPackage);
                assertTrue(
                        sourceModule.equals(targetModule),
                        () -> "Application class " + source.getFullName()
                                + " depends on foreign application package " + targetPackage
                );
            }
        }
    }

    @Test
    void applicationMustNotDependOnForeignModuleDomainExceptAllowedPorts() {
        for (JavaClass source : importedClasses) {
            String sourcePackage = source.getPackageName();
            if (!isApplicationPackage(sourcePackage)) {
                continue;
            }
            String sourceModule = moduleId(sourcePackage);

            for (Dependency dependency : source.getDirectDependenciesFromSelf()) {
                JavaClass target = dependency.getTargetClass();
                String targetPackage = target.getPackageName();
                if (!isDomainPackage(targetPackage)) {
                    continue;
                }
                String targetModule = moduleId(targetPackage);
                if (sourceModule.equals(targetModule)) {
                    continue;
                }
                assertTrue(
                        isAllowedCrossModuleDomainDependency(sourcePackage, target.getFullName()),
                        () -> "Application class " + source.getFullName()
                                + " depends on foreign domain class " + target.getFullName()
                );
            }
        }
    }

    @Test
    void authApplicationMustNotDependOnAuditLogService() {
        noClasses()
                .that().resideInAPackage("..modules.auth.application..")
                .should().dependOnClassesThat().haveFullyQualifiedName(
                        "com.panol_project.backendpanol.modules.auth.application.AuditLogService"
                )
                .check(importedClasses);
    }

    @Test
    void catalogClassesMustBelongToKnownSubmodules() {
        Set<String> allowedModules = Set.of(
                "catalog.implement",
                "catalog.stock",
                "catalog.category",
                "catalog.location",
                "catalog.room",
                "catalog.subject"
        );

        for (JavaClass source : importedClasses) {
            String sourcePackage = source.getPackageName();
            if (!sourcePackage.contains(".modules.catalog.")) {
                continue;
            }
            String module = moduleId(sourcePackage);
            assertTrue(
                    allowedModules.contains(module),
                    () -> "Catalog class " + source.getFullName() + " is outside allowed submodules"
            );
        }
    }

    private boolean isAllowedCrossModuleDomainDependency(String sourcePackage, String targetClassName) {
        return sourcePackage.contains(".modules.users.application.")
                && AUTH_AUDIT_LOG_PORT.equals(targetClassName);
    }

    private boolean isApiPackage(String packageName) {
        return packageName.contains(".modules.") && packageName.contains(".api.");
    }

    private boolean isApplicationPackage(String packageName) {
        return packageName.contains(".modules.") && packageName.contains(".application.");
    }

    private boolean isApplicationContractPackage(String packageName) {
        return packageName.contains(".application.contract.");
    }

    private boolean isDomainPackage(String packageName) {
        return packageName.contains(".modules.") && packageName.contains(".domain.");
    }

    private boolean isInfrastructurePackage(String packageName) {
        return packageName.contains(".modules.") && packageName.contains(".infrastructure.");
    }

    private String moduleId(String packageName) {
        if (!packageName.contains(MODULES_PREFIX)) {
            return "";
        }
        String rest = packageName.substring(packageName.indexOf(MODULES_PREFIX) + MODULES_PREFIX.length());
        String[] parts = rest.split("\\.");
        if (parts.length == 0) {
            return "";
        }
        if ("catalog".equals(parts[0]) && parts.length > 1) {
            return parts[0] + "." + parts[1];
        }
        return parts[0];
    }
}
