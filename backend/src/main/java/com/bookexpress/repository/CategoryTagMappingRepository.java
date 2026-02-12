package com.bookexpress.backend.repository;

import com.bookexpress.backend.util.JsonUtil;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Repository that builds and serves constrained category->tag mappings
 * from existing samples in DB.
 *
 * Rule:
 * - Output category must be one of existing categories in DB.
 * - Output tags must be one of existing tags bound to selected category.
 */
@Repository
public class CategoryTagMappingRepository {

    private final JdbcTemplate jdbc;
    private final JsonUtil jsonUtil;

    // In-memory cache for fast inference
    private volatile Map<String, Set<String>> categoryToTags = new ConcurrentHashMap<>();
    private volatile Map<String, Integer> categoryFrequency = new ConcurrentHashMap<>();

    public CategoryTagMappingRepository(JdbcTemplate jdbc, JsonUtil jsonUtil) {
        this.jdbc = jdbc;
        this.jsonUtil = jsonUtil;
    }

    /**
     * Rebuild mapping from DB samples.
     * It reads all non-null category + tags rows from books table.
     */
    public synchronized void rebuild() {
        Map<String, Set<String>> c2t = new HashMap<>();
        Map<String, Integer> freq = new HashMap<>();

        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT trademe_categories, shopify_tags
                FROM books
                WHERE trademe_categories IS NOT NULL
                  AND TRIM(trademe_categories) <> ''
                """);

        for (Map<String, Object> row : rows) {
            String category = (String) row.get("trademe_categories");
            String tagsJson = (String) row.get("shopify_tags");

            if (category == null || category.isBlank()) continue;
            category = category.trim();

            freq.merge(category, 1, Integer::sum);

            Set<String> tags = c2t.computeIfAbsent(category, k -> new LinkedHashSet<>());
            for (String t : jsonUtil.fromJsonArray(tagsJson)) {
                String norm = normalizeTag(t);
                if (!norm.isBlank()) tags.add(norm);
            }
        }

        this.categoryToTags = c2t;
        this.categoryFrequency = freq;
    }

    public boolean isLoaded() {
        return !categoryToTags.isEmpty();
    }

    public Set<String> getAllCategories() {
        return categoryToTags.keySet();
    }

    public Set<String> getTagsByCategory(String category) {
        return categoryToTags.getOrDefault(category, Collections.emptySet());
    }

    public String chooseMostFrequentCategory() {
        return categoryFrequency.entrySet().stream()
                .max(Comparator.comparingInt(Map.Entry::getValue))
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    /**
     * Prefer category that contains author bucket text.
     * If none found, fallback to global most frequent category.
     */
    public String chooseCategoryByAuthorBucket(String bucket) {
        String bucketLower = bucket == null ? "" : bucket.toLowerCase();
        Optional<Map.Entry<String, Integer>> hit = categoryFrequency.entrySet().stream()
                .filter(e -> e.getKey() != null && e.getKey().toLowerCase().contains(bucketLower))
                .max(Comparator.comparingInt(Map.Entry::getValue));

        if (hit.isPresent()) return hit.get().getKey();
        return chooseMostFrequentCategory();
    }

    /**
     * Choose category by title-token overlap against known categories.
     * If no overlap, fallback to chooseCategoryByAuthorBucket.
     */
    public String chooseBestCategoryByText(String titleNorm, String authorBucket) {
        if (titleNorm == null) titleNorm = "";
        Set<String> tokens = new HashSet<>(Arrays.asList(titleNorm.split("\\s+")));

        String bestCategory = null;
        int bestScore = -1;
        int bestFreq = -1;

        for (String c : categoryToTags.keySet()) {
            String lower = c.toLowerCase();
            int score = 0;
            for (String t : tokens) {
                if (!t.isBlank() && lower.contains(t)) score++;
            }
            int freq = categoryFrequency.getOrDefault(c, 0);

            // Primary: overlap score, Secondary: frequency
            if (score > bestScore || (score == bestScore && freq > bestFreq)) {
                bestScore = score;
                bestFreq = freq;
                bestCategory = c;
            }
        }

        if (bestCategory != null && bestScore > 0) return bestCategory;
        return chooseCategoryByAuthorBucket(authorBucket);
    }

    private String normalizeTag(String s) {
        if (s == null) return "";
        String x = s.toLowerCase().trim();
        x = x.replaceAll("[^a-z0-9\\s-]", " ");
        x = x.replaceAll("\\s+", "-");
        x = x.replaceAll("-{2,}", "-");
        return x;
    }
}
