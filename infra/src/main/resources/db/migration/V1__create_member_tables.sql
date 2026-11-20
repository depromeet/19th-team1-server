-- 회원 애그리거트. 테이블 간 FK 는 걸지 않고 ID 값으로만 참조한다.

create table member (
    id                  bigint generated always as identity primary key,
    nickname            varchar(30)  not null,
    email               varchar(320),
    acquisition_channel varchar(30),
    status              varchar(20)  not null,
    created_at          timestamptz  not null,
    updated_at          timestamptz  not null,
    withdrawn_at        timestamptz
);

create table member_credential (
    id               bigint generated always as identity primary key,
    member_id        bigint       not null,
    provider         varchar(20)  not null,
    provider_user_id varchar(100) not null,
    created_at       timestamptz  not null,
    revoked_at       timestamptz
);

-- 로그인. 연결을 끊은(revoked) 수단은 빼서, 탈퇴 후 같은 계정으로 다시 가입할 수 있다.
create unique index uk_member_credential_provider_user
    on member_credential (provider, provider_user_id) where revoked_at is null;
create index idx_member_credential_member on member_credential (member_id);

create table member_interest_category (
    member_id  bigint      not null,
    category   varchar(30) not null,
    created_at timestamptz not null,
    primary key (member_id, category)
);
