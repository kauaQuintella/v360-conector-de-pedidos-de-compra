# V360 - Conector de Pedidos de Compra

Este projeto é um serviço backend (API REST) desenvolvido para resolver o desafio de integração de dados da V360. A aplicação atua como uma camada intermediária que ingere pedidos de compra de diferentes clientes (Alfa Energia, Beta Alimentos e Gama Logística) em formatos variados (JSON, CSV, estruturas achatadas) e os normaliza em um contrato único.

## Tecnologias e Dependências

A aplicação foi construída em **Java 21** com **Spring Boot**, utilizando as seguintes dependências principais:

*   **Spring Web:** Criação dos endpoints REST.
*   **Spring Data JPA:** Persistência de dados utilizando o padrão Repository.
*   **H2 Database:** Banco de dados em memória para execução simplificada e testes rápidos.
*   **PostgreSQL Driver:** Configurado para escalar a persistência em ambientes de produção.
*   **Lombok:** Redução de boilerplate (getters, setters, construtores).
*   **Validation (Hibernate Validator):** Garantia de integridade dos payloads recebidos.
*   **OpenCSV:** Escolhido para o parse do cliente Beta pela facilidade de mapeamento via POJO.
*   **Docker Compose Support:** Facilita a subida da infraestrutura completa.

## Como rodar o projeto

### Opção 1: Via Docker (Recomendado)
1. Certifique-se de ter o Docker e o Docker Compose instalados.
2. Na raiz do projeto, execute:
```bash
   docker-compose up --build
```
3. A API estará disponível em `http://localhost:8080`.

### Opção 2: Localmente (IDE ou Terminal)

1. Certifique-se de ter o Java instalado.
2. Clone o repositório e navegue até a pasta raiz.
3. O banco H2 já está configurado por padrão no `application.properties`.
4. Execute o comando:

```bash
   ./mvnw spring-boot:run
```

## Arquitetura e Decisões Técnicas

Para garantir que a adição de novos clientes não quebrasse o código existente, optei por uma arquitetura inspirada em **Pipes and Filters**, orquestrada pelos padrões **Strategy** e **Factory**.

* **Ingestão e Normalização:** Cada cliente possui rotas específicas. O dado bruto entra, é validado e processado por um "tradutor" isolado antes de ser salvo no modelo único.
* **

## Regras de Negócio e Divergências (Conferência de Notas)

No endpoint de conferência de notas fiscais, as seguintes regras foram estabelecidas:

* 
## O que eu faria diferente com mais tempo

* 
---

*Nota: Para detalhes sobre o uso de Inteligência Artificial durante o desenvolvimento, consulte o arquivo `AI_USAGE.md`.*