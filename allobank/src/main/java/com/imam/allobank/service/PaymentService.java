package com.imam.allobank.service;

import com.imam.allobank.domain.BillGroup;
import com.imam.allobank.domain.Payment;
import com.imam.allobank.domain.Participant;
import com.imam.allobank.dto.CreatePaymentRequest;
import com.imam.allobank.repository.BillGroupRepository;
import com.imam.allobank.repository.PaymentRepository;
import com.imam.allobank.repository.ParticipantRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Transactional
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final ParticipantRepository participantRepository;
    private final BillGroupRepository billGroupRepository;

    public PaymentService(PaymentRepository paymentRepository,
                          ParticipantRepository participantRepository,
                          BillGroupRepository billGroupRepository) {
        this.paymentRepository = paymentRepository;
        this.participantRepository = participantRepository;
        this.billGroupRepository = billGroupRepository;
    }

    public Payment recordPayment(Long billGroupId, CreatePaymentRequest request) {
        BillGroup billGroup = billGroupRepository.findById(billGroupId)
                .orElseThrow(() -> new EntityNotFoundException("Bill group not found"));

        Participant from = participantRepository.findById(request.getFromParticipantId())
                .orElseThrow(() -> new EntityNotFoundException("Participant not found"));
        Participant to = participantRepository.findById(request.getToParticipantId())
                .orElseThrow(() -> new EntityNotFoundException("Participant not found"));

        Payment payment = Payment.builder()
                .fromParticipant(from)
                .toParticipant(to)
                .amount(request.getAmount())
                .billGroup(billGroup)
                .isPaid(true)
                .paidAt(LocalDateTime.now())
                .build();

        return paymentRepository.save(payment);
    }
}
