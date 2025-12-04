-- Script SQL para excluir contas (91 e 90) - VERSÃO DEFINITIVA
-- Este script deleta fisicamente as receitas e gastos que referenciam essas contas
-- antes de excluir as contas

-- PASSO 1: Finaliza qualquer transação abortada
ROLLBACK;

-- PASSO 2: Verifica o que será excluído (opcional - para conferência)
SELECT 'Receitas a serem excluídas:', COUNT(*) FROM receitas WHERE id_conta IN (91, 90);
SELECT 'Gastos a serem excluídos:', COUNT(*) FROM gastos WHERE id_conta IN (91, 90);
SELECT 'Investimentos a serem excluídos:', COUNT(*) FROM investimentos WHERE id_conta IN (91, 90);

-- PASSO 3: Inicia nova transação
BEGIN;

-- PASSO 4: Deleta FISICAMENTE todas as receitas que referenciam essas contas
-- (tanto ativas quanto inativas)
DELETE FROM receitas WHERE id_conta IN (91, 90);

-- PASSO 5: Deleta FISICAMENTE todos os gastos que referenciam essas contas
-- (tanto ativos quanto inativos)
DELETE FROM gastos WHERE id_conta IN (91, 90);

-- PASSO 6: Marca como inativo todos os investimentos que referenciam essas contas
-- (ou deleta fisicamente se preferir)
UPDATE investimentos 
SET ativo = FALSE 
WHERE id_conta IN (91, 90) AND ativo = TRUE;

-- Alternativa: Deletar investimentos fisicamente (descomente se preferir)
-- DELETE FROM investimentos WHERE id_conta IN (91, 90);

-- PASSO 7: Deleta FISICAMENTE todos os grupos de parcelas que referenciam essas contas
-- (tanto ativos quanto inativos)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'installment_groups') THEN
        DELETE FROM installment_groups WHERE id_conta IN (91, 90);
    END IF;
EXCEPTION
    WHEN OTHERS THEN
        NULL;
END $$;

-- PASSO 8: Agora pode excluir as contas fisicamente
DELETE FROM contas WHERE id_conta IN (91, 90);

-- PASSO 9: Confirma as alterações
COMMIT;

-- PASSO 10: Verifica se foi excluído
SELECT 'Contas excluídas com sucesso!' as resultado;

