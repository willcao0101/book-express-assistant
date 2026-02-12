package com.bookexpress.backend.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/**
 * Repository for books table access.
 */
@Repository
public class BookRepository {

    private final JdbcTemplate jdbcTemplate;

    public BookRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Insert one book record.
     * This method is used by SeedController.
     */
    public int insertBook(String title,
                          String author,
                          String category,
                          String tagsJson,
                          String normalizedTitle,
                          String normalizedAuthor) {
        String sql = """
                INSERT INTO books (
                    book_title,
                    book_author,
                    trademe_categories,
                    shopify_tags,
                    normalized_title,
                    normalized_author
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        return jdbcTemplate.update(
                sql,
                title,
                author,
                category,
                tagsJson,
                normalizedTitle,
                normalizedAuthor
        );
    }

    /**
     * Exact match by normalized title and author.
     */
    public List<Map<String, Object>> findExact(String normalizedTitle, String normalizedAuthor, int limit) {
        String sql = """
                SELECT id, book_title, book_author, trademe_categories, shopify_tags
                FROM books
                WHERE lower(trim(book_title)) = ?
                  AND lower(trim(book_author)) = ?
                LIMIT ?
                """;
        return jdbcTemplate.queryForList(sql, normalizedTitle, normalizedAuthor, limit);
    }

    /**
     * Returns rows used to build category-tag mapping cache.
     */
    public List<Map<String, Object>> findAllCategoryTagRows() {
        String sql = """
                SELECT trademe_categories, shopify_tags
                FROM books
                WHERE trademe_categories IS NOT NULL
                  AND trim(trademe_categories) <> ''
                  AND shopify_tags IS NOT NULL
                  AND trim(shopify_tags) <> ''
                """;
        return jdbcTemplate.queryForList(sql);
    }
}
