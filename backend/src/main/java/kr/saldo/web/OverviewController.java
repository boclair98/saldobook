package kr.saldo.web;

import kr.saldo.domain.LedgerTransaction;
import kr.saldo.repo.TransactionRepository;
import kr.saldo.service.CurrentUserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/overview")
public class OverviewController {
  private final TransactionRepository transactions;
  private final CurrentUserService currentUser;

  public OverviewController(TransactionRepository transactions, CurrentUserService currentUser) {
    this.transactions = transactions;
    this.currentUser = currentUser;
  }

  @GetMapping
  public Overview overview(HttpSession session) {
    var user = currentUser.require(session);
    var all = transactions.findTop100ByUserIdOrderByTransactedAtDesc(user.getId());
    ZoneId seoul = ZoneId.of("Asia/Seoul");
    ZonedDateTime now = ZonedDateTime.now(seoul);
    Instant monthStart = now.withDayOfMonth(1).toLocalDate().atStartOfDay(seoul).toInstant();
    long income = 0;
    long expense = 0;
    Map<String, Long> categories = new LinkedHashMap<>();
    Map<YearMonth, long[]> monthlyTotals = new LinkedHashMap<>();
    for (int offset = 5; offset >= 0; offset--) {
      monthlyTotals.put(YearMonth.from(now.minusMonths(offset)), new long[] {0, 0});
    }
    for (LedgerTransaction transaction : all) {
      YearMonth transactionMonth = YearMonth.from(transaction.getTransactedAt().atZone(seoul));
      long[] monthTotals = monthlyTotals.get(transactionMonth);
      if (monthTotals != null) {
        if ("INCOME".equalsIgnoreCase(transaction.getType())) monthTotals[0] += transaction.getAmount();
        else monthTotals[1] += transaction.getAmount();
      }
      if (transaction.getTransactedAt().isBefore(monthStart)) continue;
      if ("INCOME".equalsIgnoreCase(transaction.getType())) {
        income += transaction.getAmount();
      } else {
        expense += transaction.getAmount();
        categories.merge(transaction.getCategory(), transaction.getAmount(), Long::sum);
      }
    }
    List<MonthlyPoint> monthly = new ArrayList<>();
    monthlyTotals.forEach((month, totals) -> monthly.add(new MonthlyPoint(month.toString(), totals[0], totals[1])));
    return new Overview(income, expense, income - expense, categories, all.size(), monthly);
  }

  public record Overview(
    long income,
    long expense,
    long remaining,
    Map<String, Long> categories,
    int transactionCount,
    List<MonthlyPoint> monthly
  ) {}

  public record MonthlyPoint(String month, long income, long expense) {}
}
