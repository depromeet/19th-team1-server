#!/usr/bin/env bash
# 브랜치·커밋·PR 제목 규칙의 원본. 커밋 훅(scripts/git-hooks/commit-msg)과 PR 검사(.github/workflows/convention.yml)가
# 모두 이 스크립트를 쓴다. 규칙을 바꾸면 docs/conventions.md 도 같이 고친다.
set -euo pipefail

TYPES="feat|fix|refactor|docs|test|chore"
MODULES="core|storage|external|api"

MESSAGE_PATTERN="^(${TYPES})(\((${MODULES})(, (${MODULES}))*\))?: .+ \(#[0-9]+\)$"
BRANCH_PATTERN="^(${TYPES})/[0-9]+-[a-z0-9]+(-[a-z0-9]+)*$"
ISSUE_SUFFIX_PATTERN=" \(#[0-9]+\)$"
# git 이 만드는 메시지는 검사하지 않는다.
SKIP_PATTERN="^(Merge |Revert |fixup! |squash! |amend! )"

usage() {
  cat >&2 <<USAGE
사용법:
  $0 message <메시지 첫 줄>        커밋 메시지·PR 제목 검사
  $0 branch <브랜치 이름>          브랜치 이름 검사
  $0 issue-from-branch <브랜치>    브랜치 이름에서 이슈 번호 출력 (없으면 실패)
  $0 needs-issue <메시지 첫 줄>    메시지 끝에 (#번호)가 없으면 성공
USAGE
  exit 2
}

check_message() {
  local subject="$1"
  [[ "$subject" =~ $SKIP_PATTERN ]] && return 0
  [[ "$subject" =~ $MESSAGE_PATTERN ]] && return 0
  cat >&2 <<ERR
형식이 맞지 않습니다: $subject
  형식: 타입(모듈): 설명 (#이슈번호)
  예시: feat(core, api): 저장물 등록 API 추가 (#12)
  타입: ${TYPES//|/, }
  모듈: ${MODULES//|/, } (영향받는 모듈이 없으면 생략)
자세한 규칙: docs/conventions.md
ERR
  return 1
}

check_branch() {
  local branch="$1"
  [[ "$branch" =~ $BRANCH_PATTERN ]] && return 0
  cat >&2 <<ERR
브랜치 이름이 맞지 않습니다: $branch
  형식: 타입/이슈번호-영문-설명
  예시: feat/12-item-save
자세한 규칙: docs/conventions.md
ERR
  return 1
}

issue_from_branch() {
  local branch="$1"
  [[ "$branch" =~ ^(${TYPES})/([0-9]+)- ]] || return 1
  echo "${BASH_REMATCH[2]}"
}

needs_issue() {
  local subject="$1"
  [[ "$subject" =~ $SKIP_PATTERN ]] && return 1
  [[ "$subject" =~ $ISSUE_SUFFIX_PATTERN ]] && return 1
  return 0
}

[[ $# -eq 2 ]] || usage
case "$1" in
  message) check_message "$2" ;;
  branch) check_branch "$2" ;;
  issue-from-branch) issue_from_branch "$2" ;;
  needs-issue) needs_issue "$2" ;;
  *) usage ;;
esac
