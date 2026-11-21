// 요청 없이 도는 진입점. 작업 큐를 받는 워커와 정해진 시각에 도는 스케줄러를 둔다.
// 실행 모듈이 아니다. api 가 실행할 때 포함해 한 프로세스로 돈다.
dependencies {
    implementation(project(":core"))

    implementation("org.springframework:spring-context")
    implementation("org.slf4j:slf4j-api")

    // core 의 가짜 저장소·큐로 서비스를 조립해 테스트한다.
    testImplementation(testFixtures(project(":core")))
}
