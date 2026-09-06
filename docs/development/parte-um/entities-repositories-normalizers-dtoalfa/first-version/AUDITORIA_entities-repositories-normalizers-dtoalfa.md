# Análise da Arquitetura Atual: Conector de Pedidos de Compra

_GERADO POR GEMINI PRO HIGH EFFORT E REVISADO MANUALMENTE_

Esta análise detalha como a estrutura atual do diretório `src/main/java/com/v360/prosel/conectorpedidoscompra` implementa as decisões de arquitetura e design registradas no documento `DESING.md`.

O projeto encontra-se atualmente na conclusão das tarefas estipuladas para o **Dia 1 (Ingestão Alfa)** e reflete fielmente as diretrizes e padrões de projeto escolhidos.

---

## 1. Mapeamento de Camadas e Responsabilidades

A estrutura de pacotes foi desenhada para separar nitidamente as responsabilidades, aderindo à arquitetura definida na **Seção 1.2** do `DESING.md`:

### `controller/`
- **Conteúdo atual:** `AlfaController.java`
- **Papel na Arquitetura:** Ponto de entrada (POST `/ingest/alfa`).
- **Design:** Conforme a **Seção 1.3**, optou-se por ter uma rota dedicada por cliente (e.g. `/ingest/alfa`). Isso permite que o framework (Spring) injete a estratégia de ingestão correta (o `AlfaIngestor`) de forma nativa. Esta escolha eliminou a necessidade de implementar um padrão *Factory* complexo. O *Controller* foca estritamente em orquestrar a chamada ao *Ingestor* e ao *Service*, não possuindo regras de negócio.

### `dto/`
- **Conteúdo atual:** Pacote `alfa/` contendo `AlfaPedidoDTO`, `AlfaFornecedorDTO` e `AlfaItemDTO`.
- **Papel na Arquitetura:** Espelhar fielmente o payload bruto e aninhado (JSON) que vem do sistema de origem Alfa. Essa separação garante que os *Controllers* e *Ingestors* lidem com dados validados (`@Valid` em uso) antes da conversão para entidades de domínio.

### `ingestor/`
- **Conteúdo atual:** Interface `strategy/PedidoIngestor.java` e a implementação `AlfaIngestor.java`.
- **Papel na Arquitetura:** Coração do padrão **Strategy** (**Seção 1.3**). A interface `PedidoIngestor<T>` define o contrato genérico `Pedido toEntity(T dto)`. O `AlfaIngestor` aplica as transformações necessárias para o cliente Alfa (usando normalizadores) sem acessar o banco de dados. Isso obedece rigorosamente ao *Open/Closed Principle*; quando o cliente Beta ou Gama entrarem, não será necessário alterar o `AlfaIngestor`.

### `normalizer/`
- **Conteúdo atual:** `CnpjSanitizer.java`, `StatusMapper.java` (interface) e `AlfaStatusMapper.java`.
- **Papel na Arquitetura:** Funções de normalização compartilhadas (ou especializadas) aplicadas durante a fase de **Pipes and Filters** (**Seção 1.1**). 
  - O `CnpjSanitizer` atua para garantir a normalização centralizada de CNPJs (somente dígitos), conforme a **Seção 2**.
  - O `AlfaStatusMapper` cuida da tradução dos status específicos do cliente Alfa para o modelo único de Domínio (Enum `StatusPedido`).

### `service/`
- **Conteúdo atual:** `PedidoService.java`.
- **Papel na Arquitetura:** Regras de persistência em alto nível e lógica transacional (`@Transactional`).
- **Design em destaque:** A estratégia de **Upsert** definida na **Seção 3.5** e **4.2** foi implementada com precisão. O método `upsert()` evita o uso de `INSERT ... ON CONFLICT` nativo do SQL e utiliza a abordagem do Spring Data JPA (`findBy...` seguido de `save`). O serviço é responsável por orquestrar a persistência completa em cascata (Fornecedor → Pedido → Itens).

### `entity/` e `repository/`
- **Conteúdo atual:** `Pedido.java`, `Item.java`, `Fornecedor.java` e as interfaces JPA no pacote `repository/`.
- **Papel na Arquitetura:** Mapeamento de dados e interface com o PostgreSQL.

---

## 2. Decisões Críticas Refletidas no Código

Analisando a implementação, é notável o nível de aderência e rigor em seguir as restrições mais complexas definidas no documento:

1. **A Coluna Computada (`quantidade_pendente`)**
   - Na **Seção 3.4**, foi decidido que o banco (PostgreSQL) deveria ser responsável pelo cálculo da `quantidade_pendente` através da funcionalidade `GENERATED ALWAYS AS ... STORED`.
   - Na classe `entity/Item.java`, isso foi implementado perfeitamente utilizando os atributos `@Column(name = "quantidade_pendente", insertable = false, updatable = false)`. O banco está no controle absoluto desse valor, impedindo erros silenciosos na camada Java caso um desenvolvedor esqueça de atualizar a quantidade durante uma reingestão.

2. **Chaves Únicas e Modelo Único**
   - Na entidade `Item.java`, a anotação `@Table(uniqueConstraints = ...)` impõe a trava de duplicidade por `(id_pedido, linha)`, estipulada na **Seção 3**.
   - Na entidade `Pedido.java`, observamos a tipagem e os vínculos definidos no modelo único, garantindo uma fonte da verdade consistente independente do JSON de origem. Tipos financeiros estão protegidos (`NUMERIC(15,4)` configurado via `precision = 15, scale = 4`).

3. **Infraestrutura Pronta desde o Dia Zero**
   - Conforme a **Seção 4.1**, a equipe escolheu focar em PostgreSQL via Docker logo de início para ter suporte às *generated columns*. O arquivo `compose.yaml` comprova isso com os serviços `postgres` e `app` devidamente declarados, integrando o ambiente ao Spring Boot através do `application.properties`.

---

## 3. Considerações e Próximos Passos (Conforme Kanban)

O que **ainda não está implementado** no diretório (pois são o foco dos Dias 2, 3 e 4 do Kanban - `DESING.md` seção 7):

- **Ingestão Beta/Gama:** Os diretórios `dto/beta`, `dto/gama`, os parsers em CSV e as implementações `BetaIngestor` e `GamaIngestor` ainda serão construídos, mas se acoplarão de forma harmoniosa à arquitetura *Strategy* que já está desenhada.
- **Domínios de Relatório e Conferência:** As entidades `Conferencia` e `Divergencia` (**Seção 3.1**) ainda precisam ser criadas para dar suporte aos fluxos de consulta e validação de notas fiscais.
- **Questões "Em Aberto":**
   - Tolerância de divergência de preço.
   - Tratamento na recepção de pedidos com itens removidos (na reingestão).
   - Casamento de nota com múltiplas linhas de mesmo material.

### Resumo
A base de código em `src/main/java/com/v360/prosel/conectorpedidoscompra` demonstra forte obediência e sincronia com o `DESING.md`. A arquitetura base, especialmente a modularização de *Ingestors* e *Normalizadores*, está sólida e perfeitamente preparada para a inclusão da carga dos próximos clientes (Beta e Gama) sem ferir os contratos e a lógica preexistentes.

