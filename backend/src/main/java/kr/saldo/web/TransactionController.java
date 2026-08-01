package kr.saldo.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.servlet.http.HttpSession;
import kr.saldo.domain.LedgerTransaction;
import kr.saldo.repo.TransactionRepository;
import kr.saldo.service.CurrentUserService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {
  private final TransactionRepository transactions;
  private final CurrentUserService currentUser;

  public TransactionController(TransactionRepository transactions, CurrentUserService currentUser) {
    this.transactions = transactions;
    this.currentUser = currentUser;
  }

  @GetMapping
  public List<LedgerTransaction> list(HttpSession session) {
    var user = currentUser.require(session);
    return transactions.findTop100ByUserIdOrderByTransactedAtDesc(user.getId());
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public LedgerTransaction create(
    @Valid @RequestBody CreateTransaction request,
    HttpSession session
  ) {
    var user = currentUser.require(session);
    Instant occurredAt = request.transactedAt() == null ? Instant.now() : request.transactedAt();
    var transaction = new LedgerTransaction(
      user.getId(),
      request.merchant(),
      request.category(),
      request.amount(),
      request.type(),
      occurredAt,
      "MANUAL"
    );
    return transactions.save(transaction);
  }

  @DeleteMapping("/{transactionId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(
    @PathVariable UUID transactionId,
    HttpSession session
  ) {
    var user = currentUser.require(session);
    var transaction = transactions.findByIdAndUserId(transactionId, user.getId())
      .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND));
    transactions.delete(transaction);
  }

  public record CreateTransaction(
    @NotBlank @Size(max = 120) String merchant,
    @NotBlank @Size(max = 40) String category,
    @Min(1) long amount,
    @NotBlank @Pattern(regexp = "INCOME|EXPENSE") String type,
    Instant transactedAt
  ) {}
}
