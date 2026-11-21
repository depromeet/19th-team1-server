#!/usr/bin/env bash
# cleanup-images.sh 가 남기고 지우는 버전을 가짜 버전 목록과 가짜 gh 로 검사한다.
# 실행: scripts/ci/test-cleanup-images.sh
set -euo pipefail

script="$(cd "$(dirname "$0")" && pwd)/cleanup-images.sh"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
failures=0

fail() { echo "FAIL: $1" >&2; failures=$((failures + 1)); }

# 지울 버전 ID 를 공백으로 구분해 돌려준다.
deleted_ids() {
  VERSIONS_JSON="$1" KEEP="${2:-5}" DRY_RUN=true "$script" | sed -n 's/^| 지움 | \([0-9]*\) .*/\1/p' | sort -n | tr '\n' ' ' | sed 's/ $//'
}
expect_deleted() {
  local actual
  actual=$(deleted_ids "$1" "$3")
  [[ "$actual" == "$2" ]] || fail "$4: 지울 버전이 '$2' 여야 하는데 '$actual'"
}

# 1. main 이력 7개(버전 1~7, 7이 최신) → 오래된 1, 2 를 지운다. fp- 같은 다른 태그는 순서에 쓰지 않는다.
cat >"$work/seven.json" <<'JSON'
[
  {"id":1,"tags":["fp-a","main-20261101-000000-aaaaaaa"]},
  {"id":2,"tags":["fp-b","main-20261102-000000-bbbbbbb"]},
  {"id":3,"tags":["fp-c","main-20261103-000000-ccccccc"]},
  {"id":4,"tags":["fp-d","main-20261104-000000-ddddddd"]},
  {"id":5,"tags":["fp-e","main-20261105-000000-eeeeeee"]},
  {"id":6,"tags":["fp-f","main-20261106-000000-fffffff"]},
  {"id":7,"tags":["fp-g","main-20261107-000000-1111111","main"]}
]
JSON
expect_deleted "$work/seven.json" "1 2" 5 "main 최근 5개"

# 2. 태그만 옮긴 푸시로 한 버전에 이력 태그가 여러 개 → 1개로 센다.
#    최신순 이력: 4, 1, 1, 3, 2, 1. 버전으로 세면 최근 3개는 4, 1, 3 이라 2 만 지운다(태그로 세면 3 도 지워진다).
cat >"$work/retag.json" <<'JSON'
[
  {"id":1,"tags":["fp-a","main-20261101-000000-aaaaaaa","main-20261110-000000-aaaaaa2","main-20261111-000000-aaaaaa3"]},
  {"id":2,"tags":["fp-b","main-20261102-000000-bbbbbbb"]},
  {"id":3,"tags":["fp-c","main-20261103-000000-ccccccc"]},
  {"id":4,"tags":["fp-d","main-20261112-000000-ddddddd","main"]}
]
JSON
expect_deleted "$work/retag.json" "2" 3 "같은 버전의 이력 태그는 1개로 셈"

# 3. 두 브랜치가 같은 버전을 씀 → 한쪽에라도 남으면 지우지 않는다.
#    main 최근 2: 5, 3 / develop 최근 2: 4, 1 → 2 만 지운다. 버전 3 은 develop 에선 오래됐지만 main 쪽에 남는다.
cat >"$work/shared.json" <<'JSON'
[
  {"id":1,"tags":["fp-a","develop-20261105-000000-aaaaaaa"]},
  {"id":2,"tags":["fp-b","main-20261101-000000-bbbbbbb","develop-20261101-000000-bbbbbbb"]},
  {"id":3,"tags":["fp-c","develop-20261102-000000-ccccccc","main-20261103-000000-ccccccc"]},
  {"id":4,"tags":["fp-d","develop-20261106-000000-ddddddd","develop"]},
  {"id":5,"tags":["fp-e","main-20261104-000000-eeeeeee","main"]}
]
JSON
expect_deleted "$work/shared.json" "2" 2 "두 브랜치 합집합"

# 4. 지금 브랜치 태그가 붙은 버전은 이력이 오래돼도 남긴다. 태그 없는 버전은 지운다.
cat >"$work/branch-tag.json" <<'JSON'
[
  {"id":1,"tags":["fp-a","develop-20261101-000000-aaaaaaa","develop"]},
  {"id":2,"tags":["fp-b","develop-20261102-000000-bbbbbbb"]},
  {"id":3,"tags":["fp-c","develop-20261103-000000-ccccccc"]},
  {"id":4,"tags":[]}
]
JSON
expect_deleted "$work/branch-tag.json" "2 4" 1 "브랜치 태그와 태그 없는 버전"

# 5. 버전이 없으면 아무것도 하지 않는다.
echo '[]' >"$work/empty.json"
expect_deleted "$work/empty.json" "" 5 "빈 목록"

# 6. 버전은 있는데 남길 것이 없다고 계산되면 지우지 않고 실패한다.
echo '[{"id":1,"tags":["fp-a"]},{"id":2,"tags":[]}]' >"$work/none.json"
if VERSIONS_JSON="$work/none.json" DRY_RUN=true "$script" >/dev/null 2>&1; then
  fail "남길 것이 없으면 실패해야 함"
fi

# 7. DRY_RUN=false 면 지울 버전마다 DELETE 를 부른다. DRY_RUN 이 기본(true)이면 부르지 않는다.
mkdir -p "$work/bin"
cat >"$work/bin/gh" <<SH
#!/usr/bin/env bash
echo "\$*" >>"$work/gh-calls.txt"
SH
chmod +x "$work/bin/gh"
PATH="$work/bin:$PATH" VERSIONS_JSON="$work/seven.json" DRY_RUN=false "$script" >/dev/null
calls=$(sort "$work/gh-calls.txt" | tr '\n' ';')
[[ "$calls" == "api --method DELETE orgs/depromeet/packages/container/19th-team1-server/versions/1;api --method DELETE orgs/depromeet/packages/container/19th-team1-server/versions/2;" ]] \
  || fail "DELETE 호출이 틀림: $calls"
: >"$work/gh-calls.txt"
PATH="$work/bin:$PATH" VERSIONS_JSON="$work/seven.json" "$script" >/dev/null
[[ ! -s "$work/gh-calls.txt" ]] || fail "기본값(DRY_RUN)인데 gh 를 부름"

if ((failures > 0)); then
  echo "$failures 개 실패" >&2
  exit 1
fi
echo "cleanup-images: 모두 통과"
