## ✅ Dia 1 — 100% Completo

### O que foi implementado nesta sessão

Arquivo criado:
AlfaIngestorTest.java

### Resultado dos testes

    Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.242 s
    BUILD SUCCESS

   Teste                                                                                                         | Cenário
  ---------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------
   4.1                                                                                                           | Todos os campos de cabeçalho do Pedido mapeados
   4.2                                                                                                           | CNPJ com máscara "12.345.678/0001-90" → "12345678000190"
   4.3                                                                                                           | Status "CLOSED" → StatusPedido.CLOSED
   4.4                                                                                                           | Status "BLOCKED" → StatusPedido.BLOCKED
   4.5                                                                                                           | Status em lowercase "open" aceito sem exceção
   4.6                                                                                                           | Status inválido lança IllegalArgumentException com o valor na mensagem
   4.7                                                                                                           | Todos os campos do Item mapeados + quantidadePendente == null
   4.8                                                                                                           | 3 itens no DTO → 3 itens na entidade, na ordem correta
   4.9                                                                                                           | Cada Item tem referência bidirecional ao Pedido pai

### DIA1.md atualizado — todos os 10 itens marcados como [x]