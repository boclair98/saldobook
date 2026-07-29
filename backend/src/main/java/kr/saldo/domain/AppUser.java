package kr.saldo.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_users", uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "provider_id"}))
public class AppUser {
  @Id private UUID id;
  @Column(nullable = false, length = 20) private String provider;
  @Column(name = "provider_id", nullable = false, length = 160) private String providerId;
  private String email;
  @Column(name = "display_name", nullable = false, length = 100) private String displayName;
  @Column(name = "created_at", nullable = false) private Instant createdAt;

  protected AppUser() {}
  public AppUser(String provider, String providerId, String email, String displayName) {
    this.id = UUID.randomUUID(); this.provider = provider; this.providerId = providerId;
    this.email = email; this.displayName = displayName; this.createdAt = Instant.now();
  }
  public UUID getId() { return id; }
  public String getDisplayName() { return displayName; }
  public String getEmail() { return email; }
}
