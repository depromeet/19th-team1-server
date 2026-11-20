#!/usr/bin/env bash
# 바뀐 파일에 영향을 받는 모듈을 골라 실행할 테스트를 정한다. CI 워크플로(.github/workflows/ci.yml)의 "테스트 선택" 단계가 실행한다.
# 결과로 파일 두 개를 만든다. build/ 는 git 이 무시하는 빌드 출력 폴더라 실행하기 전에는 없다.
#   build/ci/tasks.txt     실행할 Gradle 태스크, 한 줄에 하나. 다음 단계가 읽어 ./gradlew 로 실행한다
#   build/ci/selection.md  고른 모듈과 이유를 적은 표. CI 가 실행 화면의 요약(Summary)에 붙인다
# 고르는 규칙과 그렇게 정한 이유는 docs/ci.md 에 있다.
#
# 고르는 규칙
#   1. 빌드 전체에 영향을 주는 파일이 바뀌면 전체 실행
#   2. 모듈 안의 파일이 바뀌면 그 모듈과, 그 모듈에 의존하는 모듈(다른 모듈을 거친 의존 포함)의 테스트 전부
#   3. 모듈 밖에서 테스트와 관계없는 파일(문서 등)은 건너뜀
#   4. 모듈 밖에서 그 밖의 파일은 속하지 않는 파일은 판단하지 않고 전체 실행. 틀려도 더 많이 돌리는 쪽으로만 틀리게 한다
#   고르지 않은 모듈의 테스트는 돌리지 않는다. 구조 테스트(*ArchitectureTest)는 항상, 포맷 검사(ktlintCheck)는 모든 모듈에 돈다.
#
# 비교 기준(base)
#   - develop: CI 가 마지막으로 성공한 develop 커밋. 직전 푸시를 기준으로 하면, 실패했거나 취소된 실행의 변경이
#     다시 테스트되지 않은 채 이미지로 나간다. 성공한 실행이 없거나 이력이 이어지지 않으면(force push) 전체 실행
#   - 그 밖의 브랜치: origin/main 과의 공통 조상
#
# 사용
#   BRANCH=<브랜치> GH_TOKEN=<actions: read> scripts/ci/select-tests.sh   # CI. develop 에서만 GH_TOKEN 을 쓴다
#   scripts/ci/select-tests.sh --changed <바뀐 파일 목록>                 # base 비교 없이 목록으로 고른다 (로컬 확인용)
set -euo pipefail

out=build/ci
mkdir -p "$out"
tasks="$out/tasks.txt"
summary="$out/selection.md"
modules="$out/modules.txt"
reasons="$out/reasons.txt"
full_reasons="$out/full-reasons.txt"

# 전체 실행으로 정하고 끝낸다. 인자는 이유.
full() {
  printf '%s\n' ktlintCheck check > "$tasks"
  { printf '## 테스트 선택\n\n**전체 실행**\n'; printf -- '- %s\n' "$@"; } > "$summary"
  cat "$summary"
  exit 0
}

affects_whole_build() {
  case "$1" in
    settings.gradle.kts | build.gradle.kts | gradle.properties | gradlew | gradlew.bat) return 0 ;;
    gradle/* | .github/workflows/* | scripts/ci/*) return 0 ;;
  esac
  return 1
}

# 모듈 밖에서 테스트 결과에 영향이 없는 파일. 포맷 검사는 선택과 관계없이 모든 모듈에 돈다.
is_ignored() {
  case "$1" in
    *.md | docs/* | .claude/* | .github/ISSUE_TEMPLATE/* | scripts/git-hooks/*) return 0 ;;
    compose.yaml | .env.example | Dockerfile | .dockerignore | .editorconfig | .gitignore | .gitattributes) return 0 ;;
    scripts/check-convention.sh | scripts/llm-eval/*) return 0 ;;
  esac
  return 1
}

# 파일이 속한 모듈의 Gradle 경로. 디렉터리가 가장 깊게 맞는 모듈. 없으면 빈 문자열.
owner_of() {
  local file=$1 best="" best_len=0 path dir deps
  while read -r path dir deps; do
    if [[ "$file" == "$dir/"* && ${#dir} -gt $best_len ]]; then
      best=$path
      best_len=${#dir}
    fi
  done < "$modules"
  echo "$best"
}

selected=" "
# 모듈을 고른다. 이미 고른 모듈이면 처음 이유를 남긴다.
select_module() {
  [[ "$selected" == *" $1 "* ]] && return 0
  selected+="$1 "
  printf '%s\t%s\n' "$1" "$2" >> "$reasons"
}

# 1. 바뀐 파일
if [[ "${1:-}" == "--changed" ]]; then
  changed=$2
else
  if [[ "${BRANCH:-}" == "develop" ]]; then
    base=$(gh run list --workflow ci.yml --branch develop --event push --status success --limit 1 \
      --json headSha --jq '.[0].headSha // empty') || full "develop 실행 기록을 조회하지 못함"
    [[ -n "$base" ]] || full "성공한 develop 실행이 없음"
    git cat-file -e "$base^{commit}" 2> /dev/null || full "마지막으로 성공한 develop 커밋($base)을 찾을 수 없음"
    git merge-base --is-ancestor "$base" HEAD || full "이력이 이어지지 않음 (force push)"
  else
    base=$(git merge-base origin/main HEAD) || full "main 과의 공통 조상이 없음"
  fi
  echo "base: $base"
  changed="$out/changed.txt"
  # 이름이 바뀐 파일은 옛 경로와 새 경로를 모두 남긴다. 옛 경로의 모듈도 영향을 받는다.
  git diff --name-only --no-renames "$base" HEAD > "$changed" || full "바뀐 파일 목록을 만들지 못함"
fi

# 2. 모듈과 모듈 간 의존 (루트 build.gradle.kts 의 ciModules)
./gradlew --quiet ciModules > "$modules" || full "모듈 목록을 읽지 못함"
[[ -s "$modules" ]] || full "모듈 목록이 비어 있음"

# 3. 파일을 분류하고 바뀐 모듈을 고른다
: > "$reasons"
: > "$full_reasons"
changed_modules=""
changed_count=0
while IFS= read -r file || [[ -n "$file" ]]; do
  [[ -n "$file" ]] || continue
  changed_count=$((changed_count + 1))
  if affects_whole_build "$file"; then
    echo "빌드 전체에 영향: $file" >> "$full_reasons"
    continue
  fi
  # 모듈 안의 파일은 종류(.md 포함)와 관계없이 그 모듈의 변경으로 본다. 리소스로 읽는 문서가 있을 수 있다.
  module=$(owner_of "$file")
  if [[ -n "$module" ]]; then
    if [[ " $changed_modules " != *" $module "* ]]; then
      changed_modules+="$module "
      select_module "$module" "바뀜: $file"
    fi
  elif ! is_ignored "$file"; then
    echo "어느 모듈에도 속하지 않음: $file" >> "$full_reasons"
  fi
done < "$changed"

if [[ -s "$full_reasons" ]]; then
  reason_lines=()
  while IFS= read -r line; do reason_lines+=("$line"); done < "$full_reasons"
  full "${reason_lines[@]}"
fi

# 4. 바뀐 모듈에 의존하는 모듈을 고른다. 다른 모듈을 거친 의존도 따라간다.
for origin in $changed_modules; do
  reached=" $origin "
  grew=1
  while ((grew)); do
    grew=0
    while read -r path dir deps; do
      [[ "$reached" == *" $path "* ]] && continue
      for dep in ${deps//,/ }; do
        if [[ "$reached" == *" $dep "* ]]; then
          reached+="$path "
          select_module "$path" "$origin 에 의존"
          grew=1
          break
        fi
      done
    done < "$modules"
  done
done

# 5. 구조 테스트가 있는 모듈을 고르지 않았으면 그 모듈의 구조 테스트만 돌린다
architecture_modules=""
while IFS= read -r file; do
  module=$(owner_of "$file")
  [[ -n "$module" && " $architecture_modules " != *" $module "* ]] && architecture_modules+="$module "
done < <(git ls-files '*/src/test/*ArchitectureTest.kt')
[[ -n "$architecture_modules" ]] || full "구조 테스트(*ArchitectureTest.kt)를 찾지 못함"

{
  echo ktlintCheck
  for module in $selected; do echo "$module:check"; done
  for module in $architecture_modules; do
    if [[ " $selected " != *" $module "* ]]; then
      echo "$module:test"
      echo "--tests"
      echo "*ArchitectureTest"
    fi
  done
} > "$tasks"

selected_count=$(wc -l < "$reasons" | tr -d ' ')
# shellcheck disable=SC2016 # 백틱은 마크다운의 코드 표시라 그대로 출력한다
{
  printf '## 테스트 선택\n\n바뀐 파일 %s개\n\n' "$changed_count"
  printf '**선택 실행**: 모듈 %s개의 테스트 전부와 구조 테스트\n' "$selected_count"
  if [[ -s "$reasons" ]]; then
    printf '\n| 모듈 | 이유 |\n| --- | --- |\n'
    sort "$reasons" | while IFS=$'\t' read -r module reason; do printf '| `%s` | %s |\n' "$module" "$reason"; done
  fi
  printf '\n실행: `./gradlew %s`\n' "$(tr '\n' ' ' < "$tasks" | sed 's/ $//')"
} > "$summary"
cat "$summary"
