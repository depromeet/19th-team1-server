-- 지표용 행동 이벤트(docs/metrics.md). 도메인 테이블과 조인하지 않는다는 규칙을 스키마로 나눈다.

create schema analytics;

create table analytics.behavior_event (
    id          bigint generated always as identity primary key,
    event_name  varchar(50) not null,
    member_id   bigint      not null,
    properties  jsonb,
    occurred_at timestamptz not null
);

create index idx_behavior_event_name_occurred on analytics.behavior_event (event_name, occurred_at);
create index idx_behavior_event_member_occurred on analytics.behavior_event (member_id, occurred_at);
