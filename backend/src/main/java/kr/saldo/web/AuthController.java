package kr.saldo.web;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import kr.saldo.domain.AppUser;
import kr.saldo.repo.AppUserRepository;
import kr.saldo.service.CurrentUserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final AppUserRepository users;
  private final CurrentUserService currentUser;
  private final RestClient http = RestClient.create();

  @Value("${GOOGLE_CLIENT_ID}") private String googleClientId;
  @Value("${GOOGLE_CLIENT_SECRET}") private String googleClientSecret;
  @Value("${KAKAO_REST_API_KEY}") private String kakaoRestApiKey;
  @Value("${KAKAO_CLIENT_SECRET}") private String kakaoClientSecret;
  @Value("${PUBLIC_URL:https://saldobook.coders.kr}") private String publicUrl;

  public AuthController(AppUserRepository users, CurrentUserService currentUser) {
    this.users = users;
    this.currentUser = currentUser;
  }

  @GetMapping("/google")
  public void googleStart(HttpSession session, HttpServletResponse response) throws IOException {
    String state = beginOAuth(session, "google");
    URI target = UriComponentsBuilder.fromUriString("https://accounts.google.com/o/oauth2/v2/auth")
      .queryParam("client_id", googleClientId)
      .queryParam("redirect_uri", callback("google"))
      .queryParam("response_type", "code")
      .queryParam("scope", "openid email profile")
      .queryParam("state", state)
      .queryParam("prompt", "select_account")
      .build().encode().toUri();
    response.sendRedirect(target.toString());
  }

  @GetMapping("/kakao")
  public void kakaoStart(HttpSession session, HttpServletResponse response) throws IOException {
    String state = beginOAuth(session, "kakao");
    URI target = UriComponentsBuilder.fromUriString("https://kauth.kakao.com/oauth/authorize")
      .queryParam("client_id", kakaoRestApiKey)
      .queryParam("redirect_uri", callback("kakao"))
      .queryParam("response_type", "code")
      .queryParam("state", state)
      .build().encode().toUri();
    response.sendRedirect(target.toString());
  }

  @GetMapping("/google/callback")
  public void googleCallback(
    @RequestParam String code,
    @RequestParam String state,
    HttpServletRequest request,
    HttpSession session,
    HttpServletResponse response
  ) throws IOException {
    validateState(session, "google", state);
    var form = new LinkedMultiValueMap<String, String>();
    form.add("code", code);
    form.add("client_id", googleClientId);
    form.add("client_secret", googleClientSecret);
    form.add("redirect_uri", callback("google"));
    form.add("grant_type", "authorization_code");
    JsonNode token = http.post().uri("https://oauth2.googleapis.com/token")
      .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(JsonNode.class);
    JsonNode profile = http.get().uri("https://openidconnect.googleapis.com/v1/userinfo")
      .headers(headers -> headers.setBearerAuth(requiredText(token, "access_token")))
      .retrieve().body(JsonNode.class);
    signIn(request, "google", requiredText(profile, "sub"), profile.path("email").asText(null), profile.path("name").asText("회원"));
    response.sendRedirect(publicUrl);
  }

  @GetMapping("/kakao/callback")
  public void kakaoCallback(
    @RequestParam String code,
    @RequestParam String state,
    HttpServletRequest request,
    HttpSession session,
    HttpServletResponse response
  ) throws IOException {
    validateState(session, "kakao", state);
    var form = new LinkedMultiValueMap<String, String>();
    form.add("grant_type", "authorization_code");
    form.add("client_id", kakaoRestApiKey);
    form.add("client_secret", kakaoClientSecret);
    form.add("redirect_uri", callback("kakao"));
    form.add("code", code);
    JsonNode token = http.post().uri("https://kauth.kakao.com/oauth/token")
      .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(JsonNode.class);
    JsonNode profile = http.get().uri("https://kapi.kakao.com/v2/user/me")
      .headers(headers -> headers.setBearerAuth(requiredText(token, "access_token")))
      .retrieve().body(JsonNode.class);
    JsonNode account = profile.path("kakao_account");
    signIn(
      request,
      "kakao",
      requiredText(profile, "id"),
      account.path("email").asText(null),
      account.path("profile").path("nickname").asText("회원")
    );
    response.sendRedirect(publicUrl);
  }

  @GetMapping("/me")
  public Map<String, Object> me(HttpSession session) {
    Object value = session.getAttribute("userId");
    if (value == null) return Map.of("authenticated", false);
    return users.findById(UUID.fromString(value.toString()))
      .<Map<String, Object>>map(user -> Map.of(
        "authenticated", true,
        "name", user.getDisplayName(),
        "userKey", user.getId().toString().substring(0, 8)
      ))
      .orElseGet(() -> Map.of("authenticated", false));
  }

  @GetMapping("/logout")
  public void logout(HttpSession session, HttpServletResponse response) throws IOException {
    session.invalidate();
    response.sendRedirect(publicUrl);
  }

  private String beginOAuth(HttpSession session, String provider) {
    String state = UUID.randomUUID().toString();
    session.setAttribute("oauthProvider", provider);
    session.setAttribute("oauthState", state);
    return state;
  }

  private void validateState(HttpSession session, String provider, String state) {
    if (!provider.equals(session.getAttribute("oauthProvider")) || !state.equals(session.getAttribute("oauthState"))) {
      throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "로그인 요청이 만료되었거나 유효하지 않습니다.");
    }
    session.removeAttribute("oauthProvider");
    session.removeAttribute("oauthState");
  }

  private void signIn(HttpServletRequest request, String provider, String providerId, String email, String name) {
    AppUser user = users.findByProviderAndProviderId(provider, providerId)
      .orElseGet(() -> users.save(new AppUser(provider, providerId, email, name)));
    currentUser.signIn(request, user);
  }

  private String callback(String provider) {
    return publicUrl + "/api/auth/" + provider + "/callback";
  }

  private String requiredText(JsonNode node, String field) {
    String value = node == null ? null : node.path(field).asText(null);
    if (value == null || value.isBlank()) throw new IllegalStateException("OAuth 응답에 " + field + " 값이 없습니다.");
    return value;
  }
}
