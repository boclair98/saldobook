package kr.saldo.service;

import kr.saldo.domain.MonthlyBudget;
import kr.saldo.repo.MonthlyBudgetRepository;
import kr.saldo.repo.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SpendingNavigatorServiceTest {
  private final TransactionRepository transactions = mock(TransactionRepository.class);
  private final MonthlyBudgetRepository budgets = mock(MonthlyBudgetRepository.class);
  private final SpendingNavigatorService service = new SpendingNavigatorService(transactions, budgets);
  private final UUID userId = UUID.randomUUID();
  private final ZoneId seoul = ZoneId.of("Asia/Seoul");

  @BeforeEach
  void setUp() {
    when(budgets.findByUserIdAndYearMonth(any(), anyString())).thenReturn(Optional.empty());
    when(transactions.sumTotals(any(), any(), any())).thenReturn(totals(0, 0));
    when(transactions.sumMonthlyTotals(any(), any(), any())).thenReturn(List.of());
  }

  @Test
  void usesBudgetAndCurrentPaceToCalculateSafeDailySpend() {
    when(transactions.sumTotals(eq(userId), any(), any())).thenReturn(totals(3_000_000, 900_000));
    when(budgets.findByUserIdAndYearMonth(userId, "2026-08"))
      .thenReturn(Optional.of(new MonthlyBudget(userId, "2026-08", 2_000_000)));

    var result = service.build(userId, ZonedDateTime.of(2026, 8, 10, 12, 0, 0, 0, seoul));

    assertThat(result.limitLabel()).isEqualTo("설정 예산");
    assertThat(result.limitAmount()).isEqualTo(2_000_000);
    assertThat(result.remainingBase()).isEqualTo(1_100_000);
    assertThat(result.remainingDays()).isEqualTo(22);
    assertThat(result.safeDaily()).isEqualTo(50_000);
    assertThat(result.projectedExpense()).isEqualTo(2_790_000);
    assertThat(result.projectedBalance()).isEqualTo(-790_000);
    assertThat(result.status()).isEqualTo("WATCH");
    assertThat(result.forecastSource()).isEqualTo("CURRENT_PACE");
  }

  @Test
  void fallsBackToIncomeWhenBudgetIsNotSet() {
    when(transactions.sumTotals(eq(userId), any(), any())).thenReturn(totals(3_000_000, 500_000));

    var result = service.build(userId, ZonedDateTime.of(2026, 8, 10, 12, 0, 0, 0, seoul));

    assertThat(result.limitLabel()).isEqualTo("이번 달 수입");
    assertThat(result.limitAmount()).isEqualTo(3_000_000);
    assertThat(result.safeDaily()).isEqualTo(113_636);
    assertThat(result.status()).isEqualTo("ON_TRACK");
  }

  @Test
  void usesRecentAverageWhenThereIsNoExpenseInCurrentMonth() {
    when(transactions.sumMonthlyTotals(any(), any(), any())).thenReturn(List.of(
      monthly("2026-05", 3_000_000, 1_200_000),
      monthly("2026-06", 3_000_000, 1_500_000),
      monthly("2026-07", 3_000_000, 1_800_000)
    ));
    when(transactions.sumTotals(eq(userId), any(), any())).thenReturn(totals(0, 0));

    var result = service.build(userId, ZonedDateTime.of(2026, 8, 10, 12, 0, 0, 0, seoul));

    assertThat(result.projectedExpense()).isEqualTo(1_500_000);
    assertThat(result.historicalAverageExpense()).isEqualTo(1_500_000);
    assertThat(result.historicalMonths()).isEqualTo(3);
    assertThat(result.forecastSource()).isEqualTo("HISTORICAL_AVERAGE");
    assertThat(result.confidence()).isEqualTo("HIGH");
    assertThat(result.status()).isEqualTo("NEEDS_DATA");
  }

  @Test
  void asksForDataInsteadOfInventingAnAllowance() {
    when(transactions.sumTotals(eq(userId), any(), any())).thenReturn(totals(0, 120_000));

    var result = service.build(userId, ZonedDateTime.of(2026, 8, 10, 12, 0, 0, 0, seoul));

    assertThat(result.limitAmount()).isZero();
    assertThat(result.safeDaily()).isZero();
    assertThat(result.status()).isEqualTo("NEEDS_DATA");
    assertThat(result.message()).contains("예산이나 수입");
  }

  private TransactionRepository.PeriodTotal totals(long income, long expense) {
    return new PeriodTotalStub(income, expense);
  }

  private TransactionRepository.MonthlyTotal monthly(String month, long income, long expense) {
    return new MonthlyTotalStub(month, income, expense);
  }

  private record PeriodTotalStub(long income, long expense) implements TransactionRepository.PeriodTotal {
    @Override public long getIncome() { return income; }
    @Override public long getExpense() { return expense; }
  }

  private record MonthlyTotalStub(String month, long income, long expense) implements TransactionRepository.MonthlyTotal {
    @Override public String getMonth() { return month; }
    @Override public long getIncome() { return income; }
    @Override public long getExpense() { return expense; }
  }
}
