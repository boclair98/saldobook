package kr.saldo.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
  name = "monthly_budgets",
  uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "year_month"})
)
public class MonthlyBudget {
  @Id private UUID id;
  @Column(name = "user_id", nullable = false) private UUID userId;
  @Column(name = "year_month", nullable = false, length = 7) private String yearMonth;
  @Column(nullable = false) private long amount;
  @Column(name = "updated_at", nullable = false) private Instant updatedAt;

  protected MonthlyBudget() {}

  public MonthlyBudget(UUID userId, String yearMonth, long amount) {
    this.id = UUID.randomUUID();
    this.userId = userId;
    this.yearMonth = yearMonth;
    this.amount = amount;
    this.updatedAt = Instant.now();
  }

  public void changeAmount(long amount) {
    this.amount = amount;
    this.updatedAt = Instant.now();
  }

  public UUID getId() { return id; }
  public String getYearMonth() { return yearMonth; }
  public long getAmount() { return amount; }
  public Instant getUpdatedAt() { return updatedAt; }
}
