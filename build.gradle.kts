import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    // 루트에 check·build 태스크를 만든다. build-logic 의 테스트를 여기에 건다.
    base
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.spring) apply false
    alias(libs.plugins.spring.boot) apply false
    // 루트에도 적용한다. git 커밋 훅 설치 태스크가 루트 프로젝트에만 생긴다.
    alias(libs.plugins.ktlint)
    // CI 의 테스트 선택 (build-logic/)
    id("team1.ci")
}

// git 훅을 로컬에서 처음 빌드할 때 설치한다. CI 와 .git 이 없는 곳(Docker 빌드 등)에서는 설치하지 않는다.
//   - pre-commit: 스테이지된 Kotlin 파일을 자동으로 포맷한다 (ktlint 플러그인 제공)
//   - commit-msg: 커밋 메시지 규칙을 검사하고 브랜치의 이슈 번호를 붙인다 (scripts/git-hooks/commit-msg)
val installGitHook = rootProject.file(".git").isDirectory && System.getenv("CI") == null

val installCommitMsgHook by tasks.registering(Copy::class) {
    from("scripts/git-hooks/commit-msg")
    into(".git/hooks")
    filePermissions { unix("rwxr-xr-x") }
}

// build-logic(CI 플러그인)의 테스트와 포맷 검사를 ./gradlew check·build 에 포함한다.
tasks.named("check") {
    dependsOn(gradle.includedBuild("build-logic").task(":check"))
}

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
            dependsOn(installCommitMsgHook)
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    // jar 에 파일 수정 시각을 넣지 않고 항목 순서를 고정한다. 그래야 같은 코드로 다시 빌드한 jar 가 바이트 단위로 같다.
    // CI 는 실행 jar 의 sha256 이 이전과 같으면 서버 이미지를 새로 만들지 않는다(.github/workflows/ci.yml).
    // 이 성질이 깨지면 코드가 그대로여도 빌드할 때마다 새 이미지가 생긴다. Gradle 9 의 기본값과 같지만 여기에 기대는 곳이 있어 명시한다.
    tasks.withType<AbstractArchiveTask>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }
}
