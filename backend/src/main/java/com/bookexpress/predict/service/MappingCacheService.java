package com.bookexpress.backend.service;

import com.bookexpress.backend.repository.BookRepository;
import com.bookexpress.backend.util.JsonUtil;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory category -> tags cache, built from books table.
 */
@Service
public class MappingCacheService {

    private final BookRepository bookRepository;
    private final JsonUtil jsonUtil;

    // category -> tags
    private final Map<String, Set<String>> categoryToTags = new ConcurrentHashMap<>();

    public MappingCacheService(BookRepository bookRepository, JsonUtil jsonUtil) {
        this.bookRepository = bookRepository;
        this.jsonUtil = jsonUtil;
    }

    /**
     * Rebuild cache from DB.
     */
    public synchronized void rebuild() {
        categoryToTags.clear();

        List<Map<String, Object>> rows = bookRepository.findAllCategoryTagRows();
        for (Map<String, Object> row : rows) {
            String category = Objects.toString(row.get("trademe_categories"), "").trim();
            String tagsJson = Objects.toString(row.get("shopify_tags"), "[]");

            if (category.isBlank()) continue;

            List<String> tags = jsonUtil.readStringListSafe(tagsJson);
            if (tags.isEmpty()) continue;

            Set<String> set = categoryToTags.computeIfAbsent(category, k -> new LinkedHashSet<>());
            for (String t : tags) {
                if (t != null && !t.isBlank()) {
                    set.add(t.trim());
                }
            }
        }
    }

    public boolean isLoaded() {
        return !categoryToTags.isEmpty();
    }

    public Set<String> getAllCategories() {
        return new LinkedHashSet<>(categoryToTags.keySet());
    }

    public Set<String> getTagsByCategory(String category) {
        return new LinkedHashSet<>(categoryToTags.getOrDefault(category, Collections.emptySet()));
    }

    /**
     * For MetaController.
     */
    public int categoryCount() {
        return categoryToTags.size();
    }

    /**
     * Optional helper for diagnostics.
     */
    public int totalTagLinks() {
        int n = 0;
        for (Set<String> s : categoryToTags.values()) n += s.size();
        return n;
    }
}
