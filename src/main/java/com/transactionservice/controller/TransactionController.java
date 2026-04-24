package com.transactionservice.controller;

import com.transactionservice.dto.TransactionRequest;
import com.transactionservice.dto.TransactionResponse;
import com.transactionservice.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping
    public ResponseEntity<TransactionResponse> createTransaction(
            @Valid @RequestBody TransactionRequest request) {
        log.info("Received POST /transactions request: type='{}', amount='{}'",
                request.type(), request.amount());

        TransactionResponse response = transactionService.processTransaction(request);

        log.info("Transaction accepted: transactionId='{}'", response.transactionId());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
}
