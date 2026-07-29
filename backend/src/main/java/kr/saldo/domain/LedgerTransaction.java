package kr.saldo.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transactions")
public class LedgerTransaction {
  @Id private UUID id;
  @Column(name = "user_id", nullable = false) private UUID userId;
  @Column(name = "account_id") private UUID accountId;
  @Column(nullable = false, length = 120) private String merchant;
  @Column(nullable = false, length = 40) private String category;
  @Column(nullable = false) private long amount;
  @Column(name = "transaction_type", nullable = false, length = 20) private String type;
  @Column(name = "transacted_at", nullable = false) private Instant transactedAt;
  @Column(nullable = false, length = 20) private String source;
  @Column(name = "external_id", length = 180) private String externalId;
  @Column(name = "created_at", nullable = false) private Instant createdAt;

  protected LedgerTransaction() {}
  public LedgerTransaction(UUID userId, String merchant, String category, long amount, String type, Instant transactedAt, String source) {
    this(userId, null, merchant, category, amount, type, transactedAt, source, null);
  }
  public LedgerTransaction(
    UUID userId,
    UUID accountId,
    String merchant,
    String category,
    long amount,
    String type,
    Instant transactedAt,
    String source,
    String externalId
  ) {
    this.id = UUID.randomUUID(); this.userId = userId; this.merchant = merchant; this.category = category;
    this.accountId = accountId; this.amount = amount; this.type = type; this.transactedAt = transactedAt;
    this.source = source; this.externalId = externalId; this.createdAt = Instant.now();
  }
  public UUID getId() { return id; }
  public String getMerchant() { return merchant; }
  public String getCategory() { return category; }
  public long getAmount() { return amount; }
  public String getType() { return type; }
  public Instant getTransactedAt() { return transactedAt; }
  public String getSource() { return source; }
  public String getExternalId() { return externalId; }
}
