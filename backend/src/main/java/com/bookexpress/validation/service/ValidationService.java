package com.bookexpress.validation.service;

import com.bookexpress.backend.repository.CategoryIdMapRepository;
import com.bookexpress.shopify.service.ShopifyService;
import com.bookexpress.validation.dto.ValidationIssue;
import com.bookexpress.validation.dto.ValidationResult;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ValidationService {

    private final CategoryIdMapRepository categoryIdMapRepository;

    public ValidationService(CategoryIdMapRepository categoryIdMapRepository) {
        this.categoryIdMapRepository = categoryIdMapRepository;
    }

    @SuppressWarnings("unchecked")
    public ValidationResult validate(Map<String, Object> productData) {
        ValidationResult result = new ValidationResult();

        Map<String, Object> summary = resolveSummary(productData);

        // Resolve productId for cache lookup
        String productId = getString(productData, "productId");
        if (productId.isBlank()) productId = getString(productData, "id");
        if (productId.isBlank() && summary != null) productId = getString(summary, "id");

        // Resolve categoryId (DO NOT change this logic)
        Long categoryId = null;

        Long fromSummary = getLong(summary, "categoryId");
        if (fromSummary != null && fromSummary > 0) {
            categoryId = fromSummary;
        }

        if ((categoryId == null || categoryId <= 0) && !productId.isBlank()) {
            categoryId = ShopifyService.getCachedCategoryId(productId);
        }

        if (categoryId == null || categoryId <= 0) {
            String pid2 = extractFromRawIfAny(productData);
            if (pid2 != null && !pid2.isBlank()) {
                categoryId = ShopifyService.getCachedCategoryId(pid2);
            }
        }

        // Category validation (now enabled)
        if (categoryId == null || categoryId <= 0) {
            result.getIssues().add(new ValidationIssue(
                    "tagsTitle",
                    "ERROR",
                    "categoryId=null (cannot validate category without categoryId)"
            ));
        } else {
            Optional<String> categoryPathOpt;
            try {
                categoryPathOpt = categoryIdMapRepository.findCategoryPathById(categoryId);
            } catch (Exception e) {
                categoryPathOpt = Optional.empty();
            }

            if (categoryPathOpt.isPresent()) {
                // Pass: show only OK + category_id
                result.getIssues().add(new ValidationIssue(
                        "tagsTitle",
                        "OK",
                        "OK (category_id=" + categoryId + ")"
                ));
            } else {
                result.getIssues().add(new ValidationIssue(
                        "tagsTitle",
                        "ERROR",
                        "Category not in category_id_map (category_id=" + categoryId + ")"
                ));
            }
        }

        // Keep existing minimal checks
        String title = getString(productData, "title");
        if (title.isBlank() && summary != null) title = getString(summary, "title");

        if (title.isBlank()) {
            result.getIssues().add(new ValidationIssue("title", "ERROR", "Title is empty."));
        }

        List<Map<String, Object>> images = extractImages(productData);
        if (images == null || images.isEmpty()) {
            result.getIssues().add(new ValidationIssue("images", "WARNING", "No images found."));
        }

        addOkIfMissing(result, "title");
        addOkIfMissing(result, "images");
        addOkIfMissing(result, "tagsTitle");

        boolean pass = true;
        for (ValidationIssue i : result.getIssues()) {
            if ("ERROR".equalsIgnoreCase(i.getLevel())) {
                pass = false;
                break;
            }
        }
        result.setPass(pass);

        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveSummary(Map<String, Object> productData) {
        if (productData == null) return null;

        Object s = productData.get("summary");
        if (s instanceof Map<?, ?> m) return (Map<String, Object>) m;

        Object v = productData.get("view");
        if (v instanceof Map<?, ?> vm) {
            Object vs = ((Map<?, ?>) vm).get("summary");
            if (vs instanceof Map<?, ?> vsm) return (Map<String, Object>) vsm;
        }

        if (looksLikeSummary(productData)) return productData;
        return null;
    }

    private boolean looksLikeSummary(Map<String, Object> m) {
        if (m == null) return false;
        boolean hasId = m.containsKey("id") || m.containsKey("productId");
        boolean hasTitle = m.containsKey("title");
        boolean hasTags = m.containsKey("tags");
        return hasId && hasTitle && hasTags;
    }

    @SuppressWarnings("unchecked")
    private String extractFromRawIfAny(Map<String, Object> productData) {
        if (productData == null) return null;

        Object rawObj = productData.get("raw");
        if (!(rawObj instanceof Map<?, ?> raw)) return null;

        Object dataObj = ((Map<?, ?>) raw).get("data");
        if (!(dataObj instanceof Map<?, ?> data)) return null;

        Object productObj = ((Map<?, ?>) data).get("product");
        if (!(productObj instanceof Map<?, ?> product)) return null;

        Object id = ((Map<?, ?>) product).get("id");
        return id == null ? null : String.valueOf(id).trim();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractImages(Map<String, Object> productData) {
        if (productData == null) return Collections.emptyList();

        Object imagesObj = productData.get("images");
        if (imagesObj instanceof List<?> list) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) out.add((Map<String, Object>) m);
            }
            return out;
        }

        Object viewObj = productData.get("view");
        if (viewObj instanceof Map<?, ?> view) {
            Object vImages = ((Map<?, ?>) view).get("images");
            if (vImages instanceof List<?> list) {
                List<Map<String, Object>> out = new ArrayList<>();
                for (Object o : list) {
                    if (o instanceof Map<?, ?> m) out.add((Map<String, Object>) m);
                }
                return out;
            }
        }

        return Collections.emptyList();
    }

    private static void addOkIfMissing(ValidationResult result, String fieldPath) {
        boolean exists = false;
        for (ValidationIssue i : result.getIssues()) {
            if (fieldPath.equals(i.getFieldPath())) {
                exists = true;
                break;
            }
        }
        if (!exists) {
            result.getIssues().add(new ValidationIssue(fieldPath, "OK", "OK"));
        }
    }

    private static String getString(Map<String, Object> map, String key) {
        if (map == null) return "";
        Object v = map.get(key);
        return v == null ? "" : String.valueOf(v).trim();
    }

    private static Long getLong(Map<String, Object> map, String key) {
        if (map == null) return null;
        Object v = map.get(key);
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        String s = String.valueOf(v).trim();
        if (s.isBlank()) return null;
        try {
            return Long.parseLong(s);
        } catch (Exception ignore) {
            return null;
        }
    }
}