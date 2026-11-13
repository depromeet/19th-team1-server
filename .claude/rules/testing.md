---
paths:
  - "core/src/test/**"
  - "core/src/testFixtures/**"
  - "infra/src/test/**"
  - "infra/src/testFixtures/**"
  - "api/src/test/**"
  - "worker/src/test/**"
  - "worker/src/testFixtures/**"
---

# 테스트 규칙

계층별 방식과 이유는 `docs/testing.md`에 있다.

## 대역

- 서비스·워커 테스트는 Spring 없이 생성자로 조립하고, 바깥 의존은 직접 만든 가짜 구현으로 바꾼다. 목을 쓰지 않는다.
- 목(Mockito)은 컨트롤러 테스트에서 서비스를 대신할 때만 쓴다.
- 쿼리는 실제 MySQL(`MySqlContainerConfiguration`)에서 실행해 검증한다. `EntityManager`나 Spring Data 리포지토리를 목으로 만들지 않는다. H2를 쓰지 않는다.
- 외부 연동 클라이언트는 가짜 HTTP 서버로 테스트한다. 실제 LLM을 호출하지 않는다.

## 가짜 구현과 공용 도구

- 가짜 구현은 테스트 파일마다 만들지 않고 test-fixtures에 둔다. `core/src/testFixtures`에는 가짜 구현과 도메인 객체를 만드는 함수, `infra/src/testFixtures`에는 MySQL 컨테이너 설정과 저장소 테스트 도우미를 둔다.
- 가짜 구현은 실제 구현의 계약(번호 발급, 유니크 제약 위반, 없는 대상 조회)을 따른다. 테스트 전용 조회 메서드는 둬도 된다.
- 시각은 `Clock`을 주입받아 고정한다.

## 실제 DB 테스트

- 컨테이너 하나를 여러 테스트가 같이 쓴다. 건수 단언은 그 테스트가 만든 데이터로 좁혀서 센다.
- 커밋 이후 동작(작업 획득, 중복 방지)을 확인하는 테스트는 `@Transactional`로 롤백하지 않는다. 커밋하고, 만든 데이터를 직접 지운다.
- 스키마는 Flyway가 만든다. 테스트용 스키마를 따로 두지 않는다.

## 작성

- 이름은 한국어 문장으로 동작을 적는다. (`` `만료된 작업은 다시 획득할 수 있다` ``)
- 준비 → 실행 → 확인 순서로 쓰고 빈 줄로 구분한다. 테스트 하나는 동작 하나를 확인한다.
- 단언은 `kotlin.test`와 JUnit을 쓴다. 단언 라이브러리를 추가하지 않는다.
