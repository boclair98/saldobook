package kr.saldo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.saldo.domain.BankAccount;
import kr.saldo.domain.LedgerTransaction;
import kr.saldo.domain.OpenBankingConnection;
import kr.saldo.repo.BankAccountRepository;
import kr.saldo.repo.OpenBankingConnectionRepository;
import kr.saldo.repo.TransactionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class OpenBankingService {
  private static final Logger log = LoggerFactory.getLogger(OpenBankingService.class);
  private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
  private static final DateTimeFormatter API_DATETIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
  private static final String RANDOM_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";

  private final OpenBankingConnectionRepository connections;
  private final BankAccountRepository accounts;
  private final TransactionRepository transactions;
  private final TokenCryptoService crypto;
  private final ObjectMapper objectMapper;
  private final RestClient http = RestClient.create();

  @Value("${OPEN_BANKING_CLIENT_ID:}") private String clientId;
  @Value("${OPEN_BANKING_CLIENT_SECRET:}") private String clientSecret;
  @Value("${OPEN_BANKING_REDIRECT_URI:}") private String redirectUri;
  @Value("${OPEN_BANKING_USE_ORG_CODE:}") private String useOrgCode;
  @Value("${OPEN_BANKING_TOKEN_URL:https://testapi.openbanking.or.kr/oauth/2.0/token}") private String tokenUrl;
  @Value("${OPEN_BANKING_API_BASE_URL:https://testapi.openbanking.or.kr/v2.0}") private String apiBaseUrl;

  public OpenBankingService(
    OpenBankingConnectionRepository connections,
    BankAccountRepository accounts,
    TransactionRepository transactions,
    TokenCryptoService crypto,
    ObjectMapper objectMapper
  ) {
    this.connections = connections;
    this.accounts = accounts;
    this.transactions = transactions;
    this.crypto = crypto;
    this.objectMapper = objectMapper;
  }

  public boolean configured() {
    return !clientId.isBlank() && !clientSecret.isBlank() && !redirectUri.isBlank();
  }

  public boolean transactionSyncConfigured() {
    return useOrgCode.matches("[A-Za-z0-9]{10}");
  }

  public boolean testMode() {
    return apiBaseUrl.contains("testapi.openbanking.or.kr");
  }

  @Transactional
  public void connect(UUID userId, String authorizationCode) {
    requireConfigured();
    var form = new LinkedMultiValueMap<String, String>();
    form.add("code", authorizationCode);
    form.add("client_id", clientId);
    form.add("client_secret", clientSecret);
    form.add("redirect_uri", redirectUri);
    form.add("grant_type", "authorization_code");
    JsonNode token = requestToken(form);
    String accessToken = required(token, "access_token");
    String refreshToken = text(token, "refresh_token");
    String userSeqNo = required(token, "user_seq_no");
    String scope = text(token, "scope");
    Instant expiresAt = expiresAt(token);

    OpenBankingConnection connection = connections.findByUserId(userId)
      .orElseGet(() -> new OpenBankingConnection(
        userId,
        crypto.encrypt(accessToken),
        crypto.encrypt(refreshToken),
        userSeqNo,
        scope,
        expiresAt
      ));
    if (connection.getId() != null) {
      connection.updateTokens(
        crypto.encrypt(accessToken),
        crypto.encrypt(refreshToken),
        userSeqNo,
        scope,
        expiresAt
      );
    }
    connections.save(connection);
    refreshAccountDirectory(connection, accessToken, true);
  }

  @Transactional
  public SyncResult sync(UUID userId) {
    OpenBankingConnection connection = connections.findByUserId(userId)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "먼저 오픈뱅킹 계좌를 연결해 주세요."));
    String accessToken = validAccessToken(connection);
    List<SyncIssue> issues = new ArrayList<>();
    try {
      refreshAccountDirectory(connection, accessToken, false);
    } catch (KftcApiException exception) {
      log.warn("Open Banking sync failed: stage=ACCOUNT_DIRECTORY, code={}, message={}",
        exception.code(), exception.getMessage());
      issues.add(new SyncIssue(null, "계좌 목록", "ACCOUNT_DIRECTORY", exception.code(), exception.getMessage()));
    }

    List<BankAccount> userAccounts = accounts.findByUserIdAndActiveTrueOrderByConnectedAtAsc(userId);
    if (!transactionSyncConfigured()) {
      return new SyncResult(userAccounts.size(), 0, 0, false, issues);
    }

    int imported = 0;
    int synced = 0;
    for (BankAccount account : userAccounts) {
      boolean balanceSynced = false;
      try {
        updateBalance(account, accessToken);
        balanceSynced = true;
      } catch (KftcApiException exception) {
        issues.add(issue(account, "BALANCE", exception));
      }
      try {
        imported += importTransactions(account, accessToken);
      } catch (KftcApiException exception) {
        issues.add(issue(account, "TRANSACTIONS", exception));
      }
      if (balanceSynced) synced++;
    }
    return new SyncResult(userAccounts.size(), synced, imported, true, issues);
  }

  @Transactional(readOnly = true)
  public ConnectionView view(UUID userId) {
    boolean connected = connections.findByUserId(userId).isPresent();
    List<AccountView> accountViews = accounts.findByUserIdAndActiveTrueOrderByConnectedAtAsc(userId).stream()
      .map(account -> new AccountView(
        account.getId(),
        account.getInstitutionName(),
        account.getMaskedNumber(),
        account.getBalance(),
        account.getAvailableBalance(),
        account.getProductName(),
        account.getLastSyncedAt()
      ))
      .toList();
    return new ConnectionView(connected, transactionSyncConfigured(), testMode(), accountViews);
  }

  @Transactional
  public void disconnect(UUID userId) {
    accounts.deleteByUserId(userId);
    connections.deleteByUserId(userId);
  }

  @Transactional
  public void disconnectAccount(UUID userId, UUID accountId) {
    BankAccount account = accounts.findByIdAndUserId(accountId, userId)
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "연결된 계좌를 찾을 수 없습니다."));
    account.disconnect();
    accounts.save(account);
  }

  private void refreshAccountDirectory(OpenBankingConnection connection, String accessToken, boolean reactivateAccounts) {
    URI uri = UriComponentsBuilder.fromUriString(apiBaseUrl + "/user/me")
      .queryParam("user_seq_no", connection.getUserSeqNo())
      .build().encode().toUri();
    JsonNode response = getJson(uri, accessToken, "계좌 목록");
    for (JsonNode item : response.path("res_list")) {
      String fintechUseNum = required(item, "fintech_use_num");
      String bankCode = required(item, "bank_code_std");
      String bankName = defaultText(item, "bank_name", "금융기관");
      String maskedNumber = defaultText(item, "account_num_masked", "계좌번호 비공개");
      BankAccount account = accounts.findByUserIdAndFintechUseNum(connection.getUserId(), fintechUseNum)
        .orElseGet(() -> new BankAccount(connection.getUserId(), bankCode, bankName, maskedNumber, fintechUseNum));
      account.updateIdentity(bankCode, bankName, maskedNumber);
      if (reactivateAccounts) account.reconnect();
      accounts.save(account);
    }
  }

  private void updateBalance(BankAccount account, String accessToken) {
    URI uri = UriComponentsBuilder.fromUriString(apiBaseUrl + "/account/balance/fin_num")
      .queryParam("bank_tran_id", bankTranId())
      .queryParam("fintech_use_num", account.getFintechUseNum())
      .queryParam("tran_dtime", API_DATETIME.format(LocalDateTime.now(KOREA)))
      .build().encode().toUri();
    JsonNode response = getJson(uri, accessToken, "잔액 조회");
    account.updateBalance(
      longValue(response, "balance_amt"),
      longValue(response, "available_amt"),
      text(response, "product_name"),
      text(response, "account_type")
    );
    accounts.save(account);
  }

  private int importTransactions(BankAccount account, String accessToken) {
    LocalDate today = LocalDate.now(KOREA);
    URI uri = UriComponentsBuilder.fromUriString(apiBaseUrl + "/account/transaction_list/fin_num")
      .queryParam("bank_tran_id", bankTranId())
      .queryParam("fintech_use_num", account.getFintechUseNum())
      .queryParam("inquiry_type", "A")
      .queryParam("inquiry_base", "D")
      .queryParam("from_date", today.minusDays(89).format(DateTimeFormatter.BASIC_ISO_DATE))
      .queryParam("from_time", "000000")
      .queryParam("to_date", today.format(DateTimeFormatter.BASIC_ISO_DATE))
      .queryParam("to_time", "235959")
      .queryParam("sort_order", "D")
      .queryParam("tran_dtime", API_DATETIME.format(LocalDateTime.now(KOREA)))
      .build().encode().toUri();
    JsonNode response = getJson(uri, accessToken, "거래내역 조회");

    int imported = 0;
    for (JsonNode item : response.path("res_list")) {
      String externalId = transactionFingerprint(account, item);
      if (transactions.existsByUserIdAndExternalId(account.getUserId(), externalId)) continue;
      String inout = defaultText(item, "inout_type", "출금");
      boolean income = inout.contains("입금");
      String merchant = defaultText(item, "print_content",
        defaultText(item, "printed_content", defaultText(item, "tran_type", "계좌 거래")));
      long amount = Math.abs(longValue(item, "tran_amt"));
      if (amount == 0) continue;
      Instant occurredAt = bankInstant(required(item, "tran_date"), defaultText(item, "tran_time", "000000"));
      transactions.save(new LedgerTransaction(
        account.getUserId(),
        account.getId(),
        merchant,
        income ? "계좌입금" : "계좌지출",
        amount,
        income ? "INCOME" : "EXPENSE",
        occurredAt,
        "OPEN_BANKING",
        externalId
      ));
      imported++;
    }
    return imported;
  }

  private String validAccessToken(OpenBankingConnection connection) {
    if (connection.getExpiresAt() == null || connection.getExpiresAt().isAfter(Instant.now().plusSeconds(120))) {
      return crypto.decrypt(connection.getAccessTokenEncrypted());
    }
    String refreshToken = crypto.decrypt(connection.getRefreshTokenEncrypted());
    if (refreshToken == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "오픈뱅킹 연결을 다시 인증해 주세요.");
    }
    var form = new LinkedMultiValueMap<String, String>();
    form.add("client_id", clientId);
    form.add("client_secret", clientSecret);
    form.add("refresh_token", refreshToken);
    form.add("grant_type", "refresh_token");
    form.add("scope", "login inquiry");
    JsonNode token = requestToken(form);
    String accessToken = required(token, "access_token");
    String nextRefreshToken = text(token, "refresh_token");
    connection.updateTokens(
      crypto.encrypt(accessToken),
      crypto.encrypt(nextRefreshToken),
      connection.getUserSeqNo(),
      defaultText(token, "scope", connection.getScope()),
      expiresAt(token)
    );
    connections.save(connection);
    return accessToken;
  }

  private JsonNode requestToken(LinkedMultiValueMap<String, String> form) {
    try {
      JsonNode response = http.post().uri(tokenUrl)
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .body(form)
        .retrieve().body(JsonNode.class);
      if (response == null || response.hasNonNull("error")) {
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "금융결제원 인증 토큰을 발급받지 못했습니다.");
      }
      return response;
    } catch (ResponseStatusException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "금융결제원 인증 서버와 통신하지 못했습니다.", exception);
    }
  }

  private JsonNode getJson(URI uri, String accessToken, String operation) {
    try {
      JsonNode response = http.get().uri(uri)
        .headers(headers -> headers.setBearerAuth(accessToken))
        .retrieve().body(JsonNode.class);
      ensureSuccess(response, operation);
      return response;
    } catch (KftcApiException exception) {
      throw exception;
    } catch (RestClientResponseException exception) {
      JsonNode response = null;
      try {
        response = objectMapper.readTree(exception.getResponseBodyAsString());
      } catch (Exception ignored) {
        // 금융결제원이 JSON이 아닌 오류 본문을 반환한 경우에는 HTTP 상태만 안내한다.
      }
      if (response != null) {
        throw apiException(response, operation);
      }
      throw new KftcApiException(
        "HTTP_" + exception.getStatusCode().value(),
        operation + " 중 금융결제원 서버가 오류를 반환했습니다."
      );
    } catch (Exception exception) {
      throw new KftcApiException("NETWORK", operation + " 중 금융결제원 서버와 통신하지 못했습니다.");
    }
  }

  private void ensureSuccess(JsonNode response, String operation) {
    String code = text(response, "rsp_code");
    if (response == null || !"A0000".equals(code)) {
      throw apiException(response, operation);
    }
  }

  private KftcApiException apiException(JsonNode response, String operation) {
    String code = defaultText(response, "rsp_code", "UNKNOWN");
    String message = defaultText(response, "rsp_message",
      defaultText(response, "bank_rsp_message", operation + "에 실패했습니다."));
    return new KftcApiException(code, operation + ": " + safeMessage(message));
  }

  private SyncIssue issue(BankAccount account, String stage, KftcApiException exception) {
    log.warn("Open Banking sync failed: stage={}, code={}, message={}",
      stage, exception.code(), exception.getMessage());
    return new SyncIssue(
      account.getId(),
      account.getInstitutionName() + " " + account.getMaskedNumber(),
      stage,
      exception.code(),
      exception.getMessage()
    );
  }

  private String safeMessage(String message) {
    if (message == null || message.isBlank()) return "금융결제원 응답을 확인해 주세요.";
    String normalized = message.replaceAll("[\\r\\n\\t]", " ").trim();
    return normalized.substring(0, Math.min(normalized.length(), 160));
  }

  private void requireConfigured() {
    if (!configured()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "오픈뱅킹 서버 설정이 완료되지 않았습니다.");
    }
  }

  private String bankTranId() {
    if (!transactionSyncConfigured()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "금융결제원 이용기관코드 등록이 필요합니다.");
    }
    StringBuilder suffix = new StringBuilder(9);
    for (int index = 0; index < 9; index++) {
      suffix.append(RANDOM_CHARS.charAt(ThreadLocalRandom.current().nextInt(RANDOM_CHARS.length())));
    }
    return useOrgCode.toUpperCase(Locale.ROOT) + "U" + suffix;
  }

  private String transactionFingerprint(BankAccount account, JsonNode item) {
    String raw = String.join("|",
      account.getFintechUseNum(),
      text(item, "tran_date"),
      text(item, "tran_time"),
      text(item, "inout_type"),
      text(item, "tran_amt"),
      text(item, "after_balance_amt"),
      defaultText(item, "print_content", defaultText(item, "printed_content", ""))
    );
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (Exception exception) {
      throw new IllegalStateException("거래 식별자를 만들지 못했습니다.", exception);
    }
  }

  private Instant bankInstant(String date, String time) {
    String normalizedTime = String.format("%-6s", time).replace(' ', '0').substring(0, 6);
    LocalDateTime value = LocalDateTime.parse(date + normalizedTime, DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    return value.atZone(KOREA).toInstant();
  }

  private Instant expiresAt(JsonNode token) {
    long seconds = token == null ? 0 : token.path("expires_in").asLong(0);
    return seconds > 0 ? Instant.now().plusSeconds(seconds) : null;
  }

  private long longValue(JsonNode node, String field) {
    String value = text(node, field);
    if (value == null || value.isBlank()) return 0;
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException exception) {
      return 0;
    }
  }

  private String required(JsonNode node, String field) {
    String value = text(node, field);
    if (value == null || value.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "금융결제원 응답에 필수 정보가 없습니다.");
    }
    return value;
  }

  private String defaultText(JsonNode node, String field, String fallback) {
    String value = text(node, field);
    return value == null || value.isBlank() ? fallback : value;
  }

  private String text(JsonNode node, String field) {
    if (node == null) return null;
    return node.path(field).asText(null);
  }

  public record AccountView(
    UUID id,
    String institutionName,
    String maskedNumber,
    long balance,
    long availableBalance,
    String productName,
    Instant lastSyncedAt
  ) {}

  public record ConnectionView(
    boolean connected,
    boolean fullSyncConfigured,
    boolean testMode,
    List<AccountView> accounts
  ) {}
  public record SyncIssue(UUID accountId, String accountName, String stage, String code, String message) {}
  public record SyncResult(
    int accountCount,
    int syncedAccountCount,
    int importedTransactionCount,
    boolean fullSync,
    List<SyncIssue> issues
  ) {}

  private static final class KftcApiException extends RuntimeException {
    private final String code;

    private KftcApiException(String code, String message) {
      super(message);
      this.code = code;
    }

    private String code() {
      return code;
    }
  }
}
