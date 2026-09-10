# V360 - Conector de Pedidos de Compra

Este projeto é um serviço backend (API REST) desenvolvido para resolver o desafio de integração de dados da V360. A aplicação atua como uma camada intermediária que ingere pedidos de compra de diferentes clientes (Alfa Energia, Beta Alimentos e Gama Logística) em formatos variados (JSON, CSV, estruturas achatadas) e os normaliza em um contrato único.

## Tecnologias e Dependências

A aplicação foi construída em **Java 21** com **Spring Boot**, utilizando as seguintes dependências principais:

*   **Spring Web:** Criação dos endpoints REST.
*   **Spring Data JPA:** Persistência de dados utilizando o padrão Repository.
*   **PostgreSQL:** Banco de dados principal. A aplicação usa Postgres desde o primeiro dia via `compose.yaml` 
    (variáveis de ambiente configuradas em .env).
*   **Lombok:** Redução de boilerplate (getters, setters, construtores).
*   **Validation (Hibernate Validator):** Garantia de integridade dos payloads recebidos.
*   **OpenCSV:** Escolhido para o parse do cliente Beta pela facilidade de mapeamento via POJO (`@CsvBindByPosition`).
*   **Docker Compose Support:** Facilita a subida da infraestrutura completa (Postgres 17 + app + pgAdmin).

## Como rodar o projeto

### Opção 1: Via Docker (Recomendado)
1. Certifique-se de ter o Java 21, Docker e o Docker Compose instalados.
2. Configure as variáveis de ambiente de conexão ao banco (host, porta, usuário, senha) inserindo o arquivo .env 
   modelo na raiz do projeto.
```bash
   POSTGRES_DB=mydatabase
   POSTGRES_USER=myuser
   POSTGRES_PASSWORD=secret
   POSTGRES_PORT=5432
   
   APP_PORT=8080
   
   PGADMIN_DEFAULT_EMAIL=admin@admin.com
   PGADMIN_DEFAULT_PASSWORD=admin
   PGADMIN_DEFAULT_PORT=5050

```
2. Na raiz do projeto, execute:
```bash
   docker-compose up -d --build
```
3. Você pode acessar os seguintes links para acessar a API:
   - **API:** `http://localhost:8080`
   - **pgAdmin:** `http://localhost:5050`
   - **Swagger:** `http://localhost:8080/swagger-ui.html`

> As credenciais e a URL do banco são gerenciadas pelas variáveis de ambiente definidas no `.env` que são usadas 
> pelo `compose.yaml`. Não é necessário configurar nada manualmente.

## Arquitetura e Decisões Técnicas

Para garantir que a adição de novos clientes não quebrasse o código existente, optei por uma arquitetura em camadas com ingestão no estilo **Pipes and Filters**, orquestrada pelo padrão **Strategy**.

* **Ingestão e Normalização:** Cada cliente possui rotas dedicadas. O dado bruto entra, é validado pelo `@Valid` no controller, processado por um parser/agrupador e traduzido por um ingestor isolado antes de ser salvo no modelo único via `PedidoService`.
* **Factory dispensada:** há uma rota por cliente; o Spring injeta o ingestor correto no controller correspondente.
* **Open/Closed na prática:** a adição da Gama (Parte 2) foi 100% aditiva — `GamaItemLinhaDTO`, `GamaPedidoDTO`, `GamaPayloadAgrupador`, `GamaDateParser`, `GamaStatusMapper`, `UnitConverter`, `GamaIngestor` e `GamaController` foram criados sem alterar Alfa, Beta, conferência, schema ou `PedidoService`.

### Fluxo ponta a ponta

```
Alfa (JSON aninhado)     Beta (CSV 2 arquivos)     Gama (JSON achatado)
        |                        |                          |
   POST /ingest/alfa      POST /ingest/beta         POST /ingest/gama
        |                        |                          |
    AlfaIngestor       BetaCsvParser → BetaIngestor     GamaPayloadAgrupador → GamaIngestor
        |                        |                          |
        +------------------------+--------------------------+
                                  |
                 Normalizadores (CNPJ, status, data, string, unidade)
                                  |
                            PedidoService (upsert por numero_pedido_origem + cliente_origem)
                                  |
                              PostgreSQL
        (Fornecedor, Pedido, Item, Conferencia, Divergencia)
                                  |
        +-------------------------+-------------------------+
        |                         |                         |
    Consulta               Conferência                Relatório
  GET /pedidos          POST /notas-fiscais/      GET /relatorios/
  GET /pedidos/{id}          conferir               conferencias
```

### Normalizadores

| Peça | Uso |
|---|---|
| `CnpjSanitizer` | Compartilhado por Alfa, Beta e Gama — só dígitos no contrato |
| `StringSanitizer` | Remove `\n`, `\r`, `\t` e faz trim — reutilizado pelo Gama |
| `AlfaStatusMapper` | `open/closed/blocked` → enum `OPEN/CLOSED/BLOCKED` |
| `BetaStatusMapper` | `"EM ABERTO"/"BLOQUEADO"/"ENCERRADO"` → enum |
| `GamaStatusMapper` | Inteiro `1/2/3` → `OPEN/CLOSED/BLOCKED`; **não** implementa a interface `StatusMapper` (que é `map(String)`) — assimetria consciente |
| `AlfaDateParser` | `YYYY-MM-DD` (`LocalDate`) → `Instant` (`America/Sao_Paulo`) |
| `BetaDateParser` | `dd/MM/yyyy` → `Instant` |
| `BetaNumberParser` | Número BR (`1.200,000` / `6,49`) → `BigDecimal` |
| `GamaDateParser` | Epoch segundos (`Long`) → `Instant.ofEpochSecond`; null-safe |
| `UnitConverter` | `qtd × fator_conv` (pedida **e** recebida); `(centavos/100) / fator_conv` (preço, escala 4, `HALF_UP`) |

### Particularidades por cliente

| Cliente | Formato | Conversões principais |
|---|---|---|
| **Alfa Energia** | JSON aninhado (lote em `purchase_orders`) | `created_at` é `YYYY-MM-DD` (não ISO com horário); `StringSanitizer` no nome do fornecedor |
| **Beta Alimentos** | CSV duplo (`;`, padrão BR) — `cabecalho.csv` + `itens.csv` | Join por `NUMERO_PEDIDO`; CSV não é RFC 4180 — tratado por `BetaCsvRecordAssembler` (máquina de estados + lookahead) |
| **Gama Logística** | JSON achatado (array: 1 objeto = 1 linha de item) | `dt_criacao` em epoch segundos; `preco_unit_centavos`; `situacao` numérica; `fator_conv`; unidade persistida como `"UN"` (campo `um` do JSON é unidade de compra, não vai ao banco); moeda constante `"BRL"` |

## Modelo de dados único (contrato de saída)

```json
{
  "id_pedido": "uuid",
  "numero_pedido": "GL-778",
  "cliente_origem": "GAMA",
  "data_criacao": "2026-08-15T00:00:00Z",
  "status": "OPEN",
  "moeda": "BRL",
  "fornecedor": {
    "id_fornecedor": "uuid",
    "cnpj": "34567890000112",
    "nome": "Transportes Ideal ME"
  },
  "itens": [
    {
      "id_item": "uuid",
      "linha": "1",
      "codigo_material": "TRP-01",
      "descricao": "Pallet de madeira",
      "unidade_medida": "UN",
      "quantidade_pedida": 120,
      "quantidade_recebida": 24,
      "quantidade_pendente": 96,
      "preco_unitario": 100.00
    }
  ]
}
```

> **Nota:** `quantidade_pendente` é uma **coluna gerada** no banco (`GENERATED ALWAYS AS (quantidade_pedida - quantidade_recebida) STORED`). O JPA não escreve nesse campo (`insertable = false, updatable = false`). O valor só existe após leitura do PostgreSQL.

> **Nota:** O `POST /ingest/*` devolve `numero_pedido_origem`; a consulta `GET /pedidos/{id}` devolve `numero_pedido`. São o mesmo dado com nomes ligeiramente diferentes.

> **Nota:** `Fornecedor.cnpj` é `UNIQUE` global — o mesmo CNPJ vindo de Alfa, Beta ou Gama corresponde à mesma entidade. O `PedidoService` atualiza o nome se o CNPJ já existir.

## Regras de Negócio e Divergências (Conferência de Notas)

O endpoint `POST /notas-fiscais/conferir` cruza a nota fiscal com o pedido armazenado e persiste o resultado (tabelas `Conferencia` e `Divergencia`).

### Tipos de divergência

| Tipo | Quando ocorre |
|---|---|
| `PEDIDO_NAO_ENCONTRADO` | Número do pedido da nota não existe na base |
| `FORNECEDOR_DIVERGENTE` | CNPJ da nota ≠ CNPJ do pedido |
| `MATERIAL_NAO_ENCONTRADO` | Material da nota não está nos itens do pedido |
| `QUANTIDADE_EXCEDE_PENDENTE` | Quantidade da nota > soma das `quantidade_pendente` daquele `codigo_material` |
| `VALOR_DIVERGENTE` | `\|valor_nota − (quantidade × preço_unitário)\| > R$ 0,05` |
| `PEDIDO_BLOQUEADO` | Pedido está `BLOCKED` — registra e **continua** |
| `PEDIDO_ENCERRADO` | Pedido está `CLOSED` — registra e **continua** |

### Decisões de conferência

1. **Tolerância absoluta de R$ 0,05:** a comparação de valor usa `BigDecimal`. A margem absoluta cobre resíduos de arredondamento gerados na conversão do Gama (centavos + `fator_conv`) sem mascarar erros materiais.

2. **Avaliação completa (sem `return` antecipado):** `BLOCKED` e `CLOSED` não abortam a conferência. Todas as divergências são acumuladas em uma lista e devolvidas de uma vez — evita o efeito ioiô onde o operador corrigi um problema só para descobrir o próximo. Única exceção: `PEDIDO_NAO_ENCONTRADO` encerra o cruzamento de linhas (sem pedido não há itens para verificar).

3. **Agregação por material:** os itens do pedido são agrupados por `codigo_material` e as `quantidade_pendente` são somadas. A nota é cruzada contra esse saldo agregado, não linha a linha. Preço de referência: ponderado pelo pendente quando há mais de uma linha do mesmo material.

## O que eu faria diferente com mais tempo

* Divisão de contexto de IA em sub-tasks;
* Desenvolver projeto baseado em testes e pontos de falha;
* Camada de autenticação (OAuth2/JWT) para proteger as rotas de ingestão e garantir que apenas clientes autorizados 
  possam enviar dados.
* Controle de Concorrência para prevenir condições de corrida (*race conditions*)
* Processamento Assíncrono e Mensageria para lidar com picos de tráfego e arquivos 
  CSV/JSON gigantes.
---

*Nota: Para detalhes sobre o uso de Inteligência Artificial durante o desenvolvimento, consulte o arquivo `AI_USAGE.md`.*