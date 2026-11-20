-- 저장물 처리 작업. SQS 메시지는 이 테이블의 id 만 나른다.

create table processing_job (
    id                bigint generated always as identity primary key,
    saved_item_id     bigint       not null,
    job_type          varchar(20)  not null,
    reason            varchar(20)  not null,
    status            varchar(20)  not null,
    attempt_count     int          not null,
    last_error_code   varchar(30),
    last_error_detail varchar(500),
    status_changed_at timestamptz  not null,
    created_at        timestamptz  not null,
    completed_at      timestamptz
);

-- 멈춘 작업 재발행
create index idx_processing_job_unfinished
    on processing_job (status, status_changed_at) where status in ('PENDING', 'QUEUED', 'RUNNING');
-- 저장물의 최근 작업
create index idx_processing_job_saved_item on processing_job (saved_item_id, created_at desc);
-- 저장물당 진행 중인 ANALYZE 작업은 하나. 재시도·카테고리 수정이 동시에 와도 작업이 둘 생기지 않는다.
create unique index uk_processing_job_active_analyze
    on processing_job (saved_item_id)
    where job_type = 'ANALYZE' and status in ('PENDING', 'QUEUED', 'RUNNING');
