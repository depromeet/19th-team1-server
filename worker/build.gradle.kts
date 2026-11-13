// 요청 없이 도는 진입점. 작업 테이블을 폴링하는 워커와 정해진 시각에 도는 스케줄러를 둔다.
// 실행 모듈이 아니다. api 가 실행할 때 포함해 한 프로세스로 돈다.
dependencies {
    implementation(project(":core"))

    implementation("org.springframework:spring-context")
}
