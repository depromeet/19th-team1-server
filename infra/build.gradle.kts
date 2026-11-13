import org.springframework.boot.gradle.plugin.SpringBootPlugin

// core 의 경계 인터페이스를 구현한다. DB(storage)와 외부 연동(external: LLM, 푸시, 파일 저장소)을 패키지로 나눈다.
plugins {
    // src/testFixtures 의 테스트 도구를 다른 모듈의 테스트에서 쓸 수 있게 한다.
    `java-test-fixtures`
}

dependencies {
    implementation(project(":core"))

    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-mysql")
    runtimeOnly("com.mysql:mysql-connector-j")

    testFixturesImplementation(platform(SpringBootPlugin.BOM_COORDINATES))
    testFixturesImplementation("org.springframework.boot:spring-boot-test")
    testFixturesApi("org.springframework.boot:spring-boot-testcontainers")
    testFixturesApi("org.testcontainers:testcontainers-mysql")
}
