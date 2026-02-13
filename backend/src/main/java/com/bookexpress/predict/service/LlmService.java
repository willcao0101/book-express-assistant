package com.bookexpress.backend.service;

import com.bookexpress.backend.util.JsonUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class LlmService {

    public static class Candidate {
        private String id;
        private String category;
        private List<String> tags;
        private double ruleScore;

        public Candidate() {}

        public Candidate(String id, String category, List<String> tags, double ruleScore) {
            this.id = id;
            this.category = category;
            this.tags = tags;
            this.ruleScore = ruleScore;
        }

        public String getId() { return id; }
        public String getCategory() { return category; }
        public List<String> getTags() { return tags; }
        public double getRuleScore() { return ruleScore; }

        public void setId(String id) { this.id = id; }
        public void setCategory(String category) { this.category = category; }
        public void setTags(List<String> tags) { this.tags = tags; }
        public void setRuleScore(double ruleScore) { this.ruleScore = ruleScore; }
    }

    public static class RerankResult {
        private boolean called;
        private boolean valid;
        private String bestCandidateId;
        private String raw;

        public boolean isCalled() { return called; }
        public void setCalled(boolean called) { this.called = called; }
        public boolean isValid() { return valid; }
        public void setValid(boolean valid) { this.valid = valid; }
        public String getBestCandidateId() { return bestCandidateId; }
        public void setBestCandidateId(String bestCandidateId) { this.bestCandidateId = bestCandidateId; }
        public String getRaw() { return raw; }
        public void setRaw(String raw) { this.raw = raw; }
    }

    private final ObjectMapper mapper = new ObjectMapper();
    private final JsonUtil jsonUtil;

    public LlmService(JsonUtil jsonUtil) {
        this.jsonUtil = jsonUtil;
    }

    @Value("${llm.enabled:false}")
    private boolean llmEnabled;

    @Value("${llm.endpoint:http://127.0.0.1:8010/v1/chat/completions}")
    private String llmEndpoint;

    @Value("${llm.model:ollama/deepseek-r1:8b}")
    private String llmModel;

    @Value("${llm.token:}")
    private String llmToken;

    /**
     * Backward-compatible API:
     * Given title + allowed tags, choose top N tags only from allowed list.
     */
    public List<String> constrainedTags(String title, List<String> allowedTags, int topN) {
        if (allowedTags == null || allowedTags.isEmpty()) return Collections.emptyList();
        int n = Math.max(1, topN);

        // If LLM disabled, use lexical fallback directly.
        if (!llmEnabled) {
            return lexicalFallback(title, allowedTags, n);
        }

        try {
            String tagsJson = mapper.writeValueAsString(allowedTags);
            String prompt = """
                You are a tag selector.
                Select up to %d tags from the allowed tag list for this book title.

                Rules:
                1) You MUST choose only from allowed_tags.
                2) Do NOT invent new tags.
                3) Output JSON only:
                   {"tags":["tag1","tag2"]}

                Input:
                title: %s
                allowed_tags: %s
                """.formatted(n, safe(title), tagsJson);

            String content = callChat(prompt);
            if (content == null || content.isBlank()) {
                return lexicalFallback(title, allowedTags, n);
            }

            Map<String, Object> obj = jsonUtil.readMapSafe(content);
            Object tagsObj = obj.get("tags");
            if (!(tagsObj instanceof List<?> listObj)) {
                return lexicalFallback(title, allowedTags, n);
            }

            Set<String> allow = new LinkedHashSet<>(allowedTags);
            List<String> picked = new ArrayList<>();
            for (Object o : listObj) {
                if (o == null) continue;
                String t = String.valueOf(o).trim();
                if (!t.isEmpty() && allow.contains(t) && !picked.contains(t)) {
                    picked.add(t);
                }
            }

            if (picked.isEmpty()) return lexicalFallback(title, allowedTags, n);
            return picked.stream().limit(n).collect(Collectors.toList());

        } catch (Exception e) {
            return lexicalFallback(title, allowedTags, n);
        }
    }

    /**
     * Rerank candidate categories/tags by LLM.
     * The chosen id must be one of provided candidate ids.
     */
    public RerankResult rerankByCandidates(String title, String author, List<Candidate> candidates) {
        RerankResult rr = new RerankResult();
        rr.setCalled(false);
        rr.setValid(false);

        if (!llmEnabled || candidates == null || candidates.isEmpty()) {
            return rr;
        }

        rr.setCalled(true);

        try {
            String candidatesJson = mapper.writeValueAsString(candidates);
            String prompt = """
                You are a ranking engine.
                Choose exactly ONE candidate id from the provided list.

                Rules:
                1) You MUST choose one id from candidates.
                2) Do NOT invent new categories or tags.
                3) Return JSON ONLY in this exact format:
                   {"best_candidate_id":"C1","reason":"..."}

                Input:
                title: %s
                author: %s
                candidates: %s
                """.formatted(safe(title), safe(author), candidatesJson);

            String content = callChat(prompt);
            rr.setRaw(content);

            if (content == null || content.isBlank()) return rr;

            Map<String, Object> obj = jsonUtil.readMapSafe(content);
            Object idObj = obj.get("best_candidate_id");
            if (idObj == null) return rr;

            String bestId = String.valueOf(idObj).trim();
            if (bestId.isEmpty()) return rr;

            // Ensure best id is really in candidate set.
            Set<String> candidateIds = candidates.stream()
                    .map(Candidate::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            if (!candidateIds.contains(bestId)) return rr;

            rr.setBestCandidateId(bestId);
            rr.setValid(true);
            return rr;

        } catch (Exception e) {
            return rr;
        }
    }

    /**
     * Send one chat-completions request and return assistant content.
     * Uses HttpURLConnection with fixed-length body to avoid chunked transfer issues.
     */
    private String callChat(String prompt) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", llmModel);
            body.put("stream", false);
            body.put("messages", List.of(Map.of("role", "user", "content", prompt)));

            String reqJson = mapper.writeValueAsString(body);
            String rawResponse = postJson(llmEndpoint, llmToken, reqJson);
            return extractAssistantContent(rawResponse);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Low-level HTTP POST (JSON) with explicit Content-Length and Connection: close.
     */
    private String postJson(String endpoint, String token, String jsonBody) throws Exception {
        URL url = new URL(endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);
        conn.setUseCaches(false);

        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Connection", "close");

        if (token != null && !token.isBlank()) {
            conn.setRequestProperty("Authorization", "Bearer " + token);
        }

        byte[] bodyBytes = jsonBody.getBytes(StandardCharsets.UTF_8);
        conn.setFixedLengthStreamingMode(bodyBytes.length);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(bodyBytes);
            os.flush();
        }

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();

        String respBody = "";
        if (is != null) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
                respBody = sb.toString();
            }
        }

        if (code < 200 || code >= 300) {
            throw new RuntimeException("LLM HTTP " + code + ": " + respBody);
        }
        return respBody;
    }

    private String extractAssistantContent(String rawResponse) {
        try {
            JsonNode root = mapper.readTree(rawResponse);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) return null;
            return choices.get(0).path("message").path("content").asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> lexicalFallback(String title, List<String> allowedTags, int n) {
        String q = normalize(title);
        List<String> sorted = new ArrayList<>(allowedTags);
        sorted.sort((a, b) -> Integer.compare(scoreTag(q, b), scoreTag(q, a)));
        return sorted.stream().limit(n).collect(Collectors.toList());
    }

    private int scoreTag(String q, String tag) {
        String t = normalize(tag);
        if (q.isEmpty() || t.isEmpty()) return 0;
        int s = 0;
        for (String tk : t.split("\\s+")) {
            if (!tk.isBlank() && q.contains(tk)) s += 1;
        }
        return s;
    }

    private String normalize(String s) {
        if (s == null) return "";
        String x = s.toLowerCase(Locale.ROOT);
        x = x.replaceAll("[^a-z0-9\\s]+", " ");
        x = x.replaceAll("\\s+", " ").trim();
        return x;
    }

    private String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
