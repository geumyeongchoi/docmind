package dev.gychoi.docmind.arch

import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses

/** CLAUDE.md 구조 규칙을 코드로 강제한다 (설계 문서 §5). */
@AnalyzeClasses(packages = ["dev.gychoi.docmind"], importOptions = [ImportOption.DoNotIncludeTests::class])
class ArchitectureTest {
    @ArchTest
    val domainHasNoFrameworkDependency: ArchRule =
        noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "jakarta..", "com.fasterxml..")
            .because("domain은 프레임워크를 몰라야 모델·벡터DB 교체가 가능하다")

    @ArchTest
    val domainDoesNotDependOnOuterLayers: ArchRule =
        noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..api..", "..application..", "..infra..", "..mcp..")

    @ArchTest
    val applicationDoesNotDependOnInfraOrApi: ArchRule =
        noClasses()
            .that()
            .resideInAPackage("..application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..infra..", "..api..", "..mcp..")
            .because("유스케이스는 포트(domain)만 본다")

    @ArchTest
    val onlyInfraTouchesSpringAi: ArchRule =
        noClasses()
            .that()
            .resideOutsideOfPackages("..infra..", "..mcp..", "..api..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("org.springframework.ai..")
            .because("Spring AI 타입은 어댑터 계층에 격리한다")
}
