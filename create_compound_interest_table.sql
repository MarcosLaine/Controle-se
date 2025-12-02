-- Script para criar a tabela compound_interest_calculations
-- Execute este script no seu banco de dados PostgreSQL se a tabela não existir

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

-- Índices
CREATE INDEX IF NOT EXISTS idx_compound_interest_usuario ON compound_interest_calculations(id_usuario);
CREATE INDEX IF NOT EXISTS idx_compound_interest_ativo ON compound_interest_calculations(ativo);
CREATE INDEX IF NOT EXISTS idx_compound_interest_data ON compound_interest_calculations(data_calculo);

-- Trigger para updated_at
CREATE TRIGGER update_compound_interest_updated_at BEFORE UPDATE ON compound_interest_calculations
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Comentário
COMMENT ON TABLE compound_interest_calculations IS 'Cálculos de juros compostos salvos pelos usuários';


