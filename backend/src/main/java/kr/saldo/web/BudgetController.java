package kr.saldo.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.servlet.http.HttpSession;
import kr.saldo.domain.MonthlyBudget;
import kr.saldo.repo.MonthlyBudgetRepository;
import kr.saldo.service.CurrentUserService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;

@RestController
@RequestMapping("/api/budget")
public class BudgetController {
  private final MonthlyBudgetRepository budgets;
  private final CurrentUserService currentUser;

  public BudgetController(MonthlyBudgetRepository budgets, CurrentUserService currentUser) {
    this.budgets = budgets;
    this.currentUser = currentUser;
  }

  @GetMapping
  public BudgetResponse get(
    @RequestParam(required = false) String month,
    HttpSession session
  ) {
    var user = currentUser.require(session);
    String yearMonth = normalize(month);
    return budgets.findByUserIdAndYearMonth(user.getId(), yearMonth)
      .map(value -> new BudgetResponse(value.getYearMonth(), value.getAmount()))
      .orElse(new BudgetResponse(yearMonth, 0));
  }

  @PutMapping
  @Transactional
  public BudgetResponse save(
    @Valid @RequestBody SaveBudget request,
    HttpSession session
  ) {
    var user = currentUser.require(session);
    var budget = budgets.findByUserIdAndYearMonth(user.getId(), request.month())
      .orElseGet(() -> new MonthlyBudget(user.getId(), request.month(), request.amount()));
    budget.changeAmount(request.amount());
    MonthlyBudget saved = budgets.save(budget);
    return new BudgetResponse(saved.getYearMonth(), saved.getAmount());
  }

  private String normalize(String month) {
    if (month == null || month.isBlank()) return YearMonth.now().toString();
    return YearMonth.parse(month).toString();
  }

  public record SaveBudget(
    @Pattern(regexp = "\\d{4}-\\d{2}") String month,
    @Min(0) long amount
  ) {}

  public record BudgetResponse(String month, long amount) {}
}
