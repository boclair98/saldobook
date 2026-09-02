package kr.saldo.service;

import kr.saldo.repo.MonthlyBudgetRepository;
import kr.saldo.repo.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Turns a user's recorded cash flow into a deliberately conservative daily
 * spending guide. This is a planning aid, not a promise of future income.
 */
@Service
public class SpendingNavigatorService {
  private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
  private final TransactionRepository transactions;
  private final MonthlyBudgetRepository budgets;

  public SpendingNavigatorService(TransactionRepository transactions, MonthlyBudgetRepository budgets) {
    this.transactions = transactions;
    this.budgets = budgets;
  }

  @Transactional(readOnly = true)
  public Navigator build(UUID userId) {
    return build(userId, ZonedDateTime.now(KOREA));
  }

  Navigator build(UUID userId, ZonedDateTime now) {
    YearMonth month = YearMonth.from(now);
    var monthStart = month.atDay(1).atStartOfDay(KOREA).toInstant();
    var nextMonthStart = month.plusMonths(1).atDay(1).atStartOfDay(KOREA).toInstant();
    var historyStart = month.minusMonths(3).atDay(1).atStartOfDay(KOREA).toInstant();

    TransactionRepository.PeriodTotal totals = transactions.sumTotals(userId, monthStart, nextMonthStart);
    long income = totals == null ? 0 : Math.max(0, totals.getIncome());
    long expense = totals == null ? 0 : Math.max(0, totals.getExpense());
    long budget = budgets.findByUserIdAndYearMonth(userId, month.toString())
      .map(value -> Math.max(0, value.getAmount()))
      .orElse(0L);

    List<TransactionRepository.MonthlyTotal> history = transactions.sumMonthlyTotals(
      userId,
      historyStart,
      monthStart
    );
    double averageHistoricalExpense = history.stream()
      .mapToLong(value -> Math.max(0, value.getExpense()))
      .average()
      .orElse(0);

    int elapsedDays = Math.max(1, now.getDayOfMonth());
    int remainingDays = Math.max(1, month.lengthOfMonth() - now.getDayOfMonth() + 1);
    long limitAmount = budget > 0 ? budget : income;
    String limitLabel = budget > 0 ? "설정 예산" : income > 0 ? "이번 달 수입" : "예산 또는 수입";
    long remainingBase = Math.max(0, limitAmount - expense);
    long safeDaily = limitAmount > 0 ? remainingBase / remainingDays : 0;

    Forecast forecast = forecast(expense, elapsedDays, month.lengthOfMonth(), averageHistoricalExpense, history.size());
    long projectedBalance = limitAmount > 0 ? limitAmount - forecast.projectedExpense() : income - forecast.projectedExpense();
    long pacePercent = limitAmount > 0
      ? Math.round(expense * 100.0 / limitAmount)
      : averageHistoricalExpense > 0 ? Math.round(expense * 100.0 / averageHistoricalExpense) : 0;
    Status status = status(limitAmount, expense, forecast.projectedExpense());
    String message = message(status, projectedBalance, limitAmount, expense, forecast.projectedExpense());
    String confidence = history.size() >= 3 ? "HIGH" : history.isEmpty() ? "LOW" : "MEDIUM";

    return new Navigator(
      month.toString(),
      income,
      expense,
      budget,
      limitAmount,
      limitLabel,
      remainingBase,
      safeDaily,
      forecast.projectedExpense(),
      projectedBalance,
      remainingDays,
      elapsedDays,
      Math.max(0, pacePercent),
      status.name(),
      message,
      confidence,
      history.size(),
      forecast.source().name(),
      Math.round(averageHistoricalExpense)
    );
  }

  private Forecast forecast(
    long expense,
    int elapsedDays,
    int daysInMonth,
    double averageHistoricalExpense,
    int historyMonths
  ) {
    if (expense > 0) {
      return new Forecast(
        Math.round(expense * (double) daysInMonth / elapsedDays),
        ForecastSource.CURRENT_PACE
      );
    }
    if (historyMonths > 0 && averageHistoricalExpense > 0) {
      return new Forecast(Math.round(averageHistoricalExpense), ForecastSource.HISTORICAL_AVERAGE);
    }
    return new Forecast(0, ForecastSource.NO_DATA);
  }

  private Status status(long limitAmount, long expense, long projectedExpense) {
    if (limitAmount <= 0) return Status.NEEDS_DATA;
    if (expense >= limitAmount) return Status.OVER_BUDGET;
    if (projectedExpense > limitAmount) return Status.WATCH;
    return Status.ON_TRACK;
  }

  private String message(Status status, long projectedBalance, long limitAmount, long expense, long projectedExpense) {
    return switch (status) {
      case ON_TRACK -> "현재 속도라면 월말에 약 " + won(Math.max(0, projectedBalance)) + " 정도 남을 수 있어요.";
      case WATCH -> "현재 속도라면 기준액보다 약 " + won(projectedExpense - limitAmount) + " 더 쓸 수 있어요.";
      case OVER_BUDGET -> "기준액을 이미 " + won(expense - limitAmount) + " 초과했어요. 추가 지출을 따로 계획해 보세요.";
      case NEEDS_DATA -> "예산이나 수입을 한 번 기록하면 오늘 써도 되는 금액을 계산해 드려요.";
    };
  }

  private String won(long amount) {
    return String.format("%,d원", Math.max(0, amount));
  }

  private record Forecast(long projectedExpense, ForecastSource source) {}

  private enum ForecastSource { CURRENT_PACE, HISTORICAL_AVERAGE, NO_DATA }

  private enum Status { ON_TRACK, WATCH, OVER_BUDGET, NEEDS_DATA }

  public record Navigator(
    String month,
    long income,
    long expense,
    long budget,
    long limitAmount,
    String limitLabel,
    long remainingBase,
    long safeDaily,
    long projectedExpense,
    long projectedBalance,
    int remainingDays,
    int elapsedDays,
    long pacePercent,
    String status,
    String message,
    String confidence,
    int historicalMonths,
    String forecastSource,
    long historicalAverageExpense
  ) {}
}
