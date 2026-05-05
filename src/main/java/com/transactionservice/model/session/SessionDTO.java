package com.transactionservice.model.session;

import java.util.List;

public record SessionDTO(
        String userId,
        String username,
        List<String> roles,
        Long escolaId,
        List<Long> alunosIds
) {}
