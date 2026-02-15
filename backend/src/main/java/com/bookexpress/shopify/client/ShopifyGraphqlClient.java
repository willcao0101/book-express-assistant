package com.bookexpress.shopify.client;

import com.bookexpress.account.entity.ShopifyAccountEntity;
import com.bookexpress.account.service.AccountService;
import com.bookexpress.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Component
public class ShopifyGraphqlClient {

    private final AccountService accountService;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.shopify.api-version}")
    private String apiVersion;

    @Value("${app.shopify.graph-url-template}")
    private String graphUrlTemplate;

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

        ShopifyAccountEntity account = accountService.getById(accountId);
        String token = accountService.getAccessTokenPlain(accountId);

        String url = graphUrlTemplate
                .replace("{shopDomain}", account.getShopDomain())
                .replace("{apiVersion}", apiVersion);

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
        variables.put("id", normalizeProductId(productId));

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("query", query);
        requestBody.put("variables", variables);

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

    private String normalizeProductId(String productId) {
        String input = productId.trim();
        if (input.startsWith("gid://shopify/Product/")) {
            return input;
        }
        if (!input.matches("\\d+")) {
            throw new BusinessException("productId must be numeric (e.g. 8112925769802) or full gid");
        }
        return "gid://shopify/Product/" + input;
    }
}
