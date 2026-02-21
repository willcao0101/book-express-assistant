import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CategoryIdMapTagsBackfill {

    // =========================
    // Fill these values
    // =========================
    private static final String SQLITE_DB_PATH = "/Users/will/Projects/Java/book-express-assistant/backend/data/books.db";
    private static final String SHOP_DOMAIN = "book-express-nz.myshopify.com";
    private static final String ACCESS_TOKEN = System.getenv("SHOPIFY_ACCESS_TOKEN");
    private static final String API_VERSION = "2025-01";

    // Shopify pagination size
    private static final int FIRST = 100;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern GID_NUM_PATTERN = Pattern.compile(".*/(\\d+)$");

    public static void main(String[] args) throws Exception {
        validateInput();

        System.out.println("1) Fetching smart collections and building map: collectionId -> tags...");
        Map<Long, LinkedHashSet<String>> collectionIdToTags = fetchAllSmartCollectionsTagRules();

        System.out.println("   Smart collections mapped: " + collectionIdToTags.size());

        System.out.println("2) Updating SQLite category_id_map.tags...");
        backfillTagsToDb(collectionIdToTags);

        System.out.println("\n✅ Done.");
    }

    // ---------------------------
    // Step 1: Shopify - fetch all smart collections and parse TAG EQUALS rules
    // ---------------------------

    private static Map<Long, LinkedHashSet<String>> fetchAllSmartCollectionsTagRules() throws Exception {
        String endpoint = "https://" + SHOP_DOMAIN + "/admin/api/" + API_VERSION + "/graphql.json";

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        String cursor = null;
        boolean hasNext = true;

        Map<Long, LinkedHashSet<String>> out = new LinkedHashMap<>();

        while (hasNext) {
            String body = querySmartCollectionsPage(client, endpoint, ACCESS_TOKEN.trim(), FIRST, cursor);

            JsonNode root = MAPPER.readTree(body);
            if (root.has("errors")) {
                throw new IllegalStateException("GraphQL errors: " + root.get("errors").toPrettyString());
            }

            JsonNode collections = root.path("data").path("collections");
            JsonNode edges = collections.path("edges");

            if (edges.isArray()) {
                for (JsonNode edge : edges) {
                    JsonNode node = edge.path("node");

                    String gid = node.path("id").asText("");
                    Long numericId = parseGidNumericId(gid);
                    if (numericId == null || numericId <= 0) continue;

                    LinkedHashSet<String> tags = extractTagEqualsRules(node);
                    if (!tags.isEmpty()) {
                        out.computeIfAbsent(numericId, k -> new LinkedHashSet<>()).addAll(tags);
                    }
                }
            }

            JsonNode pageInfo = collections.path("pageInfo");
            hasNext = pageInfo.path("hasNextPage").asBoolean(false);
            cursor = pageInfo.path("endCursor").isNull() ? null : pageInfo.path("endCursor").asText(null);
        }

        return out;
    }

    private static LinkedHashSet<String> extractTagEqualsRules(JsonNode collectionNode) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        JsonNode rules = collectionNode.path("ruleSet").path("rules");
        if (!rules.isArray()) return tags;

        for (JsonNode r : rules) {
            String column = r.path("column").asText("");
            String relation = r.path("relation").asText("");
            String condition = r.path("condition").asText("");

            if ("TAG".equalsIgnoreCase(column)
                    && "EQUALS".equalsIgnoreCase(relation)
                    && !condition.isBlank()) {
                tags.add(condition.trim());
            }
        }
        return tags;
    }

    private static String querySmartCollectionsPage(HttpClient client, String endpoint, String token, int first, String after)
            throws Exception {

        String query = """
            query SmartCollections($first: Int!, $after: String) {
              collections(first: $first, after: $after, query: "collection_type:smart") {
                edges {
                  cursor
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
                pageInfo {
                  hasNextPage
                  endCursor
                }
              }
            }
            """;

        String variables = """
            {
              "first": %d,
              "after": %s
            }
            """.formatted(first, after == null ? "null" : "\"" + escapeJson(after) + "\"");

        String payload = """
            {
              "query": %s,
              "variables": %s
            }
            """.formatted(toJsonString(query), variables);

        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .header("X-Shopify-Access-Token", token)
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        int code = response.statusCode();
        if (code >= 400) {
            throw new IllegalStateException("HTTP error: " + code + ", body=" + response.body());
        }

        return response.body();
    }

    private static Long parseGidNumericId(String gid) {
        if (gid == null || gid.isBlank()) return null;
        Matcher m = GID_NUM_PATTERN.matcher(gid.trim());
        if (!m.find()) return null;
        try {
            return Long.parseLong(m.group(1));
        } catch (Exception e) {
            return null;
        }
    }

    // ---------------------------
    // Step 2: SQLite - add tags column if needed and backfill
    // ---------------------------

    private static void backfillTagsToDb(Map<Long, LinkedHashSet<String>> collectionIdToTags) throws Exception {
        String jdbc = "jdbc:sqlite:" + SQLITE_DB_PATH;

        try (Connection conn = DriverManager.getConnection(jdbc)) {
            conn.setAutoCommit(false);

            ensureTagsColumn(conn);

            List<Long> categoryIds = loadAllCategoryIds(conn);
            System.out.println("   category_id_map rows: " + categoryIds.size());

            int updated = 0;
            int skippedNoMapping = 0;

            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE category_id_map SET tags = ? WHERE category_id = ?"
            )) {
                for (Long cid : categoryIds) {
                    LinkedHashSet<String> tags = collectionIdToTags.get(cid);
                    if (tags == null || tags.isEmpty()) {
                        skippedNoMapping++;
                        continue;
                    }

                    String joined = String.join(", ", tags);

                    ps.setString(1, joined);
                    ps.setLong(2, cid);
                    ps.addBatch();

                    updated++;
                    if (updated % 200 == 0) {
                        ps.executeBatch();
                        conn.commit();
                        System.out.println("   Updated " + updated + " rows...");
                    }
                }

                ps.executeBatch();
            }

            conn.commit();
            System.out.println("   Updated rows: " + updated);
            System.out.println("   Skipped (no mapping): " + skippedNoMapping);
        }
    }

    private static void ensureTagsColumn(Connection conn) throws SQLException {
        boolean hasTags = false;

        try (PreparedStatement ps = conn.prepareStatement("PRAGMA table_info(category_id_map)");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String name = rs.getString("name");
                if ("tags".equalsIgnoreCase(name)) {
                    hasTags = true;
                    break;
                }
            }
        }

        if (!hasTags) {
            try (Statement st = conn.createStatement()) {
                st.execute("ALTER TABLE category_id_map ADD COLUMN tags TEXT");
            }
            conn.commit();
            System.out.println("   Added column: category_id_map.tags");
        } else {
            System.out.println("   Column exists: category_id_map.tags");
        }
    }

    private static List<Long> loadAllCategoryIds(Connection conn) throws SQLException {
        List<Long> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT category_id FROM category_id_map");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.add(rs.getLong(1));
            }
        }
        return out;
    }

    // ---------------------------
    // Utils
    // ---------------------------

    private static void validateInput() {
        requireNonBlank(SQLITE_DB_PATH, "SQLITE_DB_PATH is blank");
        requireNonBlank(SHOP_DOMAIN, "SHOP_DOMAIN is blank");
        requireNonBlank(ACCESS_TOKEN, "ACCESS_TOKEN is blank");

        if (!SHOP_DOMAIN.endsWith(".myshopify.com")) {
            throw new IllegalArgumentException("SHOP_DOMAIN should look like: xxx.myshopify.com");
        }
    }

    private static void requireNonBlank(String s, String msg) {
        if (s == null || s.isBlank()) {
            throw new IllegalArgumentException(msg);
        }
    }

    private static String toJsonString(String raw) {
        return "\"" + escapeJson(raw) + "\"";
    }

    private static String escapeJson(String s) {
        return Objects.toString(s, "")
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}