package kr.saldo.web;

import jakarta.servlet.http.HttpSession;
import kr.saldo.service.CurrentUserService;
import kr.saldo.service.SpendingNavigatorService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/navigator")
public class SpendingNavigatorController {
  private final SpendingNavigatorService navigator;
  private final CurrentUserService currentUser;

  public SpendingNavigatorController(SpendingNavigatorService navigator, CurrentUserService currentUser) {
    this.navigator = navigator;
    this.currentUser = currentUser;
  }

  @GetMapping
  public SpendingNavigatorService.Navigator get(HttpSession session) {
    var user = currentUser.require(session);
    return navigator.build(user.getId());
  }
}
