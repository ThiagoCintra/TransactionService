package com.transactionservice.financeiro.service;

import com.transactionservice.domains.SqsProducerDomain;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AlunoFinanceiroService Tests")
class AlunoFinanceiroServiceTest {

    @Mock
    private AlunoFinanceiroRepository repository;

    @Mock
    private AlunoFinanceiroAccessControl accessControl;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private SqsProducerDomain sqsProducer;

    private AlunoFinanceiroService service;

    private static final Long ESCOLA_ID = 10L;
    private static final Long ALUNO_ID = 42L;
    private static final String RAW_TOKEN = "raw-jwt-token";

    @BeforeEach
    void setUp() {
        service = new AlunoFinanceiroServiceImpl(repository, accessControl, jwtTokenProvider, sqsProducer);
        SecurityContextHolder.clearContext();
    }

    private void setAuthentication(String role) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "user-1", null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        auth.setDetails(new JwtDetails("MOBILE", RAW_TOKEN));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // -------------------------------------------------------------------------
    // consultarPorAluno
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Deve retornar lista de cobranças por aluno sem filtro de mês")
    void deveRetornarCobrancasPorAluno() {
        setAuthentication("ADMIN");
        when(jwtTokenProvider.getEscolaId(RAW_TOKEN)).thenReturn(ESCOLA_ID);
        doNothing().when(accessControl).validarAcessoAluno(eq(ALUNO_ID), any());

        AlunoFinanceiroEntity entity = buildEntity(1L, StatusFinanceiro.PENDENTE);
        when(repository.findByAlunoIdAndEscolaId(ALUNO_ID, ESCOLA_ID))
                .thenReturn(List.of(entity));

        List<AlunoFinanceiroResponse> result = service.consultarPorAluno(ALUNO_ID, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).alunoId()).isEqualTo(ALUNO_ID);
        assertThat(result.get(0).escolaId()).isEqualTo(ESCOLA_ID);
    }

    @Test
    @DisplayName("Deve retornar lista filtrada por mês")
    void deveRetornarCobrancasFiltradasPorMes() {
        setAuthentication("ADMIN");
        when(jwtTokenProvider.getEscolaId(RAW_TOKEN)).thenReturn(ESCOLA_ID);
        doNothing().when(accessControl).validarAcessoAluno(eq(ALUNO_ID), any());

        AlunoFinanceiroEntity entity = buildEntity(1L, StatusFinanceiro.PENDENTE);
        when(repository.findByAlunoIdAndMesReferenciaAndEscolaId(ALUNO_ID, "2025-05", ESCOLA_ID))
                .thenReturn(List.of(entity));

        List<AlunoFinanceiroResponse> result = service.consultarPorAluno(ALUNO_ID, "2025-05");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).mesReferencia()).isEqualTo("2025-05");
    }

    @Test
    @DisplayName("Deve lançar UnauthorizedException quando não há autenticação")
    void deveLancarUnauthorizedQuandoSemAutenticacao() {
        assertThatThrownBy(() -> service.consultarPorAluno(ALUNO_ID, null))
                .isInstanceOf(UnauthorizedException.class);

        verify(repository, never()).findByAlunoIdAndEscolaId(any(), any());
    }

    @Test
    @DisplayName("Deve lançar BusinessException quando escolaId não está no JWT")
    void deveLancarBusinessExceptionQuandoEscolaIdAusente() {
        setAuthentication("ADMIN");
        when(jwtTokenProvider.getEscolaId(RAW_TOKEN)).thenReturn(null);

        assertThatThrownBy(() -> service.consultarPorAluno(ALUNO_ID, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("escola_id");

        verify(repository, never()).findByAlunoIdAndEscolaId(any(), any());
    }

    @Test
    @DisplayName("Deve propagar AccessDeniedException quando acesso ao aluno não é permitido")
    void devePropagaAccessDeniedQuandoAcessoNaoPermitido() {
        setAuthentication("RESPONSAVEL");
        when(jwtTokenProvider.getEscolaId(RAW_TOKEN)).thenReturn(ESCOLA_ID);
        doThrow(new AccessDeniedException("Acesso negado"))
                .when(accessControl).validarAcessoAluno(eq(ALUNO_ID), any());

        assertThatThrownBy(() -> service.consultarPorAluno(ALUNO_ID, null))
                .isInstanceOf(AccessDeniedException.class);
    }

    // -------------------------------------------------------------------------
    // gerarMensalidade
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Deve gerar mensalidade com total calculado no backend")
    void deveGerarMensalidadeComTotalCalculado() {
        setAuthentication("ADMIN");
        when(jwtTokenProvider.getEscolaId(RAW_TOKEN)).thenReturn(ESCOLA_ID);
        doNothing().when(accessControl).validarAcessoAdmin(any());
        when(repository.existsByAlunoIdAndMesReferenciaAndEscolaId(ALUNO_ID, "2025-05", ESCOLA_ID))
                .thenReturn(false);
        when(repository.save(any())).thenAnswer(inv -> {
            AlunoFinanceiroEntity e = inv.getArgument(0);
            e.setId(1L);
            return e;
        });

        GerarMensalidadeRequest request = new GerarMensalidadeRequest(
                ALUNO_ID, "2025-05",
                new BigDecimal("500.00"),
                new BigDecimal("100.00"),
                new BigDecimal("10.00"),
                new BigDecimal("5.00")
        );

        AlunoFinanceiroResponse response = service.gerarMensalidade(request);

        assertThat(response.total()).isEqualByComparingTo(new BigDecimal("615.00"));
        assertThat(response.status()).isEqualTo(StatusFinanceiro.PENDENTE);
        assertThat(response.escolaId()).isEqualTo(ESCOLA_ID);
    }

    @Test
    @DisplayName("Deve calcular total corretamente quando multa e juros são nulos")
    void deveCalcularTotalSemMultaEJuros() {
        setAuthentication("ADMIN");
        when(jwtTokenProvider.getEscolaId(RAW_TOKEN)).thenReturn(ESCOLA_ID);
        doNothing().when(accessControl).validarAcessoAdmin(any());
        when(repository.existsByAlunoIdAndMesReferenciaAndEscolaId(ALUNO_ID, "2025-05", ESCOLA_ID))
                .thenReturn(false);
        when(repository.save(any())).thenAnswer(inv -> {
            AlunoFinanceiroEntity e = inv.getArgument(0);
            e.setId(2L);
            return e;
        });

        GerarMensalidadeRequest request = new GerarMensalidadeRequest(
                ALUNO_ID, "2025-05",
                new BigDecimal("500.00"),
                new BigDecimal("100.00"),
                null,
                null
        );

        AlunoFinanceiroResponse response = service.gerarMensalidade(request);

        assertThat(response.total()).isEqualByComparingTo(new BigDecimal("600.00"));
        assertThat(response.multa()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.juros()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Deve lançar BusinessException quando já existe cobrança para o mês")
    void deveLancarBusinessExceptionParaMesDuplicado() {
        setAuthentication("ADMIN");
        when(jwtTokenProvider.getEscolaId(RAW_TOKEN)).thenReturn(ESCOLA_ID);
        doNothing().when(accessControl).validarAcessoAdmin(any());
        when(repository.existsByAlunoIdAndMesReferenciaAndEscolaId(ALUNO_ID, "2025-05", ESCOLA_ID))
                .thenReturn(true);

        GerarMensalidadeRequest request = new GerarMensalidadeRequest(
                ALUNO_ID, "2025-05",
                new BigDecimal("500.00"), new BigDecimal("100.00"), null, null);

        assertThatThrownBy(() -> service.gerarMensalidade(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Já existe cobrança");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Deve lançar AccessDeniedException quando não ADMIN tenta gerar mensalidade")
    void deveLancarAccessDeniedParaNaoAdminGerarMensalidade() {
        setAuthentication("RESPONSAVEL");
        when(jwtTokenProvider.getEscolaId(RAW_TOKEN)).thenReturn(ESCOLA_ID);
        doThrow(new AccessDeniedException("Acesso negado"))
                .when(accessControl).validarAcessoAdmin(any());

        GerarMensalidadeRequest request = new GerarMensalidadeRequest(
                ALUNO_ID, "2025-05",
                new BigDecimal("500.00"), new BigDecimal("100.00"), null, null);

        assertThatThrownBy(() -> service.gerarMensalidade(request))
                .isInstanceOf(AccessDeniedException.class);
    }

    // -------------------------------------------------------------------------
    // registrarPagamento
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Deve registrar pagamento com sucesso")
    void deveRegistrarPagamento() {
        setAuthentication("ADMIN");
        doNothing().when(accessControl).validarAcessoAdmin(any());

        AlunoFinanceiroEntity entity = buildEntity(1L, StatusFinanceiro.PENDENTE);
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(repository.save(any())).thenReturn(entity);

        AlunoFinanceiroResponse response = service.registrarPagamento(1L,
                new PagamentoRequest(StatusFinanceiro.PAGO));

        assertThat(response.status()).isEqualTo(StatusFinanceiro.PAGO);
        verify(repository).save(entity);
    }

    @Test
    @DisplayName("Deve lançar BusinessException quando registro não encontrado")
    void deveLancarBusinessExceptionQuandoRegistroNaoEncontrado() {
        setAuthentication("ADMIN");
        doNothing().when(accessControl).validarAcessoAdmin(any());
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registrarPagamento(99L,
                new PagamentoRequest(StatusFinanceiro.PAGO)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("não encontrado");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private AlunoFinanceiroEntity buildEntity(Long id, StatusFinanceiro status) {
        return AlunoFinanceiroEntity.builder()
                .id(id)
                .alunoId(ALUNO_ID)
                .escolaId(ESCOLA_ID)
                .mesReferencia("2025-05")
                .mensalidade(new BigDecimal("500.00"))
                .alimentacao(new BigDecimal("100.00"))
                .multa(BigDecimal.ZERO)
                .juros(BigDecimal.ZERO)
                .total(new BigDecimal("600.00"))
                .status(status)
                .dataGeracao(LocalDateTime.now())
                .dataAtualizacao(LocalDateTime.now())
                .build();
    }
}
