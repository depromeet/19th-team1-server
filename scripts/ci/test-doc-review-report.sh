#!/usr/bin/env bash
# doc-review-report.sh 가 결과 JSON 을 마크다운으로 바꾸고 BLOCKING 에 따라 성공·실패하는지 검사한다.
# 실행: scripts/ci/test-doc-review-report.sh
set -euo pipefail

script="$(cd "$(dirname "$0")" && pwd)/doc-review-report.sh"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
failures=0

fail() { echo "FAIL: $1" >&2; failures=$((failures + 1)); }

# run <결과 파일> <BLOCKING> → 출력은 $work/out.md, 종료 코드는 $code
run() {
  code=0
  BLOCKING="$2" "$script" "$1" > "$work/out.md" 2>&1 || code=$?
}
has() { grep -qF -- "$1" "$work/out.md"; }

cat > "$work/found.json" << 'JSON'
{"status":"fail","findings":[
  {"severity":"question","doc":{"file":"docs/a.md","line":3,"quote":"질문"},"code":{"file":"b.kt","line":4,"quote":"x"},"reason":"확실하지 않음"},
  {"severity":"error","doc":{"file":"docs/ci.md","line":93,"quote":"이미지 5개 | 남긴다\n둘째 줄"},"code":{"file":".github/workflows/ci.yml","line":185,"quote":"KEEP: \"3\""},"reason":"개수가 다르다"}
]}
JSON
echo '{"status":"fail","findings":[{"severity":"question","doc":{"file":"a","line":1,"quote":"q"},"code":{"file":"b","line":2,"quote":"c"},"reason":"r"}]}' > "$work/question.json"
echo '{"status":"pass","findings":[]}' > "$work/pass.json"
echo 'not json' > "$work/broken.json"

# 1. 첫 줄은 댓글을 찾는 표시
run "$work/pass.json" false
[[ "$(head -1 "$work/out.md")" == "<!-- doc-review -->" ]] || fail "첫 줄이 댓글 표시가 아님"

# 2. 어긋난 곳이 없으면 그렇게 적고 성공
has "어긋난 곳 없음." || fail "통과 결과 문구가 없음"
((code == 0)) || fail "통과인데 실패함"

# 3. 지적이 있으면 표로 적는다. 어긋남이 먼저, | 와 줄바꿈은 표를 깨지 않게 바꾼다
run "$work/found.json" false
has "어긋남 1개, 확인 필요 1개" || fail "개수 줄이 틀림"
# shellcheck disable=SC2016 # 백틱은 마크다운의 코드 표시라 그대로 비교한다
has '| 어긋남 | `docs/ci.md:93` 이미지 5개 \| 남긴다 둘째 줄 | `.github/workflows/ci.yml:185` KEEP: "3" | 개수가 다르다 |' || fail "어긋남 행이 틀림: $(cat "$work/out.md")"
first=$(grep -m1 -E '^\| (어긋남|확인 필요) \|' "$work/out.md")
[[ "$first" == "| 어긋남 |"* ]] || fail "어긋남이 먼저 나오지 않음"

# 4. BLOCKING=false 면 어긋남이 있어도 성공
((code == 0)) || fail "BLOCKING=false 인데 실패함"
has "결과만 남기고 머지를 막지 않는다." || fail "막지 않는다는 문구가 없음"

# 5. BLOCKING=true 면 어긋남이 있을 때 실패, 확인 필요만 있으면 성공
run "$work/found.json" true
((code == 1)) || fail "BLOCKING=true 이고 어긋남이 있는데 성공함"
run "$work/question.json" true
((code == 0)) || fail "BLOCKING=true 이고 확인 필요만 있는데 실패함"

# 6. 결과가 없거나 깨졌으면 "검사 못 함"으로 성공 (BLOCKING 이어도 막지 않는다)
for f in "$work/broken.json" "$work/missing.json"; do
  run "$f" true
  has "검사 못 함" || fail "$(basename "$f"): 검사 못 함 문구가 없음"
  ((code == 0)) || fail "$(basename "$f"): 결과가 없는데 실패함"
done

if ((failures > 0)); then
  echo "$failures 개 실패" >&2
  exit 1
fi
echo "doc-review-report: 모두 통과"
