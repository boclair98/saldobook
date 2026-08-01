package kr.saldo.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class WriteRequestFilterTest {
  private final WriteRequestFilter filter = new WriteRequestFilter();

  @Test
  void rejectsUnsafeRequestWithoutApplicationHeader() throws Exception {
    var request = new MockHttpServletRequest("POST", "/api/transactions");
    var response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertThat(response.getStatus()).isEqualTo(403);
  }

  @Test
  void acceptsUnsafeRequestWithApplicationHeader() throws Exception {
    var request = new MockHttpServletRequest("DELETE", "/api/transactions/123");
    request.addHeader("X-Saldo-Request", "web");
    var response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  void acceptsSafeRequestWithoutApplicationHeader() throws Exception {
    var request = new MockHttpServletRequest("GET", "/api/overview");
    var response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertThat(response.getStatus()).isEqualTo(200);
  }
}
