package kr.saldo.repo;

import kr.saldo.domain.RecurringCharge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RecurringChargeRepository extends JpaRepository<RecurringCharge, UUID> {
  List<RecurringCharge> findByUserIdAndActiveTrueOrderByDayOfMonthAsc(UUID userId);
  Optional<RecurringCharge> findByIdAndUserId(UUID id, UUID userId);
  long countByUserIdAndActiveTrue(UUID userId);
}
