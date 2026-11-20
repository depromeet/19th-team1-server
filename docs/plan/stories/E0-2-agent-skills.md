# E0-2 에이전트 작업 스킬과 훅

이슈: #38 · 에픽: E0 작업 기반 · 담당: 나현 · 명세: —

## 세부 작업

- [x] 다음 스토리 고르기 스크립트 `scripts/claude/next-story.sh` (세션 시작 훅, 스킬 3개)
  - [x] roadmap 표를 읽고, 이슈 상태·담당자를 `gh`로 한 번에 받아 "내 열린 이슈"와 "착수 가능한 다음 스토리"를 출력한다. 고르는 기준은 roadmap "진행 방법" 1번
  - [x] 가짜 `gh`와 예시 roadmap으로 고르기 규칙(담당, 열림, 선행 닫힘, 미등록 선행, 주차 순)을 먼저 검증하는 테스트를 쓰고 통과시킨다
- [x] 세션 시작 훅 `scripts/claude/session-start.sh` (세션 시작 훅)
  - [x] 위 스크립트 결과(`--brief`)와 현재 브랜치의 이슈를 표준 출력으로 낸다(SessionStart는 표준 출력을 컨텍스트에 넣는다). `gh`가 없거나, 로그인 전이거나, 네트워크가 실패하면 아무것도 출력하지 않고 0으로 끝난다
  - [x] 세 경우(정상, `gh` 없음, 로그인 전)를 실행해 확인한다
- [x] `.claude/settings.json` (세션 시작 훅, Superpowers 프로젝트 설정)
  - [x] SessionStart 훅 등록
  - [x] `enabledPlugins`에 `"superpowers@claude-plugins-official": true`. 공식 마켓플레이스는 Claude Code가 처음 실행될 때 알아서 등록하므로 `extraKnownMarketplaces`는 넣지 않는다
  - [x] 레포 설정은 플러그인을 켜기만 하고 설치하지 않으므로, 설치 명령 한 줄을 CLAUDE.md에 적는다
  - [x] 권한(allow)은 넣지 않는다
- [x] `.claude/skills/next-story/SKILL.md` (스킬 3개)
  - [x] 스토리 ID가 없으면 `next-story.sh`로 고르고, 이슈 완료 조건과 관련 규칙을 읽어 템플릿으로 스토리 파일을 만들고, 브랜치 이름을 안내하고, 계획 확인을 받은 뒤에만 구현한다
- [x] `.claude/skills/plan-sync/SKILL.md` (스킬 3개)
  - [x] 바뀐 결정 → scope의 명세 ID·"결정 대기" 표로 영향 스토리 찾기 → scope·roadmap·진행 중 스토리·이슈 수정안 → 승인 후 문서 수정과 `gh issue edit`
- [x] `.claude/skills/doc-check/SKILL.md` (스킬 3개)
  - [x] 읽어서 판단하는 검사 목록과 보고 형식(위치·유형·근거). 고치지 않는다. 기계적 검사는 #39로 미룬다
- [x] 문서 반영 (CLAUDE.md, roadmap 반영)
  - [x] `CLAUDE.md` 작업 규칙의 "일감"에 스킬 사용 시점 한 줄
  - [x] `roadmap.md` 진행 방법에 스킬 연결, E0-2 #38, E0-3 #39
- [x] 흐름 확인 (#23 착수)
  - [x] `next-story`를 E1-1(#23)로 돌려 스토리 파일·브랜치 안내·계획 확인 요청까지 나오는지 보고 결과를 보여 준다. #23 구현은 하지 않는다. 만든 E1-1 스토리 파일은 이 브랜치에서 지웠다

## 참고

- 규칙: `CLAUDE.md` 작업 규칙·커밋·PR
- 문서: `docs/plan/roadmap.md` 진행 방법·계획이 바뀔 때, `docs/plan/scope.md` 결정 대기, `docs/conventions.md` 2. 브랜치
- 결정: 기계적 문서 검사, pre-push, CI는 #39

## 작업 중 메모

- Superpowers는 공식 마켓플레이스(`claude-plugins-official`)에 `superpowers`로 등록돼 있다(로컬 마켓플레이스 목록에서 확인).
- 공식 문서(plugins/org, troubleshooting): 레포의 `enabledPlugins`는 플러그인을 켜기만 하고 설치하지 않는다. 설치를 자동으로 권하는 창이 뜬다는 설명은 문서에 없다. 설치하지 않은 팀원은 `/plugin`의 Errors 탭에 "isn't installed here"가 보이고, `claude plugin install <이름>@<마켓플레이스> --scope project`를 직접 실행해야 한다.
- roadmap에는 "참고" 열이 없다. next-story는 CLAUDE.md의 규칙 파일 표를 보고 이슈가 다루는 경로에 맞는 규칙을 골라 스토리 파일의 "참고"에 적는다.
- 스킬을 추가한 직후에는 Skill 도구에서 인식되지 않았다(`Unknown skill: next-story`). 흐름 확인은 SKILL.md 단계를 그대로 따라 했다. 이후 같은 세션의 스킬 목록에 세 스킬이 나타났다. 새 세션에서의 동작과 세션 시작 훅 실행은 확인하지 못했다.
- 훅 `timeout`은 초 단위(command 기본 600). 15로 둔다. matcher는 생략해 모든 시작 경우에 돈다.
- writing-skills의 서브에이전트 압박 시나리오 테스트는 하지 않았다. 레포 절차 안내 스킬이라 #23 실제 실행으로 대신했다.
