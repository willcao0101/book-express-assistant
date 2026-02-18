package com.bookexpress.shopify.client;

import com.bookexpress.account.entity.ShopifyAccountEntity;
import com.bookexpress.account.service.AccountService;
import com.bookexpress.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal Shopify Admin GraphQL client used by:
 * - Product fetch (query)
 * - Smart collection mapping for tagsTitle (query)
 * - Product commit (mutation productUpdate)
 */
@Component
public class ShopifyGraphqlClient {

    private final AccountService accountService;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.shopify.api-version}")
    private String apiVersion;

    @Value("${app.shopify.graph-url-template}")
    private String graphUrlTemplate;

    // Very small in-memory cache to avoid re-fetching smart collections too often.
    private static final long TAG_MAPPING_TTL_SECONDS = 300; // 5 minutes
    private static final Map<Long, TagMappingCache> TAG_MAPPING_CACHE = new ConcurrentHashMap<>();

    public ShopifyGraphqlClient(AccountService accountService) {
        this.accountService = accountService;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> queryProduct(Long accountId, String productId) {
        if (accountId == null) {
            throw new BusinessException("accountId is required");
        }
        if (productId == null || productId.isBlank()) {
            throw new BusinessException("productId is required");
        }

        String query = """
            query ($id: ID!) {
              product(id: $id) {
                id
                title
                handle
                status
                vendor
                productType
                tags
                descriptionHtml
                createdAt
                updatedAt

                seo {
                  title
                  description
                }

                featuredImage {
                  id
                  altText
                  url
                  width
                  height
                }

                images(first: 20) {
                  edges {
                    node {
                      id
                      altText
                      url
                      width
                      height
                    }
                  }
                }

                options {
                  id
                  name
                  values
                }

                variants(first: 50) {
                  edges {
                    node {
                      id
                      title
                      sku
                      barcode
                      price
                      compareAtPrice
                      inventoryQuantity
                      taxable
                      selectedOptions {
                        name
                        value
                      }
                      inventoryItem {
                        id
                        tracked
                        requiresShipping
                      }
                    }
                  }
                }

                metafields(first: 100) {
                  edges {
                    node {
                      id
                      namespace
                      key
                      value
                      type
                    }
                  }
                }
              }
            }
            """;

        Map<String, Object> variables = new HashMap<>();
        variables.put("id", normalizeProductGid(productId));

        return postGraphql(accountId, query, variables);
    }

    /**
     * Fetch mapping: TAG(EQUALS) -> [smart collection titles]
     * Used for displaying "tagsTitle" in summary.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Set<String>> querySmartCollectionTagEqualsMappings(Long accountId) {
        if (accountId == null) throw new BusinessException("accountId is required");

        TagMappingCache cached = TAG_MAPPING_CACHE.get(accountId);
        if (cached != null && !cached.isExpired()) {
            return cached.tagToTitles;
        }

        Map<String, Set<String>> tagToTitles = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        boolean hasNext = true;
        String cursor = null;

        while (hasNext) {
            String gql = """
                query SmartCollections($first: Int!, $after: String) {
                  collections(first: $first, after: $after, query: "collection_type:smart") {
                    edges {
                      node {
                        title
                        ruleSet {
                          rules {
                            column
                            relation
                            condition
                          }
                        }
                      }
                    }
                    pageInfo {
                      hasNextPage
                      endCursor
                    }
                  }
                }
                """;

            Map<String, Object> variables = new LinkedHashMap<>();
            variables.put("first", 100);
            variables.put("after", cursor);

            Map<String, Object> root = postGraphql(accountId, gql, variables);

            Map<String, Object> data = (Map<String, Object>) root.get("data");
            Map<String, Object> collections = data == null ? null : (Map<String, Object>) data.get("collections");
            if (collections == null) break;

            Object edgesObj = collections.get("edges");
            if (edgesObj instanceof List<?> edges) {
                for (Object edgeObj : edges) {
                    if (!(edgeObj instanceof Map<?, ?> edgeMap)) continue;
                    Object nodeObj = edgeMap.get("node");
                    if (!(nodeObj instanceof Map<?, ?> nodeMap)) continue;

                    String title = normalizeString(nodeMap.get("title"));
                    Object ruleSetObj = nodeMap.get("ruleSet");
                    if (!(ruleSetObj instanceof Map<?, ?> ruleSetMap)) continue;

                    Object rulesObj = ruleSetMap.get("rules");
                    if (!(rulesObj instanceof List<?> rules)) continue;

                    for (Object rObj : rules) {
                        if (!(rObj instanceof Map<?, ?> rMap)) continue;

                        String column = normalizeString(rMap.get("column"));
                        String relation = normalizeString(rMap.get("relation"));
                        String condition = normalizeString(rMap.get("condition"));

                        if ("TAG".equalsIgnoreCase(column)
                                && "EQUALS".equalsIgnoreCase(relation)
                                && !condition.isBlank()
                                && !title.isBlank()) {
                            tagToTitles.computeIfAbsent(condition, k -> new LinkedHashSet<>()).add(title);
                        }
                    }
                }
            }

            Map<String, Object> pageInfo = (Map<String, Object>) collections.get("pageInfo");
            boolean next = false;
            String endCursor = null;
            if (pageInfo != null) {
                Object hn = pageInfo.get("hasNextPage");
                next = hn instanceof Boolean b && b;
                Object ec = pageInfo.get("endCursor");
                endCursor = ec == null ? null : String.valueOf(ec);
            }
            hasNext = next;
            cursor = (endCursor == null || endCursor.isBlank()) ? null : endCursor;
        }

        TAG_MAPPING_CACHE.put(accountId, new TagMappingCache(tagToTitles));
        return tagToTitles;
    }

    /**
     * Commit to Shopify via productUpdate.
     *
     * Expected payload shape (from frontend):
     * {
     *   title, vendor, productType, tags: [..], descriptionHtml,
     *   metafields: [{namespace,key,type,value}, ...]
     * }
     */
    @SuppressWarnings("unchecked")
    public void productUpdate(Long accountId, String productId, Map<String, Object> updatePayload) {
        if (accountId == null) throw new BusinessException("accountId is required");
        if (productId == null || productId.isBlank()) throw new BusinessException("productId is required");

        String gql = """
            mutation ProductUpdate($input: ProductInput!) {
              productUpdate(input: $input) {
                product { id title }
                userErrors { field message }
              }
            }
            """;

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("id", normalizeProductGid(productId));

        if (updatePayload != null) {
            putIfPresent(input, "title", updatePayload.get("title"));
            putIfPresent(input, "vendor", updatePayload.get("vendor"));
            putIfPresent(input, "productType", updatePayload.get("productType"));
            putIfPresent(input, "descriptionHtml", updatePayload.get("descriptionHtml"));

            Object tagsObj = updatePayload.get("tags");
            if (tagsObj instanceof List<?> tagsList) {
                List<String> tags = new ArrayList<>();
                for (Object t : tagsList) {
                    String s = normalizeString(t);
                    if (!s.isBlank()) tags.add(s);
                }
                input.put("tags", tags);
            }

            Object statusObj = updatePayload.get("status");
            if (statusObj != null && !normalizeString(statusObj).isBlank()) {
                input.put("status", normalizeString(statusObj));
            }

            Object metafieldsObj = updatePayload.get("metafields");
            if (metafieldsObj instanceof List<?> list) {
                List<Map<String, Object>> metafields = new ArrayList<>();
                for (Object mfObj : list) {
                    if (!(mfObj instanceof Map<?, ?> mf)) continue;
                    String ns = normalizeString(mf.get("namespace"));
                    String key = normalizeString(mf.get("key"));
                    String type = normalizeString(mf.get("type"));
                    Object valObj = mf.get("value");

                    if (ns.isBlank() || key.isBlank() || type.isBlank()) continue;

                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("namespace", ns);
                    m.put("key", key);
                    m.put("type", type);
                    m.put("value", valObj == null ? "" : String.valueOf(valObj));
                    metafields.add(m);
                }
                if (!metafields.isEmpty()) {
                    input.put("metafields", metafields);
                }
            }
        }

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("input", input);

        Map<String, Object> root = postGraphql(accountId, gql, variables);

        Map<String, Object> data = (Map<String, Object>) root.get("data");
        Map<String, Object> productUpdate = data == null ? null : (Map<String, Object>) data.get("productUpdate");
        Object userErrorsObj = productUpdate == null ? null : productUpdate.get("userErrors");

        if (userErrorsObj instanceof List<?> userErrors && !userErrors.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (Object eObj : userErrors) {
                if (!(eObj instanceof Map<?, ?> e)) continue;
                String msg = normalizeString(e.get("message"));
                Object fieldObj = e.get("field");
                String field = "";
                if (fieldObj instanceof List<?> fieldList) {
                    List<String> parts = new ArrayList<>();
                    for (Object p : fieldList) {
                        String s = normalizeString(p);
                        if (!s.isBlank()) parts.add(s);
                    }
                    field = String.join(".", parts);
                } else if (fieldObj != null) {
                    field = normalizeString(fieldObj);
                }
                if (sb.length() > 0) sb.append(" | ");
                if (!field.isBlank()) sb.append(field).append(": ");
                sb.append(msg.isBlank() ? "Unknown error" : msg);
            }
            throw new BusinessException("Shopify productUpdate failed: " + sb);
        }
    }

    // -----------------------------
    // Internal helpers
    // -----------------------------
    @SuppressWarnings("unchecked")
    private Map<String, Object> postGraphql(Long accountId, String query, Map<String, Object> variables) {
        ShopifyAccountEntity account = accountService.getById(accountId);
        String token = accountService.getAccessTokenPlain(accountId);

        String url = graphUrlTemplate
                .replace("{shopDomain}", account.getShopDomain())
                .replace("{apiVersion}", apiVersion);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("query", query);
        requestBody.put("variables", variables == null ? Collections.emptyMap() : variables);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Shopify-Access-Token", token);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<Map> response;
        try {
            response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, Map.class);
        } catch (Exception ex) {
            throw new BusinessException("Shopify GraphQL request failed: " + ex.getMessage());
        }

        Map<String, Object> body = response.getBody();
        if (body == null) {
            throw new BusinessException("Shopify GraphQL response is empty");
        }

        Object errors = body.get("errors");
        if (errors != null) {
            throw new BusinessException("Shopify GraphQL returned errors: " + errors);
        }

        return body;
    }

    private String normalizeProductGid(String productId) {
        String input = productId.trim();
        if (input.startsWith("gid://shopify/Product/")) {
            return input;
        }
        if (!input.matches("\\d+")) {
            throw new BusinessException("productId must be numeric (e.g. 8112925769802) or full gid");
        }
        return "gid://shopify/Product/" + input;
    }

    private static void putIfPresent(Map<String, Object> out, String key, Object val) {
        if (val == null) return;
        String s = String.valueOf(val).trim();
        if (!s.isBlank()) out.put(key, val);
    }

    private static String normalizeString(Object v) {
        if (v == null) return "";
        return String.valueOf(v).trim();
    }

    private static class TagMappingCache {
        final Map<String, Set<String>> tagToTitles;
        final Instant createdAt;

        TagMappingCache(Map<String, Set<String>> tagToTitles) {
            this.tagToTitles = tagToTitles == null ? new TreeMap<>(String.CASE_INSENSITIVE_ORDER) : tagToTitles;
            this.createdAt = Instant.now();
        }

        boolean isExpired() {
            return Instant.now().isAfter(createdAt.plusSeconds(TAG_MAPPING_TTL_SECONDS));
        }
    }
}
