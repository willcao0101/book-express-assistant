package com.bookexpress.backend.service;

import org.springframework.stereotype.Service;

@Service
public class NormalizationService {

    public String normalize(String s) {
        if (s == null) return "";
        String x = s.toLowerCase().trim();
        x = x.replaceAll("[^a-z0-9\\s]", " ");
        x = x.replaceAll("\\s+", " ").trim();
        return x;
    }

    public String authorBucket(String authorNorm) {
        if (authorNorm == null || authorNorm.isBlank()) return "Author Other";
        char c = authorNorm.charAt(0);
        if (c < 'a' || c > 'z') return "Author Other";
        if (c <= 'c') return "Author A-C";
        if (c <= 'f') return "Author D-F";
        if (c <= 'i') return "Author G-I";
        if (c <= 'l') return "Author J-L";
        if (c <= 'o') return "Author M-O";
        if (c <= 'r') return "Author P-R";
        if (c <= 'u') return "Author S-U";
        return "Author V-Z";
    }
}
