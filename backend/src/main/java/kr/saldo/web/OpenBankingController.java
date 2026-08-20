package kr.saldo.web;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import kr.saldo.service.CurrentUserService;
import kr.saldo.service.OpenBankingService;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/openbanking")
public class OpenBankingController {
  private static final Logger log = LoggerFactory.getLogger(OpenBankingController.class);
  private final CurrentUserService currentUser;
  private final OpenBankingService openBanking;

  @Value("${OPEN_BANKING_CLIENT_ID:}") private String clientId;
  @Value("${OPEN_BANKING_REDIRECT_URI:}") private String redirectUri;
  @Value("${OPEN_BANKING_AUTHORIZE_URL:https://testapi.openbanking.or.kr/oauth/2.0/authorize}") private String authorizeUrl;
  @Value("${PUBLIC_URL:https://saldobook.coders.kr}") private String publicUrl;

  public OpenBankingController(CurrentUserService currentUser, OpenBankingService openBanking) {
    this.currentUser = currentUser;
    this.openBanking = openBanking;
  }

  @PostMapping("/connect")
  public Map<String, String> connect(HttpSession session) {
    var user = currentUser.require(session);
    if (!openBanking.enabled()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "계좌 연동 기능이 현재 비활성화되어 있습니다.");
    }
    if (!openBanking.configured()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "오픈뱅킹 서버 설정이 완료되지 않았습니다.");
    }
    // 금융결제원 테스트베드는 state를 최대 32자로 제한한다.
    String state = UUID.randomUUID().toString().replace("-", "");
    session.setAttribute("openBankingState", state);
    session.setAttribute("openBankingUserId", user.getId().toString());
    String url = UriComponentsBuilder.fromUriString(authorizeUrl)
      .queryParam("response_type", "code")
      .queryParam("client_id", clientId)
      .queryParam("redirect_uri", redirectUri)
      .queryParam("scope", "login inquiry")
      .queryParam("state", state)
      .queryParam("auth_type", "0")
      .build().encode().toUriString();
    return Map.of("authorizeUrl", url);
  }

  @GetMapping("/callback")
  public void callback(
    @RequestParam(required = false) String code,
    @RequestParam String state,
    @RequestParam(required = false) String error,
    HttpSession session,
    HttpServletResponse response
  ) throws IOException {
    Object expectedState = session.getAttribute("openBankingState");
    Object expectedUserId = session.getAttribute("openBankingUserId");
    session.removeAttribute("openBankingState");
    session.removeAttribute("openBankingUserId");
    if (expectedState == null || expectedUserId == null || !state.equals(expectedState.toString())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "만료되었거나 올바르지 않은 계좌 연결 요청입니다.");
    }
    if (error != null || code == null || code.isBlank()) {
      response.sendRedirect(publicUrl + "/?openbanking=cancelled");
      return;
    }
    var user = currentUser.require(session);
    if (!user.getId().toString().equals(expectedUserId.toString())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "계좌 연결 사용자가 일치하지 않습니다.");
    }
    try {
      openBanking.connect(user.getId(), code);
      response.sendRedirect(publicUrl + "/?openbanking=connected");
    } catch (Exception exception) {
      log.warn("Open Banking callback could not complete: {}", exception.getMessage());
      response.sendRedirect(publicUrl + "/?openbanking=error");
    }
  }

  @GetMapping("/accounts")
  public OpenBankingService.ConnectionView accounts(HttpSession session) {
    var user = currentUser.require(session);
    return openBanking.view(user.getId());
  }

  @PostMapping("/sync")
  public OpenBankingService.SyncResult sync(HttpSession session) {
    var user = currentUser.require(session);
    return openBanking.sync(user.getId());
  }

  @DeleteMapping("/connection")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void disconnect(HttpSession session) {
    var user = currentUser.require(session);
    openBanking.disconnect(user.getId());
  }

  @DeleteMapping("/accounts/{accountId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void disconnectAccount(@PathVariable UUID accountId, HttpSession session) {
    var user = currentUser.require(session);
    openBanking.disconnectAccount(user.getId(), accountId);
  }
}
