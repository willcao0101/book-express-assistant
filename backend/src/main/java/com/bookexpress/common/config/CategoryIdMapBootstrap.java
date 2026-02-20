package com.bookexpress.backend.config;

import com.bookexpress.backend.repository.CategoryIdMapRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Seeds static Trade Me category mapping table (category_id_map).
 *
 * - Runs on startup.
 * - Creates table if missing.
 * - Seeds ONLY when table is empty.
 *
 * Source file priority:
 * 1) classpath: db/category_id_map.tsv  (recommended)
 * 2) filesystem: ./data/category_id_map.tsv  (matches current repo layout: backend/data/category_id_map.tsv)
 */
@Component
public class CategoryIdMapBootstrap implements ApplicationRunner {

    private final CategoryIdMapRepository repository;

    public CategoryIdMapBootstrap(CategoryIdMapRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        repository.ensureTable();

        long existing = repository.count();
        if (existing > 0) {
            System.out.println("[CategoryIdMapBootstrap] category_id_map already seeded. rows=" + existing);
            return;
        }

        // 1) Try classpath
        InputStream in = null;
        ClassPathResource cp = new ClassPathResource("db/category_id_map.tsv");
        if (cp.exists()) {
            in = cp.getInputStream();
            System.out.println("[CategoryIdMapBootstrap] Seeding from classpath: db/category_id_map.tsv");
        } else {
            // 2) Try filesystem (relative to working dir)
            Path fs = Path.of("data", "category_id_map.tsv");
            if (Files.exists(fs)) {
                in = Files.newInputStream(fs);
                System.out.println("[CategoryIdMapBootstrap] Seeding from filesystem: " + fs.toAbsolutePath());
            }
        }

        if (in == null) {
            System.out.println("[CategoryIdMapBootstrap] category_id_map.tsv not found (classpath db/ or filesystem ./data/). Skipping seeding.");
            return;
        }

        List<CategoryIdMapRepository.Row> rows = new ArrayList<>(4096);

        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            boolean first = true;

            while ((line = br.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;

                // Skip header if present: categoryid<TAB>Category
                if (first) {
                    first = false;
                    String lower = trimmed.toLowerCase();
                    if (lower.startsWith("categoryid") && lower.contains("category")) {
                        continue;
                    }
                }

                // Expect: <id>\t<Category path>
                String[] parts = trimmed.split("\t", 2);
                if (parts.length < 2) continue;

                String idStr = parts[0].trim();
                String path = parts[1].trim();
                if (idStr.isEmpty() || path.isEmpty()) continue;

                long id;
                try {
                    id = Long.parseLong(idStr);
                } catch (NumberFormatException ignore) {
                    continue;
                }

                rows.add(new CategoryIdMapRepository.Row(id, path));
            }
        }

        repository.batchInsertIgnore(rows);

        System.out.println("[CategoryIdMapBootstrap] category_id_map seeded. rows=" + repository.count());
    }
}