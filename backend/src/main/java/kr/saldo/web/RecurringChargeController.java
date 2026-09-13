package kr.saldo.web;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import kr.saldo.domain.RecurringCharge;
import kr.saldo.repo.RecurringChargeRepository;
import kr.saldo.service.CurrentUserService;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/recurring")
public class RecurringChargeController {
  private static final long MAX_ACTIVE_CHARGES = 50;

  private final RecurringChargeRepository recurringCharges;
  private final CurrentUserService currentUser;

  public RecurringChargeController(RecurringChargeRepository recurringCharges, CurrentUserService currentUser) {
    this.recurringCharges = recurringCharges;
    this.currentUser = currentUser;
  }

  @GetMapping
  @Transactional(readOnly = true)
  public List<RecurringChargeResponse> list(HttpSession session) {
    return recurringCharges.findByUserIdAndActiveTrueOrderByDayOfMonthAsc(currentUser.require(session).getId()).stream()
      .map(RecurringChargeController::response)
      .toList();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  public RecurringChargeResponse create(@Valid @RequestBody CreateRecurringCharge request, HttpSession session) {
    var user = currentUser.require(session);
    if (recurringCharges.countByUserIdAndActiveTrue(user.getId()) >= MAX_ACTIVE_CHARGES) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "고정비는 최대 50개까지 저장할 수 있습니다.");
    }
    var saved = recurringCharges.save(new RecurringCharge(
      user.getId(), request.name(), request.category(), request.amount(), request.dayOfMonth()
    ));
    return response(saved);
  }

  @DeleteMapping("/{chargeId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Transactional
  public void delete(@PathVariable UUID chargeId, HttpSession session) {
    var user = currentUser.require(session);
    var charge = recurringCharges.findByIdAndUserId(chargeId, user.getId())
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "고정비를 찾을 수 없습니다."));
    charge.deactivate();
    recurringCharges.save(charge);
  }

  private static RecurringChargeResponse response(RecurringCharge charge) {
    return new RecurringChargeResponse(charge.getId(), charge.getName(), charge.getCategory(), charge.getAmount(), charge.getDayOfMonth());
  }

  public record RecurringChargeResponse(UUID id, String name, String category, long amount, int dayOfMonth) {}

  public record CreateRecurringCharge(
    @NotBlank @Size(max = 120) String name,
    @NotBlank @Size(max = 40) String category,
    @Min(1) @Max(999_999_999_999L) long amount,
    @Min(1) @Max(31) int dayOfMonth
  ) {}
}
