create table recurring_charges (
  id uuid primary key,
  user_id uuid not null references app_users(id) on delete cascade,
  name varchar(120) not null,
  category varchar(40) not null,
  amount bigint not null check (amount > 0),
  day_of_month integer not null check (day_of_month between 1 and 31),
  active boolean not null default true,
  created_at timestamptz not null default now()
);

create index recurring_charges_user_active_day_idx
  on recurring_charges(user_id, active, day_of_month);
