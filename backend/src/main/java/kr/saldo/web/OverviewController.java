package kr.saldo.web;

import kr.saldo.repo.TransactionRepository;
import kr.saldo.repo.RecurringChargeRepository;
import kr.saldo.service.CurrentUserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/overview")
public class OverviewController {
  private final TransactionRepository transactions;
  private final RecurringChargeRepository recurringCharges;
  private final CurrentUserService currentUser;

  public OverviewController(TransactionRepository transactions, RecurringChargeRepository recurringCharges, CurrentUserService currentUser) {
    this.transactions = transactions;
    this.recurringCharges = recurringCharges;
    this.currentUser = currentUser;
  }

  @GetMapping
  @Transactional(readOnly = true)
  public Overview overview(HttpSession session) {
    var user = currentUser.require(session);
    ZoneId seoul = ZoneId.of("Asia/Seoul");
    ZonedDateTime now = ZonedDateTime.now(seoul);
    YearMonth currentMonth = YearMonth.from(now);
    var monthStart = currentMonth.atDay(1).atStartOfDay(seoul).toInstant();
    var nextMonthStart = currentMonth.plusMonths(1).atDay(1).atStartOfDay(seoul).toInstant();
    var sixMonthStart = currentMonth.minusMonths(5).atDay(1).atStartOfDay(seoul).toInstant();

    long income = transactions.sumAmount(user.getId(), monthStart, nextMonthStart, "INCOME");
    long expense = transactions.sumAmount(user.getId(), monthStart, nextMonthStart, "EXPENSE");
    Map<String, Long> categories = new LinkedHashMap<>();
    transactions.sumExpensesByCategory(user.getId(), monthStart, nextMonthStart)
      .forEach(value -> categories.put(value.getCategory(), value.getAmount()));
    Map<YearMonth, long[]> monthlyTotals = new LinkedHashMap<>();
    for (int offset = 5; offset >= 0; offset--) {
      monthlyTotals.put(currentMonth.minusMonths(offset), new long[] {0, 0});
    }
    transactions.sumMonthlyTotals(user.getId(), sixMonthStart, nextMonthStart)
      .forEach(value -> {
        long[] totals = monthlyTotals.get(YearMonth.parse(value.getMonth()));
        if (totals != null) {
          totals[0] = value.getIncome();
          totals[1] = value.getExpense();
        }
      });
    List<MonthlyPoint> monthly = new ArrayList<>();
    monthlyTotals.forEach((month, totals) -> monthly.add(new MonthlyPoint(month.toString(), totals[0], totals[1])));
    List<RecurringChargeView> recurring = recurringCharges.findByUserIdAndActiveTrueOrderByDayOfMonthAsc(user.getId()).stream()
      .map(value -> new RecurringChargeView(value.getId(), value.getName(), value.getCategory(), value.getAmount(), value.getDayOfMonth()))
      .toList();
    return new Overview(income, expense, income - expense, categories, (int) Math.min(Integer.MAX_VALUE, transactions.countByUserId(user.getId())), monthly, recurring);
  }

  public record Overview(
    long income,
    long expense,
    long remaining,
    Map<String, Long> categories,
    int transactionCount,
    List<MonthlyPoint> monthly,
    List<RecurringChargeView> recurringCharges
  ) {}

  public record MonthlyPoint(String month, long income, long expense) {}
  public record RecurringChargeView(UUID id, String name, String category, long amount, int dayOfMonth) {}
}
