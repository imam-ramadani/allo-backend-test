package com.imam.allobank.service;

import com.imam.allobank.domain.BillGroup;
import com.imam.allobank.domain.Expense;
import com.imam.allobank.domain.Participant;
import com.imam.allobank.dto.*;
import com.imam.allobank.repository.BillGroupRepository;
import com.imam.allobank.repository.ExpenseRepository;
import com.imam.allobank.repository.ParticipantRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Map;
import java.math.BigDecimal;

@Service
@Transactional
public class BillGroupService {
        private final BillGroupRepository billGroupRepository;
        private final ParticipantRepository participantRepository;
        private final ExpenseRepository expenseRepository;
        private final com.imam.allobank.repository.SplitTypeRepository splitTypeRepository;

        public BillGroupService(BillGroupRepository billGroupRepository,
                                                  ParticipantRepository participantRepository,
                                                  ExpenseRepository expenseRepository) {
                this.billGroupRepository = billGroupRepository;
                this.participantRepository = participantRepository;
                this.expenseRepository = expenseRepository;
                this.splitTypeRepository = null;
        }

        // New constructor used by Spring when SplitTypeRepository is available
        @org.springframework.beans.factory.annotation.Autowired
        public BillGroupService(BillGroupRepository billGroupRepository,
                                                        ParticipantRepository participantRepository,
                                                        ExpenseRepository expenseRepository,
                                                        com.imam.allobank.repository.SplitTypeRepository splitTypeRepository) {
                this.billGroupRepository = billGroupRepository;
                this.participantRepository = participantRepository;
                this.expenseRepository = expenseRepository;
                this.splitTypeRepository = splitTypeRepository;
        }

    public BillGroupDTO createBillGroup(CreateBillGroupRequest request) {
        BillGroup billGroup = BillGroup.builder()
                .name(request.getName())
                .build();
        billGroup = billGroupRepository.save(billGroup);

        // Create participants
                // Ensure participants set initialized (builder may leave it null)
                if (billGroup.getParticipants() == null) {
                        billGroup.setParticipants(new java.util.HashSet<>());
                }

                for (CreateBillGroupRequest.CreateParticipantRequest participant : request.getParticipants()) {
            Participant p = Participant.builder()
                    .name(participant.getName())
                    .email(participant.getEmail())
                    .billGroup(billGroup)
                    .build();
            participantRepository.save(p);
            billGroup.getParticipants().add(p);
        }

        billGroup = billGroupRepository.save(billGroup);
        return mapToDTO(billGroup);
    }

    public BillGroupDTO getBillGroup(Long id) {
        BillGroup billGroup = billGroupRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Bill group not found"));
        return mapToDTO(billGroup);
    }

    public void addExpense(Long billGroupId, CreateExpenseRequest request) {
        BillGroup billGroup = billGroupRepository.findById(billGroupId)
                .orElseThrow(() -> new EntityNotFoundException("Bill group not found"));

        Participant paidBy = participantRepository.findById(request.getPaidById())
                .orElseThrow(() -> new EntityNotFoundException("Participant not found"));

        Set<Participant> splitBetween = request.getSplitBetweenIds().stream()
                .map(id -> participantRepository.findById(id)
                        .orElseThrow(() -> new EntityNotFoundException("Participant not found")))
                .collect(Collectors.toSet());

                Expense.ExpenseCategory category = request.getCategory() != null
                                ? request.getCategory()
                                : Expense.ExpenseCategory.OTHER;

                // Determine split type using the new enum-based resolution method
                Expense.SplitType splitType = request.resolveSplitType();

                Expense expense = Expense.builder()
                                .description(request.getDescription())
                                .amount(request.getAmount())
                                .paidBy(paidBy)
                                .category(category)
                                .splitType(splitType)
                                .splitBetween(splitBetween)
                                .billGroup(billGroup)
                                .build();

                // Build split details map: participantId -> amount
                Map<Long, BigDecimal> splitDetails = new java.util.HashMap<>();
                BigDecimal total = request.getAmount();

                if (splitType == Expense.SplitType.EQUAL) {
                        BigDecimal per = total.divide(BigDecimal.valueOf(splitBetween.size()), 2, BigDecimal.ROUND_HALF_UP);
                        for (Participant p : splitBetween) {
                                splitDetails.put(p.getId(), per);
                        }
                } else if (splitType == Expense.SplitType.EXACT_AMOUNT) {
                        if (request.getSplitAmounts() != null && !request.getSplitAmounts().isEmpty()) {
                                for (Map.Entry<Long, BigDecimal> e : request.getSplitAmounts().entrySet()) {
                                        splitDetails.put(e.getKey(), e.getValue());
                                }
                        } else {
                                // fallback to equal
                                BigDecimal per = total.divide(BigDecimal.valueOf(splitBetween.size()), 2, BigDecimal.ROUND_HALF_UP);
                                for (Participant p : splitBetween) {
                                        splitDetails.put(p.getId(), per);
                                }
                        }
                } else if (splitType == Expense.SplitType.PERCENTAGE) {
                        if (request.getSplitPercentages() != null && !request.getSplitPercentages().isEmpty()) {
                                for (Map.Entry<Long, BigDecimal> e : request.getSplitPercentages().entrySet()) {
                                        BigDecimal pct = e.getValue();
                                        BigDecimal amt = total.multiply(pct).divide(BigDecimal.valueOf(100), 2, BigDecimal.ROUND_HALF_UP);
                                        splitDetails.put(e.getKey(), amt);
                                }
                        } else {
                                BigDecimal per = total.divide(BigDecimal.valueOf(splitBetween.size()), 2, BigDecimal.ROUND_HALF_UP);
                                for (Participant p : splitBetween) {
                                        splitDetails.put(p.getId(), per);
                                }
                        }
                }

                // attach split details JSON to expense for later settlement calculation
                ObjectMapper mapper = new ObjectMapper();
                try {
                        String json = mapper.writeValueAsString(splitDetails);
                        expense.setSplitDetailsJson(json);
                } catch (JsonProcessingException e) {
                        // best-effort: leave splitDetailsJson null (settlement will fallback to equal)
                }

                expenseRepository.save(expense);
    }

    private BillGroupDTO mapToDTO(BillGroup billGroup) {
        Set<ParticipantDTO> participantsDTO = java.util.Optional.ofNullable(billGroup.getParticipants())
                .orElseGet(java.util.Collections::emptySet)
                .stream()
                .map(p -> ParticipantDTO.builder()
                        .id(p.getId())
                        .name(p.getName())
                        .email(p.getEmail())
                        .build())
                .collect(Collectors.toSet());

        return BillGroupDTO.builder()
                .id(billGroup.getId())
                .name(billGroup.getName())
                .participants(participantsDTO)
                .build();
    }
}
