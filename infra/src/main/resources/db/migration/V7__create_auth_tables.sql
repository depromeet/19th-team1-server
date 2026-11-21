-- 인증. 테이블 간 FK 는 걸지 않고 ID 값으로만 참조한다.

-- 약관 동의 이력. 동의할 때마다 행을 더하고, 고치거나 지우지 않는다.
-- 약관 종류는 앱 enum 이다. 종류·문구는 결정 대기 11 을 따른다.
create table member_terms_agreement (
    id            bigint generated always as identity primary key,
    member_id     bigint      not null,
    terms_type    varchar(30) not null,
    terms_version varchar(20) not null,
    agreed_at     timestamptz not null
);

-- 같은 약관의 같은 버전에는 한 번만 동의한다. 회원별 조회도 이 인덱스를 쓴다.
create unique index uk_member_terms_agreement
    on member_terms_agreement (member_id, terms_type, terms_version);

-- 리프레시 토큰. 원문은 저장하지 않고 SHA-256 해시(16진수 64자)만 둔다.
-- 재발급(로테이션)·로그아웃·탈퇴 때 revoked_at 을 기록하고, 폐기됐거나 만료된 토큰은 쓰지 않는다.
create table refresh_token (
    id         bigint generated always as identity primary key,
    member_id  bigint      not null,
    token_hash varchar(64) not null,
    expires_at timestamptz not null,
    created_at timestamptz not null,
    revoked_at timestamptz
);

-- 재발급·로그아웃은 해시로 찾는다.
create unique index uk_refresh_token_hash on refresh_token (token_hash);
-- 탈퇴할 때 회원의 살아 있는 토큰을 한꺼번에 폐기한다.
create index idx_refresh_token_member on refresh_token (member_id) where revoked_at is null;
