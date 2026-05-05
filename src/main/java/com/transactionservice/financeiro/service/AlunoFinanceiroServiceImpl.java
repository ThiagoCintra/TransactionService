package com.transactionservice.financeiro.service;

import com.transactionservice.exception.BusinessException;
import com.transactionservice.exception.UnauthorizedException;
import com.transactionservice.financeiro.domain.StatusFinanceiro;
import com.transactionservice.financeiro.dto.AlunoFinanceiroResponse;
import com.transactionservice.financeiro.dto.GerarMensalidadeRequest;
import com.transactionservice.financeiro.dto.PagamentoRequest;
import com.transactionservice.financeiro.entity.AlunoFinanceiroEntity;
import com.transactionservice.financeiro.repository.AlunoFinanceiroRepository;
import com.transactionservice.financeiro.security.AlunoFinanceiroAccessControl;
import com.transactionservice.infrastructure.security.JwtDetails;
import com.transactionservice.infrastructure.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlunoFinanceiroServiceImpl implements AlunoFinanceiroService {

    private final AlunoFinanceiroRepository repository;
    private final AlunoFinanceiroAccessControl accessControl;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public List<AlunoFinanceiroResponse> consultarPorAluno(Long alunoId, String mes) {
        Authentication auth = getAuthentication();
        Long escolaId = extrairEscolaId(auth);
        accessControl.validarAcessoAluno(alunoId, auth);

        log.info("Consultando financeiro: alunoId='{}', mes='{}', escolaId='{}'",
                alunoId, mes, escolaId);

        if (mes != null && !mes.isBlank()) {
            return repository
                    .findByAlunoIdAndMesReferenciaAndEscolaId(alunoId, mes, escolaId)
                    .stream()
                    .map(AlunoFinanceiroResponse::from)
                    .toList();
        }

        return repository.findByAlunoIdAndEscolaId(alunoId, escolaId)
                .stream()
                .map(AlunoFinanceiroResponse::from)
                .toList();
    }

    @Override
    public AlunoFinanceiroResponse gerarMensalidade(GerarMensalidadeRequest request) {
        Authentication auth = getAuthentication();
        Long escolaId = extrairEscolaId(auth);
        accessControl.validarAcessoAdmin(auth);

        log.info("Gerando mensalidade: alunoId='{}', mes='{}', escolaId='{}'",
                request.alunoId(), request.mesReferencia(), escolaId);

        if (repository.existsByAlunoIdAndMesReferenciaAndEscolaId(
                request.alunoId(), request.mesReferencia(), escolaId)) {
            throw new BusinessException(
                    "Já existe cobrança para o aluno " + request.alunoId()
                            + " no mês " + request.mesReferencia());
        }

        BigDecimal multa = request.multa() != null ? request.multa() : BigDecimal.ZERO;
        BigDecimal juros = request.juros() != null ? request.juros() : BigDecimal.ZERO;

        // Cálculo obrigatório no backend — total nunca vem do cliente
        BigDecimal total = request.mensalidade()
                .add(request.alimentacao())
                .add(multa)
                .add(juros);

        AlunoFinanceiroEntity entity = AlunoFinanceiroEntity.builder()
                .alunoId(request.alunoId())
                .escolaId(escolaId)
                .mesReferencia(request.mesReferencia())
                .mensalidade(request.mensalidade())
                .alimentacao(request.alimentacao())
                .multa(multa)
                .juros(juros)
                .total(total)
                .status(StatusFinanceiro.PENDENTE)
                .dataGeracao(LocalDateTime.now())
                .dataAtualizacao(LocalDateTime.now())
                .build();

        AlunoFinanceiroEntity saved = repository.save(entity);
        log.info("Mensalidade gerada: id='{}', total='{}'", saved.getId(), saved.getTotal());
        return AlunoFinanceiroResponse.from(saved);
    }

    @Override
    public AlunoFinanceiroResponse registrarPagamento(Long id, PagamentoRequest request) {
        Authentication auth = getAuthentication();
        accessControl.validarAcessoAdmin(auth);

        log.info("Registrando pagamento: id='{}', novoStatus='{}'", id, request.status());

        AlunoFinanceiroEntity entity = repository.findById(id)
                .orElseThrow(() -> new BusinessException(
                        "Registro financeiro não encontrado: " + id));

        entity.setStatus(request.status());
        entity.setDataAtualizacao(LocalDateTime.now());

        AlunoFinanceiroEntity saved = repository.save(entity);
        log.info("Pagamento registrado: id='{}', status='{}'", saved.getId(), saved.getStatus());
        return AlunoFinanceiroResponse.from(saved);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Authentication getAuthentication() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new UnauthorizedException("Autenticação inválida ou ausente");
        }
        return auth;
    }

    private Long extrairEscolaId(Authentication auth) {
        JwtDetails jwtDetails = (JwtDetails) auth.getDetails();
        Long escolaId = jwtTokenProvider.getEscolaId(jwtDetails.rawToken());
        if (escolaId == null) {
            throw new BusinessException("Token inválido: claim escola_id não encontrado");
        }
        return escolaId;
    }
}
