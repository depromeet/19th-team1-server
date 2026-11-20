-- 저장물 애그리거트. 카테고리는 테이블 없이 core 의 Category enum 이름을 저장한다.

create table saved_item (
    id                  bigint generated always as identity primary key,
    member_id           bigint        not null,
    original_url        text          not null,
    normalized_url      text          not null,
    normalized_url_hash varchar(64)   not null,
    platform            varchar(30)   not null,
    collect_level       varchar(20)   not null,
    memo                varchar(200),
    title               varchar(500),
    og_image_url        varchar(2048),
    process_status      varchar(20)   not null,
    fail_code           varchar(30),
    category            varchar(30),
    category_source     varchar(10),
    summary             text,
    category_data       jsonb,
    prompt_version      varchar(30),
    version             bigint        not null,
    created_at          timestamptz   not null,
    updated_at          timestamptz   not null,
    deleted_at          timestamptz
);

-- 중복 저장 방지. 삭제 후 다시 저장하면 새 행이다. 삽입은 ON CONFLICT 에 이 WHERE 조건까지 적어야 동작한다.
create unique index uk_saved_item_member_url
    on saved_item (member_id, normalized_url_hash) where deleted_at is null;
-- 카테고리별 목록·개수
create index idx_saved_item_member_category
    on saved_item (member_id, category, created_at desc, id desc) where deleted_at is null;
-- 전체 목록, 홈의 기간 집계
create index idx_saved_item_member_created
    on saved_item (member_id, created_at desc, id desc) where deleted_at is null;

create table saved_item_keyword (
    id            bigint generated always as identity primary key,
    saved_item_id bigint      not null,
    keyword       varchar(50) not null,
    sort_order    smallint    not null,
    created_at    timestamptz not null,
    constraint uk_saved_item_keyword unique (saved_item_id, keyword)
);

-- 벡터 인덱스는 두지 않는다. 회원 범위의 수백 건을 앱에서 계산한다.
create table saved_item_embedding (
    saved_item_id   bigint      primary key,
    member_id       bigint      not null,
    embedding       bytea       not null, -- float32 배열, little-endian
    dimension       int         not null,
    embedding_model varchar(50) not null,
    created_at      timestamptz not null
);

create index idx_saved_item_embedding_member on saved_item_embedding (member_id);
