package kr.saldo.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.saldo.domain.BankAccount;
import kr.saldo.domain.LedgerTransaction;
import kr.saldo.domain.OpenBankingConnection;
import kr.saldo.repo.BankAccountRepository;
import kr.saldo.repo.OpenBankingConnectionRepository;
import kr.saldo.repo.TransactionRepository;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OpenBankingServiceTest {
  private final OpenBankingConnectionRepository connections = mock(OpenBankingConnectionRepository.class);
  private final BankAccountRepository accounts = mock(BankAccountRepository.class);
  private final TransactionRepository transactions = mock(TransactionRepository.class);
  private final TokenCryptoService crypto = new TokenCryptoService(
    Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8))
  );
  private MockWebServer server;
  private OpenBankingService service;

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
    service = new OpenBankingService(connections, accounts, transactions, crypto, new ObjectMapper());
    String baseUrl = server.url("/v2.0").toString().replaceAll("/$", "");
    ReflectionTestUtils.setField(service, "clientId", "client-id");
    ReflectionTestUtils.setField(service, "clientSecret", "client-secret");
    ReflectionTestUtils.setField(service, "redirectUri", "https://saldobook.example/api/openbanking/callback");
    ReflectionTestUtils.setField(service, "useOrgCode", "A123456789");
    ReflectionTestUtils.setField(service, "tokenUrl", server.url("/oauth/2.0/token").toString());
    ReflectionTestUtils.setField(service, "apiBaseUrl", baseUrl);
  }

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  @Test
  void exchangesAuthorizationCodeEncryptsTokensAndDiscoversAccounts() throws Exception {
    UUID userId = UUID.randomUUID();
    when(connections.findByUserId(userId)).thenReturn(Optional.empty());
    when(accounts.findByUserIdAndFintechUseNum(userId, "fin-001")).thenReturn(Optional.empty());
    enqueue("""
      {"access_token":"access-123","refresh_token":"refresh-123","user_seq_no":"42","scope":"login inquiry","expires_in":3600}
      """);
    enqueue("""
      {"rsp_code":"A0000","res_list":[{"fintech_use_num":"fin-001","bank_code_std":"004","bank_name":"국민은행","account_num_masked":"123-***-456"}]}
      """);

    service.connect(userId, "authorization-code");

    ArgumentCaptor<OpenBankingConnection> connection = ArgumentCaptor.forClass(OpenBankingConnection.class);
    verify(connections).save(connection.capture());
    assertThat(connection.getValue().getAccessTokenEncrypted()).doesNotContain("access-123");
    assertThat(crypto.decrypt(connection.getValue().getAccessTokenEncrypted())).isEqualTo("access-123");
    verify(accounts).save(argThat(account ->
      account.getUserId().equals(userId) && account.getFintechUseNum().equals("fin-001")
    ));
    assertThat(server.takeRequest().getPath()).isEqualTo("/oauth/2.0/token");
    assertThat(server.takeRequest().getPath()).contains("/v2.0/user/me?user_seq_no=42");
  }

  @Test
  void synchronizesBalanceAndAllTransactionPagesWithoutDuplicates() throws Exception {
    UUID userId = UUID.randomUUID();
    var connection = new OpenBankingConnection(
      userId, crypto.encrypt("access-token"), crypto.encrypt("refresh-token"),
      "42", "login inquiry", Instant.now().plusSeconds(3600)
    );
    var account = new BankAccount(userId, "004", "국민은행", "123-***-456", "fin-001");
    when(connections.findByUserId(userId)).thenReturn(Optional.of(connection));
    when(accounts.findByUserIdAndFintechUseNum(userId, "fin-001")).thenReturn(Optional.of(account));
    when(accounts.findByUserIdAndActiveTrueOrderByConnectedAtAsc(userId)).thenReturn(List.of(account));
    when(transactions.existsByUserIdAndExternalId(eq(userId), any())).thenReturn(false);

    enqueue("""
      {"rsp_code":"A0000","res_list":[{"fintech_use_num":"fin-001","bank_code_std":"004","bank_name":"국민은행","account_num_masked":"123-***-456"}]}
      """);
    enqueue("""
      {"rsp_code":"A0000","balance_amt":"105000","available_amt":"100000","product_name":"입출금통장","account_type":"1"}
      """);
    enqueue("""
      {"rsp_code":"A0000","next_page_yn":"Y","befor_inquiry_trace_info":"NEXT-TRACE","res_list":[
        {"tran_date":"20260801","tran_time":"101010","inout_type":"출금","tran_amt":"5000","after_balance_amt":"100000","print_content":"편의점"}
      ]}
      """);
    enqueue("""
      {"rsp_code":"A0000","next_page_yn":"N","res_list":[
        {"tran_date":"20260731","tran_time":"090000","inout_type":"입금","tran_amt":"100000","after_balance_amt":"105000","print_content":"급여"}
      ]}
      """);

    OpenBankingService.SyncResult result = service.sync(userId);

    assertThat(result.accountCount()).isEqualTo(1);
    assertThat(result.syncedAccountCount()).isEqualTo(1);
    assertThat(result.importedTransactionCount()).isEqualTo(2);
    assertThat(result.issues()).isEmpty();
    assertThat(account.getBalance()).isEqualTo(105000);
    ArgumentCaptor<LedgerTransaction> saved = ArgumentCaptor.forClass(LedgerTransaction.class);
    verify(transactions, times(2)).save(saved.capture());
    assertThat(saved.getAllValues()).extracting(LedgerTransaction::getType)
      .containsExactly("EXPENSE", "INCOME");

    server.takeRequest();
    server.takeRequest();
    String firstPage = server.takeRequest().getPath();
    String secondPage = server.takeRequest().getPath();
    assertThat(firstPage).doesNotContain("befor_inquiry_trace_info");
    assertThat(secondPage).contains("befor_inquiry_trace_info=NEXT-TRACE");
  }

  private void enqueue(String json) {
    server.enqueue(new MockResponse()
      .setHeader("Content-Type", "application/json")
      .setBody(json));
  }
}
