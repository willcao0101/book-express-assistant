package com.bookexpress.backend.config;

import com.bookexpress.backend.repository.CategoryTagMappingRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Build constrained category-tag mapping at startup.
 */
@Component
public class MappingBootstrap implements ApplicationRunner {

    private final CategoryTagMappingRepository mappingRepository;

    public MappingBootstrap(CategoryTagMappingRepository mappingRepository) {
        this.mappingRepository = mappingRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        mappingRepository.rebuild();
        System.out.println("[MappingBootstrap] category-tag mapping loaded. categories="
                + mappingRepository.getAllCategories().size());
    }
}
