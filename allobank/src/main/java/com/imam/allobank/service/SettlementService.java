package com.imam.allobank.service;

import com.imam.allobank.domain.*;
import com.imam.allobank.dto.*;
import com.imam.allobank.repository.BillGroupRepository;
import com.imam.allobank.repository.ExpenseRepository;
import com.imam.allobank.repository.PaymentRepository;
import com.imam.allobank.repository.ParticipantRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class SettlementService {
    private final BillGroupRepository billGroupRepository;
    private final ExpenseRepository expenseRepository;
    private final ParticipantRepository participantRepository;
    private final PaymentRepository paymentRepository;

    // Service charge percentage for imam-ramadani
    // GitHub username: imam-ramadani
    // Unicode sum: i(105) + m(109) + a(97) + m(109) + -(45) + r(114) + a(97) + m(109) + a(97) + d(100) + a(97) + n(110) + i(105) = 1279
    // service_charge_pct = 1279 % 10 = 9
    private static final BigDecimal SERVICE_CHARGE_PCT = BigDecimal.valueOf(9);

    public SettlementService(BillGroupRepository billGroupRepository,
                           ExpenseRepository expenseRepository,
                           ParticipantRepository participantRepository,
                           PaymentRepository paymentRepository) {
        this.billGroupRepository = billGroupRepository;
        this.expenseRepository = expenseRepository;
        this.participantRepository = participantRepository;
        this.paymentRepository = paymentRepository;
    }

    public SettlementDTO calculateSettlement(Long billGroupId) {
        BillGroup billGroup = billGroupRepository.findById(billGroupId)
                .orElseThrow(() -> new EntityNotFoundException("Bill group not found"));

        // Calculate total expenses (sum of unpaid expense amounts)
        BigDecimal totalExpenses = billGroup.getExpenses().stream()
                .filter(expense -> !expense.getIsPaid())
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Calculate individual balances and participation details
        Map<Participant, BigDecimal> participantBalances = calculateParticipantBalances(billGroup);
        Map<Participant, BigDecimal> totalPaidMap = calculateTotalPaid(billGroup);
        Map<Participant, BigDecimal> totalOwedMap = calculateTotalOwed(billGroup);

        // Calculate service charge
        BigDecimal serviceChargeAmount = totalExpenses
                .multiply(SERVICE_CHARGE_PCT)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        // Apply recorded payments to participant balances
        // Payment reduces debt: debtor's balance increases (becomes less negative), creditor's balance decreases
        List<Payment> payments = paymentRepository.findByBillGroupId(billGroupId);
        for (Payment p : payments) {
            if (p.getIsPaid() != null && p.getIsPaid()) {
                Participant from = p.getFromParticipant();
                Participant to = p.getToParticipant();
                java.math.BigDecimal amt = p.getAmount() != null ? p.getAmount() : java.math.BigDecimal.ZERO;
                // Debtor paid money to creditor -> their debt decreases
                participantBalances.put(from, participantBalances.getOrDefault(from, java.math.BigDecimal.ZERO).add(amt));
                // Creditor received money -> their claim decreases
                participantBalances.put(to, participantBalances.getOrDefault(to, java.math.BigDecimal.ZERO).subtract(amt));
            }
        }

        // Optimize settlement after applying payments
        List<SettlementDTO.SettlementTransactionDTO> transactions = optimizeSettlement(participantBalances);

        // Build settlement balance responses, sorted by participant ID for deterministic ordering
        List<SettlementDTO.ParticipantBalanceDTO> participantBalancesList = billGroup.getParticipants().stream()
                .sorted((a, b) -> a.getId().compareTo(b.getId()))
                .map(participant -> SettlementDTO.ParticipantBalanceDTO.builder()
                        .name(participant.getName())
                        .totalPaid(totalPaidMap.getOrDefault(participant, BigDecimal.ZERO))
                        .totalOwed(totalOwedMap.getOrDefault(participant, BigDecimal.ZERO))
                        .balance(participantBalances.getOrDefault(participant, BigDecimal.ZERO))
                        .build())
                .collect(Collectors.toList());

        return SettlementDTO.builder()
                .billGroupId(billGroupId)
                .billGroupName(billGroup.getName())
                .transactions(transactions)
                .participantBalances(participantBalancesList)
                .totalExpenses(totalExpenses)
                .serviceChargePct(SERVICE_CHARGE_PCT)
                .serviceChargeAmount(serviceChargeAmount)
                .build();
    }

    public java.util.Map<String, java.math.BigDecimal> categorySummary(Long billGroupId) {
        BillGroup billGroup = billGroupRepository.findById(billGroupId)
                .orElseThrow(() -> new EntityNotFoundException("Bill group not found"));

        java.util.Map<String, java.math.BigDecimal> summary = new java.util.HashMap<>();
        for (Expense e : billGroup.getExpenses()) {
            if (e.getIsPaid() != null && e.getIsPaid()) continue; // only unpaid considered
            String cat = e.getCategory() != null ? e.getCategory().name() : "OTHER";
            summary.put(cat, summary.getOrDefault(cat, java.math.BigDecimal.ZERO).add(e.getAmount()));
        }
        return summary;
    }

    /**
     * Calculate total amount each participant paid (as payer)
     */
    private Map<Participant, BigDecimal> calculateTotalPaid(BillGroup billGroup) {
        Map<Participant, BigDecimal> totalPaid = new HashMap<>();
        
        // Initialize all participants
        for (Participant p : billGroup.getParticipants()) {
            totalPaid.put(p, BigDecimal.ZERO);
        }
        
        // Sum amounts paid by each participant
        for (Expense e : billGroup.getExpenses()) {
            if (!e.getIsPaid()) {
                Participant payer = e.getPaidBy();
                totalPaid.put(payer, totalPaid.getOrDefault(payer, BigDecimal.ZERO).add(e.getAmount()));
            }
        }
        
        return totalPaid;
    }

    /**
     * Calculate total amount each participant owes (their share of unpaid expenses)
     */
    private Map<Participant, BigDecimal> calculateTotalOwed(BillGroup billGroup) {
        Map<Participant, BigDecimal> totalOwed = new HashMap<>();
        
        // Initialize all participants
        for (Participant p : billGroup.getParticipants()) {
            totalOwed.put(p, BigDecimal.ZERO);
        }
        
        // Sum amounts owed by each participant based on splits
        for (Expense e : billGroup.getExpenses()) {
            if (!e.getIsPaid()) {
                Map<Participant, BigDecimal> splitAmounts = calculateSplit(e);
                for (Map.Entry<Participant, BigDecimal> entry : splitAmounts.entrySet()) {
                    totalOwed.put(entry.getKey(), 
                            totalOwed.getOrDefault(entry.getKey(), BigDecimal.ZERO).add(entry.getValue()));
                }
            }
        }
        
        return totalOwed;
    }

    private Map<Participant, BigDecimal> calculateParticipantBalances(BillGroup billGroup) {
        Map<Participant, BigDecimal> balances = new HashMap<>();

        // Initialize all participants with 0 balance
        for (Participant participant : billGroup.getParticipants()) {
            balances.put(participant, BigDecimal.ZERO);
        }

        // Process expenses
        for (Expense expense : billGroup.getExpenses()) {
            if (!expense.getIsPaid()) {
                // Add amount to payer (positive balance means they paid more)
                balances.put(expense.getPaidBy(), 
                        balances.get(expense.getPaidBy()).add(expense.getAmount()));

                // Distribute the expense among split participants
                Map<Participant, BigDecimal> splitAmounts = calculateSplit(expense);
                for (Map.Entry<Participant, BigDecimal> entry : splitAmounts.entrySet()) {
                    balances.put(entry.getKey(), 
                            balances.get(entry.getKey()).subtract(entry.getValue()));
                }
            }
        }

        return balances;
    }

    private Map<Participant, BigDecimal> calculateSplit(Expense expense) {
        Map<Participant, BigDecimal> split = new HashMap<>();
        Set<Participant> participants = expense.getSplitBetween();
        switch (expense.getSplitType()) {
            case EQUAL:
                BigDecimal perPerson = expense.getAmount()
                        .divide(BigDecimal.valueOf(participants.size()), 2, RoundingMode.HALF_UP);
                for (Participant p : participants) {
                    split.put(p, perPerson);
                }
                break;

            case EXACT_AMOUNT:
            case PERCENTAGE:
                // Try to read split details JSON saved on the expense (participantId -> amount)
                String json = expense.getSplitDetailsJson();
                if (json != null && !json.isBlank()) {
                    try {
                        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                        java.util.Map<String, java.math.BigDecimal> details = mapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, java.math.BigDecimal>>(){});
                        for (Participant p : participants) {
                            String idStr = String.valueOf(p.getId());
                            java.math.BigDecimal amt = details.get(idStr);
                            if (amt == null) {
                                // fallback to equal for missing entries
                                amt = expense.getAmount().divide(BigDecimal.valueOf(participants.size()), 2, RoundingMode.HALF_UP);
                            }
                            split.put(p, amt);
                        }
                    } catch (Exception e) {
                        // parsing failed; fallback to equal
                        BigDecimal per = expense.getAmount()
                                .divide(BigDecimal.valueOf(participants.size()), 2, RoundingMode.HALF_UP);
                        for (Participant p : participants) {
                            split.put(p, per);
                        }
                    }
                } else {
                    BigDecimal per = expense.getAmount()
                            .divide(BigDecimal.valueOf(participants.size()), 2, RoundingMode.HALF_UP);
                    for (Participant p : participants) {
                        split.put(p, per);
                    }
                }
                break;
        }

        return split;
    }

    /**
     * Optimize settlement to minimize the number of transactions needed
     * Using a greedy algorithm approach
     */
    private List<SettlementDTO.SettlementTransactionDTO> optimizeSettlement(
            Map<Participant, BigDecimal> balances) {
        List<SettlementDTO.SettlementTransactionDTO> transactions = new ArrayList<>();

        // Separate debtors (negative balance) and creditors (positive balance)
        List<Map.Entry<Participant, BigDecimal>> debtors = balances.entrySet().stream()
                .filter(e -> e.getValue().compareTo(BigDecimal.ZERO) < 0)
                .sorted((a, b) -> a.getValue().compareTo(b.getValue())) // most negative first
                .collect(Collectors.toList());

        List<Map.Entry<Participant, BigDecimal>> creditors = balances.entrySet().stream()
                .filter(e -> e.getValue().compareTo(BigDecimal.ZERO) > 0)
                .sorted((a, b) -> b.getValue().compareTo(a.getValue())) // largest positive first
                .collect(Collectors.toList());

        // Match debtors with creditors
        while (!debtors.isEmpty() && !creditors.isEmpty()) {
            Map.Entry<Participant, BigDecimal> debtorEntry = debtors.get(0);
            Map.Entry<Participant, BigDecimal> creditorEntry = creditors.get(0);

            BigDecimal debtAmount = debtorEntry.getValue().abs();
            BigDecimal creditAmount = creditorEntry.getValue();

            BigDecimal settlement = debtAmount.min(creditAmount);

            transactions.add(SettlementDTO.SettlementTransactionDTO.builder()
                    .fromParticipant(debtorEntry.getKey().getName())
                    .toParticipant(creditorEntry.getKey().getName())
                    .amount(settlement)
                    .build());

            // Update amounts
            debtorEntry.setValue(debtorEntry.getValue().add(settlement));
            creditorEntry.setValue(creditorEntry.getValue().subtract(settlement));

            // Remove if zero
            if (debtorEntry.getValue().compareTo(BigDecimal.ZERO) == 0) {
                debtors.remove(0);
            }
            if (creditorEntry.getValue().compareTo(BigDecimal.ZERO) == 0) {
                creditors.remove(0);
            }

            // Re-sort after updates
            debtors.sort((a, b) -> a.getValue().compareTo(b.getValue()));
            creditors.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        }

        return transactions;
    }
}
