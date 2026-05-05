package com.transactionservice.financeiro.security;

import com.transactionservice.infrastructure.security.JwtDetails;
import com.transactionservice.infrastructure.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Controle de acesso para o módulo financeiro.
 *
 * <p>Regras:
 * <ul>
 *   <li>ADMIN → acesso total a todos os recursos.</li>
 *   <li>RESPONSAVEL → acesso apenas aos alunos vinculados (via claim {@code alunos_ids} no JWT).</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AlunoFinanceiroAccessControl {

    private static final String ROLE_ADMIN = "ADMIN";

    private final JwtTokenProvider jwtTokenProvider;

    /**
     * Valida que o usuário autenticado pode acessar os dados do {@code alunoId} informado.
     *
     * @param alunoId ID do aluno a ser acessado
     * @param auth    autenticação corrente do Spring Security
     * @throws AccessDeniedException se o acesso não for permitido
     */
    public void validarAcessoAluno(Long alunoId, Authentication auth) {
        List<String> roles = extrairRoles(auth);

        if (roles.contains(ROLE_ADMIN)) {
            log.debug("Acesso ADMIN concedido para alunoId='{}'", alunoId);
            return;
        }

        // RESPONSAVEL: valida vínculo via claim alunos_ids do JWT
        JwtDetails jwtDetails = (JwtDetails) auth.getDetails();
        List<Long> alunosIds = jwtTokenProvider.getAlunosIds(jwtDetails.rawToken());

        if (!alunosIds.contains(alunoId)) {
            log.warn("Acesso negado: usuario='{}' tentou acessar alunoId='{}'",
                    auth.getPrincipal(), alunoId);
            throw new AccessDeniedException(
                    "Acesso negado: você não tem permissão para acessar os dados deste aluno");
        }

        log.debug("Acesso RESPONSAVEL concedido: usuario='{}', alunoId='{}'",
                auth.getPrincipal(), alunoId);
    }

    /**
     * Valida que o usuário autenticado possui role ADMIN.
     *
     * @throws AccessDeniedException se não for ADMIN
     */
    public void validarAcessoAdmin(Authentication auth) {
        if (!extrairRoles(auth).contains(ROLE_ADMIN)) {
            log.warn("Acesso ADMIN negado para usuario='{}'", auth.getPrincipal());
            throw new AccessDeniedException(
                    "Acesso negado: operação restrita a ADMIN");
        }
    }

    private List<String> extrairRoles(Authentication auth) {
        JwtDetails jwtDetails = (JwtDetails) auth.getDetails();
        return jwtTokenProvider.getRoles(jwtDetails.rawToken());
    }
}
