package com.imam.allobank.repository;

import com.imam.allobank.domain.BillGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BillGroupRepository extends JpaRepository<BillGroup, Long> {
}
