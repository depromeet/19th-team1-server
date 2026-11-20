// CI 의 테스트 선택 Gradle 플러그인을 담는 포함 빌드. 루트 settings.gradle.kts 가 포함한다.
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
