package com.bookexpress.backend.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class CategoryRepository {
    private final JdbcTemplate jdbc;

    public CategoryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insertIgnore(String path) {
        jdbc.update("INSERT OR IGNORE INTO categories(path) VALUES(?)", path);
    }

    public List<String> findAll() {
        return jdbc.query("SELECT path FROM categories ORDER BY path",
                (rs, rowNum) -> rs.getString(1));
    }
}
