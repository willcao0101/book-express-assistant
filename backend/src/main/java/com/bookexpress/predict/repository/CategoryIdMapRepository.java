package com.bookexpress.backend.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;

/**
 * Category ID <-> Category Path mapping repository.
 *
 * Data is treated as "static reference data":
 * - seeded once when DB is created / app starts for the first time
 * - afterwards used for query only (no updates in UI for now)
 */
@Repository
public class CategoryIdMapRepository {

    private final JdbcTemplate jdbc;

    public CategoryIdMapRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void ensureTable() {
        // SQLite compatible DDL
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS category_id_map (
                category_id INTEGER PRIMARY KEY,
                category_path TEXT NOT NULL
            );
        """);

        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_category_id_map_path ON category_id_map(category_path);");
    }

    public long count() {
        Long c = jdbc.queryForObject("SELECT COUNT(1) FROM category_id_map", Long.class);
        return c == null ? 0L : c;
    }

    /**
     * Insert rows in batch. Duplicates will be ignored (INSERT OR IGNORE).
     */
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

    public Optional<String> findCategoryPathById(long categoryId) {
        List<String> list = jdbc.query(
                "SELECT category_path FROM category_id_map WHERE category_id = ?",
                (rs, rowNum) -> rs.getString(1),
                categoryId
        );
        return list.isEmpty() ? Optional.empty() : Optional.ofNullable(list.get(0));
    }

    /**
     * Simple keyword search by category path.
     */
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

    public record Row(long categoryId, String categoryPath) {}
}
