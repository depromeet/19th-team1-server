plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":core"))

    // 구현 모듈은 실행할 때만 담는다. api 코드에서 storage·external 의 클래스를 직접 쓰면 컴파일되지 않는다.
    runtimeOnly(project(":storage"))
    runtimeOnly(project(":external"))

    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("tools.jackson.module:jackson-module-kotlin")

    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-mysql")
    testImplementation(libs.archunit)
}

// 실행 가능한 jar 하나만 만든다.
tasks.named<Jar>("jar") {
    enabled = false
}
