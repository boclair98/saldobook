package kr.saldo.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "open_banking_connections")
public class OpenBankingConnection {
  @Id private UUID id;
  @Column(name = "user_id", nullable = false, unique = true) private UUID userId;
  @Column(name = "access_token_encrypted", nullable = false, columnDefinition = "text")
  private String accessTokenEncrypted;
  @Column(name = "refresh_token_encrypted", columnDefinition = "text")
  private String refreshTokenEncrypted;
  @Column(name = "user_seq_no", nullable = false, length = 20) private String userSeqNo;
  @Column(length = 160) private String scope;
  @Column(name = "expires_at") private Instant expiresAt;
  @Column(name = "created_at", nullable = false) private Instant createdAt;
  @Column(name = "updated_at", nullable = false) private Instant updatedAt;

  protected OpenBankingConnection() {}

  public OpenBankingConnection(
    UUID userId,
    String accessTokenEncrypted,
    String refreshTokenEncrypted,
    String userSeqNo,
    String scope,
    Instant expiresAt
  ) {
    this.id = UUID.randomUUID();
    this.userId = userId;
    this.createdAt = Instant.now();
    updateTokens(accessTokenEncrypted, refreshTokenEncrypted, userSeqNo, scope, expiresAt);
  }

  public void updateTokens(
    String accessTokenEncrypted,
    String refreshTokenEncrypted,
    String userSeqNo,
    String scope,
    Instant expiresAt
  ) {
    this.accessTokenEncrypted = accessTokenEncrypted;
    if (refreshTokenEncrypted != null && !refreshTokenEncrypted.isBlank()) {
      this.refreshTokenEncrypted = refreshTokenEncrypted;
    }
    this.userSeqNo = userSeqNo;
    this.scope = scope;
    this.expiresAt = expiresAt;
    this.updatedAt = Instant.now();
  }

  public UUID getId() { return id; }
  public UUID getUserId() { return userId; }
  public String getAccessTokenEncrypted() { return accessTokenEncrypted; }
  public String getRefreshTokenEncrypted() { return refreshTokenEncrypted; }
  public String getUserSeqNo() { return userSeqNo; }
  public String getScope() { return scope; }
  public Instant getExpiresAt() { return expiresAt; }
  public Instant getUpdatedAt() { return updatedAt; }
}
