package com.transactionservice.financeiro.dto;

import com.transactionservice.financeiro.domain.StatusFinanceiro;
import com.transactionservice.financeiro.entity.AlunoFinanceiroEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AlunoFinanceiroResponse(
        Long id,
        Long alunoId,
        Long escolaId,
        String mesReferencia,
        BigDecimal mensalidade,
        BigDecimal alimentacao,
        BigDecimal multa,
        BigDecimal juros,
        BigDecimal total,
        StatusFinanceiro status,
        LocalDateTime dataGeracao,
        LocalDateTime dataAtualizacao
) {
    public static AlunoFinanceiroResponse from(AlunoFinanceiroEntity entity) {
        return new AlunoFinanceiroResponse(
                entity.getId(),
                entity.getAlunoId(),
                entity.getEscolaId(),
                entity.getMesReferencia(),
                entity.getMensalidade(),
                entity.getAlimentacao(),
                entity.getMulta(),
                entity.getJuros(),
                entity.getTotal(),
                entity.getStatus(),
                entity.getDataGeracao(),
                entity.getDataAtualizacao()
        );
    }
}
