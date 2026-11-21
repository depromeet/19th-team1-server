#!/usr/bin/env bash
# GHCR 서버 이미지를 브랜치(main, develop)별로 최근 KEEP 개만 남기고 나머지 버전을 지운다. CI 의 image 잡 마지막 단계가 실행한다.
# 규칙과 이유는 docs/ci.md 4장에 있다.
#
# 남기는 버전
#   - 브랜치 이력 태그(<브랜치>-<UTC yyyymmdd-HHMMSS>-<커밋 7자리>)를 최신순으로 훑어 나온 서로 다른 버전 KEEP 개. 브랜치마다 따로 센다.
#     같은 버전에 이력 태그가 여러 개 붙어 있으면(태그만 옮긴 푸시) 1개로 센다.
#   - 지금 브랜치 태그(main, develop)가 붙은 버전
#   - 두 브랜치 중 한쪽에라도 남는 버전은 지우지 않는다.
# 남길 버전이 하나도 없는데 버전이 있으면 아무것도 지우지 않고 실패한다.
#
# 환경변수
#   DRY_RUN        true 면 지울 목록만 출력한다. 기본 true
#   KEEP           브랜치마다 남길 수. 기본 5
#   OWNER          패키지를 가진 조직. 기본 depromeet
#   PACKAGE        패키지 이름. 기본 19th-team1-server
#   GH_TOKEN       패키지 읽기·지우기 권한이 있는 토큰 (CI 는 packages: write 의 GITHUB_TOKEN)
#   VERSIONS_JSON  [{"id":1,"tags":["main",...]}, ...] 형식의 파일. 있으면 GHCR 대신 이 목록을 읽는다 (테스트용)
set -euo pipefail

DRY_RUN=${DRY_RUN:-true}
KEEP=${KEEP:-5}
OWNER=${OWNER:-depromeet}
PACKAGE=${PACKAGE:-19th-team1-server}
BRANCHES='["main","develop"]'
api="orgs/$OWNER/packages/container/$PACKAGE/versions"

if [[ -n "${VERSIONS_JSON:-}" ]]; then
  versions=$(cat "$VERSIONS_JSON")
else
  versions=$(gh api --paginate "$api?per_page=100" --jq '.[] | {id, tags: .metadata.container.tags}' | jq -s .)
fi

# 남길 버전 ID 목록
kept=$(jq -c --argjson keep "$KEEP" --argjson branches "$BRANCHES" '
  . as $versions
  | def recent($branch):
      [ $versions[] | .id as $id | .tags[]
        | select(test("^" + $branch + "-[0-9]{8}-[0-9]{6}-[0-9a-f]{7}$")) | {tag: ., id: $id} ]
      | sort_by(.tag) | reverse | map(.id)
      | reduce .[] as $id ([]; if (index([$id]) != null) or (length >= $keep) then . else . + [$id] end);
  ([ $branches[] as $b | recent($b)[] ]
   + [ $versions[] | select(any(.tags[]; . as $t | $branches | index([$t]) != null)) | .id ])
  | unique' <<<"$versions")

total=$(jq length <<<"$versions")
if [[ "$total" -gt 0 && "$kept" == "[]" ]]; then
  echo "남길 이미지가 없다고 계산돼 아무것도 지우지 않는다 (버전 $total 개)" >&2
  exit 1
fi

to_delete=$(jq -c --argjson kept "$kept" '[ .[] | . as $v | select(($kept | index([$v.id])) == null) ]' <<<"$versions")
to_keep=$(jq -c --argjson kept "$kept" '[ .[] | . as $v | select(($kept | index([$v.id])) != null) ]' <<<"$versions")

# 결과 표. CI 에서는 실행 화면의 요약(Summary)에 붙는다.
mode=$([[ "$DRY_RUN" == "true" ]] && echo "지울 목록만 출력 (DRY_RUN)" || echo "삭제")
# shellcheck disable=SC2016 # 백틱은 마크다운의 코드 표시라 그대로 출력한다
{
  printf '## 이미지 정리: %s\n\n' "$mode"
  printf '브랜치(main, develop)마다 최근 %s개를 남긴다. 남김 %s개, 지움 %s개\n\n' \
    "$KEEP" "$(jq length <<<"$to_keep")" "$(jq length <<<"$to_delete")"
  printf '| 구분 | 버전 ID | 태그 |\n| --- | --- | --- |\n'
  jq -r '.[] | "| 남김 | \(.id) | `\(.tags | join("`, `"))` |"' <<<"$to_keep"
  jq -r '.[] | "| 지움 | \(.id) | \(if (.tags | length) == 0 then "(태그 없음)" else "`" + (.tags | join("`, `")) + "`" end) |"' <<<"$to_delete"
} | tee -a "${GITHUB_STEP_SUMMARY:-/dev/null}"

[[ "$DRY_RUN" == "true" ]] && exit 0

for id in $(jq -r '.[].id' <<<"$to_delete"); do
  gh api --method DELETE "$api/$id" > /dev/null
  echo "지움: $id"
done
