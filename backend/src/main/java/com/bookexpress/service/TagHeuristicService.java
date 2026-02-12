package com.bookexpress.backend.service;

import com.bookexpress.backend.model.dto.CandidateTag;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class TagHeuristicService {

    /**
     * Merge existing tag scores with keyword overlap signal.
     */
    public List<CandidateTag> rerankByKeywords(List<CandidateTag> inputTags, List<String> keywords, int topN) {
        if (inputTags == null || inputTags.isEmpty()) return List.of();
        int n = Math.max(1, topN);

        String keywordText = normalize(String.join(" ", keywords == null ? List.of() : keywords));

        List<CandidateTag> rescored = new ArrayList<>();
        for (CandidateTag t : inputTags) {
            String tag = t.getTag();
            double base = t.getScore();

            double bonus = 0.0;
            String normTag = normalize(tag.replace("-", " "));
            for (String token : normTag.split("\\s+")) {
                if (!token.isBlank() && keywordText.contains(token)) {
                    bonus += 0.03;
                }
            }

            double finalScore = Math.min(0.99, base + bonus);
            rescored.add(new CandidateTag(tag, finalScore));
        }

        rescored.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));

        // deduplicate by tag
        LinkedHashMap<String, CandidateTag> map = new LinkedHashMap<>();
        for (CandidateTag t : rescored) {
            map.putIfAbsent(t.getTag(), t);
        }

        return map.values().stream().limit(n).collect(Collectors.toList());
    }

    private String normalize(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
