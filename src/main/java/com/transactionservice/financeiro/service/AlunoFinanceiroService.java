package com.transactionservice.financeiro.service;

import com.transactionservice.financeiro.dto.AlunoFinanceiroResponse;
import com.transactionservice.financeiro.dto.GerarMensalidadeRequest;
import com.transactionservice.financeiro.dto.PagamentoRequest;

import java.util.List;

public interface AlunoFinanceiroService {

    /**
     * Consulta cobranças de um aluno, filtradas opcionalmente por mês.
     * Aplica multi-tenancy via escolaId extraído do JWT.
     */
    List<AlunoFinanceiroResponse> consultarPorAluno(Long alunoId, String mes);

    /**
     * Gera cobrança mensal para um aluno.
     * O total é calculado exclusivamente no backend: mensalidade + alimentacao + multa + juros.
     * Requer role ADMIN.
     */
    AlunoFinanceiroResponse gerarMensalidade(GerarMensalidadeRequest request);

    /**
     * Registra pagamento (atualização interna de status). Uso restrito a ADMIN.
     */
    AlunoFinanceiroResponse registrarPagamento(Long id, PagamentoRequest request);
}
