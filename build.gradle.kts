import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.spring) apply false
    alias(libs.plugins.spring.boot) apply false
    // 루트에도 적용한다. git 커밋 훅 설치 태스크가 루트 프로젝트에만 생긴다.
    alias(libs.plugins.ktlint)
}

// 커밋할 때 스테이지된 Kotlin 파일을 자동으로 포맷하는 git pre-commit 훅을, 로컬에서 처음 빌드할 때 설치한다.
// CI 와 .git 이 없는 곳(Docker 빌드 등)에서는 설치하지 않는다.
val installGitHook = rootProject.file(".git").isDirectory && System.getenv("CI") == null

// 모든 모듈에 공통으로 적용하는 설정. 모듈별 의존성은 각 모듈의 build.gradle.kts 에 둔다.
subprojects {
    group = "com.depromeet.team1"
    version = "0.0.1-SNAPSHOT"

    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.plugin.spring")
    // 포맷 검사. ./gradlew check(build) 에 포함되고, ./gradlew ktlintFormat 으로 자동 수정한다.
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    extensions.configure<KotlinJvmProjectExtension> {
        jvmToolchain(25)
        compilerOptions {
            freeCompilerArgs.addAll(
                "-Xjsr305=strict",
                "-Xannotation-default-target=param-property",
            )
        }
    }

    dependencies {
        // 라이브러리 버전은 Spring Boot BOM 이 정한다. 모듈에서는 버전 없이 선언한다.
        "implementation"(platform(SpringBootPlugin.BOM_COORDINATES))
        "implementation"("org.jetbrains.kotlin:kotlin-reflect")

        "testImplementation"("org.springframework.boot:spring-boot-starter-test")
        "testImplementation"("org.jetbrains.kotlin:kotlin-test-junit5")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    if (installGitHook) {
        tasks.matching { it.name == "compileKotlin" }.configureEach {
            dependsOn(rootProject.tasks.named("addKtlintFormatGitPreCommitHook"))
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}
