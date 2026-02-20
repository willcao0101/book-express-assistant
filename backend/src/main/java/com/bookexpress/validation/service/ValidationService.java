package com.bookexpress.validation.service;

import com.bookexpress.backend.repository.CategoryIdMapRepository;
import com.bookexpress.shopify.client.ShopifyGraphqlClient;
import com.bookexpress.validation.dto.ValidationIssue;
import com.bookexpress.validation.dto.ValidationResult;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ValidationService {

    private static final Pattern LONG_NUMBER = Pattern.compile("\\b(\\d{9,15})\\b");

    private final CategoryIdMapRepository categoryIdMapRepository;
    private final ShopifyGraphqlClient shopifyClient;

    public ValidationService(CategoryIdMapRepository categoryIdMapRepository,
                             ShopifyGraphqlClient shopifyClient) {
        this.categoryIdMapRepository = categoryIdMapRepository;
        this.shopifyClient = shopifyClient;
    }

    /**
     * Current validation rules:
     * 1) CategoryId must exist in local category_id_map table (Trade Me category list).
     *    CategoryId is parsed from tags/tagsTitle. If multiple numbers exist, pick the first one that exists in DB.
     * 2) Title:
     *    - any single word > 20 chars -> error
     *    - total title length > 200 chars -> error
     * 3) Images:
     *    Photos must be at least 500 pixels on the longest side.
     */
    @SuppressWarnings("unchecked")
    public ValidationResult validate(Map<String, Object> productData) {
        ValidationResult result = new ValidationResult();
        result.setTotal(3);

        if (productData == null) productData = Collections.emptyMap();

        // -----------------------------
        // Rule 2: Title rules
        // -----------------------------
        String title = readString(productData, "title");
        if (title != null && !title.isBlank()) {
            String[] words = title.trim().split("\\s+");
            for (String w : words) {
                if (w != null && w.length() > 20) {
                    result.getIssues().add(new ValidationIssue(
                            "title",
                            "ERROR",
                            "A single word in the title must not exceed 20 characters."
                    ));
                    break;
                }
            }
            if (title.length() > 200) {
                result.getIssues().add(new ValidationIssue(
                        "title",
                        "ERROR",
                        "Title must not exceed 200 characters."
                ));
            }
        }

        // -----------------------------
        // Rule 1: CategoryId exists in Trade Me list
        // -----------------------------
        Long accountId = readLong(productData, "accountId");
        String productId = readString(productData, "id");
        if (productId == null || productId.isBlank()) {
            productId = readString(productData, "productId");
        }

        Long categoryId = extractCategoryId(productData);

        // If still not found in payload, try fetch from Shopify
        if (categoryId == null && accountId != null && productId != null && !productId.isBlank()) {
            try {
                Map<String, Object> raw = shopifyClient.queryProduct(accountId, productId);
                categoryId = extractCategoryIdFromRaw(raw);
            } catch (Exception ignore) {
                // ignore
            }
        }

        boolean categoryOk = false;
        if (categoryId != null) {
            categoryOk = categoryIdMapRepository.findCategoryPathById(categoryId).isPresent();
        }
        if (!categoryOk) {
            // IMPORTANT: use fieldPath that UI can bind to the category field.
            // In your UI, the field is displayed as "categoryId".
            result.getIssues().add(new ValidationIssue(
                    "categoryId",
                    "ERROR",
                    "Category is not in the Trade Me list."
            ));
        }

        // -----------------------------
        // Rule 3: Images at least 500px on longest side
        // -----------------------------
        List<int[]> dims = extractImageDims(productData);

        if (dims.isEmpty() && accountId != null && productId != null && !productId.isBlank()) {
            // Try fetch from Shopify if images were not included in request
            try {
                Map<String, Object> raw = shopifyClient.queryProduct(accountId, productId);
                dims = extractImageDimsFromRaw(raw);
            } catch (Exception ignore) {
                // ignore
            }
        }

        boolean imageRuleFailed = false;
        for (int[] d : dims) {
            int w = d[0];
            int h = d[1];
            if (w <= 0 || h <= 0) continue;
            if (Math.max(w, h) < 500) {
                imageRuleFailed = true;
                break;
            }
        }

        if (imageRuleFailed) {
            result.getIssues().add(new ValidationIssue(
                    "images",
                    "ERROR",
                    "Photos must be at least 500 pixels on the longest side"
            ));
        }

        // Add OK markers so UI can display "OK" instead of empty.
        // IMPORTANT: failed count must exclude OK.
        addOkIfMissing(result, "title");
        addOkIfMissing(result, "categoryId");
        addOkIfMissing(result, "images");

        // Final stats: count only non-OK issues
        int failed = 0;
        for (ValidationIssue it : result.getIssues()) {
            if (it == null) continue;
            if (!"OK".equalsIgnoreCase(it.getLevel())) failed++;
        }
        result.setFailed(failed);
        result.setPass(failed == 0);

        return result;
    }

    // =========================================================
    // Helpers
    // =========================================================

    private String readString(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) return null;
        return String.valueOf(v);
    }

    private Long readLong(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try {
            String s = String.valueOf(v).trim();
            if (s.isEmpty()) return null;
            return Long.parseLong(s);
        } catch (Exception ignore) {
            return null;
        }
    }

    /**
     * Extract categoryId by scanning:
     * - tagsTitle (string, possibly contains ids)
     * - tags list
     * - nested summary.tagsTitle / summary.tags
     *
     * We may find multiple long numbers; we select the first one that exists in category_id_map table.
     */
    @SuppressWarnings("unchecked")
    private Long extractCategoryId(Map<String, Object> productData) {
        List<Long> candidates = new ArrayList<>();

        collectLongNumbers(candidates, readString(productData, "tagsTitle"));
        collectLongNumbersFromTags(candidates, productData.get("tags"));

        Object summaryObj = productData.get("summary");
        if (summaryObj instanceof Map<?, ?> summary) {
            Object tt = summary.get("tagsTitle");
            collectLongNumbers(candidates, tt == null ? null : String.valueOf(tt));
            collectLongNumbersFromTags(candidates, summary.get("tags"));
        }

        // If the UI already sends categoryId explicitly, use it first.
        Object explicit = productData.get("categoryId");
        if (explicit != null) {
            Long v = toLong(explicit);
            if (v != null) return v;
        }

        // de-dup, keep order
        LinkedHashSet<Long> uniq = new LinkedHashSet<>(candidates);

        // Prefer the first id that exists in DB
        for (Long id : uniq) {
            if (id == null) continue;
            if (categoryIdMapRepository.findCategoryPathById(id).isPresent()) {
                return id;
            }
        }

        // If none match DB, return the first parsed id (so the error is still deterministic)
        return uniq.stream().filter(Objects::nonNull).findFirst().orElse(null);
    }

    private Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try {
            String s = String.valueOf(v).trim();
            if (s.isEmpty()) return null;
            return Long.parseLong(s);
        } catch (Exception ignore) {
            return null;
        }
    }

    private void collectLongNumbersFromTags(List<Long> out, Object tagsObj) {
        if (tagsObj == null) return;

        if (tagsObj instanceof List<?> list) {
            for (Object t : list) {
                collectLongNumbers(out, t == null ? null : String.valueOf(t));
            }
        } else {
            collectLongNumbers(out, String.valueOf(tagsObj));
        }
    }

    private void collectLongNumbers(List<Long> out, String text) {
        if (text == null) return;
        Matcher m = LONG_NUMBER.matcher(text);
        while (m.find()) {
            try {
                out.add(Long.parseLong(m.group(1)));
            } catch (Exception ignore) {
                // ignore
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Long extractCategoryIdFromRaw(Map<String, Object> raw) {
        if (raw == null) return null;

        Object dataObj = raw.get("data");
        if (!(dataObj instanceof Map<?, ?> data)) return null;

        Object productObj = data.get("product");
        if (!(productObj instanceof Map<?, ?> product)) return null;

        // Try tags
        Object tagsObj = product.get("tags");
        if (tagsObj != null) {
            List<Long> candidates = new ArrayList<>();
            collectLongNumbersFromTags(candidates, tagsObj);

            LinkedHashSet<Long> uniq = new LinkedHashSet<>(candidates);
            for (Long id : uniq) {
                if (id == null) continue;
                if (categoryIdMapRepository.findCategoryPathById(id).isPresent()) return id;
            }
            return uniq.stream().filter(Objects::nonNull).findFirst().orElse(null);
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private List<int[]> extractImageDims(Map<String, Object> productData) {
        List<int[]> dims = new ArrayList<>();

        Object imagesObj = productData.get("images");
        if (imagesObj instanceof List<?> list) {
            for (Object it : list) {
                if (it instanceof Map<?, ?> m) {
                    Integer w = toInt(m.get("width"));
                    Integer h = toInt(m.get("height"));
                    if (w != null && h != null) dims.add(new int[]{w, h});
                }
            }
        }

        Object featuredObj = productData.get("featuredImage");
        if (featuredObj instanceof Map<?, ?> m) {
            Integer w = toInt(m.get("width"));
            Integer h = toInt(m.get("height"));
            if (w != null && h != null) dims.add(new int[]{w, h});
        }

        Object summaryObj = productData.get("summary");
        if (summaryObj instanceof Map<?, ?> summary) {
            Object imgs2 = summary.get("images");
            if (imgs2 instanceof List<?> list2) {
                for (Object it : list2) {
                    if (it instanceof Map<?, ?> m3) {
                        Integer w = toInt(m3.get("width"));
                        Integer h = toInt(m3.get("height"));
                        if (w != null && h != null) dims.add(new int[]{w, h});
                    }
                }
            }
        }

        Object raw = productData.get("raw");
        if (raw instanceof Map<?, ?> rawMap) {
            dims.addAll(extractImageDimsFromRaw((Map<String, Object>) rawMap));
        }

        return dims;
    }

    @SuppressWarnings("unchecked")
    private List<int[]> extractImageDimsFromRaw(Map<String, Object> raw) {
        List<int[]> dims = new ArrayList<>();
        if (raw == null) return dims;

        Object dataObj = raw.get("data");
        if (!(dataObj instanceof Map<?, ?> data)) return dims;

        Object productObj = data.get("product");
        if (!(productObj instanceof Map<?, ?> product)) return dims;

        Object imagesObj = product.get("images");
        if (!(imagesObj instanceof Map<?, ?> images)) return dims;

        Object edgesObj = images.get("edges");
        if (!(edgesObj instanceof List<?> edges)) return dims;

        for (Object e : edges) {
            if (!(e instanceof Map<?, ?> edge)) continue;
            Object nodeObj = edge.get("node");
            if (!(nodeObj instanceof Map<?, ?> node)) continue;

            Integer w = toInt(node.get("width"));
            Integer h = toInt(node.get("height"));
            if (w != null && h != null) dims.add(new int[]{w, h});
        }
        return dims;
    }

    private Integer toInt(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        try {
            String s = String.valueOf(v).trim();
            if (s.isEmpty()) return null;
            return Integer.parseInt(s);
        } catch (Exception ignore) {
            return null;
        }
    }

    private void addOkIfMissing(ValidationResult result, String fieldPath) {
        if (result == null || fieldPath == null) return;

        for (ValidationIssue it : result.getIssues()) {
            if (it == null) continue;
            if (fieldPath.equals(it.getFieldPath())) {
                // already has ERROR/WARN/OK
                return;
            }
        }
        result.getIssues().add(new ValidationIssue(fieldPath, "OK", "OK"));
    }
}