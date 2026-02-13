package com.bookexpress.backend.controller;

import com.bookexpress.backend.repository.BookRepository;
import com.bookexpress.backend.repository.CategoryRepository;
import com.bookexpress.backend.service.NormalizationService;
import com.bookexpress.backend.util.JsonUtil;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/seed")
public class SeedController {
    private final CategoryRepository categoryRepository;
    private final BookRepository bookRepository;
    private final NormalizationService norm;
    private final JsonUtil jsonUtil;

    public SeedController(CategoryRepository categoryRepository,
                          BookRepository bookRepository,
                          NormalizationService norm,
                          JsonUtil jsonUtil) {
        this.categoryRepository = categoryRepository;
        this.bookRepository = bookRepository;
        this.norm = norm;
        this.jsonUtil = jsonUtil;
    }

    @PostMapping("/categories")
    public Map<String, Object> seedCategories(@RequestBody List<String> categories) {
        int cnt = 0;
        for (String c : categories) {
            if (c != null && !c.isBlank()) {
                categoryRepository.insertIgnore(c.trim());
                cnt++;
            }
        }
        return Map.of("ok", true, "insertedOrIgnored", cnt);
    }

    @PostMapping("/sample-books")
    public Map<String, Object> seedSampleBooks() {
        List<Sample> data = List.of(
                new Sample("The TranzAlpine Express", "Roy Sinclair", "Books ~ Non-fiction ~ Transport ~ Rail", List.of("rail", "new-zealand", "transport")),
                new Sample("Steps Across the Alps", "Paula Ridge", "Books ~ Non-fiction ~ Sport ~ Climbing, hiking & tramping", List.of("hiking", "tramping", "outdoors")),
                new Sample("Mystery at Milford Sound", "Quinn Harper", "Books ~ Fiction & literature ~ Mystery & thriller ~ Author P-R", List.of("mystery", "thriller", "nz-fiction")),
                new Sample("Pacific Tides", "Sina Vea", "Books ~ Non-fiction ~ Travel ~ Pacific Islands", List.of("travel", "pacific", "culture")),
                new Sample("Rails of Aotearoa", "Murray Cole", "Books ~ Non-fiction ~ New Zealand", List.of("new-zealand", "history", "rail")),
                new Sample("Desert Tracks Australia", "Adam Brown", "Books ~ Non-fiction ~ Travel ~ Australia", List.of("travel", "australia", "guide")),
                new Sample("The Last Signal", "Victor Stone", "Books ~ Fiction & literature ~ Science fiction & fantasy ~ Author V-Z", List.of("science-fiction", "future", "adventure")),
                new Sample("Business Laws Made Simple", "Helen Marsh", "Books ~ Non-fiction ~ Business, finance & law ~ Law", List.of("business", "law", "reference")),
                new Sample("World War I Frontlines", "Peter Grant", "Books ~ Non-fiction ~ War & military ~ World War I", List.of("war", "history", "ww1")),
                new Sample("Healthy Mind Reset", "Sophie Lin", "Books ~ Non-fiction ~ Health & lifestyle ~ Mental health", List.of("mental-health", "wellbeing", "self-help"))
        );

        int inserted = 0;
        for (Sample s : data) {
            String tNorm = norm.normalize(s.title);
            String aNorm = norm.normalize(s.author);
            bookRepository.insertBook(s.title, s.author, s.category, jsonUtil.toJson(s.tags), tNorm, aNorm);
            inserted++;
        }
        return Map.of("ok", true, "inserted", inserted);
    }

    record Sample(String title, String author, String category, List<String> tags) {}
}
