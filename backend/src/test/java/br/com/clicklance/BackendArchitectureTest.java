package br.com.clicklance;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

@AnalyzeClasses(packages = "br.com.clicklance", importOptions = ImportOption.DoNotIncludeTests.class)
class BackendArchitectureTest {
    @ArchTest
    static final ArchRule persistenceOnlyInRepositories = noClasses().that().areNotAnnotatedWith(Repository.class)
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework.jdbc..", "java.sql..", "javax.sql..");

    @ArchTest
    static final ArchRule controllersDoNotAccessRepositories = noClasses().that().areAnnotatedWith(RestController.class)
            .should().dependOnClassesThat().areAnnotatedWith(Repository.class);

    @ArchTest
    static final ArchRule servicesDoNotDependOnHttp = noClasses().that().areAnnotatedWith(Service.class)
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework.web..", "org.springframework.http..", "jakarta.servlet..");

    @ArchTest
    static final ArchRule repositoriesDoNotDependOnServices = noClasses().that().areAnnotatedWith(Repository.class)
            .should().dependOnClassesThat().areAnnotatedWith(Service.class);

    @ArchTest
    static final ArchRule repositoriesDoNotDependOnWeb = noClasses().that().areAnnotatedWith(Repository.class)
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework.web..", "org.springframework.http..", "jakarta.servlet..",
                    "br.com.clicklance.controller..", "br.com.clicklance.exception..", "br.com.clicklance.validation..");

    @ArchTest
    static final ArchRule servicesFollowNaming = classes().that().areAnnotatedWith(Service.class)
            .should().haveSimpleNameEndingWith("Service");

    @ArchTest
    static final ArchRule repositoriesFollowNaming = classes().that().areAnnotatedWith(Repository.class)
            .should().haveSimpleNameEndingWith("Repository");

    @ArchTest
    static final ArchRule controllersBelongToControllerPackage = classes().that().areAnnotatedWith(RestController.class)
            .should().resideInAPackage("br.com.clicklance.controller");

    @ArchTest
    static final ArchRule servicesBelongToServicePackage = classes().that().areAnnotatedWith(Service.class)
            .should().resideInAPackage("br.com.clicklance.service");

    @ArchTest
    static final ArchRule repositoriesBelongToRepositoryPackage = classes().that().areAnnotatedWith(Repository.class)
            .should().resideInAPackage("br.com.clicklance.repository");

    @ArchTest
    static final ArchRule modelsDoNotDependOnApplicationLayers = noClasses().that().resideInAPackage("br.com.clicklance.model")
            .should().dependOnClassesThat().resideInAnyPackage("br.com.clicklance.controller..", "br.com.clicklance.service..",
                    "br.com.clicklance.repository..", "br.com.clicklance.config..", "br.com.clicklance.security..",
                    "br.com.clicklance.cli..", "org.springframework..", "jakarta.servlet..", "java.sql..", "javax.sql..");
}
