package com.transactionservice.financeiro.entity;

import com.transactionservice.financeiro.domain.StatusFinanceiro;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlunoFinanceiroEntity {

    private Long id;

    /** ID do aluno (referência externa — não acessa diretamente o banco de alunos). */
    private Long alunoId;

    /** ID da escola: impõe multi-tenancy em todos os acessos. */
    private Long escolaId;

    /** Mês de referência no formato YYYY-MM. */
    private String mesReferencia;

    private BigDecimal mensalidade;
    private BigDecimal alimentacao;
    private BigDecimal multa;
    private BigDecimal juros;

    /**
     * Total calculado exclusivamente no backend:
     * total = mensalidade + alimentacao + multa + juros
     */
    private BigDecimal total;

    private StatusFinanceiro status;
    private LocalDateTime dataGeracao;
    private LocalDateTime dataAtualizacao;
}
