package kr.saldo.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class TokenCryptoService {
  private static final int NONCE_LENGTH = 12;
  private static final int TAG_LENGTH_BITS = 128;
  private final SecretKeySpec key;
  private final SecureRandom random = new SecureRandom();

  public TokenCryptoService(@Value("${TOKEN_ENCRYPTION_KEY}") String encodedKey) {
    byte[] decoded = Base64.getDecoder().decode(encodedKey);
    if (decoded.length != 32) {
      throw new IllegalStateException("TOKEN_ENCRYPTION_KEY는 Base64로 인코딩한 32바이트 키여야 합니다.");
    }
    this.key = new SecretKeySpec(decoded, "AES");
  }

  public String encrypt(String plainText) {
    if (plainText == null || plainText.isBlank()) return null;
    try {
      byte[] nonce = new byte[NONCE_LENGTH];
      random.nextBytes(nonce);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
      byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(
        ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted).array()
      );
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("토큰을 암호화하지 못했습니다.", exception);
    }
  }

  public String decrypt(String encoded) {
    if (encoded == null || encoded.isBlank()) return null;
    try {
      byte[] payload = Base64.getDecoder().decode(encoded);
      ByteBuffer buffer = ByteBuffer.wrap(payload);
      byte[] nonce = new byte[NONCE_LENGTH];
      buffer.get(nonce);
      byte[] encrypted = new byte[buffer.remaining()];
      buffer.get(encrypted);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
      return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    } catch (GeneralSecurityException | IllegalArgumentException exception) {
      throw new IllegalStateException("저장된 토큰을 복호화하지 못했습니다.", exception);
    }
  }
}
