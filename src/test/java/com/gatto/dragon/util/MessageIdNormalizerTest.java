package com.gatto.dragon.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class MessageIdNormalizerTest {

    MessageIdNormalizer norm;

    @BeforeEach
    void setUp() {
        norm = new MessageIdNormalizer();
    }

    // --- normalizeAdId ---

    @Test
    void returnsRawTrimmed_whenNotEncrypted() {
        String out = norm.normalizeAdId("  ABC123  ", false);
        assertThat(out).isEqualTo("ABC123");
    }

    @Test
    void returnsEmptyString_whenRawNull_andNotEncrypted() {
        String out = norm.normalizeAdId(null, false);
        assertThat(out).isEqualTo("");
    }

    @Test
    void encrypted_standardBase64_decodesToEightAlnum_thenUseDecoded() {
        // "Ab12Cd34" -> standard base64:
        String b64 = java.util.Base64.getEncoder().encodeToString("Ab12Cd34".getBytes());
        String out = norm.normalizeAdId(b64, true);
        assertThat(out).isEqualTo("Ab12Cd34");
    }

    @Test
    void encrypted_urlSafeBase64_decodesToEightAlnum_thenUseDecoded() {
        // "uXkN2TQI" -> url-safe base64:
        String urlB64 = java.util.Base64.getUrlEncoder().encodeToString("uXkN2TQI".getBytes());
        String out = norm.normalizeAdId(urlB64, true);
        assertThat(out).isEqualTo("uXkN2TQI");
    }

    @Test
    void encrypted_base64ButDecodedNotEightAlnum_returnsRaw() {
        // decoded -> "TOO_LONG_ID" (not 8 alphanumeric)
        String badDecoded = "TOO_LONG_ID";
        String b64 = java.util.Base64.getEncoder().encodeToString(badDecoded.getBytes());
        String out = norm.normalizeAdId(b64, true);
        assertThat(out).isEqualTo(b64); // keep as is
    }

    @Test
    void encrypted_notBase64Looking_returnsRaw() {
        String raw = "NOT_B64*"; // asterisk breaks the filter
        String out = norm.normalizeAdId(raw, true);
        assertThat(out).isEqualTo(raw);
    }

    @Test
    void encrypted_malformedBase64_caughtAndReturnsRaw() {
        String raw = "AAAA===="; // incorrect padding scheme
        String out = norm.normalizeAdId(raw, true);
        assertThat(out).isEqualTo(raw);
    }

    @Test
    void encrypted_nullRaw_becomesEmpty_notBase64Looking_returnsEmpty() {
        String out = norm.normalizeAdId(null, true);
        assertThat(out).isEqualTo("");
    }

    @Test
    void trimsBeforeBase64Decode() {
        String token = "Ab12Cd34";
        String b64 = java.util.Base64.getEncoder().encodeToString(token.getBytes());
        String withSpaces = "  " + b64 + "  ";
        String out = norm.normalizeAdId(withSpaces, true);
        assertThat(out).isEqualTo(token);
    }

    // --- looksLikeBase64 ---

    @Test
    void looksLikeBase64_acceptsStandardAndUrlSafe() {
        String std = java.util.Base64.getEncoder().encodeToString("Ab12Cd34".getBytes());   // length % 4 == 0
        String url = java.util.Base64.getUrlEncoder().encodeToString("uXkN2TQI".getBytes()); // also %4==0
        assertThat(norm.looksLikeBase64(std)).isTrue();
        assertThat(norm.looksLikeBase64(url)).isTrue();
    }

    @Test
    void looksLikeBase64_rejectsWrongLengthOrChars() {
        assertThat(norm.looksLikeBase64("abc")).isFalse();            // length%4!=0
        assertThat(norm.looksLikeBase64("abcd$")).isFalse();          // invalid character
        assertThat(norm.looksLikeBase64("====")).isFalse();
    }
}
