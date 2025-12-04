-- =====================================================
-- SCRIPT PARA REMOVER TODOS OS DADOS DO USUÁRIO ID 6
-- =====================================================
-- ATENÇÃO: Esta operação é IRREVERSÍVEL!
-- Execute com cuidado e faça backup antes se necessário.
-- =====================================================

BEGIN;

-- Remover dados de tabelas relacionadas que podem ter RESTRICT
-- (gastos, receitas, investimentos referenciam contas com RESTRICT)

-- 1. Remover gastos do usuário 6
DELETE FROM gastos WHERE id_usuario = 6;

-- 2. Remover receitas do usuário 6
DELETE FROM receitas WHERE id_usuario = 6;

-- 3. Remover investimentos do usuário 6
DELETE FROM investimentos WHERE id_usuario = 6;

-- 4. Remover grupos de parcelas do usuário 6
DELETE FROM installment_groups WHERE id_usuario = 6;

-- 5. Remover orçamentos do usuário 6 (tem CASCADE, mas removendo explicitamente)
DELETE FROM orcamentos WHERE id_usuario = 6;

-- 6. Remover tags do usuário 6 (tem CASCADE, mas removendo explicitamente)
DELETE FROM tags WHERE id_usuario = 6;

-- 7. Remover categorias do usuário 6 (tem CASCADE, mas removendo explicitamente)
DELETE FROM categorias WHERE id_usuario = 6;

-- 8. Remover contas do usuário 6 (tem CASCADE, mas removendo explicitamente)
DELETE FROM contas WHERE id_usuario = 6;

-- 9. Remover cálculos de juros compostos do usuário 6
DELETE FROM compound_interest_calculations WHERE id_usuario = 6;

-- 10. Por fim, remover o próprio usuário 6
DELETE FROM usuarios WHERE id_usuario = 6;

COMMIT;

-- =====================================================
-- NOTA: As tabelas categoria_gasto, transacao_tag e 
-- gasto_observacoes serão removidas automaticamente
-- devido às foreign keys com ON DELETE CASCADE
-- =====================================================

