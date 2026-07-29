package kr.saldo.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import kr.saldo.domain.AppUser;
import kr.saldo.repo.AppUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class CurrentUserService {
  private final AppUserRepository users;

  public CurrentUserService(AppUserRepository users) {
    this.users = users;
  }

  public AppUser require(HttpSession session) {
    Object value = session.getAttribute("userId");
    if (value == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
    }
    return users.findById(UUID.fromString(value.toString()))
      .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인 정보를 찾을 수 없습니다."));
  }

  public void signIn(HttpServletRequest request, AppUser user) {
    request.changeSessionId();
    request.getSession().setAttribute("userId", user.getId().toString());
  }
}
