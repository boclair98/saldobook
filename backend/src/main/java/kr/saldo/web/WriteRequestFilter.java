package kr.saldo.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
public class WriteRequestFilter extends OncePerRequestFilter {
  private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");
  private static final String HEADER = "X-Saldo-Request";

  @Override
  protected void doFilterInternal(
    HttpServletRequest request,
    HttpServletResponse response,
    FilterChain filterChain
  ) throws ServletException, IOException {
    if (!SAFE_METHODS.contains(request.getMethod())
      && !"web".equals(request.getHeader(HEADER))) {
      response.sendError(HttpStatus.FORBIDDEN.value(), "요청 검증 헤더가 없습니다.");
      return;
    }
    filterChain.doFilter(request, response);
  }
}
