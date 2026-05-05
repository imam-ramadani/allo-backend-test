package com.imam.allobank.repository;

import com.imam.allobank.domain.SplitTypeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SplitTypeRepository extends JpaRepository<SplitTypeEntity, Long> {
    Optional<SplitTypeEntity> findByStrategy(Integer strategy);
}
