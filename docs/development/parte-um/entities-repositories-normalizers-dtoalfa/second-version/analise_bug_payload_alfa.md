# Análise de Problemas no Payload do Alfa Energia

Este documento detalha a investigação de dois problemas observados ao processar o arquivo `alfaEnergiaPayload.json` via Postman, que resultou em código 200, mas com anomalias nos dados armazenados no banco.

## 1. Problema: Itens do Pedido não são armazenados

### Causa Raiz
A falha ao salvar os itens está localizada na classe `PedidoService.java`, na forma como o fluxo do método `upsert` gerencia a instância transitória do pedido.

O fluxo de execução é o seguinte no `upsert`:
1. Resolução do fornecedor.
2. `resolverPedido(incoming, fornecedor)` é chamado. Quando o pedido é novo, o método delega para `criarPedido(incoming, fornecedor)`.
3. Dentro do método `criarPedido`, o código realiza uma alteração destrutiva no objeto original `incoming`:
   ```java
   incoming.setItens(new ArrayList<>());
   ```
4. Em seguida, ao voltar ao método base `upsert`, o código tenta persistir os itens através de:
   ```java
   resolverItens(incoming.getItens(), pedido);
   ```

**Conclusão:** Como a propriedade `itens` do objeto `incoming` foi reescrita e substituída por uma lista vazia no passo 3, o método `resolverItens` itera sobre uma lista sem elementos. Como resultado, nenhum item do payload chega ao banco de dados.

### Sugestão de Correção
A solução envolve parar de alterar a lista no objeto `incoming` (DTO/Entidade transiente que carrega o estado do request). Outra alternativa é salvar a lista de itens em uma variável separada antes de evocar a cadeia de `resolverPedido`.

---

## 2. Problema: Caractere de nova linha (`\n`) no nome do Fornecedor

### Causa Raiz
No payload original de teste, o atributo do fornecedor está formatado em duas linhas:
```json
"name": "Metalúrgica São Jorge\nS.A."
```
Ao analisar a classe `AlfaIngestor.java`, o mapeamento do DTO para a entidade ocorre de maneira crua:
```java
private Fornecedor mapFornecedor(AlfaPedidoDTO dto) {
    Fornecedor fornecedor = new Fornecedor();
    fornecedor.setCnpj(CnpjSanitizer.sanitize(dto.fornecedor().cnpj()));
    fornecedor.setNome(dto.fornecedor().nome()); // Nenhuma sanitização é feita aqui
    return fornecedor;
}
```

### Faz sentido tratar isso no sistema?
**Sim, faz muito sentido e é altamente recomendável.**

Nomes de empresas/fornecedores são categorizados como dados cadastrais de linha única. Permitir a persistência de quebras de linha (`\n`), retornos de carro (`\r`) ou tabulações (`\t`) traz diversos riscos:
1. **Quebra de Relatórios e Exportações:** Ao gerar arquivos CSV, as quebras de linha não escapadas podem desalinhar as colunas e corromper o relatório.
2. **Inconsistências em Buscas (Queries):** A string `"Metalúrgica São Jorge\nS.A."` é diferente de `"Metalúrgica São Jorge S.A."`. Isso impossibilita consultas exatas e gera confusão na validação de unicidade ou em sistemas de busca textuais.
3. **Falhas em Front-ends ou Integrações:** Alguns sistemas que consumirão esta API no futuro podem não prever que um nome tenha múltiplas linhas, quebrando renderizações ou falhando em validações de contratos rigorosos.

### Sugestão de Correção
Assim como foi feito para remover a máscara do CNPJ (`CnpjSanitizer`), seria prudente criar um utilitário (ex: `StringSanitizer`) para remover quebras de linha (substituindo por espaços) e realizar um `trim()` em propriedades textuais como nomes e descrições, ou aplicar isso diretamente no pipeline do `AlfaIngestor`.
