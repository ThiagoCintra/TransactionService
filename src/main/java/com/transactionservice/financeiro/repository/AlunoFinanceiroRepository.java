package com.transactionservice.financeiro.repository;

import com.transactionservice.financeiro.entity.AlunoFinanceiroEntity;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Repositório em memória para {@link AlunoFinanceiroEntity}.
 *
 * <p>Em produção, substituir por implementação JPA (Spring Data) sem alterar a interface
 * de serviço, respeitando o princípio de inversão de dependência.
 */
@Repository
public class AlunoFinanceiroRepository {

    private final Map<Long, AlunoFinanceiroEntity> store = new ConcurrentHashMap<>();
    private final AtomicLong idSequence = new AtomicLong(1);

    public AlunoFinanceiroEntity save(AlunoFinanceiroEntity entity) {
        if (entity.getId() == null) {
            entity.setId(idSequence.getAndIncrement());
        }
        store.put(entity.getId(), entity);
        return entity;
    }

    public Optional<AlunoFinanceiroEntity> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    public List<AlunoFinanceiroEntity> findByAlunoIdAndEscolaId(Long alunoId, Long escolaId) {
        return store.values().stream()
                .filter(e -> e.getAlunoId().equals(alunoId) && e.getEscolaId().equals(escolaId))
                .collect(Collectors.toList());
    }

    public List<AlunoFinanceiroEntity> findByAlunoIdAndMesReferenciaAndEscolaId(
            Long alunoId, String mesReferencia, Long escolaId) {
        return store.values().stream()
                .filter(e -> e.getAlunoId().equals(alunoId)
                        && e.getMesReferencia().equals(mesReferencia)
                        && e.getEscolaId().equals(escolaId))
                .collect(Collectors.toList());
    }

    public boolean existsByAlunoIdAndMesReferenciaAndEscolaId(
            Long alunoId, String mesReferencia, Long escolaId) {
        return store.values().stream()
                .anyMatch(e -> e.getAlunoId().equals(alunoId)
                        && e.getMesReferencia().equals(mesReferencia)
                        && e.getEscolaId().equals(escolaId));
    }
}
