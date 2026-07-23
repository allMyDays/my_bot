package com.example.my_bot.unit.service;

import com.example.my_bot.service.CryptoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CryptoServiceTest {

    private static final String TEST_KEY = "1234567890123456";
    private CryptoService cryptoService;

    @BeforeEach
    void setUp() {
        cryptoService = new CryptoService(TEST_KEY);
    }

    @Test
    void shouldEncryptAndDecryptSuccessfully() {
        String original = "Hello, World!";
        String encrypted = cryptoService.encrypt(original);
        assertThat(encrypted).isNotNull().isNotEqualTo(original);
        String decrypted = cryptoService.decrypt(encrypted);
        assertThat(decrypted).isEqualTo(original);
    }

    @Test
    void shouldHandleEmptyString() {
        String original = "";
        String encrypted = cryptoService.encrypt(original);
        assertThat(encrypted).isNotNull();
        String decrypted = cryptoService.decrypt(encrypted);
        assertThat(decrypted).isEmpty();
    }

    @Test
    void shouldHandleLongText() {
        String original = "a".repeat(1000);
        String encrypted = cryptoService.encrypt(original);
        String decrypted = cryptoService.decrypt(encrypted);
        assertThat(decrypted).isEqualTo(original);
    }

    @Test
    void shouldThrowRuntimeExceptionForInvalidDecrypt() {
        String invalidEncrypted = "invalid_base64!!!";
        assertThatThrownBy(() -> cryptoService.decrypt(invalidEncrypted))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void shouldThrowRuntimeExceptionForNullEncrypt() {
        assertThatThrownBy(() -> cryptoService.encrypt(null))
                .isInstanceOf(RuntimeException.class);
    }
}