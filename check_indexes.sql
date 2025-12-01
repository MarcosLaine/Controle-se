-- =====================================================
-- QUERY PARA VERIFICAR ÍNDICES NO BANCO DE DADOS
-- =====================================================
-- Execute esta query na sua ferramenta de banco de dados
-- para verificar se todos os índices foram criados

-- Lista todos os índices do schema public
SELECT 
    schemaname,
    tablename,
    indexname,
    indexdef
FROM pg_indexes
WHERE schemaname = 'public'
ORDER BY tablename, indexname;

-- Contagem de índices por tabela
SELECT 
    tablename,
    COUNT(*) as total_indices
FROM pg_indexes
WHERE schemaname = 'public'
GROUP BY tablename
ORDER BY tablename;

-- Verificar índices de performance específicos
SELECT 
    tablename,
    indexname,
    CASE 
        WHEN indexname LIKE 'idx_%_usuario_ativo_data%' THEN '✓ Índice de performance (ORDER BY data)'
        WHEN indexname LIKE 'idx_%_recorrencia%' THEN '✓ Índice de recorrência'
        WHEN indexname LIKE 'idx_%_tag%' THEN '✓ Índice de JOIN com tags'
        WHEN indexname LIKE 'idx_%_composto%' THEN '✓ Índice composto'
        ELSE 'Índice padrão'
    END as tipo_indice
FROM pg_indexes
WHERE schemaname = 'public'
    AND (
        indexname LIKE 'idx_%_usuario_ativo_data%'
        OR indexname LIKE 'idx_%_recorrencia%'
        OR indexname LIKE 'idx_%_tag%'
        OR indexname LIKE 'idx_%_composto%'
        OR indexname LIKE 'idx_%_categoria_ativo%'
    )
ORDER BY tablename, indexname;

