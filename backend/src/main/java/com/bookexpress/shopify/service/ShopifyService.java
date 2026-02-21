package com.bookexpress.shopify.service;

import com.bookexpress.shopify.client.ShopifyGraphqlClient;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class ShopifyService {

    private final ShopifyGraphqlClient client;

    private static final Map<String, Long> CATEGORY_CACHE_BY_PRODUCT_ID = new ConcurrentHashMap<>();
    private static final Map<String, String> TAGS_TITLE_CACHE_BY_PRODUCT_ID = new ConcurrentHashMap<>();

    private static final Map<String, String> MEDIA_CACHE_BY_PRODUCT_ID = new ConcurrentHashMap<>();
    private static final Map<String, Integer> MEDIA_MIN_LONGEST_SIDE_CACHE_BY_PRODUCT_ID = new ConcurrentHashMap<>();

    public ShopifyService(ShopifyGraphqlClient client) {
        this.client = client;
    }

    public static Long getCachedCategoryId(String productIdOrGid) {
        if (productIdOrGid == null || productIdOrGid.isBlank()) return null;
        return CATEGORY_CACHE_BY_PRODUCT_ID.get(productIdOrGid.trim());
    }

    public static String getCachedTagsTitle(String productIdOrGid) {
        if (productIdOrGid == null || productIdOrGid.isBlank()) return null;
        return TAGS_TITLE_CACHE_BY_PRODUCT_ID.get(productIdOrGid.trim());
    }

    public static String getCachedMedia(String productIdOrGid) {
        if (productIdOrGid == null || productIdOrGid.isBlank()) return null;
        return MEDIA_CACHE_BY_PRODUCT_ID.get(productIdOrGid.trim());
    }

    public static Integer getCachedMediaMinLongestSide(String productIdOrGid) {
        if (productIdOrGid == null || productIdOrGid.isBlank()) return null;
        return MEDIA_MIN_LONGEST_SIDE_CACHE_BY_PRODUCT_ID.get(productIdOrGid.trim());
    }

    private static void cacheCategoryAndTagsTitle(String productIdOrGid, long categoryId, String tagsTitle) {
        if (productIdOrGid == null || productIdOrGid.isBlank()) return;
        String key = productIdOrGid.trim();

        if (categoryId > 0) CATEGORY_CACHE_BY_PRODUCT_ID.put(key, categoryId);
        if (tagsTitle != null && !tagsTitle.isBlank()) TAGS_TITLE_CACHE_BY_PRODUCT_ID.put(key, tagsTitle);

        String numeric = extractNumericProductId(key);
        if (numeric != null) {
            if (categoryId > 0) CATEGORY_CACHE_BY_PRODUCT_ID.put(numeric, categoryId);
            if (tagsTitle != null && !tagsTitle.isBlank()) TAGS_TITLE_CACHE_BY_PRODUCT_ID.put(numeric, tagsTitle);
        }
    }

    private static void cacheMedia(String productIdOrGid, String mediaText, Integer minLongestSide) {
        if (productIdOrGid == null || productIdOrGid.isBlank()) return;
        String key = productIdOrGid.trim();

        if (mediaText != null && !mediaText.isBlank()) MEDIA_CACHE_BY_PRODUCT_ID.put(key, mediaText);
        if (minLongestSide != null) MEDIA_MIN_LONGEST_SIDE_CACHE_BY_PRODUCT_ID.put(key, minLongestSide);

        String numeric = extractNumericProductId(key);
        if (numeric != null) {
            if (mediaText != null && !mediaText.isBlank()) MEDIA_CACHE_BY_PRODUCT_ID.put(numeric, mediaText);
            if (minLongestSide != null) MEDIA_MIN_LONGEST_SIDE_CACHE_BY_PRODUCT_ID.put(numeric, minLongestSide);
        }
    }

    private static String extractNumericProductId(String productGid) {
        if (productGid == null) return null;
        int idx = productGid.lastIndexOf('/');
        if (idx < 0 || idx == productGid.length() - 1) return null;
        String tail = productGid.substring(idx + 1).trim();
        return tail.matches("\\d+") ? tail : null;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> fetchProduct(Long accountId, String productId) {
        Map<String, Object> raw = client.queryProduct(accountId, productId);

        Map<String, Object> data = (Map<String, Object>) raw.get("data");
        Map<String, Object> product = data == null ? null : (Map<String, Object>) data.get("product");

        Map<String, Object> view = buildView(accountId, product);

        Map<String, Object> result = new HashMap<>();
        result.put("raw", raw);
        result.put("view", view);

        Map<String, Object> summary = (Map<String, Object>) view.get("summary");
        if (summary != null) {
            String pid = summary.get("id") == null ? null : String.valueOf(summary.get("id")).trim();
            Long cat = summary.get("categoryId") instanceof Number n ? n.longValue() : null;
            String tagsTitle = summary.get("tagsTitle") == null ? null : String.valueOf(summary.get("tagsTitle")).trim();
            String mediaText = summary.get("media") == null ? null : String.valueOf(summary.get("media")).trim();
            Integer minLongestSide = summary.get("mediaMinLongestSide") instanceof Number n ? n.intValue() : null;

            if (pid != null && cat != null) {
                cacheCategoryAndTagsTitle(pid, cat, tagsTitle);
            }
            if (pid != null) {
                cacheMedia(pid, mediaText, minLongestSide);
            }
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildView(Long accountId, Map<String, Object> product) {
        Map<String, Object> view = new LinkedHashMap<>();
        if (product == null) {
            view.put("summary", Collections.emptyMap());
            view.put("images", Collections.emptyList());
            view.put("variants", Collections.emptyList());
            view.put("metafields", Collections.emptyList());
            return view;
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", product.get("id"));
        summary.put("title", product.get("title"));
        summary.put("handle", product.get("handle"));
        summary.put("status", product.get("status"));
        summary.put("vendor", product.get("vendor"));
        summary.put("productType", product.get("productType"));
        summary.put("tags", product.get("tags"));
        summary.put("descriptionHtml", product.get("descriptionHtml"));
        summary.put("createdAt", product.get("createdAt"));
        summary.put("updatedAt", product.get("updatedAt"));
        summary.put("seo", product.get("seo"));
        summary.put("featuredImage", product.get("featuredImage"));
        summary.put("options", product.get("options"));

        TagsTitleAndCategoryId tt = extractFromCollections(product.get("tags"), product.get("collections"));
        summary.put("tagsTitle", tt.tagsTitle());
        summary.put("categoryId", tt.categoryId());

        MediaSummary mediaSummary = buildMediaSummary(product);
        summary.put("media", mediaSummary.text());
        summary.put("mediaMinLongestSide", mediaSummary.minLongestSide());

        System.out.println("========== Concise Summary ==========");
        System.out.println("productId  : " + String.valueOf(summary.get("id")));
        System.out.println("title      : " + String.valueOf(summary.get("title")));
        System.out.println("tags       : " + String.valueOf(summary.get("tags")));
        System.out.println("tagsTitle  : " + tt.tagsTitle());
        System.out.println("categoryId : " + tt.categoryId());
        System.out.println("media      : " + mediaSummary.text());
        System.out.println("minSide    : " + mediaSummary.minLongestSide());

        view.put("summary", summary);

        List<Map<String, Object>> images = flattenEdges(product, "images");
        view.put("images", images);

        List<Map<String, Object>> variants = flattenEdges(product, "variants");
        view.put("variants", variants);

        List<Map<String, Object>> metafields = flattenEdges(product, "metafields");
        view.put("metafields", metafields);

        Map<String, List<Map<String, Object>>> metafieldsByNamespace = metafields.stream()
                .collect(Collectors.groupingBy(
                        m -> String.valueOf(m.getOrDefault("namespace", "unknown")),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        view.put("metafieldsByNamespace", metafieldsByNamespace);

        String pid = summary.get("id") == null ? null : String.valueOf(summary.get("id")).trim();
        cacheCategoryAndTagsTitle(pid, tt.categoryId(), tt.tagsTitle());
        cacheMedia(pid, mediaSummary.text(), mediaSummary.minLongestSide());

        return view;
    }

    private MediaSummary buildMediaSummary(Map<String, Object> product) {
        List<int[]> sizes = new ArrayList<>();

        Object featured = product.get("featuredImage");
        if (featured instanceof Map<?, ?> m) {
            Integer w = toInt(((Map<?, ?>) m).get("width"));
            Integer h = toInt(((Map<?, ?>) m).get("height"));
            if (w != null && h != null && w > 0 && h > 0) sizes.add(new int[]{w, h});
        }

        List<Map<String, Object>> images = flattenEdges(product, "images");
        for (Map<String, Object> img : images) {
            Integer w = toInt(img.get("width"));
            Integer h = toInt(img.get("height"));
            if (w != null && h != null && w > 0 && h > 0) sizes.add(new int[]{w, h});
        }

        List<int[]> mediaSizes = extractMediaImageSizes(product.get("media"));
        sizes.addAll(mediaSizes);

        if (sizes.isEmpty()) return new MediaSummary("", null);

        StringBuilder sb = new StringBuilder();
        int idx = 1;
        for (int[] wh : sizes) {
            if (idx > 1) sb.append("; ");
            sb.append(idx).append(") ").append(wh[0]).append("x").append(wh[1]);
            idx++;
        }

        Integer minLongest = null;
        for (int[] wh : sizes) {
            int longest = Math.max(wh[0], wh[1]);
            if (minLongest == null || longest < minLongest) minLongest = longest;
        }

        return new MediaSummary(sb.toString(), minLongest);
    }

    @SuppressWarnings("unchecked")
    private List<int[]> extractMediaImageSizes(Object mediaObj) {
        if (!(mediaObj instanceof Map<?, ?> media)) return Collections.emptyList();
        Object edgesObj = media.get("edges");
        if (!(edgesObj instanceof List<?> edges)) return Collections.emptyList();

        List<int[]> out = new ArrayList<>();
        for (Object edgeObj : edges) {
            if (!(edgeObj instanceof Map<?, ?> edge)) continue;
            Object nodeObj = edge.get("node");
            if (!(nodeObj instanceof Map<?, ?> node)) continue;

            Object imageObj = node.get("image");
            if (!(imageObj instanceof Map<?, ?> image)) continue;

            Integer w = toInt(image.get("width"));
            Integer h = toInt(image.get("height"));
            if (w != null && h != null && w > 0 && h > 0) out.add(new int[]{w, h});
        }

        return out;
    }

    private Integer toInt(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        String s = String.valueOf(v).trim();
        if (s.isBlank()) return null;
        try {
            return Integer.parseInt(s);
        } catch (Exception ignore) {
            return null;
        }
    }

    private TagsTitleAndCategoryId extractFromCollections(Object tagsObj, Object collectionsObj) {
        Set<String> tags = normalizeTags(tagsObj);
        if (tags.isEmpty()) return new TagsTitleAndCategoryId("", 0L);

        if (!(collectionsObj instanceof Map<?, ?> collections)) {
            return new TagsTitleAndCategoryId("", 0L);
        }

        Object edgesObj = collections.get("edges");
        if (!(edgesObj instanceof List<?> edges)) {
            return new TagsTitleAndCategoryId("", 0L);
        }

        LinkedHashSet<String> titles = new LinkedHashSet<>();
        long categoryId = 0L;

        for (Object edgeObj : edges) {
            if (!(edgeObj instanceof Map<?, ?> edge)) continue;
            Object nodeObj = edge.get("node");
            if (!(nodeObj instanceof Map<?, ?> node)) continue;

            String nodeIdGid = node.get("id") == null ? "" : String.valueOf(node.get("id")).trim();
            long nodeId = parseGidToLong(nodeIdGid);
            String nodeTitle = node.get("title") == null ? "" : String.valueOf(node.get("title")).trim();

            Object ruleSetObj = node.get("ruleSet");
            if (!(ruleSetObj instanceof Map<?, ?> ruleSet)) continue;

            Object rulesObj = ruleSet.get("rules");
            if (!(rulesObj instanceof List<?> rules)) continue;

            boolean matched = false;
            for (Object rObj : rules) {
                if (!(rObj instanceof Map<?, ?> r)) continue;

                String column = r.get("column") == null ? "" : String.valueOf(r.get("column")).trim();
                String relation = r.get("relation") == null ? "" : String.valueOf(r.get("relation")).trim();
                String condition = r.get("condition") == null ? "" : String.valueOf(r.get("condition")).trim();

                if ("TAG".equalsIgnoreCase(column)
                        && "EQUALS".equalsIgnoreCase(relation)
                        && !condition.isBlank()
                        && containsIgnoreCase(tags, condition)) {
                    matched = true;
                    break;
                }
            }

            if (matched) {
                if (!nodeTitle.isBlank()) titles.add(nodeTitle);
                if (categoryId == 0L && nodeId > 0L) categoryId = nodeId;
            }
        }

        return new TagsTitleAndCategoryId(String.join(", ", titles), categoryId);
    }

    private boolean containsIgnoreCase(Set<String> set, String value) {
        for (String s : set) {
            if (s != null && s.equalsIgnoreCase(value)) return true;
        }
        return false;
    }

    private Set<String> normalizeTags(Object tagsObj) {
        Set<String> tags = new LinkedHashSet<>();
        if (tagsObj instanceof List<?> list) {
            for (Object t : list) {
                String s = t == null ? "" : String.valueOf(t).trim();
                if (!s.isBlank()) tags.add(s);
            }
        }
        return tags;
    }

    private long parseGidToLong(String gid) {
        if (gid == null || gid.isBlank()) return 0L;
        int idx = gid.lastIndexOf('/');
        if (idx < 0 || idx == gid.length() - 1) return 0L;
        String tail = gid.substring(idx + 1).trim();
        if (!tail.matches("\\d+")) return 0L;
        try {
            return Long.parseLong(tail);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> flattenEdges(Map<String, Object> parent, String fieldName) {
        Object field = parent.get(fieldName);
        if (!(field instanceof Map<?, ?> mapField)) return Collections.emptyList();

        Object edgesObj = mapField.get("edges");
        if (!(edgesObj instanceof List<?> edges)) return Collections.emptyList();

        List<Map<String, Object>> result = new ArrayList<>();
        for (Object edgeObj : edges) {
            if (edgeObj instanceof Map<?, ?> edgeMap) {
                Object nodeObj = edgeMap.get("node");
                if (nodeObj instanceof Map<?, ?> nodeMap) {
                    result.add((Map<String, Object>) nodeMap);
                }
            }
        }
        return result;
    }

    private record TagsTitleAndCategoryId(String tagsTitle, long categoryId) {}
    private record MediaSummary(String text, Integer minLongestSide) {}
}