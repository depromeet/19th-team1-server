-- 꺼내 쓰기 대화와 메시지.

create table conversation (
    id         bigint generated always as identity primary key,
    member_id  bigint       not null,
    title      varchar(100),
    created_at timestamptz  not null,
    deleted_at timestamptz
);

create index idx_conversation_member on conversation (member_id);

create table message (
    id              bigint generated always as identity primary key,
    conversation_id bigint      not null,
    role            varchar(20) not null,
    content         text        not null,
    status          varchar(20) not null,
    created_at      timestamptz not null
);

-- 메시지 순서는 id 정렬이다.
create index idx_message_conversation on message (conversation_id, id);

create table message_saved_item (
    message_id    bigint   not null,
    saved_item_id bigint   not null,
    sort_order    smallint not null,
    primary key (message_id, saved_item_id)
);
