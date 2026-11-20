# E0-3 문서 정합성 검사와 PR 검사

이슈: #39 · 에픽: E0 작업 기반 · 담당: 나현 · 명세: —

## 세부 작업

- [x] `scripts/check-docs.sh` 테스트 먼저 (`scripts/check-docs.sh`와 실패 사례 확인)
  - [x] `scripts/test-check-docs.sh`: 임시 git 레포에 일부러 깨뜨린 경우(깨진 상대 링크, 없는 이슈 번호, 지운 표기, `Closes`인데 스토리 파일 남음, `Closes`·`Refs` 없음)와 통과 경우(예외로 둔 표기, 코드 블록 안 링크, `gh` 없음 → 이슈 검사만 건너뜀)를 만들고 실패를 확인한다
- [x] `scripts/check-docs.sh` 구현 (`scripts/check-docs.sh`와 실패 사례 확인)
  - [x] 레포 검사(기본): 추적 중인 `*.md`의 상대 링크, roadmap 이슈 번호가 실제 이슈인지(`gh` 실패 시 건너뛰고 건너뛰었다고 출력), 지운 표기 목록과 예외 목록(스크립트 안 한 곳)
  - [x] PR 검사(`pr` 모드, 본문은 환경변수로): `Closes #N`·`Refs #N` 중 하나 이상, `Closes`로 닫는 이슈의 스토리 파일이 PR 결과에 남아 있지 않은지
  - [x] 지금 main에서 돌려 실패 목록을 사람에게 보여 준다. 고치지 않는다 → 실패 없음
- [x] git pre-push 훅 (pre-push 훅)
  - [x] `scripts/git-hooks/pre-push`가 레포 검사를 돌린다. 루트 `build.gradle.kts`의 훅 설치에 추가한다
- [x] Claude Code 커밋 직전 훅 (Claude Code 커밋 직전 훅)
  - [x] `.claude/settings.json`에 PreToolUse(Bash, `git commit`) 훅 추가. SessionStart 훅은 그대로 둔다. 실패하면 커밋을 막고 이유를 Claude에게 보여 준다
- [x] CI (CI 잡 추가, `test-next-story.sh` CI 실행)
  - [x] `ci.yml`에 `docs` 잡: 레포 검사, `scripts/claude/test-next-story.sh`, `scripts/test-check-docs.sh`. `ci-result`의 `needs`에 추가
  - [x] PR 본문 검사는 Convention 워크플로에 단계로 넣는다(본문을 고칠 때도 다시 돈다)
- [x] 테스트 선택과 문서 (`docs/ci.md` 반영, 분류표에 `scripts/claude/**`)
  - [x] `select-tests.sh`·`docs/ci.md` 분류표: `scripts/claude/**`, `scripts/check-docs.sh`, `scripts/test-check-docs.sh`는 Gradle 테스트 없음(대신 `docs` 잡). PR #47과 같은 줄을 고치지 않도록 새 줄로 넣는다
  - [x] `docs/ci.md` 1·5절에 `docs` 잡, `docs/conventions.md` 6절 검사 표에 pre-push·Claude 훅·CI 잡
  - [x] 바뀐 파일로 `select-tests.sh --changed`를 돌려 Gradle 테스트가 "없음"으로 분류되는지 확인
- [ ] 승환님 리뷰 (CI 영역): `.github/` 변경이라 CODEOWNERS로 자동 요청

## 참고

- 규칙: `.claude/rules/architecture.md` (해당 코드 없음), `CLAUDE.md` 작업 규칙·커밋·PR
- 문서: `docs/ci.md` 2절 분류표·5절 검사를 추가할 자리, `docs/conventions.md` 4절 PR·6절 검사, `docs/plan/roadmap.md` 정보를 두는 곳
- 결정: 읽어서 판단하는 정합성은 `doc-check` 스킬, 여기서는 기계적 검사만

## 작업 중 메모

- PR #47이 `select-tests.sh`의 `is_ignored`와 `docs/ci.md` 분류표의 "없음" 줄에 `scripts/llm-eval/**`를 넣고 있다. 같은 줄을 고치면 충돌이 나므로 새 줄로 넣는다.
- Claude 훅(공식 hooks 문서): PreToolUse는 stdin으로 `tool_input.command`를 받고, 종료 코드 2면 도구 호출을 막고 stderr를 Claude에게 이유로 보여 준다. 훅을 특정 명령에만 거는 `if` 필드는 문서 원문에서 확인하지 못했으므로 쓰지 않고, 스크립트가 명령에 `git commit`이 있는지 직접 본다. 실행 위치가 프로젝트 루트라는 보장은 없어서 `$CLAUDE_PROJECT_DIR`를 쓴다.
- 워크트리에서는 `.git`이 파일이라 `build.gradle.kts`의 훅 자동 설치(`.git`이 디렉터리일 때만)가 돌지 않는다. 훅은 원래 폴더에서 빌드할 때 공용 `.git/hooks`에 설치된다.
- PR #47과의 충돌: `select-tests.sh` 규칙은 `case` 첫 줄에, `docs/ci.md` 분류표는 머리줄 바로 아래에 새 줄로 넣었다. PR #47 head와 `git merge-tree`로 충돌 없음 확인.
