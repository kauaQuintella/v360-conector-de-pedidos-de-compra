# PLAN — Teste Unitário do AlfaIngestor

_Gerado em: 05/09/2026 — Dia 1 do Kanban (Setup + Ingestão Alfa)_  
_Contexto: Único item pendente do Dia 1 após revisão completa do projeto._

---

## 1. Contexto e Rastreabilidade

### 1.1 Por que este documento existe

Este documento registra o raciocínio, as decisões e o plano de implementação do **último item faltante do Dia 1** conforme definido em `docs/tasks/DIA1.md`:

```
- [ ] Teste unitário do parser Alfa
```

Todos os demais itens da checklist do Dia 1 estavam implementados e validados antes desta sessão (ver seção 2). Este documento serve como **trilha de auditoria** para futuras referências sobre:

- o que existia antes
- o que foi acrescentado
- por que cada decisão foi tomada

### 1.2 Rastreabilidade com o DESING.md

| Decisão de design | Seção no DESING.md | Impacto neste teste |
|---|---|---|
| `AlfaIngestor` não acessa banco — só traduz DTO | Seção 1.2 (Camadas) | Teste 100% unitário, zero infraestrutura |
| `CnpjSanitizer` utilitário puro sem Spring | Seção 1.2 / 2 | Instanciado diretamente, sem mock |
| `AlfaStatusMapper` `@Component` sem dependências | Seção 1.3 / 6 | Instanciado com `new`, sem Spring |
| `clienteOrigem` fixo `"ALFA"` | Seção 3.5 | Caso de teste obrigatório |
| `quantidade_pendente` nunca setada pelo Java | Seção 3.4 | Asserção negativa no teste de item |
| Status Alfa: `"OPEN"/"CLOSED"/"BLOCKED"` (inglês) | Seção 6 | Happy paths e caminho de erro |
| `NUMERIC(15,4)` — BigDecimal preservado | Seção 3.3 | Asserção de precisão nos campos monetários |
| H2 serve para unitários que mockam repository | Seção 4.3 | Justifica não usar `@SpringBootTest` |

### 1.3 Histórico de implementações anteriores

O projeto passou pelas seguintes fases documentadas em `docs/development/parte-um/`:

- **`entities-repositories-normalizers-dtoalfa/`** — Primeira sessão: análise do `DESING.md`, plano para Steps 2–6 mais o complemento dos Steps 7–10. Entidades, repositories, normalizers, DTOs, `PedidoIngestor`, `AlfaIngestor`, `PedidoService`, `AlfaController` e `schema.sql` foram todos implementados nessa sessão.
- **`entities-repositories-normalizers-dtoalfa/REVISION...`** — Revisão gerada por Gemini Pro e validada manualmente, confirmando aderência ao `DESING.md`.
- **Esta pasta (`teste-unitario-alfaingestor/`)** — Sessão de fechamento do Dia 1: único item faltante.

---

## 2. Estado do Projeto Antes desta Implementação

### 2.1 Checklist do DIA1.md — situação atual

| Item | Status | Arquivo(s) |
|---|---|---|
| `docker-compose.yml` com Postgres + aplicação | ✅ Feito | `compose.yaml` |
| `application.properties` apontando pro Postgres | ✅ Feito | `src/main/resources/application.properties` |
| Entidades JPA: `Fornecedor`, `Pedido`, `Item` + enum `StatusPedido` | ✅ Feito | `entity/`, `enums/` |
| Repositories (`Fornecedor`, `Pedido`, `Item`) | ✅ Feito | `repository/` |
| `CnpjSanitizer` | ✅ Feito | `normalizer/CnpjSanitizer.java` |
| `StatusMapper` (interface + impl Alfa) | ✅ Feito | `normalizer/StatusMapper.java`, `normalizer/AlfaStatusMapper.java` |
| DTOs de entrada do Alfa | ✅ Feito | `dto/alfa/` (3 records) |
| Interface `PedidoIngestor` (Strategy) + `AlfaIngestor` | ✅ Feito | `ingestor/strategy/PedidoIngestor.java`, `ingestor/AlfaIngestor.java` |
| Endpoint `POST /ingest/alfa` com upsert | ✅ Feito | `controller/AlfaController.java`, `service/PedidoService.java` |
| **Teste unitário do parser Alfa** | ❌ **Pendente** | — |

### 2.2 Arquivo de teste existente

O único arquivo de teste presente é:

```
src/test/java/com/v360/prosel/conectorpedidoscompra/ConectorpedidoscompraApplicationTests.java
```

Esse arquivo contém apenas um `@SpringBootTest` de smoke test (`contextLoads()`). **Não é o teste unitário do parser pedido** no `DIA1.md`.

---

## 3. O Que Será Implementado

### 3.1 Arquivo a criar

```
src/test/java/com/v360/prosel/conectorpedidoscompra/ingestor/AlfaIngestorTest.java
```

### 3.2 Tipo de teste e justificativa

**Teste unitário puro** — sem `@SpringBootTest`, sem H2, sem banco, sem Mockito.

**Por quê:**
- `AlfaIngestor` é um componente sem estado que recebe um DTO e retorna uma entidade — pura lógica de transformação.
- Sua única dependência é `AlfaStatusMapper`, que por sua vez não tem dependência alguma.
- O `DESING.md` seção 4.3 diz explicitamente: _"H2 serve para testes unitários que mockam o repository (não testam upsert real nem colunas computadas)"_. Esse teste não testa nem upsert nem colunas computadas — é mais simples ainda, não precisa nem de H2.
- Testes unitários puros têm tempo de execução da ordem de milissegundos e não exigem infraestrutura.

**Setup do teste:**

```java
private AlfaIngestor alfaIngestor;

@BeforeEach
void setUp() {
    alfaIngestor = new AlfaIngestor(new AlfaStatusMapper());
}
```

`AlfaStatusMapper` é instanciado diretamente com `new` — é um `@Component` sem dependências externas. Isso é preferível a Mockito porque testar o comportamento real do mapper junto com o ingestor é mais valioso do que mockar uma classe que não tem efeitos colaterais.

---

## 4. Casos de Teste Detalhados

### 4.1 Happy Path — Pedido OPEN com CNPJ limpo

**Objetivo:** Verificar que todos os campos do `Pedido` resultante mapeiam corretamente para um payload Alfa típico.

**Dado:** `AlfaPedidoDTO` com:
- `numeroPedido`: `"AL-001"`
- `dataCriacao`: `Instant.parse("2026-08-15T00:00:00Z")`
- `status`: `"OPEN"`
- `moeda`: `"BRL"`
- `fornecedor`: CNPJ `"12345678000190"`, nome `"Fornecedor Teste"`
- `itens`: 1 item com todos os campos preenchidos

**Verificações:**
- `pedido.getNumeroPedidoOrigem()` == `"AL-001"`
- `pedido.getClienteOrigem()` == `"ALFA"` ← constante fixa do ingestor
- `pedido.getDataCriacao()` == `Instant.parse("2026-08-15T00:00:00Z")`
- `pedido.getStatus()` == `StatusPedido.OPEN`
- `pedido.getMoeda()` == `"BRL"`
- `pedido.getFornecedor().getCnpj()` == `"12345678000190"`
- `pedido.getFornecedor().getNome()` == `"Fornecedor Teste"`
- `pedido.getItens()` tem tamanho 1
- `pedido.getDataIngestao()` == `null` ← é responsabilidade do `PedidoService`, não do ingestor

### 4.2 CNPJ com Máscara é Sanitizado

**Objetivo:** Verificar que o `CnpjSanitizer` é aplicado corretamente pelo ingestor.

**Dado:** CNPJ `"12.345.678/0001-90"` no DTO.

**Verificação:**
- `pedido.getFornecedor().getCnpj()` == `"12345678000190"` — máscara removida

**Relevância para auditoria:** Caso o `CnpjSanitizer.sanitize()` não seja chamado no `AlfaIngestor.mapFornecedor()`, esse teste quebra. É uma rede de segurança contra regressão.

### 4.3 Status CLOSED

**Objetivo:** Verificar que `"CLOSED"` é traduzido para `StatusPedido.CLOSED`.

**Verificação:**
- `pedido.getStatus()` == `StatusPedido.CLOSED`

### 4.4 Status BLOCKED

**Objetivo:** Verificar que `"BLOCKED"` é traduzido para `StatusPedido.BLOCKED`.

**Verificação:**
- `pedido.getStatus()` == `StatusPedido.BLOCKED`

### 4.5 Status em Lowercase é Aceito

**Objetivo:** Verificar que `"open"` (lowercase) funciona, pois o `AlfaStatusMapper` faz `toUpperCase().trim()`.

**Dado:** `status`: `"open"`

**Verificação:**
- `pedido.getStatus()` == `StatusPedido.OPEN`
- Não lança exceção

**Relevância para auditoria:** O Alfa pode enviar em qualquer case — a robustez do `toUpperCase()` deve ser testada.

### 4.6 Status Inválido Lança Exceção

**Objetivo:** Verificar que um status desconhecido lança `IllegalArgumentException` com mensagem identificável.

**Dado:** `status`: `"PENDENTE"` (não mapeado).

**Verificações:**
- `assertThrows(IllegalArgumentException.class, () -> alfaIngestor.toEntity(dto))`
- A mensagem da exceção contém `"PENDENTE"` (para facilitar diagnóstico em produção)

### 4.7 Mapeamento Completo de Item

**Objetivo:** Verificar que todos os campos de `AlfaItemDTO` são transferidos corretamente para `Item`.

**Dado:** Item com:
- `linha`: `"1"`
- `codigoMaterial`: `"MAT-001"`
- `descricao`: `"Produto de teste"`
- `unidadeMedida`: `"UN"`
- `quantidadePedida`: `BigDecimal("10.0000")`
- `quantidadeRecebida`: `BigDecimal("3.0000")`
- `precoUnitario`: `BigDecimal("99.9900")`

**Verificações por campo:**
- `item.getLinha()` == `"1"`
- `item.getCodigoMaterial()` == `"MAT-001"`
- `item.getDescricao()` == `"Produto de teste"`
- `item.getUnidadeMedida()` == `"UN"`
- `item.getQuantidadePedida()` == `new BigDecimal("10.0000")`
- `item.getQuantidadeRecebida()` == `new BigDecimal("3.0000")`
- `item.getPrecoUnitario()` == `new BigDecimal("99.9900")`
- **`item.getQuantidadePendente()` == `null`** ← nunca setada pelo Java; Postgres calcula

### 4.8 Múltiplos Itens São Todos Mapeados

**Objetivo:** Verificar que uma lista com N itens no DTO resulta em N itens na entidade.

**Dado:** DTO com 3 itens (linhas `"1"`, `"2"`, `"3"`).

**Verificações:**
- `pedido.getItens().size()` == `3`
- Linhas presentes: `"1"`, `"2"`, `"3"` (na ordem original)

### 4.9 Referência Bidirecional — Cada Item Aponta para o Pedido

**Objetivo:** Verificar que `item.getPedido()` não é `null` e aponta para o pedido correto.

**Verificação:**
- `item.getPedido()` é o mesmo objeto `pedido` retornado pelo ingestor

**Relevância para auditoria:** O JPA precisa dessa referência para popular a FK `id_pedido` na tabela `Item`. Se o `AlfaIngestor.mapItem()` esquecer de chamar `item.setPedido(pedido)`, o `PedidoService` teria que compensar — mas o teste garante que o ingestor cumpre sua responsabilidade de camada.

---

## 5. Estrutura do Arquivo de Teste (Referência)

```java
package com.v360.prosel.conectorpedidoscompra.ingestor;

import com.v360.prosel.conectorpedidoscompra.dto.alfa.AlfaFornecedorDTO;
import com.v360.prosel.conectorpedidoscompra.dto.alfa.AlfaItemDTO;
import com.v360.prosel.conectorpedidoscompra.dto.alfa.AlfaPedidoDTO;
import com.v360.prosel.conectorpedidoscompra.entity.Item;
import com.v360.prosel.conectorpedidoscompra.entity.Pedido;
import com.v360.prosel.conectorpedidoscompra.enums.StatusPedido;
import com.v360.prosel.conectorpedidoscompra.normalizer.AlfaStatusMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AlfaIngestorTest {

    private AlfaIngestor alfaIngestor;

    @BeforeEach
    void setUp() {
        alfaIngestor = new AlfaIngestor(new AlfaStatusMapper());
    }

    // Helpers para montar DTOs de forma concisa nos testes
    // + 9 métodos @Test correspondentes às seções 4.1 a 4.9
}
```

**Biblioteca de asserção:** AssertJ (`assertThat`) — já disponível via `spring-boot-starter-test` no `pom.xml`. Preferível ao JUnit `assertEquals` por leitura mais fluente e mensagens de falha mais detalhadas.

---

## 6. Arquivos Afetados por esta Implementação

| Operação | Arquivo | Observação |
|---|---|---|
| **Criar** | `src/test/java/.../ingestor/AlfaIngestorTest.java` | Único arquivo novo |
| **Sem alteração** | Todo o código em `src/main/` | Nenhuma mudança na implementação de produção |
| **Sem alteração** | `pom.xml` | Dependências já presentes (`spring-boot-starter-test`) |

---

## 7. Critério de Conclusão do Dia 1

O Dia 1 será considerado **100% completo** quando:

1. `AlfaIngestorTest.java` existir com os 9 casos de teste descritos na seção 4
2. Todos os testes passarem com `./mvnw test -pl . -Dtest=AlfaIngestorTest`
3. O item `- [ ] Teste unitário do parser Alfa` no `DIA1.md` for marcado como `- [x]`

> **Nota sobre `git tag parte-1`:** O `DESING.md` seção 8 menciona essa tag, mas ela deve ser avaliada separadamente — a Parte 1 do enunciado pode incluir itens dos Dias 2 e 3 (ingestão Beta). Verificar o enunciado antes de criar a tag.
