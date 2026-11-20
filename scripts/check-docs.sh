#!/usr/bin/env bash
# 문서 정합성 중 기계적으로 확인할 수 있는 것을 검사한다. 읽고 판단해야 하는 정합성은 doc-check 스킬이 다룬다.
#
#   scripts/check-docs.sh       레포 검사: 문서 안 상대 링크, roadmap 이슈 번호, 지운 표기
#   scripts/check-docs.sh pr    PR 검사: 본문(PR_BODY 환경변수)의 Closes #N·Refs #N, 닫는 이슈의 스토리 파일이 남았는지
#
# 실패하면 1로 끝난다. gh 가 없거나 조회에 실패하면 roadmap 이슈 번호 검사만 건너뛰고 건너뛰었다고 출력한다.
# 실행하는 곳: git pre-push 훅, Claude Code 커밋 직전 훅, CI(docs 잡), Convention 워크플로(PR 검사)
# 테스트: scripts/test-check-docs.sh
set -euo pipefail

cd "$(dirname "$0")/.."

# 지운 표기: "정규식(대소문자 무시)|안내". 추적 중인 모든 텍스트 파일에서 찾는다.
REMOVED_TERMS=(
  "mysql|PostgreSQL 로 전환했다 (#23)"
)
# 의도적으로 남긴 곳: "파일 경로|줄 정규식". 줄 정규식이 비어 있으면 파일 전체를 뺀다.
REMOVED_TERM_EXCEPTIONS=(
  "docs/plan/roadmap.md|^\| E1-1 \|"
  "scripts/check-docs.sh|"
  "scripts/test-check-docs.sh|"
)

failures=0
report() { echo "[$1] $2"; failures=$((failures + 1)); }

check_links() {
  local file target path
  while IFS= read -r file; do
    # 코드 블록과 인라인 코드 안의 링크는 예시이므로 뺀다. 출력: "줄번호<TAB>대상"
    while IFS=$'\t' read -r line target; do
      case "$target" in
        "" | \#* | *:*) continue ;; # 앵커, http·mailto 같은 주소
      esac
      path="${target%%#*}"
      if [[ "$path" == /* ]]; then path=".${path}"; else path="$(dirname "$file")/$path"; fi
      [ -e "$path" ] || report "깨진 링크" "$file:$line → $target"
    done < <(awk '
      /^[ \t]*(```|~~~)/ { fence = !fence; next }
      fence { next }
      {
        s = $0
        gsub(/`[^`]*`/, "", s)
        while (match(s, /\]\([^)]*\)/)) {
          t = substr(s, RSTART + 2, RLENGTH - 3)
          sub(/[ \t].*$/, "", t)   # 링크 제목("...") 떼기
          print NR "\t" t
          s = substr(s, RSTART + RLENGTH)
        }
      }' "$file")
  done < <(git ls-files '*.md')
}

check_roadmap_issues() {
  local roadmap=docs/plan/roadmap.md issues n
  [ -f "$roadmap" ] || return 0
  if ! command -v gh >/dev/null 2>&1 || ! issues="$(gh issue list --state all --limit 1000 --json number --jq '.[].number' 2>/dev/null)"; then
    echo "[건너뜀] roadmap 이슈 번호: gh 가 없거나 이슈를 조회하지 못함"
    return 0
  fi
  while IFS=$'\t' read -r line n; do
    grep -qx "$n" <<<"$issues" || report "없는 이슈" "$roadmap:$line → #$n"
  done < <(awk -F'|' '/^\| E[0-9]+-[0-9]+ \|/ { gsub(/[ #]/, "", $4); if ($4 ~ /^[0-9]+$/) print NR "\t" $4 }' "$roadmap")
}

is_exception() { # is_exception <파일> <줄 내용>
  local entry path pattern
  for entry in "${REMOVED_TERM_EXCEPTIONS[@]}"; do
    path="${entry%%|*}"; pattern="${entry#*|}"
    [ "$1" = "$path" ] || continue
    [ -z "$pattern" ] && return 0
    grep -qE -- "$pattern" <<<"$2" && return 0
  done
  return 1
}

check_removed_terms() {
  local entry pattern note file line text
  for entry in "${REMOVED_TERMS[@]}"; do
    pattern="${entry%%|*}"; note="${entry#*|}"
    # 출력: 파일:줄:내용. 내용에 ':' 가 있어도 앞의 두 칸만 자른다.
    while IFS= read -r hit; do
      file="${hit%%:*}"; hit="${hit#*:}"; line="${hit%%:*}"; text="${hit#*:}"
      is_exception "$file" "$text" && continue
      report "지운 표기" "$file:$line → '$pattern' ($note)"
    done < <(git grep -n -I -i -E -- "$pattern" || true)
  done
}

check_pr() {
  local body="${PR_BODY:-}" n file
  if ! grep -qiE '(^|[^[:alnum:]_])(closes|refs) #[0-9]+' <<<"$body"; then
    report "PR 본문" "Closes #이슈번호 또는 Refs #이슈번호 가 없음 (docs/conventions.md 4절)"
    return
  fi
  # Closes 로 닫는 이슈의 스토리 파일은 그 PR 에서 지운다 (docs/plan/roadmap.md 진행 방법 4번)
  for n in $(grep -oiE '(^|[^[:alnum:]_])closes #[0-9]+' <<<"$body" | grep -oE '[0-9]+$' | sort -u); do
    for file in docs/plan/stories/*.md; do
      [ -f "$file" ] && [ "$(basename "$file")" != _template.md ] || continue
      if grep -qE "^이슈: #$n( |$)" "$file"; then report "스토리 파일" "$file 이 남아 있음 (Closes #$n)"; fi
    done
  done
}

case "${1:-}" in
  "") check_links; check_roadmap_issues; check_removed_terms ;;
  pr) check_pr ;;
  *) echo "사용법: $0 [pr]" >&2; exit 2 ;;
esac

if [ "$failures" -gt 0 ]; then
  echo "문서 검사 실패 ${failures}건"
  exit 1
fi
echo "문서 검사 통과"
