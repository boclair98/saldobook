package kr.saldo.repo;

import kr.saldo.domain.LedgerTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

public interface TransactionRepository extends JpaRepository<LedgerTransaction, UUID> {
  List<LedgerTransaction> findTop100ByUserIdOrderByTransactedAtDesc(UUID userId);
  Page<LedgerTransaction> findByUserIdOrderByTransactedAtDesc(UUID userId, Pageable pageable);
  long countByUserId(UUID userId);
  Optional<LedgerTransaction> findByIdAndUserId(UUID id, UUID userId);
  boolean existsByUserIdAndExternalId(UUID userId, String externalId);

  @Query(value = """
    select coalesce(sum(amount), 0)::bigint
    from transactions
    where user_id = :userId
      and transacted_at >= :fromTime
      and transacted_at < :toTime
      and transaction_type = :type
    """, nativeQuery = true)
  long sumAmount(
    @Param("userId") UUID userId,
    @Param("fromTime") Instant fromTime,
    @Param("toTime") Instant toTime,
    @Param("type") String type
  );

  @Query(value = """
    select
      coalesce(sum(amount) filter (where transaction_type = 'INCOME'), 0)::bigint as income,
      coalesce(sum(amount) filter (where transaction_type = 'EXPENSE'), 0)::bigint as expense
    from transactions
    where user_id = :userId
      and transacted_at >= :fromTime
      and transacted_at < :toTime
    """, nativeQuery = true)
  PeriodTotal sumTotals(
    @Param("userId") UUID userId,
    @Param("fromTime") Instant fromTime,
    @Param("toTime") Instant toTime
  );

  @Query(value = """
    select category as category, coalesce(sum(amount), 0)::bigint as amount
    from transactions
    where user_id = :userId
      and transacted_at >= :fromTime
      and transacted_at < :toTime
      and transaction_type = 'EXPENSE'
    group by category
    order by sum(amount) desc
    """, nativeQuery = true)
  List<CategoryTotal> sumExpensesByCategory(
    @Param("userId") UUID userId,
    @Param("fromTime") Instant fromTime,
    @Param("toTime") Instant toTime
  );

  @Query(value = """
    select to_char(transacted_at at time zone 'Asia/Seoul', 'YYYY-MM') as month,
      coalesce(sum(amount) filter (where transaction_type = 'INCOME'), 0)::bigint as income,
      coalesce(sum(amount) filter (where transaction_type = 'EXPENSE'), 0)::bigint as expense
    from transactions
    where user_id = :userId
      and transacted_at >= :fromTime
      and transacted_at < :toTime
    group by to_char(transacted_at at time zone 'Asia/Seoul', 'YYYY-MM')
    order by month
    """, nativeQuery = true)
  List<MonthlyTotal> sumMonthlyTotals(
    @Param("userId") UUID userId,
    @Param("fromTime") Instant fromTime,
    @Param("toTime") Instant toTime
  );

  interface CategoryTotal {
    String getCategory();
    long getAmount();
  }

  interface PeriodTotal {
    long getIncome();
    long getExpense();
  }

  interface MonthlyTotal {
    String getMonth();
    long getIncome();
    long getExpense();
  }
}
