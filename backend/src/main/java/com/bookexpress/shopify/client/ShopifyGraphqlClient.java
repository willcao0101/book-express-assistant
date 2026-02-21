package com.bookexpress.shopify.client;

import com.bookexpress.account.service.AccountService;
import com.bookexpress.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ShopifyGraphqlClient {

    private final AccountService accountService;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.shopify.api-version}")
    private String apiVersion;

    @Value("${app.shopify.graph-url-template}")
    private String graphUrlTemplate;

    private static final long TAG_MAPPING_TTL_SECONDS = 300;
    private static final Map<Long, TagMappingCache> TAG_MAPPING_CACHE = new ConcurrentHashMap<>();

    private static final Pattern GID_NUM_PATTERN = Pattern.compile(".*/(\\d+)$");

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

                collections(first: 50) {
                  edges {
                    node {
                      id
                      title
                      handle
                      ruleSet {
                        appliedDisjunctively
                        rules {
                          column
                          relation
                          condition
                        }
                      }
                    }
                  }
                }

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

                media(first: 20) {
                  edges {
                    node {
                      __typename
                      id
                      ... on MediaImage {
                        image {
                          id
                          url
                          altText
                          width
                          height
                        }
                      }
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
     * Updates Shopify product using ProductUpdateInput.
     * Important: updatePayload may contain extra fields (e.g. productId), which must be ignored.
     * This method returns the GraphQL response so SyncService can persist it.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> productUpdate(Long accountId, String productId, Map<String, Object> updatePayload) {
        if (accountId == null) throw new BusinessException("accountId is required");
        if (productId == null || productId.isBlank()) throw new BusinessException("productId is required");

        String gql = """
            mutation ProductUpdate($product: ProductUpdateInput!) {
              productUpdate(product: $product) {
                product { id title tags updatedAt }
                userErrors { field message }
              }
            }
            """;

        Map<String, Object> product = new LinkedHashMap<>();
        product.put("id", normalizeProductGid(productId));

        Map<String, Object> summary = null;
        if (updatePayload != null) {
            Object s = updatePayload.get("summary");
            if (s instanceof Map<?, ?> m) summary = (Map<String, Object>) m;
        }

        // Title/vendor/productType/status/descriptionHtml (top-level preferred, fallback summary)
        putIfNonBlank(product, "title", pickString(updatePayload, summary, "title"));
        putIfNonBlank(product, "vendor", pickString(updatePayload, summary, "vendor"));
        putIfNonBlank(product, "productType", pickString(updatePayload, summary, "productType"));
        putIfNonBlank(product, "status", pickString(updatePayload, summary, "status"));
        putIfNonBlank(product, "descriptionHtml", pickString(updatePayload, summary, "descriptionHtml"));

        // Tags: must be List<String>. Top-level preferred.
        Object tagsObj = pickObject(updatePayload, summary, "tags");
        List<String> tags = toStringList(tagsObj);
        if (!tags.isEmpty()) {
            product.put("tags", tags);
        }

        // SEO (optional)
        Object seoObj = pickObject(updatePayload, summary, "seo");
        if (seoObj instanceof Map<?, ?> seoMap) {
            Map<String, Object> seo = new LinkedHashMap<>();
            String seoTitle = seoMap.get("title") == null ? "" : String.valueOf(seoMap.get("title")).trim();
            String seoDesc = seoMap.get("description") == null ? "" : String.valueOf(seoMap.get("description")).trim();
            if (!seoTitle.isBlank()) seo.put("title", seoTitle);
            if (!seoDesc.isBlank()) seo.put("description", seoDesc);
            if (!seo.isEmpty()) product.put("seo", seo);
        }

        // Metafields: List<Map> with namespace/key/type/value
        Object metafieldsObj = updatePayload == null ? null : updatePayload.get("metafields");
        List<Map<String, Object>> metafields = toMetafieldInputs(metafieldsObj);
        if (!metafields.isEmpty()) {
            product.put("metafields", metafields);
        }

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("product", product);

        Map<String, Object> resp = postGraphql(accountId, gql, variables);

        Map<String, Object> data = (Map<String, Object>) resp.get("data");
        Map<String, Object> pu = data == null ? null : (Map<String, Object>) data.get("productUpdate");
        if (pu == null) return resp;

        Object errorsObj = pu.get("userErrors");
        if (errorsObj instanceof List<?> errors && !errors.isEmpty()) {
            throw new BusinessException("Shopify productUpdate failed: " + errors);
        }

        return resp;
    }

    private static void putIfNonBlank(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) target.put(key, value);
    }

    private static String pickString(Map<String, Object> payload, Map<String, Object> summary, String key) {
        if (payload != null) {
            Object v = payload.get(key);
            if (v != null) {
                String s = String.valueOf(v).trim();
                if (!s.isBlank()) return s;
            }
        }
        if (summary != null) {
            Object v = summary.get(key);
            if (v != null) {
                String s = String.valueOf(v).trim();
                if (!s.isBlank()) return s;
            }
        }
        return "";
    }

    private static Object pickObject(Map<String, Object> payload, Map<String, Object> summary, String key) {
        if (payload != null && payload.containsKey(key)) return payload.get(key);
        return summary == null ? null : summary.get(key);
    }

    private static List<String> toStringList(Object obj) {
        if (!(obj instanceof List<?> list)) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        for (Object o : list) {
            String s = o == null ? "" : String.valueOf(o).trim();
            if (!s.isBlank()) out.add(s);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> toMetafieldInputs(Object metafieldsObj) {
        if (!(metafieldsObj instanceof List<?> list)) return Collections.emptyList();

        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) continue;

            String namespace = m.get("namespace") == null ? "" : String.valueOf(m.get("namespace")).trim();
            String key = m.get("key") == null ? "" : String.valueOf(m.get("key")).trim();
            String value = m.get("value") == null ? "" : String.valueOf(m.get("value")).trim();
            String type = m.get("type") == null ? "" : String.valueOf(m.get("type")).trim();

            if (namespace.isBlank() || key.isBlank() || type.isBlank()) continue;

            Map<String, Object> one = new LinkedHashMap<>();
            one.put("namespace", namespace);
            one.put("key", key);
            one.put("value", value);
            one.put("type", type);
            out.add(one);
        }
        return out;
    }

    public Map<String, Set<String>> querySmartCollectionTagEqualsMappings(Long accountId) {
        TagMappingCache cache = ensureTagMappingCache(accountId);
        return cache.tagToTitles;
    }

    public Map<String, Set<Long>> querySmartCollectionTagEqualsIdMappings(Long accountId) {
        TagMappingCache cache = ensureTagMappingCache(accountId);
        return cache.tagToCollectionIds;
    }

    @SuppressWarnings("unchecked")
    private TagMappingCache ensureTagMappingCache(Long accountId) {
        if (accountId == null) throw new BusinessException("accountId is required");

        TagMappingCache cached = TAG_MAPPING_CACHE.get(accountId);
        if (cached != null && !cached.isExpired()) {
            return cached;
        }

        Map<String, Set<String>> tagToTitles = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Map<String, Set<Long>> tagToCollectionIds = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

        boolean hasNext = true;
        String cursor = null;

        while (hasNext) {
            String gql = """
                query SmartCollections($first: Int!, $after: String) {
                  collections(first: $first, after: $after, query: "collection_type:smart") {
                    edges {
                      node {
                        id
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

                    String collectionTitle = normalizeString(nodeMap.get("title"));
                    long collectionId = parseGidToLong(nodeMap.get("id"));

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
                                && !collectionTitle.isBlank()) {

                            tagToTitles.computeIfAbsent(condition, k -> new LinkedHashSet<>()).add(collectionTitle);

                            if (collectionId > 0) {
                                tagToCollectionIds.computeIfAbsent(condition, k -> new LinkedHashSet<>()).add(collectionId);
                            }
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

        TagMappingCache newCache = new TagMappingCache(tagToTitles, tagToCollectionIds);
        TAG_MAPPING_CACHE.put(accountId, newCache);
        return newCache;
    }

    private Map<String, Object> postGraphql(Long accountId, String query, Map<String, Object> variables) {
        var account = accountService.getById(accountId);
        if (account == null) throw new BusinessException("Shopify account not found: " + accountId);

        String shopDomain = account.getShopDomain();
        String token = account.getAccessToken();
        if (shopDomain == null || shopDomain.isBlank()) throw new BusinessException("shopDomain is empty");
        if (token == null || token.isBlank()) throw new BusinessException("accessToken is empty");

        String endpoint = graphUrlTemplate
                .replace("{shopDomain}", shopDomain)
                .replace("{apiVersion}", apiVersion);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("query", query);
        payload.put("variables", variables == null ? Collections.emptyMap() : variables);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Shopify-Access-Token", token);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);

        ResponseEntity<Map> resp = restTemplate.exchange(endpoint, HttpMethod.POST, entity, Map.class);

        if (!resp.getStatusCode().is2xxSuccessful()) {
            throw new BusinessException("Shopify GraphQL HTTP error: " + resp.getStatusCode());
        }

        Map<String, Object> body = resp.getBody();
        if (body == null) return Collections.emptyMap();

        Object errors = body.get("errors");
        if (errors instanceof List<?> list && !list.isEmpty()) {
            throw new BusinessException("Shopify GraphQL errors: " + list);
        }

        return body;
    }

    private String normalizeProductGid(String productId) {
        String s = productId.trim();
        if (s.startsWith("gid://")) return s;
        if (s.matches("\\d+")) return "gid://shopify/Product/" + s;
        return s;
    }

    private static String normalizeString(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }

    private static long parseGidToLong(Object gidObj) {
        if (gidObj == null) return 0L;
        String gid = String.valueOf(gidObj).trim();
        if (gid.isBlank()) return 0L;
        Matcher m = GID_NUM_PATTERN.matcher(gid);
        if (!m.find()) return 0L;
        try {
            return Long.parseLong(m.group(1));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static class TagMappingCache {
        final Map<String, Set<String>> tagToTitles;
        final Map<String, Set<Long>> tagToCollectionIds;
        final Instant createdAt = Instant.now();

        TagMappingCache(Map<String, Set<String>> tagToTitles, Map<String, Set<Long>> tagToCollectionIds) {
            this.tagToTitles = tagToTitles;
            this.tagToCollectionIds = tagToCollectionIds;
        }

        boolean isExpired() {
            return Instant.now().isAfter(createdAt.plusSeconds(TAG_MAPPING_TTL_SECONDS));
        }
    }
}