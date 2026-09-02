-- Account management filters by owner and active state on every dashboard load.
-- Keeping this partial index small avoids scanning disconnected accounts.
create index accounts_user_active_connected_idx
  on accounts(user_id, connected_at)
  where active = true;
