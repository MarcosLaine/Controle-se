-- =====================================================
-- MIGRAÇÃO: Adicionar índices para otimização de performance
-- =====================================================
-- Este script adiciona índices compostos para melhorar a performance
-- das queries mais comuns do sistema

-- =====================================================
-- ÍNDICES PARA GASTOS
-- =====================================================

-- Índice composto para otimizar query agregada de gastos por categoria
CREATE INDEX IF NOT EXISTS idx_categoria_gasto_composto 
ON categoria_gasto(id_categoria, id_gasto, ativo) 
WHERE ativo = TRUE;

-- Índice composto para otimizar queries de gastos por usuário e ativo
CREATE INDEX IF NOT EXISTS idx_gastos_usuario_ativo 
ON gastos(id_usuario, ativo) 
WHERE ativo = TRUE;

-- Índice composto para queries de gastos por usuário, ativo e data (ORDER BY data DESC)
CREATE INDEX IF NOT EXISTS idx_gastos_usuario_ativo_data 
ON gastos(id_usuario, ativo, data DESC) 
WHERE ativo = TRUE;

-- Índice para queries de gastos por data específica
CREATE INDEX IF NOT EXISTS idx_gastos_data_ativo 
ON gastos(data, ativo) 
WHERE ativo = TRUE;

-- Índice para queries de recorrência de gastos
CREATE INDEX IF NOT EXISTS idx_gastos_recorrencia_ativo 
ON gastos(proxima_recorrencia, ativo) 
WHERE proxima_recorrencia IS NOT NULL AND ativo = TRUE;

-- Índice composto para JOINs com categoria_gasto (buscar gastos por categoria)
CREATE INDEX IF NOT EXISTS idx_categoria_gasto_gasto_ativo 
ON categoria_gasto(id_gasto, ativo) 
WHERE ativo = TRUE;

-- =====================================================
-- ÍNDICES PARA RECEITAS
-- =====================================================

-- Índice composto para otimizar queries de receitas por usuário e ativo
CREATE INDEX IF NOT EXISTS idx_receitas_usuario_ativo 
ON receitas(id_usuario, ativo) 
WHERE ativo = TRUE;

-- Índice composto para queries de receitas por usuário, ativo e data (ORDER BY data DESC)
CREATE INDEX IF NOT EXISTS idx_receitas_usuario_ativo_data 
ON receitas(id_usuario, ativo, data DESC) 
WHERE ativo = TRUE;

-- Índice para queries de receitas por data específica
CREATE INDEX IF NOT EXISTS idx_receitas_data_ativo 
ON receitas(data, ativo) 
WHERE ativo = TRUE;

-- Índice para queries de recorrência de receitas
CREATE INDEX IF NOT EXISTS idx_receitas_recorrencia_ativo 
ON receitas(proxima_recorrencia, ativo) 
WHERE proxima_recorrencia IS NOT NULL AND ativo = TRUE;

-- =====================================================
-- ÍNDICES PARA CONTAS
-- =====================================================

-- Índice composto para otimizar queries de contas por usuário, tipo e ativo
CREATE INDEX IF NOT EXISTS idx_contas_usuario_tipo_ativo 
ON contas(id_usuario, tipo, ativo) 
WHERE ativo = TRUE;

-- =====================================================
-- ÍNDICES PARA INVESTIMENTOS
-- =====================================================

-- Índice composto para queries de investimentos por usuário, ativo e data_aporte (ORDER BY data_aporte DESC)
CREATE INDEX IF NOT EXISTS idx_investimentos_usuario_ativo_data 
ON investimentos(id_usuario, ativo, data_aporte DESC) 
WHERE ativo = TRUE;

-- =====================================================
-- ÍNDICES PARA TRANSAÇÃO_TAG (JOINs)
-- =====================================================

-- Índice composto para otimizar JOINs com transacao_tag (buscar gastos/receitas por tag)
CREATE INDEX IF NOT EXISTS idx_transacao_tag_tag_tipo_ativo 
ON transacao_tag(id_tag, tipo_transacao, ativo) 
WHERE ativo = TRUE;

-- Índice composto para otimizar JOINs reversos (buscar tags de uma transação)
CREATE INDEX IF NOT EXISTS idx_transacao_tag_transacao_tipo_ativo 
ON transacao_tag(id_transacao, tipo_transacao, ativo) 
WHERE ativo = TRUE;

-- =====================================================
-- ÍNDICES PARA ORÇAMENTOS
-- =====================================================

-- Índice composto para queries de orçamentos por categoria e ativo
CREATE INDEX IF NOT EXISTS idx_orcamentos_categoria_ativo 
ON orcamentos(id_categoria, ativo) 
WHERE ativo = TRUE;

-- =====================================================
-- COMENTÁRIOS PARA DOCUMENTAÇÃO
-- =====================================================

COMMENT ON INDEX idx_categoria_gasto_composto IS 'Otimiza query agregada de gastos por categoria';
COMMENT ON INDEX idx_gastos_usuario_ativo IS 'Otimiza queries de gastos filtradas por usuário e ativo';
COMMENT ON INDEX idx_gastos_usuario_ativo_data IS 'Otimiza queries de gastos com ORDER BY data DESC';
COMMENT ON INDEX idx_gastos_data_ativo IS 'Otimiza queries de gastos por data específica';
COMMENT ON INDEX idx_gastos_recorrencia_ativo IS 'Otimiza queries de recorrência de gastos';
COMMENT ON INDEX idx_receitas_usuario_ativo IS 'Otimiza queries de receitas filtradas por usuário e ativo';
COMMENT ON INDEX idx_receitas_usuario_ativo_data IS 'Otimiza queries de receitas com ORDER BY data DESC';
COMMENT ON INDEX idx_receitas_data_ativo IS 'Otimiza queries de receitas por data específica';
COMMENT ON INDEX idx_receitas_recorrencia_ativo IS 'Otimiza queries de recorrência de receitas';
COMMENT ON INDEX idx_contas_usuario_tipo_ativo IS 'Otimiza queries de contas filtradas por usuário, tipo e ativo';
COMMENT ON INDEX idx_investimentos_usuario_ativo_data IS 'Otimiza queries de investimentos com ORDER BY data_aporte DESC';
COMMENT ON INDEX idx_transacao_tag_tag_tipo_ativo IS 'Otimiza JOINs para buscar transações por tag';
COMMENT ON INDEX idx_transacao_tag_transacao_tipo_ativo IS 'Otimiza JOINs para buscar tags de uma transação';
COMMENT ON INDEX idx_orcamentos_categoria_ativo IS 'Otimiza queries de orçamentos por categoria';
