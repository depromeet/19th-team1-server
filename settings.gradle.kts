pluginManagement {
    // CI 테스트 선택 플러그인. build-logic/ 에 있다.
    includeBuild("build-logic")
}

plugins {
    // 로컬에 해당 JDK 가 없으면 Gradle 이 자동으로 내려받는다.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "team1-server"

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        mavenCentral()
    }
}

include("core", "infra", "api", "worker")
