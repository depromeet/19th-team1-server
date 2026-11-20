---
name: next-story
description: Use when starting work on a story or GitHub issue in this repo — "다음 일감", "#23 진행해 줘", "E1-1 착수" — or before writing code for an issue that has no file in docs/plan/stories/.
argument-hint: "[스토리 ID 또는 이슈 번호]"
---

# 스토리 착수

`docs/plan/roadmap.md`의 "진행 방법" 1~2번(고르기, 착수)을 수행한다. 계획을 사람이 확인하기 전에는 코드·설정·문서를 고치지 않는다. 스토리 파일만 예외다.

## 1. 고르기

- `scripts/claude/next-story.sh`를 실행한다. 출력의 "착수 가능한 스토리"가 roadmap 기준을 적용한 결과다. 고르는 규칙을 직접 다시 판단하지 않는다.
- 인자가 없으면 첫 번째 후보를 고른다.
- 인자가 있으면 roadmap에서 그 스토리 행을 찾는다. 후보 목록에 없으면 이유(선행 이슈가 열림, 이슈 미등록, 담당이 다름, 이미 닫힘)를 알리고 진행할지 묻는다.
- 스크립트가 실패하면(`gh` 없음, 로그인 전) 멈추고 사람에게 알린다. 상태를 추측하지 않는다.

## 2. 읽기

- `gh issue view <번호>`: 무엇을, 완료 조건, 계획.
- roadmap의 그 행: 선행, 주차, 명세 ID.
- 명세 ID가 있으면 `docs/plan/scope.md`의 해당 행을 읽는다. "결정 대기" 표에 이 스토리가 있으면 기다리는 결정을 계획에 적는다.
- `CLAUDE.md`의 규칙 파일 표에서 이 이슈가 다루는 경로에 해당하는 규칙을 고르고 읽는다. `architecture.md`는 항상 포함된다.

## 3. 브랜치

- 현재 브랜치가 이미 이 이슈의 브랜치이면 건너뛴다. 확인은 `scripts/check-convention.sh issue-from-branch "$(git branch --show-current)"`로 한다.
- 아니면 `docs/conventions.md` 2절 형식(`타입/이슈번호-영문-설명`)으로 이름을 정하고 `scripts/check-convention.sh branch <이름>`으로 검사한다. 아래 명령을 안내하되 직접 실행하지는 않는다.
  ```
  git switch main && git pull && git switch -c <이름>
  ```

## 4. 스토리 파일

- `docs/plan/stories/_template.md`를 복사해 `docs/plan/stories/<스토리 ID>-<영문-설명>.md`를 만든다. 이미 있으면 새로 만들지 않고 그 파일을 이어서 쓴다.
- 머리줄에 이슈 번호, 에픽, 담당, 명세 ID를 채운다.
- **세부 작업**: 완료 조건을 이루는 작업을 순서대로 적고, 작업마다 어떤 완료 조건을 위한 것인지 괄호로 적는다. 테스트를 먼저 쓰는 순서로 적는다.
- **참고**: 2단계에서 고른 규칙 파일, 문서의 절, 관련 결정을 경로와 절 이름만 적는다.
- 이슈에 있는 무엇을·왜·완료 조건을 다시 적지 않는다.

## 5. 계획 확인

아래를 보여 주고 **멈춘다**.

1. 고른 스토리와 이슈, 고른 이유(후보 순서)
2. 브랜치 안내(또는 "이미 이 브랜치")
3. 스토리 파일 경로와 세부 작업
4. 사람이 정해야 할 것: 결정 대기, 규칙과 어긋나는 변경, DB 변경, 완료 조건이 모호한 곳

사람이 확인하면 구현에 들어간다. 구현은 superpowers:test-driven-development를 따르고, 끝났다고 말하기 전에 superpowers:verification-before-completion을 따른다.

## 흔한 실수

| 실수 | 바로잡기 |
| --- | --- |
| 계획 확인 없이 구현을 시작한다 | 5단계에서 멈춘다 |
| roadmap 표를 눈으로 읽고 다음 스토리를 고른다 | `next-story.sh` 결과를 쓴다 |
| 별도 계획 파일을 만든다 | 계획은 스토리 파일의 세부 작업에만 쓴다 |
| 브랜치를 직접 만들거나 `develop`에서 딴다 | 명령을 안내만 하고, `main`에서 따도록 안내한다 |
