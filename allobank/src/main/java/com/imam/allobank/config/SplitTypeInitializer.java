package com.imam.allobank.config;

import com.imam.allobank.domain.SplitTypeEntity;
import com.imam.allobank.repository.SplitTypeRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SplitTypeInitializer implements ApplicationRunner {
    private final SplitTypeRepository repository;

    public SplitTypeInitializer(SplitTypeRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        // Insert default split strategies if not present
        List<SplitTypeEntity> all = repository.findAll();
        if (all.stream().noneMatch(s -> s.getStrategy() != null && s.getStrategy() == 1)) {
            repository.save(SplitTypeEntity.builder().strategy(1).description("EQUAL").build());
        }
        if (all.stream().noneMatch(s -> s.getStrategy() != null && s.getStrategy() == 2)) {
            repository.save(SplitTypeEntity.builder().strategy(2).description("PERCENTAGE").build());
        }
        if (all.stream().noneMatch(s -> s.getStrategy() != null && s.getStrategy() == 3)) {
            repository.save(SplitTypeEntity.builder().strategy(3).description("EXACT_AMOUNT").build());
        }
    }
}
