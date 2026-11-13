---
paths:
  - "infra/src/main/kotlin/com/depromeet/team1/infra/storage/**"
  - "infra/src/main/resources/db/migration/**"
---

# DB 구현 규칙

- 기능마다 `storage/mysql/<기능>/`에 JPA 엔티티(`ItemJpaEntity`), Spring Data 리포지토리(`ItemJpaRepository`), 경계 인터페이스 구현(`JpaItemRepository`), 변환 함수를 둔다.
- 저장소 구현의 쓰기 메서드에는 `@Transactional`을 붙인다. 여러 건을 함께 바꿔야 하는 단계는 저장소 메서드 하나로 묶는다.
- 스키마는 Flyway 스크립트로만 바꾼다. 애플리케이션은 스키마를 만들지 않는다(`ddl-auto: validate`).
- 이미 적용된 마이그레이션 파일은 고치지 않는다. 바꿀 내용은 새 파일로 추가한다.
- 시각은 UTC로 저장한다.
