package com.imam.allobank.dto;

import com.imam.allobank.domain.Expense;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateExpenseRequest {
    @NotBlank(message = "Description cannot be blank")
    private String description;

    @NotNull(message = "Amount cannot be null")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal amount;

    @NotNull(message = "Paid by participant ID cannot be null")
    @JsonAlias({"paidByParticipantId", "paidById"})
    private Long paidById;

    // Category is optional in requests; defaults to OTHER if omitted
    private Expense.ExpenseCategory category;

    // splitStrategy can be:
    // 1. String enum: "EQUAL", "PERCENTAGE", "EXACT_AMOUNT"
    // 2. Numeric (for backward compatibility): 1=EQUAL, 2=PERCENTAGE, 3=EXACT_AMOUNT
    private String splitStrategy;

    @NotEmpty(message = "At least one participant must be in the split")
    @JsonAlias({"splitBetweenParticipantIds", "splitBetweenIds"})
    private Set<Long> splitBetweenIds;

    // For EXACT_AMOUNT split type: map of participant ID to amount
    private Map<Long, BigDecimal> splitAmounts;

    // For PERCENTAGE split type: map of participant ID to percentage
    private Map<Long, BigDecimal> splitPercentages;

    /**
     * Convert splitStrategy to SplitType enum
     * Supports both string ("EQUAL", "PERCENTAGE", "EXACT_AMOUNT") and numeric (1, 2, 3) formats
     */
    public Expense.SplitType resolveSplitType() {
        if (splitStrategy == null || splitStrategy.isBlank()) {
            return Expense.SplitType.EQUAL; // default
        }
        
        // Try to parse as enum string first
        try {
            return Expense.SplitType.valueOf(splitStrategy.toUpperCase());
        } catch (IllegalArgumentException e1) {
            // Try to parse as numeric
            try {
                int numeric = Integer.parseInt(splitStrategy);
                return switch (numeric) {
                    case 1 -> Expense.SplitType.EQUAL;
                    case 2 -> Expense.SplitType.PERCENTAGE;
                    case 3 -> Expense.SplitType.EXACT_AMOUNT;
                    default -> throw new IllegalArgumentException("Invalid numeric split strategy: " + numeric);
                };
            } catch (NumberFormatException e2) {
                throw new IllegalArgumentException("Invalid split strategy format. Use enum string (EQUAL, PERCENTAGE, EXACT_AMOUNT) or numeric (1, 2, 3): " + splitStrategy);
            }
        }
    }
}
