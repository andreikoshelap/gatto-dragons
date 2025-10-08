package com.gatto.dragon.util;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Pattern;

/**
 * Normalizes adId for encrypted messages; handles standard and URL-safe base64.
 */
@Component
public class MessageIdNormalizer {

    private static final Pattern VALID_AD = Pattern.compile("^[A-Za-z0-9]{8}$");
    private static final Pattern B64_ALLOWED = Pattern.compile("^[A-Za-z0-9+/_-]+={0,2}$");

    public String normalizeAdId(String adIdRaw, Boolean encrypted) {
        // Trim raw id; keep empty string if null
        String raw = adIdRaw == null ? "" : adIdRaw.trim();

        // If not encrypted, return as-is (client will URI-encode path segments)
        if (!Boolean.TRUE.equals(encrypted)) return raw;

        // Quick base64 heuristic
        if (!looksLikeBase64(raw)) return raw;

        try {
            boolean urlSafe = raw.indexOf('-') >= 0 || raw.indexOf('_') >= 0;
            byte[] bytes = (urlSafe ? Base64.getUrlDecoder() : Base64.getDecoder()).decode(raw);
            String decoded = new String(bytes, StandardCharsets.UTF_8).trim();

            // Use decoded only if it looks like a typical 8-char token
            if (VALID_AD.matcher(decoded).matches()) return decoded;
        } catch (IllegalArgumentException ignore) {
            // Not actually base64 — fall back to raw
        }
        return raw;
    }

    public boolean looksLikeBase64(String s) {
        // multiple of 4, allowed chars, up to two '=' paddings
        return s.length() % 4 == 0 && B64_ALLOWED.matcher(s).matches();
    }
}
