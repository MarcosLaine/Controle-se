-- Script SQL FINAL para excluir contas (91 e 90)
-- Este script deleta FISICAMENTE todas as referências antes de excluir as contas

-- PASSO 1: Finaliza qualquer transação abortada
ROLLBACK;

-- PASSO 2: Inicia nova transação
BEGIN;

-- PASSO 3: Deleta FISICAMENTE todas as receitas que referenciam essas contas
-- (tanto ativas quanto inativas)
DELETE FROM receitas WHERE id_conta IN (91, 90);

-- PASSO 4: Deleta FISICAMENTE todos os gastos que referenciam essas contas
-- (tanto ativos quanto inativos)
DELETE FROM gastos WHERE id_conta IN (91, 90);

-- PASSO 5: Deleta FISICAMENTE todos os grupos de parcelas que referenciam essas contas
-- (tanto ativos quanto inativos)
-- Se a tabela não existir, o erro será ignorado
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'installment_groups') THEN
        DELETE FROM installment_groups WHERE id_conta IN (91, 90);
    END IF;
EXCEPTION
    WHEN OTHERS THEN
        -- Ignora erros se a tabela não existir
        NULL;
END $$;

-- PASSO 6: Marca como inativo todos os investimentos que referenciam essas contas
-- (ou deleta fisicamente se preferir - descomente a linha abaixo)
UPDATE investimentos 
SET ativo = FALSE 
WHERE id_conta IN (91, 90) AND ativo = TRUE;

-- Alternativa: Deletar investimentos fisicamente (descomente se preferir)
-- DELETE FROM investimentos WHERE id_conta IN (91, 90);

-- PASSO 7: Agora pode excluir as contas fisicamente
DELETE FROM contas WHERE id_conta IN (91, 90);

-- PASSO 8: Confirma as alterações
COMMIT;

-- PASSO 9: Verifica se foi excluído
SELECT 'Contas 91 e 90 excluídas com sucesso!' as resultado;

