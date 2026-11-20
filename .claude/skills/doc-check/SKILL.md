---
name: doc-check
description: Use before opening a PR, after changing docs/, .claude/rules/, API controllers or docs/plan/, or when asked whether docs, rules, API contracts, the roadmap and GitHub issues still agree.
---

# 문서 정합성 점검

문서끼리 내용이 어긋난 곳을 읽고 판단해서 찾는다. **고치지 않고 보고만 한다.** 링크가 깨졌는지, 옛 표기가 남았는지 같은 기계적 검사는 이 스킬이 다루지 않는다.

## 검사 대상

| # | 비교 | 볼 것 |
| --- | --- | --- |
| 1 | `docs/architecture.md`, `docs/testing.md` ↔ `.claude/rules/` | 같은 주제(모듈 의존, 위치와 이름, 트랜잭션, 대역, 마이그레이션)를 서로 다르게 말하는 곳. 한쪽에만 있는 규칙은 문제가 아니다. 서로 반대로 말하거나 값이 다른 경우만 문제다 |
| 2 | `CLAUDE.md` 규칙 파일 표 ↔ `.claude/rules/*.md`의 `paths` | 표의 "읽히는 때"와 실제 `paths`가 다른 곳, 표에 없는 규칙 파일 |
| 3 | `docs/api/` ↔ `api` 모듈의 `web/**/*Controller.kt` | 경로·HTTP 메서드가 한쪽에만 있거나 다른 곳. `docs/api/`가 없으면 "대상 없음"으로 적는다 |
| 4 | `docs/plan/roadmap.md` ↔ GitHub 이슈 | 이슈 번호가 가리키는 이슈의 제목이 스토리와 다른 곳, 담당과 이슈 담당자(assignees)가 다른 곳, 선행과 이슈 본문의 "선행"이 다른 곳, "미등록"인데 같은 제목의 이슈가 있는 곳 |
| 5 | `docs/plan/stories/` ↔ GitHub 이슈 | 스토리 파일이 있는데 이슈가 닫힌 곳, 파일 머리줄의 이슈 번호가 roadmap과 다른 곳 |

이슈는 `gh issue list --state all --limit 500 --json number,title,state,assignees,body`로 한 번에 받는다. 담당 이름과 GitHub 로그인이 같은 사람인지 확인할 수 없으면 둘 다 적고 질문으로 남긴다.

## 보고

어긋난 곳마다 아래를 적는다.

| 위치 | 유형 | 근거 |
| --- | --- | --- |
| `파일:줄` 또는 `#이슈` (양쪽 모두) | 위 표의 번호와 짧은 이름 | 양쪽 문장을 그대로 인용 |

- 확실하지 않으면 질문으로 쓴다.
- 어긋난 곳이 없으면 "어긋난 곳 없음"과 검사한 대상 번호를 적고 끝낸다.
- 검사하지 못한 대상(예: `gh` 실패)은 따로 적는다. 검사하지 않은 대상을 "문제없음"으로 보고하지 않는다.
- 고치는 방법을 제안할 수는 있지만 파일과 이슈를 고치지 않는다. 계획 문서를 고쳐야 하면 plan-sync를 안내한다.
