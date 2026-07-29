create table monthly_budgets (
  id uuid primary key,
  user_id uuid not null references app_users(id) on delete cascade,
  year_month varchar(7) not null,
  amount bigint not null check (amount >= 0),
  updated_at timestamptz not null default now(),
  unique(user_id, year_month)
);

create index monthly_budgets_user_month_idx on monthly_budgets(user_id, year_month);
