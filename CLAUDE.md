# CLAUDE.md

에이전트가 이 레포에서 작업할 때 따르는 규칙이다. 작업 규칙의 원본은 `.claude/rules/`, 구조 설명과 정한 이유는 `docs/`에 둔다. 같은 내용을 두 곳에 적지 않는다.

## 프로젝트

디프만 19기 1팀 서버. 저장만 하고 다시 쓰지 않는 정보를 "저장 → 다시 꺼내기 → 재사용"하게 만드는 서비스다. Kotlin, Spring Boot, MySQL.

## 규칙 파일

`.claude/rules/`의 파일은 Claude Code가 자동으로 읽는다. 경로가 지정된 파일은 그 경로의 파일을 다룰 때만 읽힌다.

| 파일 | 읽히는 때 | 내용 |
| --- | --- | --- |
| [architecture.md](.claude/rules/architecture.md) | 항상 | 모듈, 위치와 이름, 호출, 도메인 모델, 서비스와 조회, 트랜잭션 |
| [testing.md](.claude/rules/testing.md) | 테스트 코드를 다룰 때 | 대역, 공용 테스트 도구, 실제 DB 테스트, 작성 방식 |
| [storage.md](.claude/rules/storage.md) | `infra`의 storage, 마이그레이션을 다룰 때 | 엔티티·저장소 구현, 마이그레이션 |
| [external.md](.claude/rules/external.md) | `infra`의 external을 다룰 때 | 외부 응답 변환, 타임아웃, 오류, 로그 |
| [api.md](.claude/rules/api.md) | `api`의 main 코드를 다룰 때 | 경로, DTO 변환, 응답과 에러 |
| [worker.md](.claude/rules/worker.md) | `worker`의 main 코드를 다룰 때 | 패키지, 실패 기록, 켜고 끄기 |

## 설명 문서

설계를 바꾸거나 이유를 확인할 때 읽는다. 매 작업마다 읽지 않는다.

| 문서 | 내용 |
| --- | --- |
| [docs/architecture.md](docs/architecture.md) | 배포 구성, 모듈·패키지 구조, 도메인 모델 필드, 확장 지점, 결정 기록, 기능 하나를 만드는 순서 |
| [docs/testing.md](docs/testing.md) | 계층별 테스트 방식과 대역을 고른 이유 |
| [docs/conventions.md](docs/conventions.md) | 브랜치·커밋·PR 규칙. 커밋하거나 PR을 만들 때 읽는다 |
| [docs/deployment.md](docs/deployment.md) | 빌드·실행 전제, 프로필, 환경변수 목록. 설정을 바꿀 때 읽는다 |

## 빌드·실행

```bash
docker compose up -d            # 로컬 MySQL (처음엔 cp .env.example .env)
./gradlew build                 # 전체 빌드 + 테스트 (Docker 필요)
./gradlew :api:bootRun          # 애플리케이션 실행
./gradlew :core:test            # 모듈별 테스트
./gradlew :core:test --tests "com.depromeet.team1.core.SomeTest"   # 테스트 클래스 하나
```

- 모듈: `core`(도메인·서비스·경계 인터페이스), `infra`(DB·외부 연동 구현), `api`(컨트롤러, 실행 모듈), `worker`(작업 폴링·스케줄러)
- 버전은 `gradle/libs.versions.toml`에서, 라이브러리 버전은 Spring Boot BOM에서 관리한다. 모듈의 `build.gradle.kts`에는 버전을 적지 않는다.

## 코드 스타일

- 스타일 규칙의 원본은 `.editorconfig`다. ktlint가 이 파일을 읽어 검사한다.
- 커밋할 때 git pre-commit 훅이 스테이지된 Kotlin 파일을 자동으로 포맷해 커밋에 포함한다. 훅은 로컬에서 처음 빌드할 때 자동으로 설치된다.
- `./gradlew build`와 PR 검사에는 포맷 검사(`ktlintCheck`)가 포함된다. 훅을 건너뛴 커밋(`--no-verify`)은 여기서 걸린다.
- 에이전트가 Gradle을 실행할 수 없는 환경에서 커밋했다면, 사람이 푸시하기 전에 `./gradlew ktlintFormat`을 실행하도록 알린다.

## 작업 규칙

- **범위**: 이슈에 적힌 범위만 작업한다. 다음 이슈에서 필요할 것 같은 코드를 미리 만들지 않는다.
- **구조**: `.claude/rules/architecture.md`를 따른다. 규칙과 어긋나는 변경이 필요하면 구현하기 전에 사람에게 확인하고, 합의되면 규칙 파일과 문서를 같은 PR에서 고친다.
- **테스트**: 기능과 테스트를 같은 PR에 넣는다. 버그 수정에는 그 버그를 재현하는 테스트를 추가한다. 실패한 테스트를 통과시키려고 단언을 약하게 고치거나, 테스트를 지우거나 비활성화하지 않는다. 테스트가 틀렸다면 이유를 PR에 적고 고친다.
- **검증 보고**: 실행한 명령과 결과를 보고한다. 실행하지 못한 검증은 실행하지 못했다고 적고, 완료로 표현하지 않는다.
- **DB**: 테이블·컬럼·인덱스·제약 조건을 추가하거나 바꾸는 일은 사람의 확인을 받은 뒤에 한다.
- **비밀값**: 키·비밀번호·토큰·접속 문자열의 실제값을 코드·설정·문서·커밋 메시지·대화에 넣지 않는다. `.env`를 읽거나 출력하지 않는다. `.env.example`에는 이름만 적는다. 환경변수를 추가하면 `.env.example`과 `docs/deployment.md`의 표에 이름을 함께 추가한다.
- **문서**: 구조, 규칙, API가 바뀌면 같은 PR에서 `.claude/rules/`와 `docs/`를 고친다. 문서에는 현재 상태만 적고 변경 이력을 남기지 않는다.
- **추측 금지**: 확인하지 않은 동작을 사실처럼 쓰지 않는다. 코드나 실행 결과로 확인하고, 확인할 수 없으면 그렇다고 밝힌다.

## 커밋·PR

- 형식은 `docs/conventions.md`를 따른다. 규칙의 원본은 `scripts/check-convention.sh`이고, 커밋하기 전에 `scripts/check-convention.sh message "<메시지 첫 줄>"`로 확인할 수 있다.
- 이슈 생성, 커밋, 푸시, PR 생성은 사람이 요청할 때만 한다. 커밋하기 전에 메시지를 보여 주고 확인을 받는다.
- 에이전트가 작성한 커밋에는 `Co-Authored-By: Claude <noreply@anthropic.com>` 한 줄을 남긴다. 세션 링크나 세션 식별 정보는 남기지 않는다.

## 코드 리뷰

- 의미 있는 문제가 없으면 없다고 하고 끝낸다. 지적할 거리를 만들어 내지 않는다.
- 지적에는 근거가 되는 코드를 인용한다. 확실하지 않으면 질문으로 쓴다.
- 취향 수준의 스타일 지적은 "머지 가능" 등급으로만 남긴다.
