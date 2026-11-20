# 서버 이미지. api 와 worker 가 한 프로세스로 돈다. 역할은 환경변수(WORKER_ENABLED, SCHEDULER_ENABLED)로 나눈다.
# CI 가 ./gradlew :api:bootJar 로 만든 실행 jar 를 담는다. 빌드는 이미지 안에서 하지 않는다.
# CI 는 실행 jar 와 이 파일의 sha256 이 이전과 같으면 이미지를 새로 만들지 않는다. 이 파일만 바꿔도 새 이미지가 만들어진다.

# 베이스 이미지는 Java 25 JRE 태그를 쓴다. 새 이미지를 빌드할 때마다 그때의 최신 패치(보안 업데이트 포함)가 들어간다.
ARG JRE_IMAGE=eclipse-temurin:25-jre

FROM ${JRE_IMAGE} AS extract
WORKDIR /build
COPY api/build/libs/*.jar app.jar
# 의존 라이브러리와 앱 코드를 계층으로 나눈다. 코드만 바뀌면 작은 앱 계층만 새로 올라간다.
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

FROM ${JRE_IMAGE}
RUN groupadd --system app && useradd --system --gid app --no-create-home app
WORKDIR /app
COPY --from=extract /build/extracted/dependencies/ ./
COPY --from=extract /build/extracted/spring-boot-loader/ ./
COPY --from=extract /build/extracted/snapshot-dependencies/ ./
COPY --from=extract /build/extracted/application/ ./
USER app
EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
