package com.bookexpress.backend.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;

@Repository
public class CategoryIdMapRepository {

    private final JdbcTemplate jdbc;

    public CategoryIdMapRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void ensureTable() {
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS category_id_map (
                category_id INTEGER PRIMARY KEY,
                category_path TEXT NOT NULL
            );
        """);

        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_category_id_map_path ON category_id_map(category_path);");

        ensureColumnExists("category_id_map", "tags", "TEXT");
    }

    public Optional<String> findTagsById(long categoryId) {
        List<String> list = jdbc.query(
                "SELECT tags FROM category_id_map WHERE category_id = ?",
                (rs, rowNum) -> rs.getString(1),
                categoryId
        );
        return list.isEmpty() ? Optional.empty() : Optional.ofNullable(list.get(0));
    }

    public Optional<String> findCategoryPathById(long categoryId) {
        List<String> list = jdbc.query(
                "SELECT category_path FROM category_id_map WHERE category_id = ?",
                (rs, rowNum) -> rs.getString(1),
                categoryId
        );
        return list.isEmpty() ? Optional.empty() : Optional.ofNullable(list.get(0));
    }

    public long count() {
        Long c = jdbc.queryForObject("SELECT COUNT(1) FROM category_id_map", Long.class);
        return c == null ? 0L : c;
    }

    public void batchInsertIgnore(List<Row> rows) {
        if (rows == null || rows.isEmpty()) return;

        jdbc.batchUpdate(
                "INSERT OR IGNORE INTO category_id_map(category_id, category_path) VALUES (?, ?)",
                rows,
                500,
                (ps, row) -> {
                    ps.setLong(1, row.categoryId());
                    ps.setString(2, row.categoryPath());
                }
        );
    }

    public List<Map<String, Object>> searchByKeyword(String keyword, int limit) {
        String kw = keyword == null ? "" : keyword.trim();
        if (kw.isEmpty()) return Collections.emptyList();

        int lim = Math.max(1, Math.min(limit, 200));

        return jdbc.query(
                """
                SELECT category_id, category_path
                FROM category_id_map
                WHERE LOWER(category_path) LIKE ?
                ORDER BY category_path
                LIMIT ?
                """,
                (rs, rowNum) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("categoryId", rs.getLong("category_id"));
                    m.put("category", rs.getString("category_path"));
                    return m;
                },
                "%" + kw.toLowerCase(Locale.ROOT) + "%",
                lim
        );
    }

    /**
     * Aggregates tags across the entire category_id_map table.
     * category_id_map.tags stores comma-separated tags.
     */
    public List<String> findAllDistinctTags() {
        List<String> rawList = jdbc.query(
                "SELECT tags FROM category_id_map WHERE tags IS NOT NULL AND TRIM(tags) <> ''",
                (rs, rowNum) -> rs.getString(1)
        );

        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String raw : rawList) {
            if (raw == null) continue;
            String s = raw.trim();
            if (s.isEmpty()) continue;

            String[] parts = s.split(",");
            for (String p : parts) {
                String t = p == null ? "" : p.trim();
                if (!t.isEmpty()) out.add(t);
            }
        }

        return new ArrayList<>(out);
    }

    public record Row(long categoryId, String categoryPath) {}

    private void ensureColumnExists(String table, String column, String ddlType) {
        try {
            List<String> cols = jdbc.query(
                    "PRAGMA table_info(" + table + ")",
                    (rs, rowNum) -> rs.getString("name")
            );
            for (String c : cols) {
                if (column.equalsIgnoreCase(c)) return;
            }
            jdbc.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + ddlType);
        } catch (Exception ignore) {
        }
    }
}