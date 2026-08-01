package kr.saldo.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenCryptoServiceTest {
  private final TokenCryptoService crypto = new TokenCryptoService(
    Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8))
  );

  @Test
  void encryptsWithRandomNonceAndDecrypts() {
    String first = crypto.encrypt("financial-access-token");
    String second = crypto.encrypt("financial-access-token");

    assertThat(first).isNotEqualTo(second);
    assertThat(crypto.decrypt(first)).isEqualTo("financial-access-token");
    assertThat(crypto.decrypt(second)).isEqualTo("financial-access-token");
  }

  @Test
  void rejectsTamperedCiphertext() {
    byte[] payload = Base64.getDecoder().decode(crypto.encrypt("secret"));
    payload[payload.length - 1] ^= 1;

    assertThatThrownBy(() -> crypto.decrypt(Base64.getEncoder().encodeToString(payload)))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("복호화");
  }
}
