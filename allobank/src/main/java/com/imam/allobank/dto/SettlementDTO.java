package com.imam.allobank.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SettlementDTO {
    private Long billGroupId;
    private String billGroupName;
    private List<SettlementTransactionDTO> transactions;
    private List<ParticipantBalanceDTO> participantBalances;
    private BigDecimal totalExpenses;
    private BigDecimal serviceChargePct;
    private BigDecimal serviceChargeAmount;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SettlementTransactionDTO {
        private String fromParticipant;
        private String toParticipant;
        private BigDecimal amount;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ParticipantBalanceDTO {
        private String name;
        private BigDecimal totalPaid;
        private BigDecimal totalOwed;
        private BigDecimal balance;
    }
}
