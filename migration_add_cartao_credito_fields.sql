-- =====================================================
-- MIGRAÇÃO: Adicionar campos de cartão de crédito
-- =====================================================
-- Este script adiciona os campos dia_fechamento e dia_pagamento
-- à tabela contas para suportar cartões de crédito

-- Adiciona colunas se não existirem
DO $$ 
BEGIN
    -- Adiciona coluna dia_fechamento se não existir
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'contas' AND column_name = 'dia_fechamento'
    ) THEN
        ALTER TABLE contas ADD COLUMN dia_fechamento INTEGER;
    END IF;
    
    -- Adiciona coluna dia_pagamento se não existir
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'contas' AND column_name = 'dia_pagamento'
    ) THEN
        ALTER TABLE contas ADD COLUMN dia_pagamento INTEGER;
    END IF;
END $$;

-- Comentários para documentação
COMMENT ON COLUMN contas.dia_fechamento IS 'Dia do mês em que a fatura do cartão de crédito fecha (1-31)';
COMMENT ON COLUMN contas.dia_pagamento IS 'Dia do mês em que a fatura do cartão de crédito deve ser paga (1-31)';

