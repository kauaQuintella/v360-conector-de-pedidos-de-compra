# Explicação Detalhada das Implementações: AlfaIngestorTest.java

Este documento detalha as decisões de implementação, arquitetura e boas práticas aplicadas no desenvolvimento do teste unitário [AlfaIngestorTest.java](../../../../../src/test/java/com/v360/prosel/conectorpedidoscompra/ingestor/AlfaIngestorTest.java), concluindo as atividades do **Dia 1** e garantindo que todo o parser "Alfa" obedeça os requisitos.

---

## 1. Abordagem de Teste: "Teste Unitário Puro"

### O que foi feito?
O teste foi construído **sem** o uso das anotações do framework Spring Boot (como `@SpringBootTest`) e **sem** bibliotecas de simulação pesadas (como o `Mockito`). A injeção de dependências foi feita manualmente no método de setup:

```java
@BeforeEach
void setUp() {
    alfaIngestor = new AlfaIngestor(new AlfaStatusMapper());
}
```

### Por que foi escolhido?
1. **Performance:** Testes sem contexto do Spring Boot sobem e executam em milissegundos (aproximadamente 0.2s), o que facilita integrações contínuas rápidas e um bom workflow de desenvolvimento (TDD).
2. **Design Mínimo:** A classe `AlfaIngestor` é parte do padrão *Strategy* que se propõe apenas a fazer o *de-para* entre o DTO (objeto de transferência) e a Entidade de Domínio, sem salvar no banco de dados. Como o `AlfaStatusMapper` também é um componente puramente lógico sem I/O, utilizar *Mocks* adicionaria complexidade inútil e mascararia o comportamento acoplado e real das conversões.

---

## 2. Nomenclatura dos Métodos e Casos de Uso (Padrão `metodo_cenario_resultado`)

### O que foi feito?
Todos os testes adotaram a convensão estrita `[método testado]_[cenário]_[resultado esperado]`.
Exemplos aplicados:
- `toEntity_pedidoValido_mapeiaCabecalhoComSucesso`
- `toEntity_statusInvalido_lancaExcecao`
- `toEntity_listaItensVazia_retornaPedidoSemItens`

### Por que foi escolhido?
Essa padronização ajuda enormemente na hora de ler os relatórios de cobertura e falhas (logs do Maven). O desenvolvedor consegue saber, em uma única string, **onde** o erro ocorreu (`toEntity`), **sob qual condição** ele falhou (`statusInvalido`), e **o que era esperado** (`lancaExcecao`).

---

## 3. Auxiliares de Criação de DTOs (Helpers)

### O que foi feito?
Foram criados métodos utilitários privados para instanciar os objetos Records:
- `fornecedor(String cnpj, String nome)`
- `itemPadrao()`
- `pedido(...)`

### Por que foi escolhido?
Sem esses construtores, todo método de teste teria blocos enormes e verbosos apenas declarando entidades aninhadas (Pedido com um Fornecedor e uma Lista de Itens). A criação dos métodos privados foca a leitura do desenvolvedor estritamente nos **dados e na asserção**, que é o que importa no teste, e reaproveita código de instanciamento para todos os cenários.

---

## 4. Cobertura de Cenários Específicos

Foram escolhidos testes cirúrgicos que protegem as regras de negócio declaradas no documento de arquitetura (`DESING.md`):

### 4.1. Mapeamento Bidirecional
- **Implementação:** `toEntity_comItens_associaItemAoPedido`
- **Motivo:** O JPA relacional exige que os "filhos" (Itens) conheçam a referência do "Pai" (Pedido) para preencher a *Foreign Key* na tabela. Validar que cada objeto item retornado possui a referência bidirecional blindou as entidades contra exceções silenciosas de transação (`ConstraintViolation`).

### 4.2. Coluna Gerada (PostgreSQL)
- **Implementação:** Asserção no `toEntity_itemCompleto_mapeiaTodosOsCampos`, checando que `item.getQuantidadePendente() == null`.
- **Motivo:** Pelo *Design*, a `quantidade_pendente` é computada exclusivamente no banco de dados. O Java JAMAIS pode passar um valor preenchido ou causará quebras em inserção de coluna `GENERATED ALWAYS`.

### 4.3. Flexibilidade e Proteção dos Enums
- **Implementação:** Múltiplos testes para os status normais (OPEN, CLOSED, BLOCKED), testes para status errados em minúsculo (`"open"`) e para cenários que lançam `IllegalArgumentException` (`"PENDENTE"`).
- **Motivo:** Sistemas de terceiros via requisição HTTP podem falhar o contrato no *case* (enviar em minúsculo). O sistema deve ser tolerante a isso, mas implacável rejeitando chaves que não existam (segurança contra lixo entrando na base de dados).

### 4.4. A Borda de Cenários Vazios
- **Implementação:** `toEntity_listaItensVazia_retornaPedidoSemItens`
- **Motivo:** Um pedido pode ter vindo falho sem itens da API do Alfa. Embora ele deva ser validado nas portas de entrada da controller (`@Valid`), atestar que o Parser sobrevive a uma lista vazia, retornando uma coleção em branco (ao invés de dar erro de ponteiro - NullPointerException), provê uma camada adicional de robustez estrutural no Strategy.
