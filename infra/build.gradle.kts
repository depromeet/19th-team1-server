import org.springframework.boot.gradle.plugin.SpringBootPlugin

// core 의 경계 인터페이스를 구현한다. DB(storage)와 외부 연동(external: LLM, 푸시, 파일 저장소)을 패키지로 나눈다.
plugins {
    // src/testFixtures 의 테스트 도구를 다른 모듈의 테스트에서 쓸 수 있게 한다.
    `java-test-fixtures`
    // JPA 엔티티에 Hibernate 가 쓰는 인자 없는 생성자를 만든다.
    alias(libs.plugins.kotlin.jpa)
}

dependencies {
    implementation(project(":core"))

    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // 외부 연동(카카오 등)의 HTTP 호출
    implementation("org.springframework:spring-web")
    implementation("tools.jackson.module:jackson-module-kotlin")

    // 액세스 토큰(JWT) 서명·검증. api 의 Resource Server 가 여기서 만든 JwtDecoder 빈으로 검증한다.
    implementation("org.springframework.security:spring-security-oauth2-jose")

    // 외부 연동 클라이언트 테스트용 가짜 HTTP 서버
    testImplementation(libs.mockwebserver)

    testFixturesImplementation(platform(SpringBootPlugin.BOM_COORDINATES))
    testFixturesImplementation("org.springframework.boot:spring-boot-test")
    testFixturesImplementation("org.springframework:spring-jdbc")
    testFixturesApi("org.springframework.boot:spring-boot-testcontainers")
    testFixturesApi("org.testcontainers:testcontainers-postgresql")
}
