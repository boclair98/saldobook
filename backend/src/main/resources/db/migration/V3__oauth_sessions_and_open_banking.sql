create table spring_session (
  primary_id char(36) not null,
  session_id char(36) not null,
  creation_time bigint not null,
  last_access_time bigint not null,
  max_inactive_interval integer not null,
  expiry_time bigint not null,
  principal_name varchar(100),
  constraint spring_session_pk primary key (primary_id)
);

create unique index spring_session_ix1 on spring_session (session_id);
create index spring_session_ix2 on spring_session (expiry_time);
create index spring_session_ix3 on spring_session (principal_name);

create table spring_session_attributes (
  session_primary_id char(36) not null,
  attribute_name varchar(200) not null,
  attribute_bytes bytea not null,
  constraint spring_session_attributes_pk primary key (session_primary_id, attribute_name),
  constraint spring_session_attributes_fk foreign key (session_primary_id)
    references spring_session(primary_id) on delete cascade
);

create table open_banking_connections (
  id uuid primary key,
  user_id uuid not null unique references app_users(id) on delete cascade,
  access_token_encrypted text not null,
  refresh_token_encrypted text,
  user_seq_no varchar(20) not null,
  scope varchar(160),
  expires_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table accounts
  add column available_balance bigint not null default 0,
  add column product_name varchar(120),
  add column account_type varchar(20),
  add column last_synced_at timestamptz;

create unique index accounts_user_fintech_num_idx
  on accounts(user_id, fintech_use_num)
  where fintech_use_num is not null;

alter table transactions add column external_id varchar(180);
create unique index transactions_user_external_id_idx
  on transactions(user_id, external_id)
  where external_id is not null;
