package com.transactionservice.service;

import com.transactionservice.model.request.TransactionRequest;
import com.transactionservice.model.response.TransactionResponse;

/**
 * Service interface for transaction processing. Implementation must keep existing behavior.
 */
public interface TransactionService {

    TransactionResponse processTransaction(TransactionRequest request);

    TransactionResponse processTransaction(TransactionRequest request, String idempotencyKey);

}
