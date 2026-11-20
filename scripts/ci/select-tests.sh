#!/usr/bin/env bash
# 바뀐 파일에 영향을 받는 모듈을 골라 build/ci/selection.txt 에 쓴다. 고른 이유는 build/ci/selection.md 에 쓴다.
# CI 의 선택 실행 단계가 부른다. 고르는 규칙은 build-logic 의 TestSelector 에 있다.
#
# 비교 기준(base)
#   - develop: CI 가 마지막으로 성공한 develop 커밋. 직전 푸시를 기준으로 하면, 실패했거나 취소된 실행의 변경이
#     다시 테스트되지 않은 채 이미지로 나간다. 성공한 실행이 없거나 이력이 이어지지 않으면(force push) 전체 실행
#   - 그 밖의 브랜치: origin/main 과의 공통 조상
#
# base 를 정하지 못하거나 선택 도구가 실패하면 전체 실행으로 넓힌다.
#
# 필요한 환경변수: BRANCH. develop 에서는 GH_TOKEN(actions: read) 으로 실행 기록을 조회한다.
set -euo pipefail

out=build/ci
mkdir -p "$out"

full() {
  echo "mode=full" > "$out/selection.txt"
  printf '## 테스트 선택\n\n**전체 실행**\n- %s\n' "$1" > "$out/selection.md"
  echo "전체 실행: $1"
  exit 0
}

if [[ "${BRANCH:-}" == "develop" ]]; then
  base=$(gh run list --workflow ci.yml --branch develop --event push --status success --limit 1 \
    --json headSha --jq '.[0].headSha // empty') || full "develop 실행 기록을 조회하지 못함"
  [[ -n "$base" ]] || full "성공한 develop 실행이 없음"
  git cat-file -e "$base^{commit}" 2>/dev/null || full "마지막으로 성공한 develop 커밋($base)을 찾을 수 없음"
  git merge-base --is-ancestor "$base" HEAD || full "이력이 이어지지 않음 (force push)"
else
  base=$(git merge-base origin/main HEAD) || full "main 과의 공통 조상이 없음"
fi
echo "base: $base"

# 이름이 바뀐 파일은 옛 경로와 새 경로를 모두 남긴다. 옛 경로의 모듈도 영향을 받는다.
git diff --name-only --no-renames "$base" HEAD > "$out/changed.txt" || full "바뀐 파일 목록을 만들지 못함"

./gradlew --quiet ciSelectTests -Pci.changed="$out/changed.txt" || full "테스트 선택 실패"
