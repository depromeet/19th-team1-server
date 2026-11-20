#!/usr/bin/env bash
# 내 열린 이슈, 진행 중인 스토리, 착수 가능한 다음 스토리를 출력한다. 고르는 기준은 docs/plan/roadmap.md
# "진행 방법" 1번이고, 이 규칙은 이 파일에만 둔다. 세션 시작 훅(session-start.sh)과 next-story 스킬이 쓴다.
#   --brief   내 열린 이슈·진행 중은 개수만, 착수 가능한 스토리는 3개까지 출력 (세션 시작 요약용)
# 진행 중: 열린 PR 의 head 브랜치나 원격 브랜치 이름에서 이슈 번호가 나오는 스토리. 번호는
# scripts/check-convention.sh issue-from-branch 로 뽑는다. 원격 브랜치를 조회하지 못하면 로컬 origin/* 참조로
# 대신하고, 그것도 안 되면 원격 브랜치 검사만 건너뛴다.
# 테스트: scripts/claude/test-next-story.sh
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
roadmap="${ROADMAP:-$root/docs/plan/roadmap.md}"
# gh 와 git 은 현재 디렉터리로 레포를 정한다. 어디서 실행해도 이 레포를 보도록 옮긴다.
cd "$root"
brief=0
[ "${1:-}" = "--brief" ] && brief=1

[ -r "$roadmap" ] || { echo "roadmap 을 읽을 수 없습니다: $roadmap" >&2; exit 1; }

me="$(gh api user --jq .login)"
issues="$(gh issue list --state all --limit 500 --json number,state,assignees,title \
  --jq '.[] | [(.number|tostring), .state, ([.assignees[].login]|join(",")), .title] | @tsv')"

pr_branches="$(gh pr list --state open --limit 200 --json headRefName --jq '.[].headRefName')"
remote_branches="$(GIT_TERMINAL_PROMPT=0 GIT_SSH_COMMAND="${GIT_SSH_COMMAND:-ssh} -o ConnectTimeout=5 -o BatchMode=yes" \
    git ls-remote --heads origin 2>/dev/null | sed 's#.*refs/heads/##')" ||
  remote_branches="$(git for-each-ref --format='%(refname:short)' refs/remotes/origin 2>/dev/null | sed 's#^origin/##')" ||
  remote_branches=""
busy=" "
while read -r branch; do
  [ -n "$branch" ] || continue
  if n="$("$root/scripts/check-convention.sh" issue-from-branch "$branch" 2>/dev/null)"; then busy="$busy$n "; fi
done <<<"$pr_branches
$remote_branches"

awk -v me="$me" -v brief="$brief" -v busyList="$busy" '
  function trim(s) { gsub(/^[ \t]+|[ \t]+$/, "", s); return s }

  # 첫 입력: 이슈 목록 (번호, 상태, 담당자들, 제목)
  FNR == NR {
    split($0, f, "\t")
    n = f[1]; state[n] = f[2]; title[n] = f[4]
    k = split(f[3], who, ",")
    for (i = 1; i <= k; i++) if (who[i] == me) mine[n] = 1
    if (mine[n] && state[n] == "OPEN") myOpen[++myCount] = n + 0
    next
  }

  # 둘째 입력: roadmap 의 스토리 표 행
  /^\| E[0-9]+-[0-9]+ \|/ {
    split($0, c, "|")
    id = trim(c[2]); iss = trim(c[4]); sub(/^#/, "", iss)
    if (iss !~ /^[0-9]+$/) iss = ""
    order[++storyCount] = id
    storyTitle[id] = trim(c[3]); storyIssue[id] = iss; week[id] = trim(c[6]) + 0; pred[id] = trim(c[7])
    if (iss != "") storyOf[iss] = id
  }

  BEGIN { k = split(busyList, b, " "); for (i = 1; i <= k; i++) busy[b[i]] = 1 }

  function myOpenStory(id,    n) {
    n = storyIssue[id]
    return n != "" && state[n] == "OPEN" && mine[n]
  }

  function ready(id,    n, k, i, p, ps) {
    if (!myOpenStory(id) || storyIssue[id] in busy) return 0
    k = split(pred[id], ps, ",")
    for (i = 1; i <= k; i++) {
      p = trim(ps[i])
      if (p == "" || p == "—" || p == "-") continue
      if (storyIssue[p] == "" || state[storyIssue[p]] != "CLOSED") return 0
    }
    return 1
  }

  END {
    # 내 열린 이슈: 번호순
    for (i = 2; i <= myCount; i++) for (j = i; j > 1 && myOpen[j-1] > myOpen[j]; j--) { t = myOpen[j]; myOpen[j] = myOpen[j-1]; myOpen[j-1] = t }
    if (brief) print "내 열린 이슈 " myCount "개 (담당자 " me ")"
    else print "내 열린 이슈 (담당자 " me "):"
    if (!brief && myCount == 0) print "- 없음"
    for (i = 1; !brief && i <= myCount; i++) {
      n = myOpen[i] ""
      print "- #" n " " title[n] (n in storyOf ? " (" storyOf[n] ")" : " (roadmap에 없음)")
    }
    print ""

    # 진행 중: 내 열린 스토리 중 브랜치·PR 이 있는 것
    busyCount = 0
    for (i = 1; i <= storyCount; i++) if (myOpenStory(order[i]) && storyIssue[order[i]] in busy) prog[++busyCount] = order[i]
    if (brief) print "진행 중 (브랜치·PR 있음) " busyCount "개"
    else {
      print "진행 중 (브랜치·PR 있음):"
      if (busyCount == 0) print "- 없음"
      for (i = 1; i <= busyCount; i++) print "- " prog[i] " " storyTitle[prog[i]] " (#" storyIssue[prog[i]] ")"
    }
    print ""

    # 착수 가능한 스토리: 주차순, 같은 주차는 roadmap 순서
    count = 0
    for (i = 1; i <= storyCount; i++) if (ready(order[i])) cand[++count] = order[i]
    for (i = 2; i <= count; i++) for (j = i; j > 1 && week[cand[j-1]] > week[cand[j]]; j--) { t = cand[j]; cand[j] = cand[j-1]; cand[j-1] = t }
    print "착수 가능한 스토리 (내 담당, 이슈 열림, 브랜치·PR 없음, 선행 이슈 모두 닫힘, 주차순):"
    if (count == 0) print "- 없음"
    shown = (brief && count > 3) ? 3 : count
    for (i = 1; i <= shown; i++) {
      id = cand[i]
      print "- " id " " storyTitle[id] " (#" storyIssue[id] ", " week[id] "주)"
    }
    if (shown < count) print "- 외 " (count - shown) "개"
  }
' <(printf '%s\n' "$issues") "$roadmap"
