create index transactions_user_type_date_idx
  on transactions(user_id, transaction_type, transacted_at desc);
