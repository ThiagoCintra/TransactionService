# 💰 Módulo Financeiro — Documentação Viva

> **Documentação viva:** este arquivo é mantido em sincronia com o código.
> Sempre que houver alteração nos endpoints, regras de negócio ou modelo de dados, este documento deve ser atualizado.

---

## Sumário

1. [Arquitetura do módulo](#1-arquitetura-do-módulo)
2. [Fluxo completo](#2-fluxo-completo)
3. [Endpoints](#3-endpoints)
4. [Modelo de dados](#4-modelo-de-dados)
5. [Regras de negócio](#5-regras-de-negócio)
6. [Segurança](#6-segurança)
7. [Integração entre serviços](#7-integração-entre-serviços)
8. [Exemplos práticos (curl)](#8-exemplos-práticos-curl)
9. [Evolução futura](#9-evolução-futura)

---

## 1. Arquitetura do módulo

### Pacote principal

```
com.transactionservice.financeiro
├── controller/   → AlunoFinanceiroController (REST)
├── service/      → AlunoFinanceiroService (interface) + AlunoFinanceiroServiceImpl
├── repository/   → AlunoFinanceiroRepository (in-memory; substituível por JPA)
├── entity/       → AlunoFinanceiroEntity (domínio)
├── dto/          → GerarMensalidadeRequest, PagamentoRequest, AlunoFinanceiroResponse
├── domain/       → StatusFinanceiro (enum)
└── security/     → AlunoFinanceiroAccessControl
```

### Reutilização de infraestrutura existente

| Componente              | Localização                                        | Uso no módulo financeiro                           |
|-------------------------|----------------------------------------------------|----------------------------------------------------|
| `JwtTokenProvider`      | `infrastructure.security`                          | Extrai `escola_id`, `alunos_ids` e `roles` do JWT  |
| `JwtAuthenticationFilter` | `infrastructure.security`                        | Autenticação stateless para todos os endpoints     |
| `JwtDetails`            | `infrastructure.security`                          | Carrega `rawToken` disponível no contexto de segurança |
| `GlobalExceptionHandler` | `controller`                                      | Trata `BusinessException`, `AccessDeniedException` |
| `UnauthorizedException` | `exception`                                        | Lançada quando não há autenticação válida          |
| `BusinessException`     | `exception`                                        | Lançada para violações de regra de negócio         |

### Multi-tenancy

O isolamento por escola é imposto pelo claim `escola_id` presente no JWT.
**O cliente nunca fornece o `escolaId`** — ele é sempre extraído do token no servidor.

```
JWT claim: escola_id → Long escolaId
```

Todas as consultas e gravações filtram por `escolaId`, garantindo que uma escola não acesse dados de outra.

---

## 2. Fluxo completo

### Geração de cobrança mensal

```
Cliente (ADMIN)
  │
  ▼
POST /api/v1/financeiro/gerar-mensal
  │
  ├─ JwtAuthenticationFilter: valida JWT, popula SecurityContext
  ├─ AlunoFinanceiroController.gerarMensalidade()
  ├─ AlunoFinanceiroServiceImpl.gerarMensalidade()
  │    ├─ getAuthentication() → verifica autenticação
  │    ├─ extrairEscolaId() → lê claim escola_id do JWT
  │    ├─ accessControl.validarAcessoAdmin() → confirma role ADMIN
  │    ├─ repository.existsByAlunoIdAndMesReferenciaAndEscolaId() → evita duplicata
  │    ├─ calcula total = mensalidade + alimentacao + multa + juros  ← OBRIGATÓRIO NO BACKEND
  │    ├─ cria AlunoFinanceiroEntity com status PENDENTE
  │    └─ repository.save()
  └─ retorna 201 Created com AlunoFinanceiroResponse
```

### Consulta de cobranças

```
Cliente (ADMIN ou RESPONSAVEL)
  │
  ▼
GET /api/v1/financeiro/{alunoId}?mes=YYYY-MM
  │
  ├─ JwtAuthenticationFilter: valida JWT
  ├─ AlunoFinanceiroController.consultarPorAlunoPorPath()
  ├─ AlunoFinanceiroServiceImpl.consultarPorAluno()
  │    ├─ extrairEscolaId() → multi-tenancy
  │    ├─ accessControl.validarAcessoAluno() → ADMIN: passa; RESPONSAVEL: verifica alunos_ids
  │    └─ repository.findByAlunoIdAndEscolaId() ou findByAlunoIdAndMesReferenciaAndEscolaId()
  └─ retorna 200 OK com lista de AlunoFinanceiroResponse
```

### Registro de pagamento (uso interno)

```
Sistema interno (ADMIN)
  │
  ▼
PATCH /api/v1/financeiro/{id}/interno/pagamento
  │
  ├─ accessControl.validarAcessoAdmin()
  ├─ repository.findById()
  ├─ entity.setStatus(novoStatus)
  ├─ entity.setDataAtualizacao(now)
  └─ retorna 200 OK com AlunoFinanceiroResponse atualizado
```

---

## 3. Endpoints

### 3.1 GET /api/v1/financeiro/{alunoId}

Consulta cobranças de um aluno via path variable.

| Campo            | Valor                                                         |
|------------------|---------------------------------------------------------------|
| **Método HTTP**  | GET                                                           |
| **URL**          | `/api/v1/financeiro/{alunoId}?mes=YYYY-MM`                   |
| **Roles**        | ADMIN, RESPONSAVEL (vinculado ao aluno via `alunos_ids` JWT) |
| **Autenticação** | Bearer JWT obrigatório                                        |
| **Query Params** | `mes` (opcional) — formato `YYYY-MM`                         |

**Response 200:**
```json
[
  {
    "id": 1,
    "alunoId": 42,
    "escolaId": 10,
    "mesReferencia": "2025-05",
    "mensalidade": 500.00,
    "alimentacao": 100.00,
    "multa": 0.00,
    "juros": 0.00,
    "total": 600.00,
    "status": "PENDENTE",
    "dataGeracao": "2025-05-05T10:00:00",
    "dataAtualizacao": "2025-05-05T10:00:00"
  }
]
```

---

### 3.2 GET /api/v1/financeiro

Consulta cobranças via query params.

| Campo            | Valor                                                         |
|------------------|---------------------------------------------------------------|
| **Método HTTP**  | GET                                                           |
| **URL**          | `/api/v1/financeiro?alunoId=42&mes=2025-05`                  |
| **Roles**        | ADMIN, RESPONSAVEL                                            |
| **Autenticação** | Bearer JWT obrigatório                                        |

---

### 3.3 POST /api/v1/financeiro/gerar-mensal

Gera cobrança mensal para um aluno.

| Campo            | Valor                  |
|------------------|------------------------|
| **Método HTTP**  | POST                   |
| **URL**          | `/api/v1/financeiro/gerar-mensal` |
| **Roles**        | ADMIN apenas           |
| **Autenticação** | Bearer JWT obrigatório |

**Request body:**
```json
{
  "alunoId": 42,
  "mesReferencia": "2025-05",
  "mensalidade": 500.00,
  "alimentacao": 100.00,
  "multa": 10.00,
  "juros": 5.00
}
```

> ⚠️ `total` e `escolaId` **não são aceitos** no request — ambos são determinados exclusivamente pelo servidor.

**Response 201:**
```json
{
  "id": 1,
  "alunoId": 42,
  "escolaId": 10,
  "mesReferencia": "2025-05",
  "mensalidade": 500.00,
  "alimentacao": 100.00,
  "multa": 10.00,
  "juros": 5.00,
  "total": 615.00,
  "status": "PENDENTE",
  "dataGeracao": "2025-05-05T10:00:00",
  "dataAtualizacao": "2025-05-05T10:00:00"
}
```

---

### 3.4 PATCH /api/v1/financeiro/{id}/interno/pagamento

Atualiza o status de pagamento de uma cobrança. Uso **interno** — restrito a ADMIN.

| Campo            | Valor                                     |
|------------------|-------------------------------------------|
| **Método HTTP**  | PATCH                                     |
| **URL**          | `/api/v1/financeiro/{id}/interno/pagamento` |
| **Roles**        | ADMIN apenas                              |
| **Autenticação** | Bearer JWT obrigatório                    |

**Request body:**
```json
{
  "status": "PAGO"
}
```

Valores válidos para `status`: `PENDENTE`, `PAGO`, `ATRASADO`.

**Response 200:** igual ao formato de `AlunoFinanceiroResponse` com o status atualizado.

---

## 4. Modelo de dados

### AlunoFinanceiroEntity

| Campo             | Tipo            | Descrição                                              |
|-------------------|-----------------|--------------------------------------------------------|
| `id`              | `Long`          | Identificador único gerado pelo servidor               |
| `alunoId`         | `Long`          | Referência ao aluno (external ID — sem acesso direto ao banco de alunos) |
| `escolaId`        | `Long`          | ID da escola — impõe multi-tenancy; extraído do JWT    |
| `mesReferencia`   | `String`        | Mês no formato `YYYY-MM`                               |
| `mensalidade`     | `BigDecimal`    | Valor da mensalidade escolar                           |
| `alimentacao`     | `BigDecimal`    | Valor de alimentação                                   |
| `multa`           | `BigDecimal`    | Multa por atraso (default 0)                           |
| `juros`           | `BigDecimal`    | Juros por atraso (default 0)                           |
| `total`           | `BigDecimal`    | **Calculado no backend:** `mensalidade + alimentacao + multa + juros` |
| `status`          | `StatusFinanceiro` | `PENDENTE`, `PAGO` ou `ATRASADO`                   |
| `dataGeracao`     | `LocalDateTime` | Timestamp de criação                                   |
| `dataAtualizacao` | `LocalDateTime` | Timestamp da última atualização                        |

### StatusFinanceiro (enum)

| Valor      | Significado                          |
|------------|--------------------------------------|
| `PENDENTE` | Cobrança gerada, aguardando pagamento |
| `PAGO`     | Pagamento confirmado                 |
| `ATRASADO` | Prazo vencido sem pagamento          |

---

## 5. Regras de negócio

### Cálculo financeiro

```java
BigDecimal total = mensalidade + alimentacao + multa + juros;
```

- O campo `total` **nunca é aceito no request body** — calculado exclusivamente em `AlunoFinanceiroServiceImpl.gerarMensalidade()`.
- `multa` e `juros` são opcionais no request; assumem `BigDecimal.ZERO` se não informados.
- Valores negativos são rejeitados pela validação Jakarta (`@DecimalMin("0.00")`).

### Controle mensal

- Não é permitido gerar mais de uma cobrança para o mesmo aluno no mesmo mês e escola.
- Verificação via `repository.existsByAlunoIdAndMesReferenciaAndEscolaId()`.
- Tentativa duplicada lança `BusinessException`.

### Validação de acesso

Toda operação passa por `AlunoFinanceiroAccessControl` antes de qualquer lógica de negócio.

---

## 6. Segurança

### Roles e permissões

| Role          | Consultar | Gerar mensalidade | Registrar pagamento |
|---------------|-----------|-------------------|---------------------|
| `ADMIN`       | ✅ total   | ✅                | ✅                  |
| `RESPONSAVEL` | ✅ alunos vinculados | ❌          | ❌                  |

### Validação por vínculo (RESPONSAVEL)

O claim `alunos_ids` no JWT contém a lista de IDs de alunos vinculados ao responsável.

```
JWT claim: alunos_ids → List<Long>
```

`AlunoFinanceiroAccessControl.validarAcessoAluno()`:
1. Extrai `roles` do token → se `ADMIN`, concede acesso imediatamente.
2. Caso contrário, extrai `alunos_ids` do token e verifica se `alunoId` está na lista.
3. Se não estiver, lança `AccessDeniedException` → `GlobalExceptionHandler` retorna HTTP 403.

### Multi-tenancy

O claim `escola_id` é extraído via `JwtTokenProvider.getEscolaId()` e aplicado em **todas** as queries, garantindo isolamento total entre escolas.

**O cliente nunca pode informar ou manipular o `escolaId`.**

### Fluxo de autenticação

```
Request → JwtAuthenticationFilter
            ├─ Extrai Bearer token
            ├─ jwtTokenProvider.validateToken()
            ├─ Popula SecurityContext com username + authorities
            └─ JwtDetails(channel, rawToken) → disponível para extrair escola_id, alunos_ids
```

---

## 7. Integração entre serviços

### alunos-service

O módulo financeiro **não acessa diretamente** o banco de dados de alunos.  
A referência ao aluno é mantida apenas pelo `alunoId` (Long), seguindo o princípio de desacoplamento de domínios.

Para validação de vínculo RESPONSAVEL → aluno, os dados são obtidos via o claim `alunos_ids` presente no JWT, que deve ser populado pelo serviço de autenticação/alunos no momento do login.

### auth (/me — LoginService)

O `LoginClient` existente chama `GET /api/v1/auth/me` com o Bearer token para validar sessão ativa.  
O módulo financeiro utiliza o mesmo token JWT já validado pelo `JwtAuthenticationFilter` — sem chamada adicional ao LoginService para cada request financeiro.

### futuro pagamentos-service

O código está preparado para integração futura via eventos (SQS/Kafka). O `AlunoFinanceiroEntity` já possui todos os campos necessários para construir um `PagamentoEvent`.

Ponto de extensão sugerido em `AlunoFinanceiroServiceImpl.registrarPagamento()`:
```java
// TODO: publicar evento de pagamento para pagamentos-service
// sqsProducer.publish(new PagamentoConfirmadoEvent(entity));
```

---

## 8. Exemplos práticos (curl)

### Gerar mensalidade (ADMIN)

```bash
curl -X POST http://localhost:8080/api/v1/financeiro/gerar-mensal \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "alunoId": 42,
    "mesReferencia": "2025-05",
    "mensalidade": 500.00,
    "alimentacao": 100.00,
    "multa": 0.00,
    "juros": 0.00
  }'
```

**Response 201:**
```json
{
  "id": 1,
  "alunoId": 42,
  "escolaId": 10,
  "mesReferencia": "2025-05",
  "mensalidade": 500.00,
  "alimentacao": 100.00,
  "multa": 0.00,
  "juros": 0.00,
  "total": 600.00,
  "status": "PENDENTE",
  "dataGeracao": "2025-05-05T10:00:00",
  "dataAtualizacao": "2025-05-05T10:00:00"
}
```

### Consultar cobranças por aluno e mês (ADMIN ou RESPONSAVEL vinculado)

```bash
curl http://localhost:8080/api/v1/financeiro/42?mes=2025-05 \
  -H "Authorization: Bearer $TOKEN"
```

### Consultar via query params

```bash
curl "http://localhost:8080/api/v1/financeiro?alunoId=42&mes=2025-05" \
  -H "Authorization: Bearer $TOKEN"
```

### Registrar pagamento (ADMIN, uso interno)

```bash
curl -X PATCH http://localhost:8080/api/v1/financeiro/1/interno/pagamento \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"status": "PAGO"}'
```

### Erro: tentativa de geração duplicada

```bash
# Segunda chamada com mesmo alunoId + mesReferencia retorna:
HTTP 422 Unprocessable Entity
{
  "status": 422,
  "error": "Business Rule Violation",
  "message": "Já existe cobrança para o aluno 42 no mês 2025-05"
}
```

### Erro: acesso negado (RESPONSAVEL tentando acessar aluno não vinculado)

```bash
HTTP 403 Forbidden
{
  "status": 403,
  "error": "Forbidden",
  "message": "You do not have permission to perform this action"
}
```

---

## 9. Evolução futura

### Pagamentos (PIX / Boleto)

- Criar `pagamentos-service` separado
- `AlunoFinanceiroEntity` publica `PagamentoEvent` via SQS (infraestrutura já disponível no projeto)
- `pagamentos-service` consome o evento e processa o pagamento junto ao provedor escolhido

### Eventos assíncronos (Kafka / SQS)

O projeto já possui integração com **AWS SQS** via `SqsProducer`. Para o módulo financeiro:
- Evento `MensalidadeGeradaEvent` → notifica alunos-service / responsável
- Evento `PagamentoConfirmadoEvent` → atualiza status e notifica responsável

### Automação mensal

- Criar `FinanceiroScheduler` com `@Scheduled` para gerar cobranças automaticamente no início de cada mês
- Consultar lista de alunos ativos via API do alunos-service

### Persistência em banco de dados

O `AlunoFinanceiroRepository` atual usa armazenamento em memória (`ConcurrentHashMap`).  
Para produção, substituir por implementação JPA sem alterar a interface de serviço:

```java
// Substituição transparente para produção:
@Repository
public interface AlunoFinanceiroJpaRepository extends JpaRepository<AlunoFinanceiroEntity, Long> {
    List<AlunoFinanceiroEntity> findByAlunoIdAndEscolaId(Long alunoId, Long escolaId);
    // ...
}
```

### Cobrança multi-provider

Preparar `ProviderStrategy` com implementações para:
- PIX (Banco Central)
- Boleto bancário
- Cartão de crédito recorrente
