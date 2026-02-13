package com.bookexpress.backend.service;

import com.bookexpress.backend.model.dto.CandidateTag;
import com.bookexpress.backend.model.dto.PredictRequest;
import com.bookexpress.backend.model.dto.PredictResponse;
import com.bookexpress.backend.repository.BookRepository;
import com.bookexpress.backend.repository.CategoryTagMappingRepository;
import com.bookexpress.backend.util.JsonUtil;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class PredictService {

    private final BookRepository bookRepository;
    private final CategoryTagMappingRepository categoryTagMappingRepository;
    private final LlmService llmService;
    private final TagHeuristicService tagHeuristicService;
    private final JsonUtil jsonUtil;

    public PredictService(BookRepository bookRepository,
                          CategoryTagMappingRepository categoryTagMappingRepository,
                          LlmService llmService,
                          TagHeuristicService tagHeuristicService,
                          JsonUtil jsonUtil) {
        this.bookRepository = bookRepository;
        this.categoryTagMappingRepository = categoryTagMappingRepository;
        this.llmService = llmService;
        this.tagHeuristicService = tagHeuristicService;
        this.jsonUtil = jsonUtil;
    }

    public PredictResponse predict(PredictRequest req) {
        int topK = req.topK() == null ? 5 : Math.max(1, req.topK());
        boolean useAI = req.useAI() != null && req.useAI();

        String title = safe(req.title());
        String author = safe(req.author());

        String tNorm = normalize(title);
        String aNorm = normalize(author);

        // 1) Exact DB match by title+author
        List<Map<String, Object>> exactRows = bookRepository.findExact(tNorm, aNorm, 1);
        if (!exactRows.isEmpty()) {
            Map<String, Object> row = exactRows.get(0);

            String category = nvl(Objects.toString(row.get("trademe_categories"), null),
                    "Books → Non-fiction → Other");
            String tagsJson = Objects.toString(row.get("shopify_tags"), "[]");

            // Instance call (NOT static)
            List<String> rawTags = jsonUtil.fromJsonArray(tagsJson);

            List<CandidateTag> tags = rawTags.stream()
                    .filter(s -> s != null && !s.isBlank())
                    .limit(topK)
                    .map(t -> new CandidateTag(t, 0.95))
                    .collect(Collectors.toList());

            return buildResponse(
                    "EXACT",
                    category,
                    tags,
                    0.95,
                    "Exact DB match by title + author",
                    true
            );
        }

        // Ensure mapping cache loaded
        if (!categoryTagMappingRepository.isLoaded()) {
            categoryTagMappingRepository.rebuild();
        }

        // 2) Category must come from existing mapping only
        List<String> allCategories = new ArrayList<>(categoryTagMappingRepository.getAllCategories());
        if (allCategories.isEmpty()) {
            return buildResponse(
                    "RULE",
                    "Books → Non-fiction → Other",
                    List.of(new CandidateTag("tm-nf-other", 0.50)),
                    0.50,
                    "No taxonomy mapping available in DB",
                    true
            );
        }

        String selectedCategory = chooseCategoryFromExistingOnly(title, author, allCategories);

        // 3) Tags must come from selected category mapping only
        List<String> allowedTags = new ArrayList<>(categoryTagMappingRepository.getTagsByCategory(selectedCategory));

        if (allowedTags.isEmpty()) {
            return buildResponse(
                    useAI ? "LLM" : "RULE",
                    selectedCategory,
                    List.of(),
                    useAI ? 0.60 : 0.55,
                    "Selected category has no mapped tags in DB",
                    true
            );
        }

        List<CandidateTag> finalTags;
        String strategy;
        double confidence;
        String reason;

        if (useAI) {
            List<String> picked = llmService.constrainedTags(
                    title + " " + author,
                    allowedTags,
                    topK
            );

            Set<String> allowedSet = new LinkedHashSet<>(allowedTags);
            List<String> safePicked = picked == null ? List.of() :
                    picked.stream()
                            .filter(allowedSet::contains)
                            .distinct()
                            .limit(topK)
                            .toList();

            if (safePicked.isEmpty()) {
                finalTags = rulePickTags(allowedTags, topK);
                strategy = "RULE";
                confidence = 0.62;
                reason = "No DB hit; LLM returned no valid tag, fallback to mapping-only rule";
            } else {
                List<CandidateTag> base = toCandidateTags(safePicked, 0.72, 0.04);
                List<String> keywords = buildKeywords(title, author);
                finalTags = tagHeuristicService.rerankByKeywords(base, keywords, topK);

                strategy = "LLM";
                confidence = 0.70;
                reason = "No DB hit; category and tags selected from existing mapping with LLM-assisted ranking";
            }
        } else {
            finalTags = rulePickTags(allowedTags, topK);
            strategy = "RULE";
            confidence = 0.62;
            reason = "No DB hit; category and tags selected from existing mapping only";
        }

        return buildResponse(strategy, selectedCategory, finalTags, confidence, reason, true);
    }

    private PredictResponse buildResponse(String strategy,
                                          String category,
                                          List<CandidateTag> tags,
                                          double confidence,
                                          String reason,
                                          boolean allowed) {
        PredictResponse r = new PredictResponse(strategy, category, tags, confidence, reason, allowed);
        r.setSelectedBy(strategy);
        r.setLlmCalled("LLM".equals(strategy));
        r.setLlmEffective("LLM".equals(strategy));
        return r;
    }

    private String chooseCategoryFromExistingOnly(String title, String author, List<String> categories) {
        String query = normalize(title + " " + author);
        if (query.isBlank()) return categories.get(0);

        String best = categories.get(0);
        int bestScore = Integer.MIN_VALUE;

        for (String c : categories) {
            int s = lexicalScore(query, c);
            if (s > bestScore) {
                bestScore = s;
                best = c;
            }
        }
        return best;
    }

    private List<CandidateTag> toCandidateTags(List<String> tags, double startScore, double stepDown) {
        List<CandidateTag> out = new ArrayList<>();
        for (int i = 0; i < tags.size(); i++) {
            double score = Math.max(0.10, startScore - (i * stepDown));
            out.add(new CandidateTag(tags.get(i), score));
        }
        return out;
    }

    private List<CandidateTag> rulePickTags(List<String> allowedTags, int topK) {
        List<CandidateTag> out = new ArrayList<>();
        for (int i = 0; i < Math.min(topK, allowedTags.size()); i++) {
            double score = Math.max(0.40, 0.62 - i * 0.06);
            out.add(new CandidateTag(allowedTags.get(i), score));
        }
        return out;
    }

    private List<String> buildKeywords(String title, String author) {
        String text = (safe(title) + " " + safe(author)).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]+", " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (text.isBlank()) return List.of();

        Set<String> stop = Set.of(
                "the", "a", "an", "and", "or", "of", "to", "in", "on", "for", "with", "by", "from",
                "book", "novel", "guide", "edition", "author", "unknown"
        );

        LinkedHashSet<String> uniq = new LinkedHashSet<>();
        for (String w : text.split(" ")) {
            if (w.length() < 3) continue;
            if (stop.contains(w)) continue;
            uniq.add(w);
            if (uniq.size() >= 8) break;
        }
        return new ArrayList<>(uniq);
    }

    private int lexicalScore(String q, String candidate) {
        String c = normalize(candidate).replace("-", " ");
        int score = 0;
        for (String t : c.split("\\s+")) {
            if (!t.isBlank() && q.contains(t)) score++;
        }
        return score;
    }

    private String normalize(String s) {
        return safe(s).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private String nvl(String v, String dft) {
        return (v == null || v.isBlank()) ? dft : v;
    }
}
