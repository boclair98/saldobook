package kr.saldo.repo;

import kr.saldo.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
  Optional<AppUser> findByProviderAndProviderId(String provider, String providerId);
}
