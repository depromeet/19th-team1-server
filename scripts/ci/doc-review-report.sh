#!/usr/bin/env bash
# Codex 문서 정합성 검사 결과(JSON)를 PR 댓글·실행 요약용 마크다운으로 바꾼다. .github/workflows/doc-review.yml 이 실행한다.
# 결과 형식은 .github/codex/doc-review.schema.json 이다. 규칙과 이유는 docs/ci.md 5장에 있다.
#
# 사용: scripts/ci/doc-review-report.sh <결과 JSON 파일>
# 환경변수
#   BLOCKING  true 면 어긋남(error)이 있을 때 실패한다. 기본 false (결과만 남긴다)
set -euo pipefail

result="$1"
BLOCKING=${BLOCKING:-false}

# 첫 줄은 PR 댓글을 찾아 고쳐 쓰는 표시다.
echo "<!-- doc-review -->"
echo "## 문서 정합성 검사 (Codex)"
echo

if ! jq -e '(.status | IN("pass", "fail")) and (.findings | type == "array")' "$result" > /dev/null 2>&1; then
  echo "검사 못 함: 결과가 없다. 실행 로그를 확인한다 (시크릿 없음, 로그인 만료, Codex 실패 등)."
  exit 0
fi

errors=$(jq '[.findings[] | select(.severity == "error")] | length' "$result")
questions=$(jq '[.findings[] | select(.severity == "question")] | length' "$result")

if ((errors + questions == 0)); then
  echo "어긋난 곳 없음."
else
  echo "어긋남 ${errors}개, 확인 필요 ${questions}개"
  echo
  echo "| 구분 | 문서 | 코드 | 이유 |"
  echo "| --- | --- | --- | --- |"
  # 표 칸이 깨지지 않게 | 와 줄바꿈을 바꾼다.
  jq -r '
    def cell: gsub("\\|"; "\\|") | gsub("\r?\n"; " ");
    def loc: "`\(.file):\(.line)` \(.quote | cell)";
    .findings
    | sort_by(.severity != "error")[]
    | "| \(if .severity == "error" then "어긋남" else "확인 필요" end) | \(.doc | loc) | \(.code | loc) | \(.reason | cell) |"
  ' "$result"
fi

echo
if [[ "$BLOCKING" == "true" ]]; then
  echo "어긋남이 있으면 이 검사가 실패한다."
  ((errors == 0)) || exit 1
else
  echo "결과만 남기고 머지를 막지 않는다."
fi
