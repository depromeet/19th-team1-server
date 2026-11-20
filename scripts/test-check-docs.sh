#!/usr/bin/env bash
# check-docs.sh 와 Claude Code 커밋 직전 훅(scripts/claude/pre-commit-docs.sh)을 임시 레포와 가짜 gh 로 검사한다.
# 실행: scripts/test-check-docs.sh
set -euo pipefail

src="$(cd "$(dirname "$0")/.." && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
failures=0

fail() { echo "FAIL: $1" >&2; failures=$((failures + 1)); }
expect_contains() { grep -qF -- "$2" <<<"$1" || fail "$3: '$2' 가 없음"; }
expect_missing() { ! grep -qF -- "$2" <<<"$1" || fail "$3: '$2' 가 있음"; }

# 가짜 gh: 이슈 1, 2 만 있다. PR 번호(3)는 이슈 목록에 없다.
mkdir -p "$work/bin"
cat >"$work/bin/gh" <<'SH'
#!/usr/bin/env bash
[ "$1 $2" = "issue list" ] && printf '%s\n' 1 2 && exit 0
exit 1
SH
chmod +x "$work/bin/gh"

# gh 가 없는 PATH
mkdir -p "$work/nogh"
for cmd in bash env dirname basename git awk sed grep sort uniq cat mktemp rm tr cut head wc printf; do
  path="$(command -v "$cmd" 2>/dev/null)" && [ -x "$path" ] && ln -sf "$path" "$work/nogh/$cmd"
done

# 검사를 통과하는 레포를 만든다.
new_repo() {
  repo="$work/repo-$1"
  mkdir -p "$repo/scripts/claude" "$repo/docs/plan/stories"
  cp "$src/scripts/check-docs.sh" "$repo/scripts/"
  cp "$src/scripts/claude/pre-commit-docs.sh" "$repo/scripts/claude/"
  cat >"$repo/README.md" <<'MD'
[문서](docs/a.md), [절](docs/a.md#절), [외부](https://example.com), [앞부분](#제목), [폴더](docs/plan/)

`[코드 안](없는-파일.md)`

```markdown
[코드 블록 안](없는-파일.md)
```
MD
  echo "# A" >"$repo/docs/a.md"
  cat >"$repo/docs/plan/roadmap.md" <<'MD'
| 스토리 | 제목 | 이슈 | 담당 | 주차 | 선행 | 명세 |
| --- | --- | --- | --- | --- | --- | --- |
| E1-1 | MySQL에서 PostgreSQL 16으로 전환 | #1 | 나 | 1 | — | — |
| E1-2 | 미등록 스토리 | 미등록 | 나 | 1 | — | — |
MD
  cp "$src/docs/plan/stories/_template.md" "$repo/docs/plan/stories/"
  git -C "$repo" init -q
  git -C "$repo" add -A
}

run() { # run <레포> [인자...]  → out, code
  local repo="$1"; shift
  set +e
  out="$(cd "$repo" && PATH="$work/bin:$PATH" scripts/check-docs.sh "$@" 2>&1)"; code=$?
  set -e
}

# --- 통과: 예외로 둔 표기, 코드 안 링크, 외부·앵커·폴더 링크
new_repo ok
run "$repo"
[ "$code" = 0 ] || fail "통과해야 하는 레포: code=$code out='$out'"

# --- 깨진 상대 링크
new_repo link
echo "[없음](docs/없는-문서.md)" >>"$repo/docs/a.md"
git -C "$repo" add -A
run "$repo"
[ "$code" = 1 ] || fail "깨진 링크: code=$code"
expect_contains "$out" "docs/a.md" "깨진 링크 위치"
expect_contains "$out" "docs/없는-문서.md" "깨진 링크 대상"

# --- roadmap 의 없는 이슈 번호 (PR 번호 포함)
new_repo issue
echo "| E1-3 | 없는 이슈 | #99 | 나 | 1 | — | — |" >>"$repo/docs/plan/roadmap.md"
echo "| E1-4 | PR 번호 | #3 | 나 | 1 | — | — |" >>"$repo/docs/plan/roadmap.md"
git -C "$repo" add -A
run "$repo"
[ "$code" = 1 ] || fail "없는 이슈 번호: code=$code"
expect_contains "$out" "#99" "없는 이슈 번호"
expect_contains "$out" "#3" "PR 번호는 이슈가 아니다"

# --- gh 가 없으면 이슈 검사만 건너뛴다
set +e
out="$(cd "$repo" && PATH="$work/nogh" bash scripts/check-docs.sh 2>&1)"; code=$?
set -e
[ "$code" = 0 ] || fail "gh 없음: code=$code out='$out'"
expect_contains "$out" "건너뜀" "gh 없음은 건너뛰었다고 출력"

# --- 지운 표기
new_repo term
echo "로컬 DB는 MySQL 이다" >>"$repo/docs/a.md"
mkdir -p "$repo/infra/storage/mysql" && echo "package storage.mysql" >"$repo/infra/storage/mysql/A.kt"
git -C "$repo" add -A
run "$repo"
[ "$code" = 1 ] || fail "지운 표기: code=$code"
expect_contains "$out" "docs/a.md" "지운 표기 위치(문서)"
expect_contains "$out" "infra/storage/mysql/A.kt" "지운 표기 위치(코드)"
expect_missing "$out" "roadmap.md" "예외로 둔 roadmap 스토리 제목"

# --- PR: Closes·Refs 없음
new_repo pr
PR_BODY="요약만 있음" run "$repo" pr
[ "$code" = 1 ] || fail "Closes·Refs 없음: code=$code"
expect_contains "$out" "Closes" "Closes·Refs 없음 안내"

# --- PR: Closes 인데 스토리 파일이 남음
printf '# E1-1 제목\n\n이슈: #1 · 에픽: E1 · 담당: 나 · 명세: —\n' >"$repo/docs/plan/stories/E1-1-x.md"
git -C "$repo" add -A
PR_BODY=$'요약\n\nCloses #1' run "$repo" pr
[ "$code" = 1 ] || fail "스토리 파일 남음: code=$code"
expect_contains "$out" "docs/plan/stories/E1-1-x.md" "남은 스토리 파일"

# --- PR: Refs 는 스토리 파일이 남아도 된다
PR_BODY="Refs #1" run "$repo" pr
[ "$code" = 0 ] || fail "Refs 와 스토리 파일: code=$code out='$out'"

# --- PR: 스토리 파일을 지우면 통과. 번호가 앞부분만 같은 이슈(#10)와 헷갈리지 않는다
git -C "$repo" rm -q -f docs/plan/stories/E1-1-x.md
printf '# E1-9 제목\n\n이슈: #10 · 에픽: E1 · 담당: 나 · 명세: —\n' >"$repo/docs/plan/stories/E1-9-y.md"
git -C "$repo" add -A
PR_BODY="Closes #1" run "$repo" pr
[ "$code" = 0 ] || fail "스토리 파일 지움: code=$code out='$out'"

# --- Claude Code 커밋 직전 훅
hook() { # hook <레포> <stdin>  → out, code
  set +e
  out="$(cd / && PATH="$work/bin:$PATH" CLAUDE_PROJECT_DIR="$1" "$1/scripts/claude/pre-commit-docs.sh" <<<"$2" 2>&1)"; code=$?
  set -e
}
broken="$work/repo-link"
hook "$broken" '{"tool_name":"Bash","tool_input":{"command":"ls -al"}}'
[ "$code" = 0 ] && [ -z "$out" ] || fail "커밋이 아닌 명령은 그냥 통과: code=$code out='$out'"
hook "$broken" '{"tool_name":"Bash","tool_input":{"command":"git add -A && git commit -m \"x\""}}'
[ "$code" = 2 ] || fail "검사 실패 시 커밋을 막는다(종료 코드 2): code=$code"
expect_contains "$out" "docs/없는-문서.md" "막은 이유를 보여 준다"
hook "$work/repo-ok" '{"tool_name":"Bash","tool_input":{"command":"git commit -m x"}}'
[ "$code" = 0 ] || fail "검사 통과 시 커밋 허용: code=$code out='$out'"

if [ "$failures" -gt 0 ]; then
  echo "$failures 건 실패" >&2
  exit 1
fi
echo "모두 통과"
