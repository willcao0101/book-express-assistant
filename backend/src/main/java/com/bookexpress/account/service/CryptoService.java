package com.bookexpress.account.service;

import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

// Note: This is a placeholder. Use AES/GCM in production.
@Service
public class CryptoService {
    public String encrypt(String plain) {
        if (plain == null) return null;
        return Base64.getEncoder().encodeToString(plain.getBytes(StandardCharsets.UTF_8));
    }

    public String decrypt(String cipher) {
        if (cipher == null) return null;
        return new String(Base64.getDecoder().decode(cipher), StandardCharsets.UTF_8);
    }
}
