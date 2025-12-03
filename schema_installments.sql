-- Script para adicionar suporte a compras parceladas
-- Execute este script no seu banco de dados PostgreSQL

-- =====================================================
-- TABELA: installment_groups
-- Armazena informações sobre grupos de parcelas
-- =====================================================

CREATE TABLE IF NOT EXISTS installment_groups (
    id_grupo SERIAL PRIMARY KEY,
    descricao VARCHAR(500) NOT NULL,
    valor_total DECIMAL(15,2) NOT NULL,
    numero_parcelas INTEGER NOT NULL CHECK (numero_parcelas > 0),
    valor_parcela DECIMAL(15,2) NOT NULL,
    data_primeira_parcela DATE NOT NULL,
    intervalo_dias INTEGER DEFAULT 30 CHECK (intervalo_dias > 0),
    id_usuario INTEGER NOT NULL,
    id_conta INTEGER NOT NULL,
    tipo_transacao VARCHAR(10) NOT NULL CHECK (tipo_transacao IN ('GASTO', 'RECEITA')),
    ativo BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (id_usuario) REFERENCES usuarios(id_usuario) ON DELETE CASCADE,
    FOREIGN KEY (id_conta) REFERENCES contas(id_conta) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_installment_groups_usuario ON installment_groups(id_usuario);
CREATE INDEX IF NOT EXISTS idx_installment_groups_conta ON installment_groups(id_conta);
CREATE INDEX IF NOT EXISTS idx_installment_groups_ativo ON installment_groups(ativo);
CREATE INDEX IF NOT EXISTS idx_installment_groups_tipo ON installment_groups(tipo_transacao);

-- =====================================================
-- MODIFICAÇÕES NAS TABELAS EXISTENTES
-- =====================================================

-- Adicionar colunas de parcela na tabela gastos
ALTER TABLE gastos 
ADD COLUMN IF NOT EXISTS id_grupo_parcela INTEGER,
ADD COLUMN IF NOT EXISTS numero_parcela INTEGER,
ADD COLUMN IF NOT EXISTS total_parcelas INTEGER;

-- Adicionar foreign key se não existir
DO $$ 
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint 
        WHERE conname = 'gastos_id_grupo_parcela_fkey'
    ) THEN
        ALTER TABLE gastos 
        ADD CONSTRAINT gastos_id_grupo_parcela_fkey 
        FOREIGN KEY (id_grupo_parcela) 
        REFERENCES installment_groups(id_grupo) 
        ON DELETE SET NULL;
    END IF;
END $$;

-- Adicionar índices para otimizar queries de parcelas
CREATE INDEX IF NOT EXISTS idx_gastos_grupo_parcela ON gastos(id_grupo_parcela) WHERE id_grupo_parcela IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_gastos_numero_parcela ON gastos(numero_parcela) WHERE numero_parcela IS NOT NULL;

-- Adicionar colunas de parcela na tabela receitas
ALTER TABLE receitas 
ADD COLUMN IF NOT EXISTS id_grupo_parcela INTEGER,
ADD COLUMN IF NOT EXISTS numero_parcela INTEGER,
ADD COLUMN IF NOT EXISTS total_parcelas INTEGER;

-- Adicionar foreign key se não existir
DO $$ 
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint 
        WHERE conname = 'receitas_id_grupo_parcela_fkey'
    ) THEN
        ALTER TABLE receitas 
        ADD CONSTRAINT receitas_id_grupo_parcela_fkey 
        FOREIGN KEY (id_grupo_parcela) 
        REFERENCES installment_groups(id_grupo) 
        ON DELETE SET NULL;
    END IF;
END $$;

-- Adicionar índices para otimizar queries de parcelas
CREATE INDEX IF NOT EXISTS idx_receitas_grupo_parcela ON receitas(id_grupo_parcela) WHERE id_grupo_parcela IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_receitas_numero_parcela ON receitas(numero_parcela) WHERE numero_parcela IS NOT NULL;

-- Trigger para atualizar updated_at em installment_groups
CREATE OR REPLACE FUNCTION update_installment_groups_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS update_installment_groups_updated_at ON installment_groups;
CREATE TRIGGER update_installment_groups_updated_at 
    BEFORE UPDATE ON installment_groups
    FOR EACH ROW 
    EXECUTE FUNCTION update_installment_groups_updated_at();

-- Comentários
COMMENT ON TABLE installment_groups IS 'Grupos de parcelas para compras parceladas ou receitas parceladas';
COMMENT ON COLUMN installment_groups.intervalo_dias IS 'Intervalo em dias entre cada parcela (ex: 30 para mensal)';
COMMENT ON COLUMN gastos.id_grupo_parcela IS 'Referência ao grupo de parcelas (NULL se não for parcela)';
COMMENT ON COLUMN gastos.numero_parcela IS 'Número da parcela (1, 2, 3, ...)';
COMMENT ON COLUMN gastos.total_parcelas IS 'Total de parcelas do grupo';
COMMENT ON COLUMN receitas.id_grupo_parcela IS 'Referência ao grupo de parcelas (NULL se não for parcela)';
COMMENT ON COLUMN receitas.numero_parcela IS 'Número da parcela (1, 2, 3, ...)';
COMMENT ON COLUMN receitas.total_parcelas IS 'Total de parcelas do grupo';

