package kr.saldo.repo;

import kr.saldo.domain.MonthlyBudget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MonthlyBudgetRepository extends JpaRepository<MonthlyBudget, UUID> {
  Optional<MonthlyBudget> findByUserIdAndYearMonth(UUID userId, String yearMonth);
}
