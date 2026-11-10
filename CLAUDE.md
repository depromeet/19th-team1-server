# CLAUDE.md

에이전트가 이 레포에서 작업할 때 따르는 규칙이다. 규칙의 원본은 `docs/`에 있고, 이 파일은 읽을 문서의 위치와 에이전트에게만 해당하는 규칙을 담는다. 같은 규칙을 여기에 다시 적지 않는다.

## 프로젝트

디프만 19기 1팀 서버. 저장만 하고 다시 쓰지 않는 정보를 "저장 → 다시 꺼내기 → 재사용"하게 만드는 서비스다. Kotlin, Spring Boot, MySQL.

## 먼저 읽을 문서

| 문서 | 내용 |
| --- | --- |
| [docs/architecture.md](docs/architecture.md) | 배포 구성, 모듈·패키지 구조, 도메인 모델, 확장 지점, 결정 기록 |
| [docs/conventions.md](docs/conventions.md) | 브랜치·커밋·PR 규칙 |
| [docs/deployment.md](docs/deployment.md) | 빌드·실행 전제, 프로필, 환경변수 목록 |

## 빌드·실행

```bash
docker compose up -d            # 로컬 MySQL (처음엔 cp .env.example .env)
./gradlew build                 # 전체 빌드 + 테스트 (Docker 필요)
./gradlew :api:bootRun          # 애플리케이션 실행
./gradlew :core:test            # 모듈별 테스트
./gradlew :core:test --tests "com.depromeet.team1.core.SomeTest"   # 테스트 클래스 하나
```

- 모듈: `core`(도메인·서비스·경계 인터페이스), `storage`(DB 구현), `external`(외부 연동 구현), `api`(진입점, 실행 모듈)
- 버전은 `gradle/libs.versions.toml`에서, 라이브러리 버전은 Spring Boot BOM에서 관리한다. 모듈의 `build.gradle.kts`에는 버전을 적지 않는다.

## 코드 스타일

- 스타일 규칙의 원본은 `.editorconfig`다. ktlint가 이 파일을 읽어 검사한다.
- 커밋할 때 git pre-commit 훅이 스테이지된 Kotlin 파일을 자동으로 포맷해 커밋에 포함한다. 훅은 로컬에서 처음 빌드할 때 자동으로 설치된다.
- `./gradlew build`와 PR 검사에는 포맷 검사(`ktlintCheck`)가 포함된다. 훅을 건너뛴 커밋(`--no-verify`)은 여기서 걸린다.
- 에이전트가 Gradle을 실행할 수 없는 환경에서 커밋했다면, 사람이 푸시하기 전에 `./gradlew ktlintFormat`을 실행하도록 알린다.

## 작업 규칙

- **범위**: 이슈에 적힌 범위만 작업한다. 다음 이슈에서 필요할 것 같은 코드를 미리 만들지 않는다.
- **구조**: 모듈 의존 방향과 패키지 규칙은 `docs/architecture.md`를 따른다. 규칙과 어긋나는 변경이 필요하면 구현하기 전에 사람에게 확인하고, 합의되면 문서를 같은 PR에서 고친다.
- **DB**: 테이블·컬럼·인덱스·제약 조건을 추가하거나 바꾸는 일은 사람의 확인을 받은 뒤에 한다.
- **비밀값**: 키·비밀번호·토큰·접속 문자열의 실제값을 코드·설정·문서·커밋 메시지·대화에 넣지 않는다. `.env`를 읽거나 출력하지 않는다. `.env.example`에는 이름만 적는다.
- **문서**: 구조, 규칙, API가 바뀌면 같은 PR에서 문서를 고친다. 문서에는 현재 상태만 적고 변경 이력을 남기지 않는다.
- **추측 금지**: 확인하지 않은 동작을 사실처럼 쓰지 않는다. 코드나 실행 결과로 확인하고, 확인할 수 없으면 그렇다고 밝힌다.

## 커밋·PR

- 형식은 `docs/conventions.md`를 따른다.
- 이슈 생성, 커밋, 푸시, PR 생성은 사람이 요청할 때만 한다. 커밋하기 전에 메시지를 보여 주고 확인을 받는다.
- 에이전트가 작성한 커밋에는 `Co-Authored-By: Claude <noreply@anthropic.com>` 한 줄을 남긴다. 세션 링크나 세션 식별 정보는 남기지 않는다.

## 코드 리뷰

- 의미 있는 문제가 없으면 없다고 하고 끝낸다. 지적할 거리를 만들어 내지 않는다.
- 지적에는 근거가 되는 코드를 인용한다. 확실하지 않으면 질문으로 쓴다.
- 취향 수준의 스타일 지적은 "머지 가능" 등급으로만 남긴다.
