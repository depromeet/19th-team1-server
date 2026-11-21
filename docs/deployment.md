# 빌드·실행·배포 전제

CI/CD와 서버 구성이 참고하는 애플리케이션 쪽 전제를 정리한다. 클라우드와 배포 절차는 정해지면 이 문서에 추가한다.

## 1. 요구 사항

| 항목 | 버전 | 비고 |
| --- | --- | --- |
| JDK | 25 | 로컬에 없으면 Gradle이 자동으로 내려받는다 |
| Docker | Compose v2 | 로컬 PostgreSQL·ElasticMQ와 테스트(Testcontainers)에 필요하다 |

## 2. 빌드

```bash
./gradlew build
```

- 테스트가 PostgreSQL·ElasticMQ 컨테이너를 띄우므로 빌드하는 곳에 Docker가 있어야 한다.
- 산출물은 `api/build/libs/` 아래 실행 jar 하나다.
- 실행: `java -jar api/build/libs/api-0.0.1-SNAPSHOT.jar`
- 상태 확인: `GET /actuator/health`. 정상이면 200과 `{"status":"UP"}`을 돌려준다. 배포 후 확인과 외부 상태 점검에 쓴다.

## 3. 이미지

CI가 `develop`·`main` 푸시에서 이미지를 만들어 GHCR에 올린다. 만드는 조건과 태그 규칙은 [ci.md](ci.md)에 있다.

| 이미지 | 용도 |
| --- | --- |
| `ghcr.io/depromeet/19th-team1-server:develop` | 개발 서버용 (`develop`의 최신) |
| `ghcr.io/depromeet/19th-team1-server:main` | 운영 서버용 (`main`의 최신) |

- 개발 서버용과 운영 서버용 이미지는 같은 패키지에 태그로 나뉜다. 컨테이너 안에서 `java org.springframework.boot.loader.launch.JarLauncher`로 실행하고, `app` 사용자(root 아님)로 돈다. 포트는 8080이다.
- 직접 만들 때: `./gradlew :api:bootJar && docker build -t team1-server .`
- 패키지가 비공개면 서버에서 처음 한 번 `docker login ghcr.io`로 로그인한다. 비밀번호 자리에는 `read:packages` 권한만 있는 GitHub 토큰을 쓰고, 토큰은 서버에만 둔다.
- 서버 compose 파일은 이 레포에 없다. 서버 compose에 이 이미지를 쓰는 서비스를 `app`이라는 이름으로 두면, 아래 명령으로 새 이미지를 받아 그 서비스만 다시 띄운다.

  ```bash
  docker compose pull app && docker compose up -d --no-deps app
  ```

  서비스의 이미지를 개발 서버는 `ghcr.io/depromeet/19th-team1-server:${APP_IMAGE_TAG:-develop}`, 운영 서버는 `…:${APP_IMAGE_TAG:-main}`처럼 두면, `APP_IMAGE_TAG`에 브랜치 이력 태그(`<브랜치>-<UTC 시각>-<커밋 7자리>`, 예: `main-20261120-153000-abc1234`)를 넣고 같은 명령을 실행해 이전 버전으로 되돌릴 수 있다. 브랜치별 최근 5개 이미지는 정리에서 지우지 않으므로 그 안에서는 언제든 되돌릴 수 있다([ci.md](ci.md) 4장 "보관"). 태그 목록은 GHCR 패키지 화면에서 본다.

## 4. 프로필

| 프로필 | 쓰는 곳 | 설정을 읽는 곳 |
| --- | --- | --- |
| `local` | 각자 PC | 레포 루트의 `.env` |
| `dev` | 개발 서버 | 서버 환경변수 |
| `prod` | 운영 서버 | 서버 환경변수 |

- `SPRING_PROFILES_ACTIVE`를 주지 않으면 `local`로 뜬다. 서버에서는 반드시 지정한다.
- 프로필별로 다른 설정이 생기면 `api/src/main/resources/application-{프로필}.yml`을 추가한다.

## 5. 환경변수

값은 코드·설정 파일·문서에 넣지 않는다. 로컬은 `.env`(커밋 금지), 서버는 배포 환경의 비밀값 저장소로 주입한다. 이름은 `.env.example`에도 같은 목록을 둔다.

| 이름 | 필요한 곳 | 용도 |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | 서버 | `dev` 또는 `prod` |
| `DB_URL` | 앱 | JDBC URL (예: `jdbc:postgresql://호스트:5432/DB이름`) |
| `DB_USERNAME`, `DB_PASSWORD` | 앱, 로컬 PostgreSQL | DB 계정 |
| `DB_NAME` | 로컬 PostgreSQL | 컨테이너가 처음 뜰 때 만들 DB |
| `KAKAO_CLIENT_ID`, `KAKAO_CLIENT_SECRET` | 앱 | 카카오 로그인 앱의 REST API 키와 클라이언트 시크릿. 셋 중 하나라도 비어 있으면 `dev`·`prod`는 뜨지 않고, `local`은 경고를 남기고 떠서 로그인만 실패한다 |
| `KAKAO_REDIRECT_URI` | 앱 | 카카오 로그인 리다이렉트 URI. 카카오 개발자 콘솔에 등록한 값과 같아야 한다 |
| `JWT_SECRET` | 앱 | 액세스 토큰 서명 키(HS256). Base64 로 32바이트 이상. 비어 있으면 `dev`·`prod`는 뜨지 않고, `local`은 임시 키로 뜬다(재시작하면 토큰 무효). 바꾸면 발급한 액세스 토큰이 모두 무효가 된다 |
| `AUTH_REFRESH_TOKEN_REUSE_GRACE` | 앱(선택) | 재발급으로 폐기된 리프레시 토큰이 이 시간 안에 다시 오면 동시 재발급으로 보고 새 토큰 쌍을 준다. 지나서 오면 탈취로 보고 그 회원의 리프레시 토큰을 모두 지운다. 기본 `30s` |
| `SQS_QUEUE_URL` | 서버 | 작업 큐 URL. 로컬 기본값은 ElasticMQ의 `team1-jobs` |
| `SQS_REGION` | 서버 | 작업 큐 리전. 로컬 기본값은 `ap-southeast-2` |
| `SQS_ENDPOINT` | 로컬 | SQS 호환 서버 주소. 있으면 그 주소에 자격 증명 없이 붙는다. 서버에서는 비운다 |
| `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` | 서버(인스턴스 역할이 없을 때) | SQS 자격 증명. AWS SDK가 기본 순서(환경변수, 인스턴스 역할 등)로 찾는다 |
| `JOB_MAX_ATTEMPTS` | 앱(선택) | DB 시도 횟수 상한. 기본 3 |
| `JOB_VISIBILITY_TIMEOUT` | 앱(선택) | 받은 메시지의 가시성 제한 시간이자 실행 중인 작업을 멈춘 것으로 보는 시간. ISO-8601(`PT5M`). 기본 5분 |
| `JOB_REPUBLISH_AFTER` | 앱(선택) | 이보다 오래 PENDING·QUEUED인 작업을 다시 발행한다. ISO-8601. 기본 10분 |
| `WORKER_ENABLED` | 앱(선택) | 작업 큐 받기 루프를 켠다. 기본 `true` |
| `WORKER_CONCURRENCY` | 앱(선택) | 동시에 처리하는 작업 수. 기본 4 |
| `SCHEDULER_ENABLED` | 앱(선택) | 예약 작업(멈춘 작업 재발행)을 켠다. 기본 `true` |

기능이 추가되면 이 표에 함께 추가한다.

## 6. 작업 큐 (SQS)

처리 작업을 워커에 알리는 큐다. 구조는 [architecture.md](architecture.md) 8장 "비동기 처리 흐름"에 있다.

| 항목 | 값 | 이유 |
| --- | --- | --- |
| 큐 | 표준 큐 1개와 DLQ 1개, `dev`·`prod` 따로 | 순서가 필요 없다 |
| 메시지 내용 | 작업 ID만 | 사용자 데이터를 큐에 남기지 않는다 |
| 받기 | 롱 폴링 20초, 비어 있는 처리 칸 수만큼(최대 10개) | 동시 처리 수 상한을 지키고 빈 응답을 줄인다 |
| 가시성 제한 시간 | 링크 수집 타임아웃 + LLM 타임아웃 + 여유 | 재시도와 중단된 작업 복구를 SQS 재전송으로 한다. 처리 중에 다시 보내지 않게 처리 시간보다 길어야 한다 |
| 최대 수신 횟수 | DB 재시도 상한보다 크게 | 실패 판정은 DB의 시도 횟수로 한다. DLQ는 안전망이다 |
| 보관 기간 | 작업 큐 4일(기본), DLQ 14일(최대) | DLQ는 원인을 확인할 시간을 둔다 |
| 경보 | DLQ에 메시지가 생기면 CloudWatch 경보 | 원래 오지 않는 경로다 |
| 권한 | 앱 전용 자격 증명에 작업 큐의 발행·받기·삭제만 | DLQ는 사람이 콘솔에서 본다 |

- 설정의 환경변수 이름은 5절 표에 있다. 가시성 제한 시간, DB 재시도 상한, 재발행 대기 시간은 임시 기본값만 두었다. 최대 수신 횟수와 큐 이름, 운영 값은 정하지 않았다. 정하면 이 절과 5절 표에 적는다.
- 가시성 제한 시간은 앱이 메시지를 받을 때 직접 지정한다(`JOB_VISIBILITY_TIMEOUT`). 실행 중인 작업을 멈춘 것으로 보는 시간과 같은 값이라 둘이 어긋나지 않는다.
- 가시성 제한 시간은 링크 수집 타임아웃 + LLM 타임아웃 합보다 길어야 한다. 현재 기본값은 5분이다.
- 재발행은 5분마다 돈다. 한 번에 100개까지 다룬다.
- 로컬은 `docker compose`의 ElasticMQ(큐 `team1-jobs`, `docker/elasticmq.conf`)를 쓰고, 테스트는 Testcontainers로 ElasticMQ를 띄운다.

## 7. 로컬 실행

```bash
cp .env.example .env        # 처음 한 번. 비밀번호 칸을 채운다
docker compose up -d        # PostgreSQL, 작업 큐(ElasticMQ)
./gradlew :api:bootRun      # 앱 (local 프로필)
```

PostgreSQL 데이터는 Docker 볼륨에 남는다. 처음부터 다시 만들려면 `docker compose down -v`.
