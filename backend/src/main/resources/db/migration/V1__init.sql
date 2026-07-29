create table app_users (
  id uuid primary key,
  provider varchar(20) not null,
  provider_id varchar(160) not null,
  email varchar(255),
  display_name varchar(100) not null,
  created_at timestamptz not null default now(),
  unique(provider, provider_id)
);

create table accounts (
  id uuid primary key,
  user_id uuid not null references app_users(id) on delete cascade,
  institution_code varchar(20) not null,
  institution_name varchar(80) not null,
  masked_number varchar(30) not null,
  fintech_use_num varchar(80),
  balance bigint not null default 0,
  connected_at timestamptz not null default now()
);

create table transactions (
  id uuid primary key,
  user_id uuid not null references app_users(id) on delete cascade,
  account_id uuid references accounts(id) on delete set null,
  merchant varchar(120) not null,
  category varchar(40) not null,
  amount bigint not null check (amount > 0),
  transaction_type varchar(20) not null,
  transacted_at timestamptz not null,
  source varchar(20) not null default 'MANUAL',
  created_at timestamptz not null default now()
);

create index transactions_user_date_idx on transactions(user_id, transacted_at desc);
