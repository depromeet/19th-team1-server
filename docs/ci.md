# CI

GitHub Actions(`.github/workflows/ci.yml`)가 테스트를 돌리고 서버 이미지를 GHCR에 올린다. 이 문서는 무엇을 언제 돌리는지와 그렇게 정한 이유를 담는다.

## 1. 실행 범위

| 이벤트 | 테스트 | 이미지 |
| --- | --- | --- |
| `main` 대상 PR | 전체 | 없음 |
| `main` 푸시 (PR 머지) | 전체 | `main` 태그 (운영 서버용) |
| `develop` 푸시 | 영향받는 모듈의 테스트 | `develop` 태그 (개발 서버용) |
| 기능 브랜치 푸시 | 영향받는 모듈의 테스트 | 없음 |
| 수동 실행 | 전체 | 없음 |

- 브랜치 보호의 필수 검사는 CI 워크플로의 `ci-result`와 Convention 워크플로의 `convention`이다. `ci-result`는 CI의 검사 잡(`test`, `docs`) 결과를 모은 잡이다. 검사 잡을 추가하면 이 잡의 `needs`에 더한다.
- `ci-result`라는 이름은 `main` 대상 PR 실행(전체 테스트)에만 붙는다. 브랜치 푸시 실행은 `ci-result (push)`로 남아 필수 검사가 아니다. 같은 커밋에 두 실행이 같은 이름을 남기면 GitHub이 어느 결과로 머지를 허락할지 문서에 정해져 있지 않기 때문이다.
- `docs` 잡은 이벤트와 바뀐 파일에 관계없이 항상 돈다. 문서 검사(`scripts/check-docs.sh`)와 Gradle 밖의 스크립트 테스트(`scripts/test-check-docs.sh`, `scripts/claude/test-next-story.sh`, `scripts/ci/test-cleanup-images.sh`)다. PR 본문 검사는 본문을 고칠 때도 다시 돌아야 해서 Convention 워크플로에 있다.
- PR이 열린 기능 브랜치에 푸시하면 선택 실행(푸시)과 전체 실행(PR)이 함께 돈다. 선택 실행은 빠른 확인, 전체 실행은 머지 관문이다.

## 2. 영향받는 테스트 고르기

바뀐 파일이 속한 모듈과, 그 모듈에 의존하는 모듈의 테스트를 모두 돌린다. 나머지 모듈의 테스트는 돌리지 않는다. 구조 테스트는 항상 돌린다.

**순서** (`scripts/ci/select-tests.sh`. 모듈과 모듈 간 의존은 루트 `build.gradle.kts`의 `ciModules` 태스크가 출력한다)

1. 비교 기준(base)을 정한다. 기능 브랜치는 `main`과의 공통 조상, `develop`은 CI가 마지막으로 성공한 `develop` 커밋이다. 직전 푸시를 기준으로 하면, 실패했거나 취소된 실행의 변경이 다시 테스트되지 않은 채 이미지로 나간다.
2. base와 현재 커밋 사이에 바뀐 파일 목록을 만든다. 이름이 바뀐 파일은 옛 경로와 새 경로를 모두 넣는다.
3. 파일마다 아래 표로 분류한다. 모듈 안의 파일은 디렉터리가 가장 깊게 맞는 모듈의 변경으로 본다.
4. 바뀐 모듈과, Gradle 프로젝트 의존을 따라 그 모듈에 닿는 모듈을 고른다. 의존은 설정 종류(`implementation`, `runtimeOnly`, `testFixtures(project(..))` 등)를 구분하지 않는다.
5. 실행할 Gradle 태스크를 정한다. 모든 모듈의 포맷 검사(`ktlintCheck`), 고른 모듈의 `check`(테스트 전부), 구조 테스트가 있는데 고르지 않은 모듈의 `test --tests *ArchitectureTest`다. 예: `infra`가 바뀌면 `./gradlew ktlintCheck :infra:check :api:check`, 문서만 바뀌면 `./gradlew ktlintCheck :api:test --tests *ArchitectureTest`.

고른 모듈과 이유는 실행 요약(Summary)에 표로 남는다.

| 바뀐 파일 | 돌리는 테스트 |
| --- | --- |
| `scripts/check-docs.sh`, `scripts/test-check-docs.sh`, `scripts/claude/**` | Gradle 테스트 없음. `docs` 잡이 항상 테스트한다 |
| 루트 빌드 파일, `gradle/**`, `gradle.properties`, `gradlew*`, `.github/workflows/**`, `scripts/ci/**` | 전체 |
| 모듈 안의 파일 (코드, 테스트, 리소스, 마이그레이션, 모듈의 `build.gradle.kts`, `.md`를 포함한 모든 파일) | 그 모듈과 그 모듈에 의존하는 모듈 |
| 모듈 밖의 `*.md`, `docs/**`, `.claude/**`, `.github/ISSUE_TEMPLATE/**`, `compose.yaml`, `.env.example`, `Dockerfile`, `.dockerignore`, `.editorconfig`, `.gitignore`, `.gitattributes`, `scripts/git-hooks/**`, `scripts/check-convention.sh` | 없음 |
| 그 밖에 모듈 밖의 파일 | 전체 |

지금 모듈 의존은 `core ← infra, worker ← api`다. 그래서 `core`가 바뀌면 모든 모듈, `infra`·`worker`가 바뀌면 그 모듈과 `api`, `api`가 바뀌면 `api`만 돈다.

- 구조 테스트는 항상 돌린다. 테스트 디렉터리에서 이름이 `ArchitectureTest.kt`로 끝나는 파일을 찾아 그 모듈에서 돌린다. 하나도 없으면 전체를 돌린다.
- 포맷 검사(`ktlintCheck`)는 모든 모듈에 돌린다.
- base를 정하지 못하거나(`develop`에서 성공한 실행이 없거나 force push로 이력이 이어지지 않음, `main`과 공통 조상이 없음 등) 바뀐 파일 목록이나 모듈 목록을 만들지 못하면 전체를 돌린다. 그 밖에 스크립트가 오류로 멈추면 테스트 단계가 실패한다.
- 규칙에 없는 파일은 판단하지 않고 전체를 돌린다. 분류가 틀려도 더 많이 돌리는 쪽으로만 틀린다.

**놓칠 수 있는 경우**: 테스트에 영향이 없다고 분류한 파일(위 표의 "없음")을 테스트가 읽으면 놓친다. 예를 들어 테스트가 `docs/`의 파일을 읽는 경우다. 이런 테스트는 `main`으로 가는 PR의 전체 실행에서 잡힌다. 모듈 안의 변경은 Spring DI나 리플렉션으로만 이어진 연결도 모듈 테스트 전체를 돌리므로 놓치지 않는다.

## 3. 테스트 선택 방식을 고른 이유

**고른 것**: Gradle 모듈 의존 그래프로 영향받는 모듈을 고르고, `main`으로 가는 단계에서 전체를 돌린다. Google TAP(빌드 그래프의 영향받는 타깃), AndroidX와 Dropbox의 AffectedModuleDetector가 쓰는 방식이다.

- 장점: 빌드 도구가 가진 의존 정보만 쓰므로 규칙이 작고, Spring DI·슬라이스 테스트의 스캔·리플렉션처럼 코드 참조로 안 보이는 연결을 따로 다루지 않아도 된다. Spring Boot 버전이 바뀌어도 고칠 것이 없다.
- 단점: 덜 걸러낸다. 지금 구조에서는 `core`가 바뀌면 모든 모듈의 테스트가 돈다. 모듈이 기능 단위로 나뉘면 걸러지는 양이 늘어난다.

| 대안 | 고르지 않은 이유 |
| --- | --- |
| 클래스 단위 정적 분석 (바뀐 클래스에 참조로 닿는 테스트만) | 더 촘촘하지만, Spring이 실행할 때 잇는 연결(인터페이스 → 구현 빈, `@SpringBootTest`, 슬라이스 테스트의 스캔, `@Bean` 메서드)을 따로 모델링해야 하고 그래도 놓치는 경우가 남는다. 직접 관리할 코드가 크고, Spring 테스트 어노테이션의 패키지가 버전마다 바뀌면 따라가야 한다 |
| 메서드 단위 정적 분석 | 클래스 단위의 문제에 더해 인터페이스 호출 해석과 Kotlin이 만드는 메서드 처리가 크다 |
| 테스트별 커버리지(JaCoCo) 매핑 | 전에 실행되지 않은 줄이 바뀌면 아무 테스트도 고르지 못한다. 매핑을 계속 갱신해야 하고, `main`마다 계측한 전체 실행이 필요하다 |
| 머신러닝 예측 (Develocity Predictive Test Selection 등) | 유료이고, 테스트가 매우 많은 조직에서 효과가 난다 |
| 매번 전체 실행 | 테스트가 늘수록 모든 푸시가 느려진다 |

## 4. 이미지

| 항목 | 내용 |
| --- | --- |
| 위치 | `ghcr.io/depromeet/19th-team1-server` |
| 내용 | `api` 실행 jar 하나. `api`가 의존하는 `core`·`infra`·`worker` 모듈이 함께 들어간다 |
| 태그 | `fp-<지문>`(jar, `Dockerfile`, `.dockerignore` 내용), `<브랜치>-<UTC 시각>-<커밋 7자리>`(브랜치 이력, 예: `develop-20261120-153000-abc1234`), `develop`·`main`(각 서버가 받는 최신) |
| 권한 | `GITHUB_TOKEN` + `packages: write`. 따로 넣는 비밀값은 없다 |

**새 이미지를 만드는 조건**: 지문이 처음 보는 값일 때만 만든다.

- 지문은 실행 jar(`./gradlew :api:bootJar`)와 `Dockerfile`·`.dockerignore`의 `sha256`을 합친 값이다. 같은 코드로 다시 빌드하면 jar가 바이트 단위로 같다(Gradle의 재현 가능한 아카이브 설정).
- `fp-<지문>` 태그가 이미 있으면 빌드하지 않고, 브랜치 태그와 이력 태그만 그 이미지로 옮긴다. 이미지 라벨 `org.opencontainers.image.revision`은 처음 빌드한 커밋으로 남는다. 어느 커밋에 배포됐는지는 이력 태그로 본다.
- 빌드 증명(provenance)은 첨부하지 않는다. 첨부하면 이미지 하나가 레지스트리에 여러 조각으로 저장돼 정리가 복잡해진다.
- 실행 jar에 빌드 시각이 들어가면(`springBoot { buildInfo() }`의 `build-info.properties` 등) 커밋마다 지문이 달라져 새 이미지가 생긴다. 넣으려면 지문 계산에서 그 항목을 빼야 한다.
- 코드와 `Dockerfile`·`.dockerignore`가 그대로인 커밋(문서, 워크플로만 바뀜 등)은 지문이 같아 새 이미지를 만들지 않는다. 브랜치 태그와 이력 태그를 이미 있는 이미지로 옮긴다.
- 주석이나 포맷만 바꿨어도 줄 수가 달라져 뒤쪽 코드의 줄 번호가 밀리면 클래스 파일이 달라져 새 이미지가 생긴다. 한 줄 안에서 주석만 고치면 클래스 파일은 그대로다. 줄 번호를 지우고 비교(정규화)하면 막을 수 있지만 하지 않는다.
  - 지우지 말아야 할 정보를 지우면 동작이 바뀐 코드도 지문이 같아 배포가 조용히 빠진다. 정규화하지 않으면 틀려도 이미지가 한 번 더 생기는 쪽으로만 틀린다.
  - 새로 빌드하지 않은 이미지는 옛 커밋의 줄 번호를 가져서 운영 로그의 줄 번호가 최신 소스와 어긋난다.
  - 클래스 파일을 읽는 라이브러리(ASM)를 JDK를 올릴 때마다 함께 올려야 한다.

**보관**: 이미지를 올린 뒤 같은 잡에서 오래된 이미지를 정리한다(`scripts/ci/cleanup-images.sh`). 푸시마다 이미지가 쌓이므로, 올릴 때마다 오래된 것을 밀어내 브랜치별 개수를 일정하게 둔다.

- 브랜치(`main`, `develop`)마다 이력 태그를 최신순으로 훑어 서로 다른 이미지 5개를 남긴다. 태그만 옮긴 푸시로 한 이미지에 이력 태그가 여러 개 붙어 있으면 1개로 센다.
- 지금 `main`·`develop` 태그가 붙은 이미지와, 두 브랜치 중 한쪽에라도 남는 이미지는 지우지 않는다. 나머지 버전(태그 없는 버전 포함)을 지운다.
- 이미지가 만들어진 시각이 아니라 이력 태그로 순서를 정한다. `develop`에서 만든 이미지를 `main`이 태그만 옮겨 받으면, `main`에서는 최신이어도 만들어진 시각은 오래됐기 때문이다.
- 정리 단계의 `DRY_RUN`이 `"true"`면 지울 목록만 실행 요약(Summary)에 출력하고 지우지 않는다.
- 지운 버전은 30일 안에 GHCR 패키지 설정에서 복구할 수 있다.
- 정리가 지운 이미지로 다른 브랜치의 잡이 태그를 옮기려 하면 그 잡이 실패한다. 다시 실행하면 이미지를 새로 빌드한다.

**develop과 main 이미지를 따로 두는 이유**: "한 번 빌드해 여러 환경에 승격"은 같은 커밋이 개발에서 운영으로 넘어갈 때의 원칙이다. 이 레포는 `develop`을 `main`으로 머지하지 않고, 기능 브랜치가 각각 들어간다([conventions.md](conventions.md)). 두 브랜치의 코드가 다르므로 이미지도 다르다. 지문이 같으면 같은 이미지를 쓴다.

서버에서 쓰는 방법은 [deployment.md](deployment.md)에 있다.

## 5. 검사를 추가할 자리

새 검사(예: SDD 스펙과 코드가 맞는지)는 `ci.yml`에 잡으로 추가하고 `ci-result`의 `needs`에 더한다. 브랜치 보호 설정은 바꾸지 않아도 된다.
