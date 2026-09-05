-- =============================================================
-- V360 — Conector de Pedidos de Compra
-- Schema relacional conforme DESING.md seção 3
-- Executado automaticamente pelo Spring Boot (spring.sql.init.mode=always)
-- =============================================================

CREATE TABLE IF NOT EXISTS Fornecedor (
    id_fornecedor UUID PRIMARY KEY,
    cnpj          VARCHAR(255) UNIQUE NOT NULL,
    nome          VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS Pedido (
    id_pedido            UUID PRIMARY KEY,
    id_fornecedor        UUID REFERENCES Fornecedor(id_fornecedor),
    numero_pedido_origem VARCHAR(255),
    cliente_origem       VARCHAR(255),
    data_criacao         TIMESTAMPTZ,
    data_ingestao        TIMESTAMPTZ,
    status               VARCHAR(50),
    moeda                VARCHAR(3)
);

CREATE TABLE IF NOT EXISTS Item (
    id_item              UUID PRIMARY KEY,
    id_pedido            UUID REFERENCES Pedido(id_pedido),
    linha                VARCHAR(50),
    codigo_material      VARCHAR(100),
    descricao            TEXT,
    unidade_medida       VARCHAR(20),
    quantidade_pedida    NUMERIC(15, 4),
    quantidade_recebida  NUMERIC(15, 4),
    -- Coluna gerada: o Postgres calcula automaticamente. O JPA NUNCA deve escrever nesta coluna.
    quantidade_pendente  NUMERIC(15, 4) GENERATED ALWAYS AS (quantidade_pedida - quantidade_recebida) STORED,
    preco_unitario       NUMERIC(15, 4)
);

CREATE TABLE IF NOT EXISTS Conferencia (
    id_conferencia   UUID PRIMARY KEY,
    id_pedido        UUID REFERENCES Pedido(id_pedido),
    fornecedor_cnpj  VARCHAR(20),
    resultado        VARCHAR(20),    -- APROVADA / REJEITADA
    data_conferencia TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS Divergencia (
    id_divergencia   UUID PRIMARY KEY,
    id_conferencia   UUID REFERENCES Conferencia(id_conferencia),
    tipo             VARCHAR(50),    -- PEDIDO_NAO_ENCONTRADO | FORNECEDOR_DIVERGENTE |
                                     -- MATERIAL_NAO_ENCONTRADO | QUANTIDADE_EXCEDE_PENDENTE |
                                     -- VALOR_DIVERGENTE | PEDIDO_BLOQUEADO | PEDIDO_ENCERRADO
    codigo_material  VARCHAR(100),
    valor_esperado   TEXT,
    valor_recebido   TEXT,
    descricao        TEXT
);

-- Constraints únicas (criadas separadamente para suportar IF NOT EXISTS via DO block)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uk_pedido_origem'
    ) THEN
        ALTER TABLE Pedido ADD CONSTRAINT uk_pedido_origem
            UNIQUE (numero_pedido_origem, cliente_origem);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uk_item_pedido'
    ) THEN
        ALTER TABLE Item ADD CONSTRAINT uk_item_pedido
            UNIQUE (id_pedido, linha);
    END IF;
END $$;

-- Índices para os filtros do requisito de consulta (DESING.md seção 3)
CREATE INDEX IF NOT EXISTS idx_pedido_cliente_origem ON Pedido(cliente_origem);
CREATE INDEX IF NOT EXISTS idx_pedido_status          ON Pedido(status);
CREATE INDEX IF NOT EXISTS idx_pedido_fornecedor      ON Pedido(id_fornecedor);
