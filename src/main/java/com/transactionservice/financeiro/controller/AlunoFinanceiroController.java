package com.transactionservice.financeiro.controller;

import com.transactionservice.financeiro.dto.AlunoFinanceiroResponse;
import com.transactionservice.financeiro.dto.GerarMensalidadeRequest;
import com.transactionservice.financeiro.dto.PagamentoRequest;
import com.transactionservice.financeiro.service.AlunoFinanceiroService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/financeiro")
@RequiredArgsConstructor
public class AlunoFinanceiroController {

    private final AlunoFinanceiroService financeiroService;

    /**
     * GET /api/v1/financeiro/{alunoId}?mes=YYYY-MM
     * Consulta cobranças de um aluno, com filtro opcional por mês.
     */
    @GetMapping("/{alunoId}")
    public ResponseEntity<List<AlunoFinanceiroResponse>> consultarPorAlunoPorPath(
            @PathVariable Long alunoId,
            @RequestParam(required = false) String mes) {
        log.info("GET /api/v1/financeiro/{} mes='{}'", alunoId, mes);
        return ResponseEntity.ok(financeiroService.consultarPorAluno(alunoId, mes));
    }

    /**
     * GET /api/v1/financeiro?alunoId=&mes=
     * Consulta cobranças de um aluno via query params.
     */
    @GetMapping
    public ResponseEntity<List<AlunoFinanceiroResponse>> consultarPorAlunoPorQuery(
            @RequestParam Long alunoId,
            @RequestParam(required = false) String mes) {
        log.info("GET /api/v1/financeiro?alunoId={}&mes={}", alunoId, mes);
        return ResponseEntity.ok(financeiroService.consultarPorAluno(alunoId, mes));
    }

    /**
     * POST /api/v1/financeiro/gerar-mensal
     * Gera cobrança mensal. Restrito a ADMIN.
     */
    @PostMapping("/gerar-mensal")
    public ResponseEntity<AlunoFinanceiroResponse> gerarMensalidade(
            @Valid @RequestBody GerarMensalidadeRequest request) {
        log.info("POST /api/v1/financeiro/gerar-mensal: alunoId='{}', mes='{}'",
                request.alunoId(), request.mesReferencia());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(financeiroService.gerarMensalidade(request));
    }

    /**
     * PATCH /api/v1/financeiro/{id}/interno/pagamento
     * Atualização interna de status de pagamento. Uso restrito a ADMIN.
     */
    @PatchMapping("/{id}/interno/pagamento")
    public ResponseEntity<AlunoFinanceiroResponse> registrarPagamento(
            @PathVariable Long id,
            @Valid @RequestBody PagamentoRequest request) {
        log.info("PATCH /api/v1/financeiro/{}/interno/pagamento: status='{}'",
                id, request.status());
        return ResponseEntity.ok(financeiroService.registrarPagamento(id, request));
    }
}
