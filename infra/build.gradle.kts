import org.springframework.boot.gradle.plugin.SpringBootPlugin

// core 의 경계 인터페이스를 구현한다. DB(storage)와 외부 연동(external: LLM, 푸시, 파일 저장소, 링크 수집)을 패키지로 나눈다.
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

    // 링크 수집기의 웹 페이지 HTML 파싱
    implementation(libs.jsoup)
    // 링크 수집기의 웹 페이지 요청. 연결할 IP 를 정하는 DNS 해석기를 바꿔 끼울 수 있다.
    implementation("org.apache.httpcomponents.client5:httpclient5")

    // 액세스 토큰(JWT) 서명·검증. api 의 Resource Server 가 여기서 만든 JwtDecoder 빈으로 검증한다.
    implementation("org.springframework.security:spring-security-oauth2-jose")

    // 외부 연동 클라이언트 테스트용 가짜 HTTP 서버
    testImplementation(libs.mockwebserver)

    // 작업 큐(SQS). 버전은 AWS SDK BOM 이 정한다.
    implementation(platform(libs.aws.sdk.bom))
    implementation("software.amazon.awssdk:sqs")
    implementation("software.amazon.awssdk:apache5-client")

    testFixturesImplementation(platform(SpringBootPlugin.BOM_COORDINATES))
    testFixturesImplementation("org.springframework.boot:spring-boot-test")
    testFixturesImplementation("org.springframework:spring-jdbc")
    testFixturesApi("org.springframework.boot:spring-boot-testcontainers")
    testFixturesApi("org.testcontainers:testcontainers-postgresql")
}
