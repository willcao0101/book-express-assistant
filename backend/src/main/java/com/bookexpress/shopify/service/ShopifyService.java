package com.bookexpress.shopify.service;

import com.bookexpress.shopify.client.ShopifyGraphqlClient;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ShopifyService {

    private final ShopifyGraphqlClient client;

    public ShopifyService(ShopifyGraphqlClient client) {
        this.client = client;
    }

    /**
     * Return raw Shopify response + a flattened "view" object for frontend rendering.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> fetchProduct(Long accountId, String productId) {
        Map<String, Object> raw = client.queryProduct(accountId, productId);

        Map<String, Object> data = (Map<String, Object>) raw.get("data");
        Map<String, Object> product = data == null ? null : (Map<String, Object>) data.get("product");

        Map<String, Object> view = buildView(product);

        Map<String, Object> result = new HashMap<>();
        result.put("raw", raw);
        result.put("view", view);
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildView(Map<String, Object> product) {
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
        view.put("summary", summary);

        // images
        List<Map<String, Object>> images = flattenEdges(product, "images");
        view.put("images", images);

        // variants
        List<Map<String, Object>> variants = flattenEdges(product, "variants");
        view.put("variants", variants);

        // metafields
        List<Map<String, Object>> metafields = flattenEdges(product, "metafields");
        view.put("metafields", metafields);

        // grouping metafields by namespace for easier UI display
        Map<String, List<Map<String, Object>>> metafieldsByNamespace = metafields.stream()
                .collect(Collectors.groupingBy(
                        m -> String.valueOf(m.getOrDefault("namespace", "unknown")),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        view.put("metafieldsByNamespace", metafieldsByNamespace);

        return view;
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
}
