package com.imam.allobank.repository;

import com.imam.allobank.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    @Query("SELECT p FROM Payment p WHERE p.billGroup.id = :billGroupId AND p.isPaid = false")
    List<Payment> findOutstandingPayments(@Param("billGroupId") Long billGroupId);

    @Query("SELECT p FROM Payment p WHERE p.billGroup.id = :billGroupId")
    List<Payment> findByBillGroupId(@Param("billGroupId") Long billGroupId);
}
