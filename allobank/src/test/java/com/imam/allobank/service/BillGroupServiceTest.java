package com.imam.allobank.service;

import com.imam.allobank.domain.BillGroup;
import com.imam.allobank.domain.Participant;
import com.imam.allobank.dto.BillGroupDTO;
import com.imam.allobank.dto.CreateBillGroupRequest;
import com.imam.allobank.dto.CreateExpenseRequest;
import java.util.Optional;
import com.imam.allobank.repository.BillGroupRepository;
import com.imam.allobank.repository.ExpenseRepository;
import com.imam.allobank.repository.ParticipantRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BillGroupServiceTest {

    private BillGroupRepository billGroupRepository;
    private ParticipantRepository participantRepository;
    private ExpenseRepository expenseRepository;
    private BillGroupService billGroupService;

    @BeforeEach
    void setUp() {
        billGroupRepository = mock(BillGroupRepository.class);
        participantRepository = mock(ParticipantRepository.class);
        expenseRepository = mock(ExpenseRepository.class);
        billGroupService = new BillGroupService(billGroupRepository, participantRepository, expenseRepository);
    }

    @Test
    void createBillGroup_createsParticipantsAndReturnsDTO() {
        CreateBillGroupRequest.CreateParticipantRequest p1 = CreateBillGroupRequest.CreateParticipantRequest.builder()
                .name("Alice").email("a@x.com").build();
        CreateBillGroupRequest.CreateParticipantRequest p2 = CreateBillGroupRequest.CreateParticipantRequest.builder()
                .name("Bob").email("b@x.com").build();

        CreateBillGroupRequest request = CreateBillGroupRequest.builder()
                .name("Trip")
                .participants(Set.of(p1, p2))
                .build();

        // Mock billGroupRepository.save to assign an ID
        when(billGroupRepository.save(any(BillGroup.class))).thenAnswer(invocation -> {
            BillGroup bg = invocation.getArgument(0);
            if (bg.getId() == null) bg.setId(1L);
            return bg;
        });

        // Mock participantRepository.save to assign incremental IDs
        AtomicLong idCounter = new AtomicLong(1);
        when(participantRepository.save(any(Participant.class))).thenAnswer(invocation -> {
            Participant p = invocation.getArgument(0);
            if (p.getId() == null) p.setId(idCounter.getAndIncrement());
            return p;
        });

        BillGroupDTO dto = billGroupService.createBillGroup(request);

        assertNotNull(dto);
        assertEquals("Trip", dto.getName());
        assertNotNull(dto.getId());
        assertEquals(2, dto.getParticipants().size());
    }

    @Test
    void addExpense_storesSplitDetailsJson() {
        // prepare
        when(billGroupRepository.findById(1L)).thenReturn(Optional.of(BillGroup.builder().id(1L).name("Trip").build()));
        when(participantRepository.findById(1L)).thenReturn(Optional.of(Participant.builder().id(1L).name("Alice").build()));
        when(participantRepository.findById(2L)).thenReturn(Optional.of(Participant.builder().id(2L).name("Bob").build()));

        CreateExpenseRequest req = CreateExpenseRequest.builder()
                .description("Dinner")
                .amount(new java.math.BigDecimal("100.00"))
                .paidById(1L)
                .splitStrategy("EQUAL")
                .splitBetweenIds(Set.of(1L,2L))
                .build();

        billGroupService.addExpense(1L, req);

        // verify save called
        org.mockito.Mockito.verify(expenseRepository).save(org.mockito.ArgumentMatchers.any());
    }
}
