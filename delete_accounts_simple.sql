-- Script SQL SIMPLIFICADO para excluir contas (91 e 90)
-- Execute os comandos um por vez, sem transação

-- 1. Marca como inativo todas as receitas ativas
UPDATE receitas 
SET ativo = FALSE 
WHERE id_conta IN (91, 90) AND ativo = TRUE;

-- 2. Marca como inativo todos os gastos ativos
UPDATE gastos 
SET ativo = FALSE 
WHERE id_conta IN (91, 90) AND ativo = TRUE;

-- 3. Marca como inativo todos os investimentos ativos
UPDATE investimentos 
SET ativo = FALSE 
WHERE id_conta IN (91, 90) AND ativo = TRUE;

-- 4. Marca como inativo todos os grupos de parcelas ativos (se a tabela existir)
UPDATE installment_groups 
SET ativo = FALSE 
WHERE id_conta IN (91, 90) AND ativo = TRUE;

-- 5. Agora pode excluir as contas
DELETE FROM contas WHERE id_conta IN (91, 90);

