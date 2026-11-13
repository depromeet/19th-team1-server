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

    // 테스트용 MySQL 컨테이너 설정을 가져온다.
    testImplementation(testFixtures(project(":infra")))
    testImplementation(libs.archunit)
}

// 실행 가능한 jar 하나만 만든다.
tasks.named<Jar>("jar") {
    enabled = false
}
