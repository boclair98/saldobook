package kr.saldo.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class BankAccount {
  @Id private UUID id;
  @Column(name = "user_id", nullable = false) private UUID userId;
  @Column(name = "institution_code", nullable = false, length = 20) private String institutionCode;
  @Column(name = "institution_name", nullable = false, length = 80) private String institutionName;
  @Column(name = "masked_number", nullable = false, length = 30) private String maskedNumber;
  @Column(name = "fintech_use_num", length = 80) private String fintechUseNum;
  @Column(nullable = false) private long balance;
  @Column(name = "available_balance", nullable = false) private long availableBalance;
  @Column(name = "product_name", length = 120) private String productName;
  @Column(name = "account_type", length = 20) private String accountType;
  @Column(name = "connected_at", nullable = false) private Instant connectedAt;
  @Column(name = "last_synced_at") private Instant lastSyncedAt;
  @Column(nullable = false) private boolean active = true;

  protected BankAccount() {}

  public BankAccount(
    UUID userId,
    String institutionCode,
    String institutionName,
    String maskedNumber,
    String fintechUseNum
  ) {
    this.id = UUID.randomUUID();
    this.userId = userId;
    this.institutionCode = institutionCode;
    this.institutionName = institutionName;
    this.maskedNumber = maskedNumber;
    this.fintechUseNum = fintechUseNum;
    this.connectedAt = Instant.now();
  }

  public void updateIdentity(String institutionCode, String institutionName, String maskedNumber) {
    this.institutionCode = institutionCode;
    this.institutionName = institutionName;
    this.maskedNumber = maskedNumber;
  }

  public void reconnect() {
    this.active = true;
  }

  public void disconnect() {
    this.active = false;
  }

  public void updateBalance(long balance, long availableBalance, String productName, String accountType) {
    this.balance = balance;
    this.availableBalance = availableBalance;
    this.productName = productName;
    this.accountType = accountType;
    this.lastSyncedAt = Instant.now();
  }

  public UUID getId() { return id; }
  public UUID getUserId() { return userId; }
  public String getInstitutionCode() { return institutionCode; }
  public String getInstitutionName() { return institutionName; }
  public String getMaskedNumber() { return maskedNumber; }
  public String getFintechUseNum() { return fintechUseNum; }
  public long getBalance() { return balance; }
  public long getAvailableBalance() { return availableBalance; }
  public String getProductName() { return productName; }
  public String getAccountType() { return accountType; }
  public Instant getConnectedAt() { return connectedAt; }
  public Instant getLastSyncedAt() { return lastSyncedAt; }
  public boolean isActive() { return active; }
}
