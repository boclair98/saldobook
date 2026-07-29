package kr.saldo.repo;

import kr.saldo.domain.BankAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BankAccountRepository extends JpaRepository<BankAccount, UUID> {
  List<BankAccount> findByUserIdAndActiveTrueOrderByConnectedAtAsc(UUID userId);
  Optional<BankAccount> findByUserIdAndFintechUseNum(UUID userId, String fintechUseNum);
  Optional<BankAccount> findByIdAndUserId(UUID id, UUID userId);
  void deleteByUserId(UUID userId);
}
