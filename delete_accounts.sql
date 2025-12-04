-- =====================================================
-- Script SQL para excluir contas (91 e 90) e todas as suas referências
-- =====================================================
-- IMPORTANTE: Se você recebeu erro de transação abortada,
-- execute primeiro: ROLLBACK;
-- Depois execute este script completo

-- PASSO 1: Se houver transação abortada, faça rollback primeiro
ROLLBACK;

-- PASSO 2: Inicia nova transação
BEGIN;

-- PASSO 3: Marca como inativo todas as receitas ativas que referenciam essas contas
UPDATE receitas 
SET ativo = FALSE 
WHERE id_conta IN (91, 90) AND ativo = TRUE;

-- PASSO 4: Marca como inativo todos os gastos ativos que referenciam essas contas
UPDATE gastos 
SET ativo = FALSE 
WHERE id_conta IN (91, 90) AND ativo = TRUE;

-- PASSO 5: Marca como inativo todos os investimentos ativos que referenciam essas contas
UPDATE investimentos 
SET ativo = FALSE 
WHERE id_conta IN (91, 90) AND ativo = TRUE;

-- PASSO 6: Marca como inativo todos os grupos de parcelas ativos que referenciam essas contas
-- (Esta tabela pode não existir em instalações antigas)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'installment_groups') THEN
        UPDATE installment_groups 
        SET ativo = FALSE 
        WHERE id_conta IN (91, 90) AND ativo = TRUE;
    END IF;
EXCEPTION
    WHEN OTHERS THEN
        -- Ignora erros se a tabela não existir ou tiver problemas
        NULL;
END $$;

-- PASSO 7: Agora pode excluir as contas fisicamente
DELETE FROM contas WHERE id_conta IN (91, 90);

-- PASSO 8: Confirma as alterações
COMMIT;

-- Verifica se tudo foi excluído corretamente
SELECT 'Contas excluídas com sucesso!' as resultado;

