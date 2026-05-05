package com.imam.allobank.service;

import com.imam.allobank.domain.*;
import com.imam.allobank.dto.SettlementDTO;
import com.imam.allobank.repository.BillGroupRepository;
import com.imam.allobank.repository.ExpenseRepository;
import com.imam.allobank.repository.PaymentRepository;
import com.imam.allobank.repository.ParticipantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("Settlement Service Tests")
class SettlementServiceTest {
    private BillGroupRepository billGroupRepository;
    private ParticipantRepository participantRepository;
    private ExpenseRepository expenseRepository;
    private PaymentRepository paymentRepository;
    private SettlementService settlementService;

    private BillGroup testBillGroup;
    private Participant alice;
    private Participant bob;
    private Participant charlie;

    @BeforeEach
    void setUp() {
        billGroupRepository = mock(BillGroupRepository.class);
        participantRepository = mock(ParticipantRepository.class);
        expenseRepository = mock(ExpenseRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        settlementService = new SettlementService(billGroupRepository, expenseRepository, 
                                                   participantRepository, paymentRepository);

        // Create test participants
        alice = Participant.builder().id(1L).name("Alice").email("alice@test.com").build();
        bob = Participant.builder().id(2L).name("Bob").email("bob@test.com").build();
        charlie = Participant.builder().id(3L).name("Charlie").email("charlie@test.com").build();

        // Create test bill group as mock to avoid circular references
        testBillGroup = mock(BillGroup.class);
        when(testBillGroup.getId()).thenReturn(1L);
        when(testBillGroup.getName()).thenReturn("Trip to Vegas");
        when(testBillGroup.getParticipants()).thenReturn(new HashSet<>(Arrays.asList(alice, bob, charlie)));
        when(testBillGroup.getExpenses()).thenReturn(new HashSet<>());
    }

    @Test
    @DisplayName("Should return per-category summary")
    void testCategorySummary() {
        Expense e1 = Expense.builder()
                .id(10L)
                .description("Food")
                .amount(new BigDecimal("50.00"))
                .paidBy(alice)
                .category(Expense.ExpenseCategory.FOOD)
                .splitType(Expense.SplitType.EQUAL)
                .splitBetween(new HashSet<>(Arrays.asList(alice, bob)))
                .billGroup(testBillGroup)
                .isPaid(false)
                .build();

        Expense e2 = Expense.builder()
                .id(11L)
                .description("Transport")
                .amount(new BigDecimal("30.00"))
                .paidBy(bob)
                .category(Expense.ExpenseCategory.TRANSPORT)
                .splitType(Expense.SplitType.EQUAL)
                .splitBetween(new HashSet<>(Arrays.asList(alice, bob)))
                .billGroup(testBillGroup)
                .isPaid(false)
                .build();

        when(testBillGroup.getExpenses()).thenReturn(new HashSet<>(Arrays.asList(e1, e2)));
        when(billGroupRepository.findById(1L)).thenReturn(Optional.of(testBillGroup));

        java.util.Map<String, java.math.BigDecimal> summary = settlementService.categorySummary(1L);
        assertEquals(new java.math.BigDecimal("50.00"), summary.get("FOOD"));
        assertEquals(new java.math.BigDecimal("30.00"), summary.get("TRANSPORT"));
    }

    @Test
    @DisplayName("Should calculate equal split correctly")
    void testEqualSplitCalculation() {
        // Alice paid $300 for hotel, split equally among 3 people
        Expense expense = Expense.builder()
                .id(1L)
                .description("Hotel")
                .amount(new BigDecimal("300.00"))
                .paidBy(alice)
                .category(Expense.ExpenseCategory.ACCOMMODATION)
                .splitType(Expense.SplitType.EQUAL)
                .splitBetween(new HashSet<>(Arrays.asList(alice, bob, charlie)))
                .billGroup(testBillGroup)
                .isPaid(false)
                .build();

        Set<Expense> expenses = new HashSet<>(Arrays.asList(expense));
        when(testBillGroup.getExpenses()).thenReturn(expenses);

        when(billGroupRepository.findById(1L)).thenReturn(Optional.of(testBillGroup));
        when(paymentRepository.findOutstandingPayments(1L)).thenReturn(new ArrayList<>());
        when(paymentRepository.findByBillGroupId(1L)).thenReturn(new ArrayList<>());

        SettlementDTO settlement = settlementService.calculateSettlement(1L);

        assertNotNull(settlement);
        assertEquals(1L, settlement.getBillGroupId());
        assertEquals("Trip to Vegas", settlement.getBillGroupName());
        assertEquals(new BigDecimal("300.00"), settlement.getTotalExpenses());
        assertEquals(new BigDecimal("9"), settlement.getServiceChargePct());
        assertEquals(new BigDecimal("27.00"), settlement.getServiceChargeAmount());
        assertNotNull(settlement.getTransactions());
    }

    @Test
    @DisplayName("Should handle multiple expenses correctly")
    void testMultipleExpenses() {
        // Alice paid $100 for dinner, split equally
        Expense expense1 = Expense.builder()
                .id(1L)
                .description("Dinner")
                .amount(new BigDecimal("100.00"))
                .paidBy(alice)
                .category(Expense.ExpenseCategory.FOOD)
                .splitType(Expense.SplitType.EQUAL)
                .splitBetween(new HashSet<>(Arrays.asList(alice, bob)))
                .billGroup(testBillGroup)
                .isPaid(false)
                .build();

        // Bob paid $150 for car rental, split equally
        Expense expense2 = Expense.builder()
                .id(2L)
                .description("Car Rental")
                .amount(new BigDecimal("150.00"))
                .paidBy(bob)
                .category(Expense.ExpenseCategory.TRANSPORT)
                .splitType(Expense.SplitType.EQUAL)
                .splitBetween(new HashSet<>(Arrays.asList(alice, bob, charlie)))
                .billGroup(testBillGroup)
                .isPaid(false)
                .build();

        Set<Expense> expenses = new HashSet<>(Arrays.asList(expense1, expense2));
        when(testBillGroup.getExpenses()).thenReturn(expenses);

        when(billGroupRepository.findById(1L)).thenReturn(Optional.of(testBillGroup));
        when(paymentRepository.findOutstandingPayments(1L)).thenReturn(new ArrayList<>());

        SettlementDTO settlement = settlementService.calculateSettlement(1L);

        assertNotNull(settlement);
        assertEquals(new BigDecimal("250.00"), settlement.getTotalExpenses());
        assertTrue(settlement.getTransactions().size() > 0);
    }

    @Test
    @DisplayName("Should calculate service charge correctly for imam-ramadani")
    void testServiceChargeCalculation() {
        Expense expense = Expense.builder()
                .id(1L)
                .description("Test")
                .amount(new BigDecimal("100.00"))
                .paidBy(alice)
                .category(Expense.ExpenseCategory.OTHER)
                .splitType(Expense.SplitType.EQUAL)
                .splitBetween(new HashSet<>(Arrays.asList(alice, bob)))
                .billGroup(testBillGroup)
                .isPaid(false)
                .build();

        Set<Expense> expenses = new HashSet<>(Arrays.asList(expense));
        when(testBillGroup.getExpenses()).thenReturn(expenses);

        when(billGroupRepository.findById(1L)).thenReturn(Optional.of(testBillGroup));
        when(paymentRepository.findOutstandingPayments(1L)).thenReturn(new ArrayList<>());

        SettlementDTO settlement = settlementService.calculateSettlement(1L);

        // Service charge should be 9% (based on imam-ramadani username)
        assertEquals(new BigDecimal("9"), settlement.getServiceChargePct());
        // 9% of 100 = 9.00
        assertEquals(new BigDecimal("9.00"), settlement.getServiceChargeAmount());
    }

    @Test
    @DisplayName("Should not include paid expenses in calculation")
    void testPaidExpensesExcluded() {
        Expense paidExpense = Expense.builder()
                .id(1L)
                .description("Paid Expense")
                .amount(new BigDecimal("100.00"))
                .paidBy(alice)
                .category(Expense.ExpenseCategory.FOOD)
                .splitType(Expense.SplitType.EQUAL)
                .splitBetween(new HashSet<>(Arrays.asList(alice, bob)))
                .billGroup(testBillGroup)
                .isPaid(true)  // This expense is paid
                .build();

        Set<Expense> expenses = new HashSet<>(Arrays.asList(paidExpense));
        when(testBillGroup.getExpenses()).thenReturn(expenses);

        when(billGroupRepository.findById(1L)).thenReturn(Optional.of(testBillGroup));
        when(paymentRepository.findOutstandingPayments(1L)).thenReturn(new ArrayList<>());

        SettlementDTO settlement = settlementService.calculateSettlement(1L);

        // Total expenses should be 0 since the only expense is marked as paid
        assertEquals(BigDecimal.ZERO, settlement.getTotalExpenses());
    }
}
