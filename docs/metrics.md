# 지표와 이벤트

프리런칭 성과를 판단할 지표와, 지표를 계산하는 데 쓰는 이벤트를 정의한다. 이벤트를 저장하는 테이블과 기록 코드는 E8-2에서 만든다. 화면 흐름과 명세 ID는 [plan/scope.md](plan/scope.md)에 있다.

## 1. 원칙

- **내용을 넣지 않는다.** 이벤트에 URL, 제목, 요약, 주제어, 메모, 채팅 질문·답변 원문, 카카오 계정 정보를 넣지 않는다. 플랫폼·카테고리·에러 코드처럼 정해진 값 중 하나인 코드와 숫자만 넣는다.
- **사용자는 내부 `userId`로만 가리킨다.**
- **탈퇴하거나 저장물을 삭제해도 이벤트는 남긴다.** 이벤트는 다른 사용자 데이터처럼 보관한다. 프리런칭 후 처리는 [plan/scope.md](plan/scope.md) 결정 대기 11을 따른다.
- **시각은 UTC로 저장하고, 날짜·주 단위 집계는 KST로 한다.** 주는 월요일에 시작한다.
- **기록 실패가 기능을 막지 않는다.** 이벤트를 기록하지 못해도 사용자 요청은 성공해야 한다.

## 2. 지표

| 지표 | 무엇을 판단하나 | 계산 | 쓰는 이벤트 |
| --- | --- | --- | --- |
| 자발 재열람 (핵심) | 저장해 둔 것을 알림 없이 다시 꺼내 쓰는가 | 주별로, 저장 후 1일 이상 지난 저장물을 푸시가 아닌 경로로 연 사용자 수 ÷ 그 주 활동 사용자 수. 경로별(홈 카드, 채팅, 목록)로도 나눠 본다 | `item_viewed`, `item_original_opened`, `user_active` |
| 재방문 | 다시 찾아오는가 | 주간 재방문율: 이번 주 활동 사용자 중 지난주에도 활동한 비율. 첫 주 재방문율: 첫 활동 주의 다음 주에 활동한 사용자 비율 | `user_active` |
| 홈 카드 사용 | 관심사 카드가 꺼내 쓰기의 입구가 되는가 | 카드 클릭률: `home_card_clicked` 수 ÷ 카드가 1개 이상인 `home_viewed` 수. 카드 경유 열람 수: `entryPath`가 `HOME_CARD`인 열람·원본 이동 수 | `home_viewed`, `home_card_clicked`, `item_viewed`, `item_original_opened` |
| 채팅 사용 | 채팅으로 원하는 저장물을 찾는가 | 활동 사용자당 질문 수, 결과 없음 비율(`resultCount` = 0), 결과 클릭률(`entryPath`가 `CHAT`인 열람 ÷ 결과가 있는 질문), 전체 보기 비율, 이어 묻기 비율(`conversationTurn` ≥ 2) | `chat_question_sent`, `chat_view_all_clicked`, `chat_new_started`, `item_viewed` |
| 저장과 자동 정리 | 저장이 되고, 자동 정리가 쓸 만한가 | 저장 성공률: `item_saved` ÷ (`item_saved` + `item_save_rejected`). 자동 정리 성공률: 수집 수준별 `result`가 `DONE`인 `item_processed` 비율. 처리 시간 분포 | `item_saved`, `item_save_rejected`, `item_processed` |
| 메모 입력 (UT 리스크) | "링크 + 메모" 플랫폼에서 사용자가 메모를 남기는가 | 수집 수준별로, 저장할 때 메모를 쓴 비율(`hasMemo`)과 저장 뒤 메모를 추가한 비율 | `item_saved`, `item_memo_added` |
| 삭제 | 어떤 저장물이 버려지는가 | 카테고리별 주간 삭제 수, 카테고리별 정리 완료 수 대비 삭제 수, 사용자별 삭제율, 저장 후 삭제까지 걸린 일수 | `item_deleted`, `item_processed` |
| 푸시 클릭 (앱, 프리런칭 이후) | 푸시가 다시 꺼내 보게 만드는가 | 푸시 종류별 `push_clicked` ÷ `push_sent` | `push_sent`, `push_clicked` |

## 3. 이벤트

- **서버**: 서버가 API나 작업을 처리하면서 알 수 있는 이벤트다. 서버가 기록한다.
- **화면**: 웹만 알 수 있는 이벤트다. 받는 방법은 4절에서 정한다.

| 이벤트 | 언제 기록 | 속성 (공통 속성 외) | 기록 위치 |
| --- | --- | --- | --- |
| `user_active` | 사용자의 KST 날짜별 첫 인증 요청 | 없음 | 서버 |
| `item_saved` | 저장 요청이 받아들여졌을 때 | `itemId`, `platform`, `collectLevel`, `hasMemo` | 서버 |
| `item_save_rejected` | 저장 요청이 거부됐을 때 (형식 오류, 저장 불가 링크 등) | `reason`, `platform`(판별된 경우) | 서버 |
| `item_processed` | 백그라운드 수집·분석이 끝났을 때 (재시도를 다 쓰고 실패한 경우 포함) | `itemId`, `collectLevel`, `result`, `category`(`DONE`일 때), `failReason`(`FAILED`일 때), `durationSec`, `attemptCount` | 서버 |
| `item_memo_added` | 저장 뒤에 메모를 처음 추가했을 때 | `itemId`, `collectLevel` | 서버 |
| `item_viewed` | 저장물 상세를 조회했을 때 | `itemId`, `entryPath`, `category`, `daysSinceSaved` | 서버 (`entryPath`는 웹이 상세 조회 요청에 담아 보낸다) |
| `item_original_opened` | 원본 링크로 이동했을 때 | `itemId`, `entryPath` | 화면 |
| `home_viewed` | 홈 관심사 카드를 조회했을 때 | `period`, `cardCount` | 서버 |
| `home_card_clicked` | 관심사 카드를 눌렀을 때 | `rank`, `period` | 화면 |
| `chat_question_sent` | 채팅 질문에 응답했을 때 | `conversationTurn`, `fromHomeCard`, `resultCount` | 서버 |
| `chat_view_all_clicked` | 채팅 결과에서 전체 보기를 눌렀을 때 | `conversationTurn`, `resultCount` | 화면 |
| `chat_new_started` | 새 대화를 시작했을 때 | 없음 | 서버 |
| `item_deleted` | 저장물을 삭제했을 때 | `itemId`, `category`, `daysSinceSaved` | 서버 |
| `push_sent` | 푸시를 발송했을 때 (앱, 프리런칭 이후) | `pushType` | 서버 |
| `push_clicked` | 푸시를 눌러 앱을 열었을 때 (앱, 프리런칭 이후) | `pushType` | 화면 (앱) |

### 공통 속성

| 속성 | 설명 |
| --- | --- |
| `eventName` | 위 표의 이벤트 이름 |
| `userId` | 내부 사용자 ID |
| `occurredAt` | 일어난 시각 (UTC) |

### 속성 값

| 속성 | 값 |
| --- | --- |
| `platform` | 링크 정규화에서 판별한 플랫폼 코드. 예: `YOUTUBE`, `BLOG`, `NEWS`, `NOTION`, `X`, `PINTEREST`, `INSTAGRAM`, `SHOPPING`, `KAKAO_MAP`, `KAKAO_GIFT`, `OTHER`. 목록은 E4-3에서 확정한다 |
| `collectLevel` | `AUTO`(자동 정리), `PARTIAL`(부분 자동), `LINK_MEMO`(링크 + 메모). [plan/scope.md](plan/scope.md) "플랫폼별 수집 수준" |
| `hasMemo` | 저장 요청에 메모가 있었는지 (`true`/`false`). 메모 내용과 길이는 넣지 않는다 |
| `reason`, `failReason` | 에러 코드 이름 (예: `ITEM_INVALID_URL`) |
| `result` | `DONE`, `FAILED` |
| `category` | 고정 카테고리 코드 (명세 4.1.1) |
| `entryPath` | `HOME_CARD`(홈 카드의 관련 저장물), `CHAT`(채팅 결과·전체 보기), `LIST`(저장물 목록), `DETAIL`(상세 화면, 원본 이동에만), `PUSH`(푸시, 프리런칭 이후) |
| `daysSinceSaved` | 저장한 날부터 지난 일수 (KST 날짜 기준 정수) |
| `period` | 홈 조회 기간 `7`, `30` |
| `cardCount`, `rank` | 보여 준 카드 수(0~4), 누른 카드의 순서(1부터) |
| `conversationTurn` | 한 대화 안에서 몇 번째 질문인지 (1부터) |
| `fromHomeCard` | 홈 카드에서 이어진 대화인지 (명세 1.2.6) |
| `resultCount` | 질문에 대해 찾은 저장물 수 |
| `durationSec`, `attemptCount` | 저장 요청부터 처리 완료까지 걸린 초, 처리 시도 횟수 |
| `pushType` | 푸시 종류. E7-2에서 정한다 |

## 4. 화면 이벤트를 받는 방법

`item_original_opened`, `home_card_clicked`, `chat_view_all_clicked`(그리고 이후 `push_clicked`)는 웹만 안다. 받는 방법은 **웹팀과 결정**한다.

| 방법 | 장점 | 단점 |
| --- | --- | --- |
| A. 웹이 서버의 이벤트 수집 API로 보낸다 | 모든 이벤트가 한 저장소에 모여 SQL 하나로 집계한다. `userId`는 서버가 인증 정보로 채워 웹이 식별자를 다루지 않는다. 외부로 나가는 데이터가 없다 | 서버에 API와 저장소가 늘고(E8-2), 웹도 전송 코드를 만든다. 대시보드는 직접 만든다. 페이지를 떠나는 순간의 이벤트(원본 이동)는 웹이 유실되지 않게 보내야 한다 |
| B. 분석 도구(GA4, Amplitude 등)를 쓴다 | SDK만 붙이면 바로 쌓이고, 퍼널·리텐션 화면을 기본으로 준다. 서버 작업이 없다 | 서버 이벤트와 저장소가 갈라져, 합쳐 보려면 내부 `userId`를 외부 도구에 보내야 한다. 외부로 데이터가 나가므로 개인정보 처리방침에 반영이 필요할 수 있다. 도구가 기본으로 모으는 항목(페이지 URL, 제목 등)이 1절 원칙과 부딪혀 꺼야 한다. 광고 차단기로 누락될 수 있다 |

## 5. 남은 결정

| # | 결정 | 정하는 곳 |
| --- | --- | --- |
| 1 | 화면 이벤트를 받는 방법 (4절) | 서버 + 웹 |
| 2 | `entryPath`를 상세 조회에 담는 방식 | 서버 + 웹 |
| 3 | 이벤트 보관 기간 | 팀. 처리방침과 함께 |
| 4 | 팀원·테스트 계정을 집계에서 빼는 방법 | E8-2 |
