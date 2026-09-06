# Plano de Implementação — Correção de Bugs no Payload do Alfa

**Data:** 2026-09-06
**Baseado em:** `docs/analise_bug_payload_alfa.md`

---

## O que foi pedido

Ler o arquivo `analise_bug_payload_alfa.md` e gerar este plano de implementação com as decisões exatas de correção dos dois bugs identificados, para validação prévia antes de qualquer alteração ser aplicada.

---

## Entendimento dos Problemas

### Bug 1 — Itens do pedido não são persistidos

**Localização:** `PedidoService.java`, método `criarPedido` (linha 82).

**Causa raiz:** O método `criarPedido` chama `incoming.getItens().clear()` diretamente sobre a lista do objeto `incoming` antes de salvar o pedido. O comentário inline justifica a limpeza como "Itens são gerenciados separadamente em `resolverItens`" — a intenção é correta, mas a execução é destrutiva: ao limpar a lista no próprio objeto `incoming`, a referência que `upsert` possui também é afetada. Quando `upsert` chama `resolverItens(incoming.getItens(), pedido)` na linha seguinte, itera sobre uma lista **já vazia**. Nenhum item é salvo.

**O problema não ocorre no update** (`atualizarPedido`), pois este método não toca na lista de itens do `incoming`. O bug é exclusivo do caminho de criação (primeira ingestão de um pedido).

---

### Bug 2 — Caractere `\n` persiste no nome do fornecedor

**Localização:** `AlfaIngestor.java`, método `mapFornecedor`.

**Causa raiz:** O nome do fornecedor é atribuído diretamente sem sanitização: `fornecedor.setNome(dto.fornecedor().nome())`. O payload de teste contém `"name": "Metalúrgica São Jorge\nS.A."`, e o `\n` é persistido literalmente no banco.

**Impacto:** Nomes com `\n`, `\r` ou `\t` quebram geração de CSV, buscas textuais e integrações downstream. A análise conclui que **faz muito sentido tratar no sistema**.

---

## Decisão Arquitetural: Onde fica a sanitização de strings?

O `DESING.md` (seção 1.2) define que **normalizadores** são responsáveis por CNPJ, status, data e conversão de unidade. A sanitização de strings textuais (nomes, descrições) é do mesmo espírito: transformar dados brutos do cliente em um formato limpo e consistente para o contrato interno V360.

**Decisão:** Criar um `StringSanitizer` na camada `normalizer/`, seguindo o mesmo padrão de `CnpjSanitizer` — utilitário puro, estático, sem estado, sem Spring. O `AlfaIngestor` o utilizará ao mapear `nome` do fornecedor e `descricao` dos itens.

---

## Decisões de Implementação Exatas

### ETAPA 1 — Corrigir `PedidoService.java`

**Caminho:** `src/main/java/com/v360/prosel/conectorpedidoscompra/service/PedidoService.java`

**Problema exato:** Linha 82 — `incoming.getItens().clear()` esvazia a lista que será usada por `resolverItens` logo após.

**Solução:** Salvar a lista de itens em uma variável local em `upsert` **antes** de chamar `resolverPedido`. Assim, `resolverPedido` pode limpar ou fazer o que quiser no objeto `incoming` sem afetar a lista que será processada.

**Diff exato no método `upsert`:**

```java
// De:
@Transactional
public Pedido upsert(Pedido incoming) {
    Fornecedor fornecedor = resolverFornecedor(incoming.getFornecedor());
    Pedido pedido = resolverPedido(incoming, fornecedor);
    resolverItens(incoming.getItens(), pedido);
    return pedidoRepository.findById(pedido.getId()).orElse(pedido);
}

// Para:
@Transactional
public Pedido upsert(Pedido incoming) {
    List<Item> itens = new ArrayList<>(incoming.getItens()); // cópia defensiva antes de qualquer mutação
    Fornecedor fornecedor = resolverFornecedor(incoming.getFornecedor());
    Pedido pedido = resolverPedido(incoming, fornecedor);
    resolverItens(itens, pedido);
    return pedidoRepository.findById(pedido.getId()).orElse(pedido);
}
```

**Por que cópia defensiva e não remover o `.clear()` de `criarPedido`:**
O `.clear()` em `criarPedido` existe para garantir que o JPA não tente persistir os itens em cascata junto com o pedido naquele momento — os itens são gerenciados depois, individualmente via `resolverItens`, que atribui o `pedido` já persistido (com ID) a cada item. Remover o `.clear()` poderia causar problemas de cascata (salvar itens sem o `id_pedido` correto ou duplicatas). A abordagem correta é preservar a lista de itens externamente ao objeto antes que `criarPedido` o altere.

**Método `criarPedido` permanece inalterado:**
```java
private Pedido criarPedido(Pedido incoming, Fornecedor fornecedor) {
    incoming.setFornecedor(fornecedor);
    incoming.setDataIngestao(Instant.now());
    incoming.getItens().clear(); // continua necessário — impede cascata prematura
    return pedidoRepository.save(incoming);
}
```

---

### ETAPA 2 — Criar `StringSanitizer.java` (normalizador — arquivo novo)

**Caminho:** `src/main/java/com/v360/prosel/conectorpedidoscompra/normalizer/StringSanitizer.java`

**Responsabilidade:** Remover caracteres de controle invisíveis (`\n`, `\r`, `\t`) de campos textuais de linha única, substituindo-os por espaço, seguido de `trim()` para eliminar espaços extras nas bordas.

**Conteúdo exato a ser criado:**

```java
package com.v360.prosel.conectorpedidoscompra.normalizer;

/**
 * Utilitário puro (sem estado, sem Spring) para sanitização de strings textuais.
 *
 * Centraliza a remoção de caracteres de controle invisíveis (\n, \r, \t) de
 * campos cadastrais de linha única, como nomes de fornecedores e descrições.
 * Evita quebras em relatórios CSV, inconsistências em buscas e falhas em
 * integrações downstream.
 */
public class StringSanitizer {

    private StringSanitizer() {
        // utilitário estático — instanciação desnecessária
    }

    /**
     * Remove quebras de linha (\n, \r) e tabulações (\t) de uma string,
     * substituindo-as por espaço simples, e aplica trim() no resultado.
     *
     * Ex.: "Metalúrgica São Jorge\nS.A." → "Metalúrgica São Jorge S.A."
     *
     * @param valor string bruta recebida do cliente
     * @return string sanitizada, ou null se a entrada for null
     */
    public static String sanitize(String valor) {
        if (valor == null) return null;
        return valor.replaceAll("[\\n\\r\\t]", " ").trim();
    }
}
```

---

### ETAPA 3 — Alterar `AlfaIngestor.java`

**Caminho:** `src/main/java/com/v360/prosel/conectorpedidoscompra/ingestor/AlfaIngestor.java`

**Alterações exatas:**

1. **Adicionar import** do `StringSanitizer` (junto aos imports de normalizers existentes):
```java
import com.v360.prosel.conectorpedidoscompra.normalizer.StringSanitizer;
```

2. **Alterar `mapFornecedor`** — sanitizar o nome:
```java
// De:
fornecedor.setNome(dto.fornecedor().nome());

// Para:
fornecedor.setNome(StringSanitizer.sanitize(dto.fornecedor().nome()));
```

3. **Alterar `mapItem`** — sanitizar a descrição (mesma classe de problema: campo textual livre vindo do cliente):
```java
// De:
item.setDescricao(dto.descricao());

// Para:
item.setDescricao(StringSanitizer.sanitize(dto.descricao()));
```

**Métodos resultantes completos:**

```java
private Fornecedor mapFornecedor(AlfaPedidoDTO dto) {
    Fornecedor fornecedor = new Fornecedor();
    fornecedor.setCnpj(CnpjSanitizer.sanitize(dto.fornecedor().cnpj()));
    fornecedor.setNome(StringSanitizer.sanitize(dto.fornecedor().nome()));
    return fornecedor;
}

private Item mapItem(AlfaItemDTO dto, Pedido pedido) {
    Item item = new Item();
    item.setPedido(pedido);
    item.setLinha(dto.linha());
    item.setCodigoMaterial(dto.codigoMaterial());
    item.setDescricao(StringSanitizer.sanitize(dto.descricao()));
    item.setUnidadeMedida(dto.unidadeMedida());
    item.setQuantidadePedida(dto.quantidadePedida());
    item.setQuantidadeRecebida(dto.quantidadeRecebida());
    item.setPrecoUnitario(dto.precoUnitario());
    // quantidadePendente é GENERATED ALWAYS no Postgres — não é setada aqui
    return item;
}
```

> **Nota:** A sanitização de `descricao` vai além do que o bug report especifica, mas é preventiva e coerente com a mesma lógica — campos de texto livre do cliente devem passar pelo normalizador antes de persistir.

---

## Resumo das Alterações por Arquivo

| Arquivo | Ação | Motivo |
|---|---|---|
| `service/PedidoService.java` | **Alterar** | Cópia defensiva da lista de itens em `upsert` antes de `resolverPedido` |
| `normalizer/StringSanitizer.java` | **Criar** | Normalizador para remover `\n`, `\r`, `\t` de campos textuais |
| `ingestor/AlfaIngestor.java` | **Alterar** | Aplicar `StringSanitizer` em `nome` do fornecedor e `descricao` dos itens |

**Nenhuma alteração em DTOs, entidades, repositórios ou banco de dados é necessária.**
