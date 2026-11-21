# 빌드·실행·배포 전제

애플리케이션의 빌드·실행 전제와 개발 서버 구성을 정리한다. 서버 구성은 7절에 있다. 운영 환경 구성은 정하지 않았다.

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
- GHCR 패키지는 공개다. 서버에서 로그인 없이 받는다.
- 개발 서버 Compose 파일은 레포의 [deploy/compose.yaml](../deploy/compose.yaml)이다(7절). 이 이미지를 쓰는 서비스 이름은 `app`이고, 태그는 서버 `.env`의 `APP_IMAGE_TAG`로 준다(개발 서버는 `develop`). 아래 명령으로 새 이미지를 받아 그 서비스만 다시 띄운다.

  ```bash
  docker compose pull app && docker compose up -d --no-deps app
  ```

  서비스의 이미지를 개발 서버는 `ghcr.io/depromeet/19th-team1-server:${APP_IMAGE_TAG:-develop}`, 운영 서버는 `…:${APP_IMAGE_TAG:-main}`처럼 두면, `APP_IMAGE_TAG`에 브랜치 이력 태그(`<브랜치>-<UTC 시각>-<커밋 7자리>`, 예: `main-20261120-153000-abc1234`)를 넣고 같은 명령을 실행해 이전 버전으로 되돌릴 수 있다. 태그 목록은 GHCR 패키지 화면에서 본다.

## 4. 프로필

| 프로필 | 쓰는 곳 | 설정을 읽는 곳 |
| --- | --- | --- |
| `local` | 각자 PC | 레포 루트의 `.env` |
| `dev` | 개발 서버 | 서버의 `/srv/team1/.env` (7절) |
| `prod` | 운영 환경 | 미정 (운영 환경 구성을 정할 때 정한다) |

- `SPRING_PROFILES_ACTIVE`를 주지 않으면 `local`로 뜬다. 서버에서는 반드시 지정한다.
- 프로필별로 다른 설정이 생기면 `api/src/main/resources/application-{프로필}.yml`을 추가한다.

## 5. 환경변수

값은 코드·설정 파일·문서에 넣지 않는다. 로컬은 레포 루트의 `.env`(커밋 금지), 서버는 `/srv/team1/.env`(7절)에 둔다. 서버 값은 서버에서 직접 적고 서버 밖에 남기지 않는다. 이름은 `.env.example`에도 같은 목록을 둔다.

"필요한 곳"은 그 값을 읽는 쪽이다. `앱`은 애플리케이션, `Compose`는 Compose 파일(로컬 `compose.yaml`, 서버 `deploy/compose.yaml`)이다. `서버`는 개발 서버 `.env`에 반드시 넣고, `로컬`은 로컬에서만 쓴다. `(선택)`은 기본값이 있어 비워도 된다.

| 이름 | 필요한 곳 | 용도 |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | 앱, 서버 | `dev` 또는 `prod`. 비우면 `local` |
| `APP_IMAGE_TAG` | 서버 Compose | `app` 서비스의 이미지 태그. 개발 서버는 `develop`. 되돌릴 때는 이력 태그(3절) |
| `SITE_ADDRESS` | 서버 Compose | Caddy가 받는 호스트 이름. 이 이름으로 HTTPS 인증서를 받는다. 개발 서버는 고정 IP의 임시 sslip.io 주소(실제 값은 팀 디스코드에 공유) |
| `DB_URL` | 앱, 로컬 | JDBC URL (예: `jdbc:postgresql://localhost:5432/DB이름`). 서버는 Compose가 `DB_NAME`으로 만들어 넘기므로 `.env`에 두지 않는다 |
| `DB_USERNAME`, `DB_PASSWORD` | 앱, Compose | DB 계정. PostgreSQL 컨테이너가 처음 뜰 때 이 계정을 만든다 |
| `DB_NAME` | Compose | PostgreSQL 컨테이너가 처음 뜰 때 만들 DB. 서버 Compose는 `DB_URL`도 이 이름으로 만든다 |
| `KAKAO_CLIENT_ID`, `KAKAO_CLIENT_SECRET` | 앱, 서버 | 카카오 로그인 앱의 REST API 키와 클라이언트 시크릿. 셋 중 하나라도 비어 있으면 `dev`·`prod`는 뜨지 않고, `local`은 경고를 남기고 떠서 로그인만 실패한다 |
| `KAKAO_REDIRECT_URI` | 앱, 서버 | 카카오 로그인 리다이렉트 URI. 카카오 개발자 콘솔에 등록한 값과 같아야 한다 |
| `JWT_SECRET` | 앱, 서버 | 액세스 토큰 서명 키(HS256). Base64 로 32바이트 이상. 비어 있으면 `dev`·`prod`는 뜨지 않고, `local`은 임시 키로 뜬다(재시작하면 토큰 무효). 바꾸면 발급한 액세스 토큰이 모두 무효가 된다 |
| `AUTH_REFRESH_TOKEN_REUSE_GRACE` | 앱(선택) | 재발급으로 폐기된 리프레시 토큰이 이 시간 안에 다시 오면 동시 재발급으로 보고 새 토큰 쌍을 준다. 지나서 오면 탈취로 보고 그 회원의 리프레시 토큰을 모두 지운다. 기본 `30s` |
| `SQS_QUEUE_URL` | 앱, 서버 | 작업 큐 URL. 개발 서버는 `team1-dev-jobs`의 URL(6절). 로컬 기본값은 ElasticMQ의 `team1-jobs` |
| `SQS_REGION` | 앱, 서버 | 작업 큐 리전. 개발 서버는 `ap-southeast-2`. 로컬 기본값도 `ap-southeast-2` |
| `SQS_ENDPOINT` | 앱, 로컬 | SQS 호환 서버 주소. 있으면 그 주소에 자격 증명 없이 붙는다. 서버에서는 비운다 |
| `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` | 앱, 서버 | SQS 자격 증명. 개발 서버는 `team1-dev-app` IAM 사용자의 액세스 키(6절). Lightsail은 IAM 역할을 붙일 수 없어 환경변수로 준다. AWS SDK가 기본 순서(환경변수, 인스턴스 역할 등)로 찾는다 |
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
| 큐 | 표준 큐 1개와 DLQ 1개, 환경마다 따로 | 순서가 필요 없다 |
| 메시지 내용 | 작업 ID만 | 사용자 데이터를 큐에 남기지 않는다 |
| 받기 | 롱 폴링 20초, 비어 있는 처리 칸 수만큼(최대 10개). 큐의 수신 대기 시간도 20초 | 동시 처리 수 상한을 지키고 빈 응답을 줄인다 |
| 가시성 제한 시간 | 링크 수집 타임아웃 + LLM 타임아웃 + 여유. 앱이 받을 때 지정하고(기본 5분), 큐 기본값도 5분 | 재시도와 중단된 작업 복구를 SQS 재전송으로 한다. 처리 중에 다시 보내지 않게 처리 시간보다 길어야 한다 |
| 최대 수신 횟수 | 5회. 넘으면 DLQ로 옮긴다 | DB 재시도 상한(기본 3)보다 크게 둔다. 실패 판정은 DB의 시도 횟수로 한다. DLQ는 안전망이다 |
| 보관 기간 | 작업 큐 4일(기본), DLQ 14일(최대) | DLQ는 원인을 확인할 시간을 둔다 |
| 암호화 | SSE-SQS (SQS 관리 키) | 추가 비용이 없다 |
| 경보 | DLQ에 메시지가 생기면 CloudWatch 경보. 아직 만들지 않았다 | 원래 오지 않는 경로다 |
| 권한 | 앱 전용 IAM 사용자에 작업 큐의 발행·받기·삭제(`sqs:SendMessage`, `sqs:ReceiveMessage`, `sqs:DeleteMessage`)만 | 앱이 쓰는 SQS API는 이 셋뿐이다. DLQ는 사람이 콘솔에서 본다 |

개발 환경에 만든 것은 아래와 같다. 리전은 `ap-southeast-2`다. 운영 환경 구성은 정하지 않았다(7절).

| 항목 | 개발 |
| --- | --- |
| 작업 큐 | `team1-dev-jobs` |
| DLQ | `team1-dev-jobs-dlq` |
| 앱 IAM 사용자 | `team1-dev-app` (콘솔 로그인 없음) |
| 권한 정책 | 인라인 정책 `team1-dev-jobs-queue` |
| DLQ 경보 | 없음 |

- 설정의 환경변수 이름은 5절 표에 있다. 가시성 제한 시간, DB 재시도 상한, 재발행 대기 시간은 앱 기본값을 쓴다. 바꾸려면 서버 `.env`에 적는다.
- 가시성 제한 시간은 앱이 메시지를 받을 때 직접 지정한다(`JOB_VISIBILITY_TIMEOUT`). 실행 중인 작업을 멈춘 것으로 보는 시간과 같은 값이라 둘이 어긋나지 않는다. 큐에 둔 기본값은 앱이 지정하지 않은 수신에만 쓰인다.
- 가시성 제한 시간은 링크 수집 타임아웃 + LLM 타임아웃 합보다 길어야 한다. 현재 기본값은 5분이다.
- 최대 수신 횟수는 DB 재시도 상한보다 커야 한다. `JOB_MAX_ATTEMPTS`를 5 이상으로 올리면 큐의 최대 수신 횟수도 함께 올린다.
- 재발행은 5분마다 돈다. 한 번에 100개까지 다룬다.
- 로컬은 `docker compose`의 ElasticMQ(큐 `team1-jobs`, `docker/elasticmq.conf`)를 쓰고, 테스트는 Testcontainers로 ElasticMQ를 띄운다.

## 7. 서버 구성

개발 서버 구성이다. 운영 환경 구성은 정하지 않았다. 개발 환경에서 테스트하며 정한다. AWS 리소스는 모두 시드니(`ap-southeast-2`)에 있다. 지금 계정(무료 플랜)은 이 리전에만 리소스를 만들 수 있다.

| 항목 | 개발 |
| --- | --- |
| 서버 | Lightsail `team1-dev`, Ubuntu 24.04, 2GB 메모리·2 vCPU·60GB |
| 고정 IP·주소 | Lightsail 고정 IP · 임시 sslip.io 주소 (실제 값은 팀 디스코드에 공유). 도메인을 정하기 전 임시 주소다 |
| 방화벽 | 22, 80, 443 (IPv4·IPv6) |
| 이미지 태그 (`APP_IMAGE_TAG`) | `develop`. `develop` 이미지가 생기기 전에는 `main` |
| 프로필 (`SPRING_PROFILES_ACTIVE`) | `dev` |
| 작업 큐·자격 증명 | 6절 |
| 예산 알림 | `team1-dev-monthly` 월 $13 (계정 전체, 크레딧 차감 전 금액) |
| 서버 설정 | Docker CE + Compose 플러그인, swap 2GB(`/swapfile`), 사용자 `deploy`(docker 그룹), `/srv/team1`(`deploy` 소유), 비밀번호 로그인 꺼짐 |
| 배포 접속 | `deploy` 사용자로 SSH 키 접속. sudo 없이 `/srv/team1`에 쓰고 `docker compose`를 실행한다. CD(E1-3)도 이 사용자로 접속하고, 공개키 등록은 사람이 한다 |

### AWS 계정 접근

- AWS 계정은 Builder ID 방식(팀 Gmail의 Google 로그인)으로 만들었다. 그래서 IAM 루트 사용자 MFA 화면이 없다.
- 공용 계정은 결제와 팀 관리에만 쓴다. 콘솔 작업은 각자 개인 Builder ID로 한다. 공용 계정의 AWS 설정 → 팀에서 초대받아 들어오고, 팀원은 모두 관리자 권한이다.
- MFA는 공용 계정과 개인 Builder ID에 각각 건다. 각 계정 profile의 보안 메뉴에서 등록한다.
- 앱은 콘솔 계정을 쓰지 않는다. 앱 자격 증명은 IAM 사용자(6절)의 액세스 키다.

### 서버 폴더

```text
/srv/team1/
├── compose.yaml     레포의 deploy/compose.yaml
├── Caddyfile        레포의 deploy/Caddyfile
├── .env             서버에서 직접 적는다. 이름은 5절 "서버"·"서버 Compose" 항목
└── postgres-data/   PostgreSQL 데이터 (바인드 마운트)
```

- 서버마다 다른 값은 `.env`로만 준다.
- `.env`에는 `DB_URL`을 두지 않는다. Compose가 `DB_NAME`으로 `jdbc:postgresql://postgres:5432/<DB_NAME>`을 만들어 앱에 넘긴다.
- Caddy가 받은 인증서는 이름 있는 볼륨(`caddy-data`)에 남는다.

### 컨테이너와 메모리

| 서비스 | 이미지 | 메모리 상한 | 바깥 포트 | 비고 |
| --- | --- | --- | --- | --- |
| `app` | `ghcr.io/depromeet/19th-team1-server:${APP_IMAGE_TAG}` | 768MB | 없음 | 힙 상한은 컨테이너 메모리의 55%(약 420MB, `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=55`). `postgres`가 healthy가 된 뒤 뜬다 |
| `postgres` | `postgres:16` | 640MB | 없음 | `max_connections=30` (앱 풀 10 + 백업·수동 접속 여유) |
| `caddy` | `caddy:2` | 없음 | 80, 443 | `SITE_ADDRESS`의 HTTPS 인증서를 자동으로 받고 `app:8080`으로 넘긴다 |

- 나머지 약 640MB는 OS·Docker·Caddy 몫이고, 모자라면 swap 2GB를 쓴다.
- 개발 서버를 막 띄운 직후(요청·작업 없음) 사용량은 app 355MB, postgres 48MB, caddy 21MB였다. 요청·작업이 늘면 다시 잰다.
- 모든 서비스는 `restart: unless-stopped`이고, 로그는 json-file로 20MB × 5개까지 남긴다.
- DB 덤프는 아직 없다([architecture.md](architecture.md) 11장).

### 처음 띄우기

`deploy` 사용자로 `/srv/team1`에 `compose.yaml`, `Caddyfile`, `.env`를 둔 뒤 실행한다.

```bash
cd /srv/team1
docker compose up -d
curl -fsS https://<SITE_ADDRESS>/actuator/health   # 200, {"status":"UP"}. 인증서는 Caddy가 처음 뜰 때 받는다
docker stats --no-stream                            # 메모리 확인
```

- 새 이미지 배포는 3절 명령(`docker compose pull app && docker compose up -d --no-deps app`)을 `/srv/team1`에서 실행한다.
- 되돌리기는 `.env`의 `APP_IMAGE_TAG`를 이력 태그(3절)로 바꾸고 같은 명령을 실행한다. 되돌린 뒤에는 원래 태그(`develop`·`main`)로 돌려놓아야 다음 배포가 최신 이미지를 받는다.
- `.env`를 고친 뒤에는 `docker compose up -d --force-recreate app`으로 앱을 다시 만든다. `docker compose restart`는 `.env`를 다시 읽지 않는다.
- `docker compose down`은 쓰지 않는다. 모든 컨테이너를 멈추고 지워 DB와 HTTPS까지 끊기고, `-v`를 붙이면 인증서 볼륨이 지워져 인증서를 다시 받아야 한다.

## 8. 로컬 실행

```bash
cp .env.example .env        # 처음 한 번. 비밀번호 칸을 채운다
docker compose up -d        # PostgreSQL, 작업 큐(ElasticMQ)
./gradlew :api:bootRun      # 앱 (local 프로필)
```

PostgreSQL 데이터는 Docker 볼륨에 남는다. 처음부터 다시 만들려면 `docker compose down -v`.
