#!/usr/bin/env bash
# Claude Code SessionStart 훅(.claude/settings.json). 현재 브랜치의 이슈, 내 열린 이슈 수, 다음 스토리를 짧게 출력하면
# Claude Code 가 세션 컨텍스트에 넣는다. gh 가 없거나, 로그인 전이거나, 조회에 실패하면 아무것도 출력하지 않고
# 0으로 끝난다. 세션을 막지 않는다.
here="$(cd "$(dirname "$0")" && pwd)"

command -v gh >/dev/null 2>&1 || exit 0
gh auth status >/dev/null 2>&1 || exit 0
summary="$("$here/next-story.sh" --brief 2>/dev/null)" || exit 0

echo "[일감 요약] 일감은 docs/plan/roadmap.md \"진행 방법\"을 따른다. 착수할 때 next-story 스킬을 쓴다."
branch="$(git -C "$here" symbolic-ref --short -q HEAD 2>/dev/null)"
if issue="$("$here/../check-convention.sh" issue-from-branch "$branch" 2>/dev/null)"; then
  echo "현재 브랜치 $branch → #$issue"
fi
echo "$summary"
exit 0
