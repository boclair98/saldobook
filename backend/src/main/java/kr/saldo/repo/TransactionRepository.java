package kr.saldo.repo;

import kr.saldo.domain.LedgerTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<LedgerTransaction, UUID> {
  List<LedgerTransaction> findTop100ByUserIdOrderByTransactedAtDesc(UUID userId);
  Optional<LedgerTransaction> findByIdAndUserId(UUID id, UUID userId);
  boolean existsByUserIdAndExternalId(UUID userId, String externalId);
}
