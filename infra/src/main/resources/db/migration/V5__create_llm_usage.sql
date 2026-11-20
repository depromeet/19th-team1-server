-- LLM 호출 한 건의 토큰 사용량. 사용자별·전체 기간 상한의 기준이다.

create table llm_usage (
    id                bigint generated always as identity primary key,
    member_id         bigint      not null,
    purpose           varchar(20) not null,
    model             varchar(50) not null,
    input_tokens      int         not null,
    output_tokens     int         not null,
    processing_job_id bigint,
    message_id        bigint,
    created_at        timestamptz not null
);

create index idx_llm_usage_member_created on llm_usage (member_id, created_at);
create index idx_llm_usage_created on llm_usage (created_at);
