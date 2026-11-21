// 도메인 모델, 서비스, 경계 인터페이스. DB·웹·외부 연동 기술을 의존하지 않는다.
plugins {
    // src/testFixtures 의 가짜 구현을 다른 모듈의 테스트에서 쓸 수 있게 한다.
    `java-test-fixtures`
}

dependencies {
    implementation("org.springframework:spring-context")
    implementation("org.springframework:spring-tx")
    implementation("org.slf4j:slf4j-api")
}
