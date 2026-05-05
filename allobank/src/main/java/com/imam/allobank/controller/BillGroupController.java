package com.imam.allobank.controller;

import com.imam.allobank.dto.BillGroupDTO;
import com.imam.allobank.dto.CreateBillGroupRequest;
import com.imam.allobank.dto.CreateExpenseRequest;
import com.imam.allobank.dto.SettlementDTO;
import com.imam.allobank.service.BillGroupService;
import com.imam.allobank.service.SettlementService;
import com.imam.allobank.service.PaymentService;
import com.imam.allobank.dto.CreatePaymentRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/bill-groups")
public class BillGroupController {
    private final BillGroupService billGroupService;
    private final SettlementService settlementService;
    private final PaymentService paymentService;

    public BillGroupController(BillGroupService billGroupService,
                             SettlementService settlementService,
                             PaymentService paymentService) {
        this.billGroupService = billGroupService;
        this.settlementService = settlementService;
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<BillGroupDTO> createBillGroup(@Valid @RequestBody CreateBillGroupRequest request) {
        BillGroupDTO billGroupDTO = billGroupService.createBillGroup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(billGroupDTO);
    }

    @GetMapping("/{id}")
    public ResponseEntity<BillGroupDTO> getBillGroup(@PathVariable Long id) {
        BillGroupDTO billGroupDTO = billGroupService.getBillGroup(id);
        return ResponseEntity.ok(billGroupDTO);
    }

    @PostMapping("/{id}/expenses")
    public ResponseEntity<Void> addExpense(@PathVariable Long id,
                                          @Valid @RequestBody CreateExpenseRequest request) {
        billGroupService.addExpense(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/{id}/settlement")
    public ResponseEntity<SettlementDTO> getSettlement(@PathVariable Long id) {
        SettlementDTO settlementDTO = settlementService.calculateSettlement(id);
        return ResponseEntity.ok(settlementDTO);
    }

    @PostMapping("/{id}/payments")
    public ResponseEntity<Void> recordPayment(@PathVariable Long id,
                                              @Valid @RequestBody CreatePaymentRequest request) {
        paymentService.recordPayment(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/{id}/categories-summary")
    public ResponseEntity<java.util.Map<String, java.math.BigDecimal>> getCategorySummary(@PathVariable Long id) {
        java.util.Map<String, java.math.BigDecimal> summary = settlementService.categorySummary(id);
        return ResponseEntity.ok(summary);
    }
}
