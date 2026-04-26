package com.transactionservice.controller;

import com.transactionservice.model.request.TransactionRequest;
import com.transactionservice.model.response.TransactionResponse;
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
	public ResponseEntity<TransactionResponse> createTransaction(@Valid @RequestBody TransactionRequest request,
			@org.springframework.web.bind.annotation.RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
		log.info("Received POST /transactions request: type='{}', amount='{}'", request.type(), request.amount());

		TransactionResponse response = transactionService.processTransaction(request, idempotencyKey);

		log.info("Transaction accepted: transactionId='{}'", response.transactionId());
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
	}
}
