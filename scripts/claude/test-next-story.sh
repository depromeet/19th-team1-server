#!/usr/bin/env bash
# next-story.sh 의 고르기 규칙과 session-start.sh 의 조용한 건너뛰기를 가짜 gh·git 으로 검사한다.
# 실행: scripts/claude/test-next-story.sh
set -euo pipefail

here="$(cd "$(dirname "$0")" && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
failures=0

fail() { echo "FAIL: $1" >&2; failures=$((failures + 1)); }
expect_contains() { grep -qF -- "$2" <<<"$1" || fail "$3: '$2' 가 없음"; }
expect_missing() { ! grep -qF -- "$2" <<<"$1" || fail "$3: '$2' 가 있음"; }

cat >"$work/roadmap.md" <<'MD'
# 로드맵

| 스토리 | 제목 | 이슈 | 담당 | 주차 | 선행 | 명세 |
| --- | --- | --- | --- | --- | --- | --- |
| E9-1 | 선행 없음 2주 | #101 | 나 | 2 | — | — |
| E9-2 | 선행 닫힘 1주 | #102 | 나 | 1 | E9-7 | — |
| E9-3 | 선행 열림 | #103 | 나 | 1 | E9-1 | — |
| E9-4 | 남의 이슈 | #104 | 남 | 1 | — | — |
| E9-5 | 닫힌 이슈 | #105 | 나 | 1 | — | — |
| E9-6 | 선행 미등록 | #106 | 나 | 1 | E9-8 | — |
| E9-7 | 닫힌 선행 | #107 | 나 | 1 | — | — |
| E9-8 | 이슈 미등록 | 미등록 | 나 | 1 | — | — |
| E9-9 | 선행 하나만 닫힘 | #109 | 나 | 1 | E9-7, E9-1 | — |
| E9-10 | 열린 PR 있음 | #111 | 나 | 1 | — | — |
| E9-11 | 원격 브랜치만 있음 | #112 | 나 | 1 | — | — |
MD

# 가짜 gh: 인자를 보고 정해진 출력을 낸다. 실제 gh 는 --jq 로 같은 모양을 만든다.
# EXPECT_ROOT 가 있으면 그 디렉터리에서 실행될 때만 응답한다 (레포 기준 실행 확인).
mkdir -p "$work/bin"
cat >"$work/bin/gh" <<'SH'
#!/usr/bin/env bash
if [ -n "${EXPECT_ROOT:-}" ] && [ "$PWD" != "$EXPECT_ROOT" ]; then echo "레포 밖에서 실행됨: $PWD" >&2; exit 1; fi
case "$1 $2" in
  "auth status") [ "${FAKE_GH_LOGGED_OUT:-}" = 1 ] && exit 1; exit 0 ;;
  "api user") echo me ;;
  "issue list")
    printf '%s\t%s\t%s\t%s\n' \
      101 OPEN me "선행 없음 2주" \
      102 OPEN me "선행 닫힘 1주" \
      103 OPEN me "선행 열림" \
      104 OPEN other "남의 이슈" \
      105 CLOSED me "닫힌 이슈" \
      106 OPEN me "선행 미등록" \
      107 CLOSED me "닫힌 선행" \
      109 OPEN "me,other" "선행 하나만 닫힘" \
      110 OPEN me "로드맵에 없는 이슈" \
      111 OPEN me "열린 PR 있음" \
      112 OPEN me "원격 브랜치만 있음" ;;
  "pr list") printf '%s\n' feat/111-open-pr main ;;
  *) exit 1 ;;
esac
SH
chmod +x "$work/bin/gh"

# 가짜 git: 원격 브랜치 조회만 흉내 내고 나머지는 실제 git 에 넘긴다.
#   FAKE_GIT_OFFLINE=1  ls-remote 실패 (로컬 origin/* 참조로 대신해야 함)
#   FAKE_GIT_NOREFS=1   로컬 참조 조회도 실패 (원격 브랜치 검사를 건너뛰어야 함)
real_git="$(command -v git)"
cat >"$work/bin/git" <<SH
#!/usr/bin/env bash
case "\$1" in
  ls-remote)
    [ "\${FAKE_GIT_OFFLINE:-}" = 1 ] && exit 128
    printf 'abc\trefs/heads/%s\n' main develop feat/112-remote-only ;;
  for-each-ref)
    [ "\${FAKE_GIT_NOREFS:-}" = 1 ] && exit 128
    printf '%s\n' origin/HEAD origin/main origin/feat/112-remote-only ;;
  *) exec "$real_git" "\$@" ;;
esac
SH
chmod +x "$work/bin/git"

# --- next-story.sh: 고르기 규칙
out="$(PATH="$work/bin:$PATH" ROADMAP="$work/roadmap.md" "$here/next-story.sh")"
full="$out"
candidates="$(sed -n '/^착수 가능한 스토리/,$p' <<<"$out")"

expect_contains "$candidates" "E9-1" "선행 없는 내 열린 스토리"
expect_contains "$candidates" "E9-2" "선행이 닫힌 스토리"
expect_missing "$candidates" "E9-3" "선행이 열린 스토리"
expect_missing "$candidates" "E9-4" "남의 스토리"
expect_missing "$candidates" "E9-5" "닫힌 스토리"
expect_missing "$candidates" "E9-6" "선행 이슈가 미등록인 스토리"
expect_missing "$candidates" "E9-8" "이슈가 미등록인 스토리"
expect_missing "$candidates" "E9-9" "선행 중 하나가 열린 스토리"
expect_missing "$candidates" "E9-10" "열린 PR 이 있는 스토리"
expect_missing "$candidates" "E9-11" "원격 브랜치가 있는 스토리"

progress="$(sed -n '/^진행 중/,/^$/p' <<<"$out")"
expect_contains "$progress" "E9-10" "열린 PR 이 있는 스토리는 진행 중"
expect_contains "$progress" "E9-11" "원격 브랜치가 있는 스토리는 진행 중"
expect_missing "$progress" "E9-1 " "브랜치·PR 없는 스토리는 진행 중이 아니다"

# 네트워크 실패: 로컬 origin/* 참조로 대신한다
out="$(PATH="$work/bin:$PATH" FAKE_GIT_OFFLINE=1 ROADMAP="$work/roadmap.md" "$here/next-story.sh")"
progress="$(sed -n '/^진행 중/,/^$/p' <<<"$out")"
expect_contains "$progress" "E9-11" "ls-remote 실패 시 로컬 참조로 진행 중 판단"

# 원격 브랜치 조회가 모두 실패: 그 검사만 건너뛰고 나머지는 그대로
set +e
out="$(PATH="$work/bin:$PATH" FAKE_GIT_OFFLINE=1 FAKE_GIT_NOREFS=1 ROADMAP="$work/roadmap.md" "$here/next-story.sh" 2>&1)"; code=$?
set -e
[ "$code" = 0 ] || fail "원격 브랜치 조회 실패 시 종료 코드: $code"
candidates="$(sed -n '/^착수 가능한 스토리/,$p' <<<"$out")"
expect_contains "$candidates" "E9-11" "원격 브랜치 검사를 건너뛰면 후보에 남는다"
expect_missing "$candidates" "E9-10" "PR 검사는 그대로 적용"

# 레포 밖에서 실행해도 gh 는 레포 디렉터리에서 실행된다
repo="$(cd "$here/../.." && pwd)"
set +e
out="$(cd / && PATH="$work/bin:$PATH" EXPECT_ROOT="$repo" ROADMAP="$work/roadmap.md" "$here/next-story.sh" 2>&1)"; code=$?
set -e
[ "$code" = 0 ] || fail "레포 밖에서 실행: code=$code out='$out'"
first="$(grep -m1 '^- E' <<<"$candidates" || true)"
[[ "$first" == "- E9-2"* ]] || fail "주차가 이른 스토리가 먼저: 첫 줄이 '$first'"

mine="$(sed -n '/^내 열린 이슈/,/^$/p' <<<"$full")"
expect_contains "$mine" "#110" "로드맵에 없는 내 열린 이슈도 보인다"
expect_contains "$mine" "#103" "선행이 열린 내 이슈도 보인다"
expect_missing "$mine" "#104" "남의 이슈"
expect_missing "$mine" "#105" "닫힌 이슈"

# --- next-story.sh --brief: 내 열린 이슈는 개수만, 스토리는 3개까지
out="$(PATH="$work/bin:$PATH" ROADMAP="$work/roadmap.md" "$here/next-story.sh" --brief)"
expect_contains "$out" "내 열린 이슈 8개" "brief 의 열린 이슈 개수"
expect_contains "$out" "진행 중 (브랜치·PR 있음) 2개" "brief 의 진행 중 개수"
expect_missing "$out" "E9-10" "brief 에는 진행 중 목록이 없다"
expect_missing "$out" "#110" "brief 에는 이슈 목록이 없다"
expect_contains "$out" "E9-2" "brief 의 다음 스토리"

# --- session-start.sh: 정상, gh 없음, 로그인 전, gh 실패
out="$(PATH="$work/bin:$PATH" ROADMAP="$work/roadmap.md" "$here/session-start.sh")"
expect_contains "$out" "E9-2" "세션 시작 요약에 다음 스토리"
expect_missing "$out" "#110" "세션 시작 요약은 짧게"

nogh="$work/nogh"
mkdir -p "$nogh"
for cmd in bash env dirname sed awk grep sort head cat mktemp rm; do
  ln -s "$(command -v "$cmd")" "$nogh/$cmd"
done
set +e
out="$(PATH="$nogh" ROADMAP="$work/roadmap.md" "$nogh/bash" "$here/session-start.sh" 2>&1)"; code=$?
set -e
[ "$code" = 0 ] && [ -z "$out" ] || fail "gh 없음: code=$code out='$out'"

set +e
out="$(PATH="$work/bin:$PATH" FAKE_GH_LOGGED_OUT=1 ROADMAP="$work/roadmap.md" "$here/session-start.sh" 2>&1)"; code=$?
set -e
[ "$code" = 0 ] && [ -z "$out" ] || fail "로그인 전: code=$code out='$out'"

set +e
out="$(PATH="$work/bin:$PATH" ROADMAP="$work/없는-파일.md" "$here/session-start.sh" 2>&1)"; code=$?
set -e
[ "$code" = 0 ] && [ -z "$out" ] || fail "roadmap 읽기 실패: code=$code out='$out'"

if [ "$failures" -gt 0 ]; then
  echo "$failures 건 실패" >&2
  exit 1
fi
echo "모두 통과"
