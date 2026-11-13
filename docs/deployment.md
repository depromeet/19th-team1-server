# 빌드·실행·배포 전제

CI/CD와 서버 구성이 참고하는 애플리케이션 쪽 전제를 정리한다. 클라우드와 배포 절차는 정해지면 이 문서에 추가한다.

## 1. 요구 사항

| 항목 | 버전 | 비고 |
| --- | --- | --- |
| JDK | 25 | 로컬에 없으면 Gradle이 자동으로 내려받는다 |
| Docker | Compose v2 | 로컬 MySQL과 테스트(Testcontainers)에 필요하다 |

## 2. 빌드

```bash
./gradlew build
```

- 테스트가 MySQL 컨테이너를 띄우므로 빌드하는 곳에 Docker가 있어야 한다.
- 산출물은 `api/build/libs/` 아래 실행 jar 하나다.
- 실행: `java -jar api/build/libs/api-0.0.1-SNAPSHOT.jar`
- 상태 확인: `GET /actuator/health`. 정상이면 200과 `{"status":"UP"}`을 돌려준다. 배포 후 확인과 외부 상태 점검에 쓴다.

## 3. 프로필

| 프로필 | 쓰는 곳 | 설정을 읽는 곳 |
| --- | --- | --- |
| `local` | 각자 PC | 레포 루트의 `.env` |
| `dev` | 개발 서버 | 서버 환경변수 |
| `prod` | 운영 서버 | 서버 환경변수 |

- `SPRING_PROFILES_ACTIVE`를 주지 않으면 `local`로 뜬다. 서버에서는 반드시 지정한다.
- 프로필별로 다른 설정이 생기면 `api/src/main/resources/application-{프로필}.yml`을 추가한다.

## 4. 환경변수

값은 코드·설정 파일·문서에 넣지 않는다. 로컬은 `.env`(커밋 금지), 서버는 배포 환경의 비밀값 저장소로 주입한다. 이름은 `.env.example`에도 같은 목록을 둔다.

| 이름 | 필요한 곳 | 용도 |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | 서버 | `dev` 또는 `prod` |
| `DB_URL` | 앱 | JDBC URL (예: `jdbc:mysql://호스트:3306/DB이름`) |
| `DB_USERNAME`, `DB_PASSWORD` | 앱, 로컬 MySQL | DB 계정 |
| `DB_NAME`, `DB_ROOT_PASSWORD` | 로컬 MySQL | 컨테이너가 처음 뜰 때 만들 DB와 root 비밀번호 |

기능이 추가되면 이 표에 함께 추가한다. 예정된 항목(LLM 키·사용량 상한, 워커·스케줄러 플래그)은 `docs/architecture.md` 4장과 8장을 따른다.

## 5. 로컬 실행

```bash
cp .env.example .env        # 처음 한 번. 비밀번호 칸을 채운다
docker compose up -d        # MySQL
./gradlew :api:bootRun      # 앱 (local 프로필)
```

MySQL 데이터는 Docker 볼륨에 남는다. 처음부터 다시 만들려면 `docker compose down -v`.
