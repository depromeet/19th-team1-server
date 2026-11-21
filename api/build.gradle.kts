plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":core"))

    // 구현 모듈과 워커는 실행할 때만 담는다. api 코드에서 infra·worker 의 클래스를 직접 쓰면 컴파일되지 않는다.
    runtimeOnly(project(":infra"))
    runtimeOnly(project(":worker"))

    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation(libs.springdoc.webmvc.ui)
    // 요청의 액세스 토큰(JWT) 검증. 검증에 쓰는 JwtDecoder 빈은 infra(security/jwt)가 만든다.
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")

    // 테스트용 PostgreSQL 컨테이너 설정을 가져온다.
    testImplementation(testFixtures(project(":infra")))
    testImplementation(libs.archunit)
    // 컨트롤러·보안 설정만 띄우는 테스트(@WebMvcTest)
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server-test")
}

// 실행 가능한 jar 하나만 만든다.
tasks.named<Jar>("jar") {
    enabled = false
}
