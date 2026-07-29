package kr.saldo.repo;

import kr.saldo.domain.OpenBankingConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface OpenBankingConnectionRepository extends JpaRepository<OpenBankingConnection, UUID> {
  Optional<OpenBankingConnection> findByUserId(UUID userId);
  void deleteByUserId(UUID userId);
}
