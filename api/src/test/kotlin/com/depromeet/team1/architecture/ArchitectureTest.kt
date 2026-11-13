package com.depromeet.team1.architecture

import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.Test
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.RestController

/**
 * 모듈 안의 구조 규칙을 검사한다. 모듈 사이의 의존 방향은 Gradle 설정이 컴파일 단계에서 막으므로 여기서 다루지 않는다.
 * 규칙 설명은 docs/architecture.md 6장을 따른다.
 */
class ArchitectureTest {
    @Test
    fun `core 의 서비스가 아닌 클래스는 Spring 에 의존하지 않는다`() {
        noClasses().that().resideInAPackage(CORE)
            .and().haveSimpleNameNotEndingWith("Service")
            .should().dependOnClassesThat().resideInAPackage("org.springframework..")
            .because("도메인 모델과 경계 인터페이스는 프레임워크 없이 만들고 테스트할 수 있어야 한다")
            .check(productionClasses)
    }

    @Test
    fun `core 의 서비스는 @Service 로 등록한다`() {
        classes().that().resideInAPackage(CORE)
            .and().haveSimpleNameEndingWith("Service")
            .and().areNotInterfaces()
            .should().beAnnotatedWith(Service::class.java)
            .check(productionClasses)
    }

    @Test
    fun `JPA 엔티티는 infra 의 storage 패키지에 두고 이름을 JpaEntity 로 끝낸다`() {
        classes().that().areAnnotatedWith("jakarta.persistence.Entity")
            .should().resideInAPackage(STORAGE)
            .andShould().haveSimpleNameEndingWith("JpaEntity")
            .because("도메인 모델(Item)과 JPA 엔티티(ItemJpaEntity)를 이름으로 구분한다")
            .check(productionClasses)
    }

    @Test
    fun `컨트롤러는 api 의 web 패키지에 두고 이름을 Controller 로 끝낸다`() {
        classes().that().areAnnotatedWith(RestController::class.java)
            .should().resideInAPackage(API_WEB)
            .andShould().haveSimpleNameEndingWith("Controller")
            .check(productionClasses)
    }

    @Test
    fun `예약 작업은 worker 에 둔다`() {
        methods().that().areAnnotatedWith(Scheduled::class.java)
            .should().beDeclaredInClassesThat().resideInAPackage(WORKER)
            .because("요청 없이 도는 진입점을 한 모듈에 모아, 나중에 따로 배포할 수 있게 한다")
            .check(productionClasses)
    }

    companion object {
        private const val CORE = "com.depromeet.team1.core.."
        private const val STORAGE = "com.depromeet.team1.infra.storage.."
        private const val API_WEB = "com.depromeet.team1.api.web.."
        private const val WORKER = "com.depromeet.team1.worker.."

        private val productionClasses: JavaClasses = ClassFileImporter()
            .withImportOption(ImportOption.DoNotIncludeTests())
            .importPackages("com.depromeet.team1")
    }
}
