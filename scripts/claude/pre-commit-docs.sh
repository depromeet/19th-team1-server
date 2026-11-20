#!/usr/bin/env bash
# Claude Code 커밋 직전 훅(.claude/settings.json 의 PreToolUse, Bash). 실행하려는 명령에 `git commit` 이 있으면
# scripts/check-docs.sh 를 돌리고, 실패하면 종료 코드 2로 커밋을 막는다. 막은 이유(stderr)는 Claude 에게 전달된다.
# 그 밖의 명령은 아무것도 하지 않고 통과시킨다.
# 검사 대상은 세션을 시작한 레포($CLAUDE_PROJECT_DIR)다. 다른 워크트리에서 커밋해도 그 워크트리를 검사하지 않는다.
input="$(cat)"
grep -q 'git commit' <<<"$input" || exit 0

root="${CLAUDE_PROJECT_DIR:-$(cd "$(dirname "$0")/../.." && pwd)}"
if ! result="$("$root/scripts/check-docs.sh" 2>&1)"; then
  printf '%s\n문서 검사가 실패해 커밋을 막았다. 위 항목을 고친 뒤 다시 커밋한다.\n' "$result" >&2
  exit 2
fi
exit 0
