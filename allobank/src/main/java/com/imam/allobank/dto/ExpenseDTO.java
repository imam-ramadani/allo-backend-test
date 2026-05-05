package com.imam.allobank.dto;

import com.imam.allobank.domain.Expense;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExpenseDTO {
    private Long id;
    private String description;
    private BigDecimal amount;
    private Long paidById;
    private String paidByName;
    private Expense.ExpenseCategory category;
    private Expense.SplitType splitType;
    private Set<Long> splitBetweenIds;
}
