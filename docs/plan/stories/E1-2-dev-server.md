# E1-2 개발 서버 구성

이슈: #24 · 에픽: E1 · 담당: 나현 · 명세: —

## 세부 작업

- [x] AWS 준비 (완료 조건 1)
  - [x] 예산 알림 `team1-dev-monthly` (월 $13)
  - [x] IAM 사용자 `team1-dev-app` (작업 큐에 보내기·받기·삭제만, 콘솔 로그인 없음)
  - [x] 액세스 키 발급 (사람)
  - [ ] 계정 MFA: 공용 계정은 Builder ID(Google 로그인)라 IAM 루트 MFA 화면이 없다. 콘솔 작업은 개인 Builder ID로 하고, 공용 계정의 AWS 설정 → 팀에서 초대한다(전원 관리자). MFA는 공용 계정과 개인 Builder ID 각각 profile의 보안 메뉴에서 건다(`docs/deployment.md` 7절)
    - [x] 나현 개인 Builder ID 초대 수락·MFA
    - [ ] 승환 개인 Builder ID 초대 수락·MFA
    - [ ] 공용 계정 MFA(profile 보안 메뉴, 인증 앱을 두 사람 기기에 등록)
- [x] Lightsail `team1-dev` (시드니, Ubuntu 24.04, 2GB), 고정 IP, 방화벽 22·80·443 (완료 조건 2)
- [x] SQS `team1-dev-jobs`, `team1-dev-jobs-dlq` (완료 조건 2)
- [x] 서버 초기 설정: Docker·Compose, swap 2GB, `deploy` 사용자(docker 그룹), `/srv/team1`, 비밀번호 로그인 꺼짐 확인 (완료 조건 2, 3)
- [x] 서버 Compose 파일 작성 (완료 조건 2)
  - [x] `deploy/compose.yaml`: `app`(GHCR 이미지, `APP_IMAGE_TAG`, 메모리 768MB, 힙 비율), `postgres`(16, 640MB, `max_connections=30`, `/srv/team1/postgres-data` 바인드 마운트, 포트 미공개), `caddy`(80·443, 인증서 볼륨). 모든 서비스 로그 20MB × 5
  - [x] `deploy/Caddyfile`: `{$SITE_ADDRESS}` → `app:8080`
  - [x] 로컬에서 `docker compose -f deploy/compose.yaml config`로 문법 확인
- [x] 서버에 배포해 확인 (완료 조건 2)
  - [x] 서버 `/srv/team1/.env` 작성 (사람, 값은 서버에만)
  - [x] GHCR 로그인: 패키지가 공개라 필요 없음
  - [x] `docker compose up -d` 후 `https://15-135-160-110.sslip.io/actuator/health` 200 확인
  - [x] `docker stats`로 메모리 확인
- [ ] 배포용 SSH 접속 정보 승환님께 전달: 호스트, 사용자 `deploy`, 키는 승환님이 만든 공개키를 `deploy`에 등록 (완료 조건 3)
- [x] 문서 (완료 조건 4)
  - [x] `docs/deployment.md`: 3절 "서버 compose 파일은 이 레포에 없다" 수정, 개발 서버 구성 절 추가, 6절 큐 이름·최대 수신 횟수, 5절 `SITE_ADDRESS`·`APP_IMAGE_TAG`
  - [x] `.env.example`에 새 이름 추가
  - [x] `docs/architecture.md` 4장 DB 위치 `[제안]` 상태 확인

## 참고

- 규칙: `.claude/rules/architecture.md`
- 문서: `docs/deployment.md` 3·4·5·6절, `docs/architecture.md` 4장, `docs/ci.md` 4절, 인프라 구성안(서버 파트 공유 HTML, 11/14)
- 결정: 기능 브랜치 → develop 직접 머지, 이미지 승격 없음(`develop`·`main` 태그 분리)

## 작업 중 메모

- 11/21 서버 첫 기동: `APP_IMAGE_TAG=main`, dev 프로필. HTTPS로 `/actuator/health` → `{"status":"UP"}`(Caddy가 sslip.io 인증서 발급). 기동 직후 `docker stats`: app 355MB/768MB, postgres 48MB/640MB, caddy 21MB.
- 무료 플랜 계정은 시드니(ap-southeast-2)에서만 리소스를 만들 수 있다.
- 최대 수신 횟수 5: DB 재시도 상한(3)보다 크게. 나현 확인함.
- dev 프로필은 `JWT_SECRET`, `KAKAO_*` 셋이 비어 있으면 뜨지 않는다. 카카오 앱이 먼저 있어야 한다.
- #54 머지 뒤 rebase할 때 할 일: `docs/deployment.md` 3절 되돌리기 문장을 "서버 `.env`의 `APP_IMAGE_TAG`를 이력 태그로 바꾸고 같은 명령을 실행한다"로 고친다. `scripts/ci/select-tests.sh`와 `docs/ci.md` 4장의 테스트 없는 경로에 `deploy/**`를 넣는다.

## 세션 전달 (나현 → CLI 세션, 11/21)

AWS 콘솔과 서버 초기 설정은 Cowork 세션에서 끝냈다. 이 세션은 레포 작업만 한다. 서버 접속, 콘솔 조작, 비밀값 입력은 하지 않는다.

### 정해진 것

1. **서버 Compose 파일은 레포에 둔다.** `deploy/compose.yaml`, `deploy/Caddyfile` 하나씩. 개발·운영이 같은 파일을 쓰고 차이는 서버 `/srv/team1/.env`로만 준다(`docs/architecture.md` 4장 "같은 이미지와 같은 Compose 파일"). E1-3(승환)의 CI/CD가 이 파일을 서버로 복사해 쓴다.
2. **문서는 개발·운영을 같이 최신화한다.** 운영 서버는 아직 없지만(프리런칭 1주 전 생성) 같은 구성으로 만든다. 개발·운영이 다른 값은 한 표에 나란히 적는다. 지금 상태만 적고 변경 이력은 남기지 않는다.
3. **SQS 값 확정.** 아래 "만든 것" 표의 값을 `docs/deployment.md` 6절과 5절에 반영한다. "최대 수신 횟수와 큐 이름, 운영 값은 정하지 않았다" 문장을 현재 상태로 고친다.
4. **GHCR 패키지는 공개(Public)다.** 서버에서 `docker login`이 필요 없다. 3절의 "패키지가 비공개면 … 로그인" 문단을 현재 상태에 맞게 고친다.
5. **`develop` 이미지 태그가 아직 GHCR에 없다**(11/21 확인, `main`·`main-*`·`fp-*`만 있음). 첫 확인은 `APP_IMAGE_TAG=main`으로 띄운다. `develop` 푸시는 사람이 정한다. 문서에는 개발 서버 기본 태그를 `develop`으로 적는다.

### 만든 것 (전부 ap-southeast-2. 무료 플랜 계정은 이 리전만 쓸 수 있다)

| 항목 | 개발 | 운영(예정, 같은 규칙) |
| --- | --- | --- |
| Lightsail | `team1-dev`, Ubuntu 24.04, 2GB/2vCPU/60GB, 키 페어 `team1-dev-key` | `team1-prod` |
| 고정 IP | `team1-dev-ip` = 15.135.160.110 | `team1-prod-ip` |
| 방화벽 | 22, 80, 443 (IPv4·IPv6) | 같음 |
| 주소 | `15-135-160-110.sslip.io` (도메인 정하기 전 임시) | 도메인 정한 뒤 `api.<도메인>` |
| 작업 큐 | `team1-dev-jobs`: 표준, 기본 표시 제한 5분, 수신 대기 20초, 보관 4일, SSE-SQS, DLQ `team1-dev-jobs-dlq`로 최대 수신 5회 | `team1-prod-jobs` |
| DLQ | `team1-dev-jobs-dlq`: 보관 14일 | `team1-prod-jobs-dlq` |
| DLQ 경보 | 아직 없음 | 운영 생성 때 같이 |
| 앱 자격 증명 | IAM 사용자 `team1-dev-app`, 인라인 정책 `team1-dev-jobs-queue`: `sqs:SendMessage`, `sqs:ReceiveMessage`, `sqs:DeleteMessage` on `team1-dev-jobs`만. 액세스 키는 사람이 보관 | `team1-prod-app` |
| 예산 알림 | `team1-dev-monthly` 월 $13 (계정 전체, 크레딧 차감 전 금액) | 운영 생성 때 금액 조정 |
| 서버 설정 | Docker CE + Compose 플러그인, swap 2GB(`/swapfile`), 사용자 `deploy`(docker 그룹), `/srv/team1`(deploy 소유), 비밀번호 로그인 꺼짐 | 같음 |

- 앱이 쓰는 SQS API는 send·receive·delete 셋뿐이다(코드 확인함, create·delete queue는 테스트에서만).
- Lightsail은 IAM 역할을 붙일 수 없어 액세스 키를 환경변수로 준다.

### 할 일

1. `deploy/compose.yaml`
   - `app`: `ghcr.io/depromeet/19th-team1-server:${APP_IMAGE_TAG:?}` 형태(개발 기본 `develop`, 운영 `main`. 기본값을 compose에 박을지 `.env`로만 줄지 정해서 문서와 맞춘다). `env_file: .env`. 메모리 상한 768MB, 힙은 약 420MB가 되게 `JAVA_TOOL_OPTIONS`(예: `-XX:MaxRAMPercentage`). 포트는 바깥에 열지 않는다. `postgres`가 healthy 된 뒤 뜨게 한다.
   - `postgres`: `postgres:16`, 메모리 상한 640MB, `max_connections=30`(앱 풀 10 + 백업·수동 접속 여유. Hikari 기본 풀 크기가 10인지 확인), 데이터는 `/srv/team1/postgres-data` 바인드 마운트, 포트 미공개, healthcheck.
   - `caddy`: 80·443 공개, 인증서·설정 볼륨(이름 있는 볼륨), `SITE_ADDRESS`로 호스트를 받는다.
   - 모든 서비스: `restart: unless-stopped`, 로그 json-file 20MB × 5.
   - 배포는 `up -d`만 쓰고 `down`을 쓰지 않는다는 주석.
2. `deploy/Caddyfile`: `{$SITE_ADDRESS}` → `reverse_proxy app:8080`.
3. 검증: `docker compose -f deploy/compose.yaml config`를 더미 `.env`로 실행해 문법을 본다(실제 값 넣지 않는다). 가능하면 로컬에서 `APP_IMAGE_TAG=main`, `SITE_ADDRESS=:80` 같은 값으로 띄워 `/actuator/health`가 200인지 본다. 실행 못 한 검증은 못 했다고 적는다.
4. 문서 정합성 (개발·운영 둘 다)
   - `docs/deployment.md`: 3절(compose 위치, GHCR 공개, 개발/운영 태그), 새 절 "서버 구성"(위 표의 개발·운영 열, 서버 폴더 구조 `/srv/team1/{compose.yaml,Caddyfile,.env,postgres-data}`, 메모리 배분, 처음 띄우는 명령, 되돌리기), 5절(새 환경변수 `SITE_ADDRESS`, `APP_IMAGE_TAG`, compose가 읽는 `DB_*`; "필요한 곳" 열 정리), 6절(큐 이름, 최대 수신 5, 표시 제한·수신 대기 큐 기본값, 경보는 아직 없음, 권한 정책 이름).
   - `.env.example`: 새 이름 추가(이름만).
   - `docs/architecture.md` 4장: DB 위치 `[제안]`이 실제 구성과 맞는지, 도메인 문장, 11장 정해지지 않은 부분.
   - `docs/ci.md`, `CLAUDE.md` 표, `.claude/rules/`에서 서버 compose나 배포를 언급하는 곳이 어긋나지 않는지.
   - 끝나면 `doc-check` 스킬을 돌린다.
5. 스토리 파일 체크박스를 갱신한다.

### 하지 않을 것

- 서버 SSH 접속, AWS·GitHub 설정 변경, `.env` 읽기·출력, 실제 비밀값 작성.
- `develop` 머지·푸시, 커밋·푸시·PR (사람이 요청할 때만, 커밋 메시지는 먼저 보여 준다).
- DB 테이블·컬럼 변경 (이번 범위 아님).

### 사람이 따로 하는 것

- 카카오 앱 생성과 키 확인, IAM 액세스 키 발급, 루트 MFA.
- 서버 `/srv/team1/.env` 작성(값은 서버에서 직접), 첫 `docker compose up -d`.
- 승환님 SSH 공개키를 `deploy`에 등록.
