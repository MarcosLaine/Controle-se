-- =====================================================
-- SCHEMA POSTGRESQL PARA CONTROLE-SE
-- Compatível com Aiven PostgreSQL
-- =====================================================

-- Extensões úteis (se necessário)
-- CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- =====================================================
-- TABELAS PRINCIPAIS
-- =====================================================

-- Tabela: usuarios
CREATE TABLE IF NOT EXISTS usuarios (
    id_usuario SERIAL PRIMARY KEY,
    nome VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    senha VARCHAR(500) NOT NULL,  -- Senha criptografada RSA
    ativo BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_usuarios_email ON usuarios(email);
CREATE INDEX IF NOT EXISTS idx_usuarios_ativo ON usuarios(ativo);

-- Tabela: categorias
CREATE TABLE IF NOT EXISTS categorias (
    id_categoria SERIAL PRIMARY KEY,
    nome VARCHAR(255) NOT NULL,
    id_usuario INTEGER NOT NULL,
    ativo BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (id_usuario) REFERENCES usuarios(id_usuario) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_categorias_usuario ON categorias(id_usuario);
CREATE INDEX IF NOT EXISTS idx_categorias_ativo ON categorias(ativo);

-- Tabela: contas
CREATE TABLE IF NOT EXISTS contas (
    id_conta SERIAL PRIMARY KEY,
    nome VARCHAR(255) NOT NULL,
    tipo VARCHAR(100) NOT NULL,
    saldo_atual DECIMAL(15,2) DEFAULT 0,
    id_usuario INTEGER NOT NULL,
    ativo BOOLEAN DEFAULT TRUE,
    dia_fechamento INTEGER,
    dia_pagamento INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (id_usuario) REFERENCES usuarios(id_usuario) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_contas_usuario ON contas(id_usuario);
CREATE INDEX IF NOT EXISTS idx_contas_tipo ON contas(tipo);
CREATE INDEX IF NOT EXISTS idx_contas_ativo ON contas(ativo);
-- Índice composto para otimizar queries de contas por usuário, tipo e ativo
CREATE INDEX IF NOT EXISTS idx_contas_usuario_tipo_ativo ON contas(id_usuario, tipo, ativo) WHERE ativo = TRUE;

-- Tabela: gastos
CREATE TABLE IF NOT EXISTS gastos (
    id_gasto SERIAL PRIMARY KEY,
    descricao VARCHAR(500) NOT NULL,
    valor DECIMAL(15,2) NOT NULL,
    data DATE NOT NULL,
    frequencia VARCHAR(50),
    id_usuario INTEGER NOT NULL,
    id_conta INTEGER NOT NULL,
    proxima_recorrencia DATE,
    id_gasto_original INTEGER DEFAULT 0,
    ativo BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (id_usuario) REFERENCES usuarios(id_usuario) ON DELETE CASCADE,
    FOREIGN KEY (id_conta) REFERENCES contas(id_conta) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_gastos_usuario ON gastos(id_usuario);
CREATE INDEX IF NOT EXISTS idx_gastos_data ON gastos(data);
CREATE INDEX IF NOT EXISTS idx_gastos_conta ON gastos(id_conta);
CREATE INDEX IF NOT EXISTS idx_gastos_ativo ON gastos(ativo);
CREATE INDEX IF NOT EXISTS idx_gastos_recorrencia ON gastos(proxima_recorrencia) WHERE proxima_recorrencia IS NOT NULL;
-- Índice composto para otimizar queries de gastos por usuário e ativo
CREATE INDEX IF NOT EXISTS idx_gastos_usuario_ativo ON gastos(id_usuario, ativo) WHERE ativo = TRUE;
-- Índice composto para queries de gastos por usuário, ativo e data (ORDER BY data DESC)
CREATE INDEX IF NOT EXISTS idx_gastos_usuario_ativo_data ON gastos(id_usuario, ativo, data DESC) WHERE ativo = TRUE;
-- Índice para queries de gastos por data específica
CREATE INDEX IF NOT EXISTS idx_gastos_data_ativo ON gastos(data, ativo) WHERE ativo = TRUE;
-- Índice para queries de recorrência de gastos
CREATE INDEX IF NOT EXISTS idx_gastos_recorrencia_ativo ON gastos(proxima_recorrencia, ativo) WHERE proxima_recorrencia IS NOT NULL AND ativo = TRUE;

-- Tabela: receitas
CREATE TABLE IF NOT EXISTS receitas (
    id_receita SERIAL PRIMARY KEY,
    descricao VARCHAR(500) NOT NULL,
    valor DECIMAL(15,2) NOT NULL,
    data DATE NOT NULL,
    frequencia VARCHAR(50),
    id_usuario INTEGER NOT NULL,
    id_conta INTEGER NOT NULL,
    proxima_recorrencia DATE,
    id_receita_original INTEGER DEFAULT 0,
    ativo BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (id_usuario) REFERENCES usuarios(id_usuario) ON DELETE CASCADE,
    FOREIGN KEY (id_conta) REFERENCES contas(id_conta) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_receitas_usuario ON receitas(id_usuario);
CREATE INDEX IF NOT EXISTS idx_receitas_data ON receitas(data);
CREATE INDEX IF NOT EXISTS idx_receitas_conta ON receitas(id_conta);
CREATE INDEX IF NOT EXISTS idx_receitas_ativo ON receitas(ativo);
CREATE INDEX IF NOT EXISTS idx_receitas_recorrencia ON receitas(proxima_recorrencia) WHERE proxima_recorrencia IS NOT NULL;
-- Índice composto para otimizar queries de receitas por usuário e ativo
CREATE INDEX IF NOT EXISTS idx_receitas_usuario_ativo ON receitas(id_usuario, ativo) WHERE ativo = TRUE;
-- Índice composto para queries de receitas por usuário, ativo e data (ORDER BY data DESC)
CREATE INDEX IF NOT EXISTS idx_receitas_usuario_ativo_data ON receitas(id_usuario, ativo, data DESC) WHERE ativo = TRUE;
-- Índice para queries de receitas por data específica
CREATE INDEX IF NOT EXISTS idx_receitas_data_ativo ON receitas(data, ativo) WHERE ativo = TRUE;
-- Índice para queries de recorrência de receitas
CREATE INDEX IF NOT EXISTS idx_receitas_recorrencia_ativo ON receitas(proxima_recorrencia, ativo) WHERE proxima_recorrencia IS NOT NULL AND ativo = TRUE;

-- Tabela: orcamentos
CREATE TABLE IF NOT EXISTS orcamentos (
    id_orcamento SERIAL PRIMARY KEY,
    valor_planejado DECIMAL(15,2) NOT NULL,
    periodo VARCHAR(50) NOT NULL,
    id_categoria INTEGER NOT NULL,
    id_usuario INTEGER NOT NULL,
    ativo BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (id_categoria) REFERENCES categorias(id_categoria) ON DELETE CASCADE,
    FOREIGN KEY (id_usuario) REFERENCES usuarios(id_usuario) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_orcamentos_usuario ON orcamentos(id_usuario);
CREATE INDEX IF NOT EXISTS idx_orcamentos_categoria ON orcamentos(id_categoria);
CREATE INDEX IF NOT EXISTS idx_orcamentos_ativo ON orcamentos(ativo);
-- Índice composto para queries de orçamentos por categoria e ativo
CREATE INDEX IF NOT EXISTS idx_orcamentos_categoria_ativo ON orcamentos(id_categoria, ativo) WHERE ativo = TRUE;

-- Tabela: tags
CREATE TABLE IF NOT EXISTS tags (
    id_tag SERIAL PRIMARY KEY,
    nome VARCHAR(255) NOT NULL,
    cor VARCHAR(50),
    id_usuario INTEGER NOT NULL,
    ativo BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (id_usuario) REFERENCES usuarios(id_usuario) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_tags_usuario ON tags(id_usuario);
CREATE INDEX IF NOT EXISTS idx_tags_ativo ON tags(ativo);

-- Tabela: investimentos
CREATE TABLE IF NOT EXISTS investimentos (
    id_investimento SERIAL PRIMARY KEY,
    nome VARCHAR(255) NOT NULL,
    nome_ativo VARCHAR(255),
    categoria VARCHAR(100),
    quantidade DECIMAL(15,4),
    preco_aporte DECIMAL(15,2),
    corretagem DECIMAL(15,2),
    corretora VARCHAR(255),
    data_aporte DATE,
    id_usuario INTEGER NOT NULL,
    id_conta INTEGER NOT NULL,
    moeda VARCHAR(10),
    tipo_investimento VARCHAR(100),
    tipo_rentabilidade VARCHAR(100),
    indice VARCHAR(100),
    percentual_indice DECIMAL(5,2),
    taxa_fixa DECIMAL(5,2),
    data_vencimento DATE,
    ativo BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (id_usuario) REFERENCES usuarios(id_usuario) ON DELETE CASCADE,
    FOREIGN KEY (id_conta) REFERENCES contas(id_conta) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_investimentos_usuario ON investimentos(id_usuario);
CREATE INDEX IF NOT EXISTS idx_investimentos_conta ON investimentos(id_conta);
CREATE INDEX IF NOT EXISTS idx_investimentos_ativo ON investimentos(ativo);
-- Índice composto para queries de investimentos por usuário, ativo e data_aporte (ORDER BY data_aporte DESC)
CREATE INDEX IF NOT EXISTS idx_investimentos_usuario_ativo_data ON investimentos(id_usuario, ativo, data_aporte DESC) WHERE ativo = TRUE;

-- =====================================================
-- TABELAS DE RELACIONAMENTO N:N
-- =====================================================

-- Tabela: categoria_gasto (Relacionamento N:N)
CREATE TABLE IF NOT EXISTS categoria_gasto (
    id_categoria_gasto SERIAL PRIMARY KEY,
    id_categoria INTEGER NOT NULL,
    id_gasto INTEGER NOT NULL,
    ativo BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (id_categoria) REFERENCES categorias(id_categoria) ON DELETE CASCADE,
    FOREIGN KEY (id_gasto) REFERENCES gastos(id_gasto) ON DELETE CASCADE,
    UNIQUE(id_categoria, id_gasto)
);

CREATE INDEX IF NOT EXISTS idx_categoria_gasto_categoria ON categoria_gasto(id_categoria);
CREATE INDEX IF NOT EXISTS idx_categoria_gasto_gasto ON categoria_gasto(id_gasto);
CREATE INDEX IF NOT EXISTS idx_categoria_gasto_ativo ON categoria_gasto(ativo);
-- Índice composto para otimizar query agregada de gastos por categoria
CREATE INDEX IF NOT EXISTS idx_categoria_gasto_composto ON categoria_gasto(id_categoria, id_gasto, ativo) WHERE ativo = TRUE;
-- Índice composto para JOINs com categoria_gasto (buscar gastos por categoria)
CREATE INDEX IF NOT EXISTS idx_categoria_gasto_gasto_ativo ON categoria_gasto(id_gasto, ativo) WHERE ativo = TRUE;

-- Tabela: transacao_tag (Relacionamento N:N)
CREATE TABLE IF NOT EXISTS transacao_tag (
    id_transacao_tag SERIAL PRIMARY KEY,
    id_transacao INTEGER NOT NULL,
    tipo_transacao VARCHAR(10) NOT NULL CHECK (tipo_transacao IN ('GASTO', 'RECEITA')),
    id_tag INTEGER NOT NULL,
    ativo BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (id_tag) REFERENCES tags(id_tag) ON DELETE CASCADE,
    UNIQUE(id_transacao, tipo_transacao, id_tag)
);

CREATE INDEX IF NOT EXISTS idx_transacao_tag_transacao ON transacao_tag(id_transacao, tipo_transacao);
CREATE INDEX IF NOT EXISTS idx_transacao_tag_tag ON transacao_tag(id_tag);
CREATE INDEX IF NOT EXISTS idx_transacao_tag_ativo ON transacao_tag(ativo);
-- Índice composto para otimizar JOINs com transacao_tag (buscar gastos/receitas por tag)
CREATE INDEX IF NOT EXISTS idx_transacao_tag_tag_tipo_ativo ON transacao_tag(id_tag, tipo_transacao, ativo) WHERE ativo = TRUE;
-- Índice composto para otimizar JOINs reversos (buscar tags de uma transação)
CREATE INDEX IF NOT EXISTS idx_transacao_tag_transacao_tipo_ativo ON transacao_tag(id_transacao, tipo_transacao, ativo) WHERE ativo = TRUE;

-- =====================================================
-- TABELA PARA ATRIBUTO MULTIVALORADO (OBSERVAÇÕES)
-- =====================================================

-- Tabela: gasto_observacoes
CREATE TABLE IF NOT EXISTS gasto_observacoes (
    id_observacao SERIAL PRIMARY KEY,
    id_gasto INTEGER NOT NULL,
    observacao TEXT NOT NULL,
    ordem INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (id_gasto) REFERENCES gastos(id_gasto) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_gasto_observacoes_gasto ON gasto_observacoes(id_gasto);

-- Tabela: compound_interest_calculations
CREATE TABLE IF NOT EXISTS compound_interest_calculations (
    id_calculo SERIAL PRIMARY KEY,
    id_usuario INTEGER NOT NULL,
    aporte_inicial DECIMAL(15,2) NOT NULL DEFAULT 0,
    aporte_mensal DECIMAL(15,2) NOT NULL DEFAULT 0,
    frequencia_aporte VARCHAR(20) NOT NULL, -- mensal, quinzenal, anual
    taxa_juros DECIMAL(10,4) NOT NULL,
    tipo_taxa VARCHAR(20) NOT NULL, -- mensal, anual
    prazo INTEGER NOT NULL,
    tipo_prazo VARCHAR(20) NOT NULL, -- meses, anos
    total_investido DECIMAL(15,2) NOT NULL,
    saldo_final DECIMAL(15,2) NOT NULL,
    total_juros DECIMAL(15,2) NOT NULL,
    monthly_data JSONB,
    data_calculo TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ativo BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (id_usuario) REFERENCES usuarios(id_usuario) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_compound_interest_usuario ON compound_interest_calculations(id_usuario);
CREATE INDEX IF NOT EXISTS idx_compound_interest_ativo ON compound_interest_calculations(ativo);
CREATE INDEX IF NOT EXISTS idx_compound_interest_data ON compound_interest_calculations(data_calculo);

-- =====================================================
-- TRIGGERS PARA UPDATED_AT
-- =====================================================

-- Função para atualizar updated_at automaticamente
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Triggers para cada tabela
CREATE TRIGGER update_usuarios_updated_at BEFORE UPDATE ON usuarios
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_categorias_updated_at BEFORE UPDATE ON categorias
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_contas_updated_at BEFORE UPDATE ON contas
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_gastos_updated_at BEFORE UPDATE ON gastos
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_receitas_updated_at BEFORE UPDATE ON receitas
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_orcamentos_updated_at BEFORE UPDATE ON orcamentos
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_tags_updated_at BEFORE UPDATE ON tags
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_investimentos_updated_at BEFORE UPDATE ON investimentos
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_compound_interest_updated_at BEFORE UPDATE ON compound_interest_calculations
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- =====================================================
-- VIEWS ÚTEIS (OPCIONAL)
-- =====================================================

-- View: gastos_com_categorias
CREATE OR REPLACE VIEW gastos_com_categorias AS
SELECT 
    g.*,
    COALESCE(
        STRING_AGG(DISTINCT c.nome, ', ' ORDER BY c.nome),
        ''
    ) as categorias
FROM gastos g
LEFT JOIN categoria_gasto cg ON g.id_gasto = cg.id_gasto AND cg.ativo = TRUE
LEFT JOIN categorias c ON cg.id_categoria = c.id_categoria AND c.ativo = TRUE
WHERE g.ativo = TRUE
GROUP BY g.id_gasto;

-- View: gastos_com_tags
CREATE OR REPLACE VIEW gastos_com_tags AS
SELECT 
    g.*,
    COALESCE(
        STRING_AGG(DISTINCT t.nome, ', ' ORDER BY t.nome),
        ''
    ) as tags
FROM gastos g
LEFT JOIN transacao_tag tt ON g.id_gasto = tt.id_transacao 
    AND tt.tipo_transacao = 'GASTO' AND tt.ativo = TRUE
LEFT JOIN tags t ON tt.id_tag = t.id_tag AND t.ativo = TRUE
WHERE g.ativo = TRUE
GROUP BY g.id_gasto;

-- =====================================================
-- COMENTÁRIOS NAS TABELAS (DOCUMENTAÇÃO)
-- =====================================================

COMMENT ON TABLE usuarios IS 'Usuários do sistema Controle-se';
COMMENT ON TABLE categorias IS 'Categorias de gastos e orçamentos';
COMMENT ON TABLE contas IS 'Contas bancárias e financeiras';
COMMENT ON TABLE gastos IS 'Despesas registradas pelos usuários';
COMMENT ON TABLE receitas IS 'Receitas registradas pelos usuários';
COMMENT ON TABLE orcamentos IS 'Orçamentos planejados por categoria';
COMMENT ON TABLE tags IS 'Tags para classificação de transações';
COMMENT ON TABLE investimentos IS 'Investimentos financeiros';
COMMENT ON TABLE categoria_gasto IS 'Relacionamento N:N entre categorias e gastos';
COMMENT ON TABLE transacao_tag IS 'Relacionamento N:N entre transações (gastos/receitas) e tags';
COMMENT ON TABLE gasto_observacoes IS 'Observações multivaloradas dos gastos';
COMMENT ON TABLE compound_interest_calculations IS 'Cálculos de juros compostos salvos pelos usuários';

