package server.services;

import server.model.*;
import server.repository.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Serviço para processar recorrências automáticas de gastos e receitas
 */
public class RecurrenceService {
    private static final Logger LOGGER = Logger.getLogger(RecurrenceService.class.getName());
    private final ExpenseRepository expenseRepository;
    private final IncomeRepository incomeRepository;
    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;
    
    public RecurrenceService() {
        this.expenseRepository = new ExpenseRepository();
        this.incomeRepository = new IncomeRepository();
        this.categoryRepository = new CategoryRepository();
        this.tagRepository = new TagRepository();
    }
    
    /**
     * Processa todas as recorrências pendentes (gastos e receitas)
     * @return Número total de transações criadas
     */
    public int processarRecorrencias() {
        int totalCriadas = 0;
        LocalDate hoje = LocalDate.now();
        
        LOGGER.info("[RECORRÊNCIAS] Processando recorrências para " + hoje);
        
        // Processa gastos recorrentes
        totalCriadas += processarGastosRecorrentes(hoje);
        
        // Processa receitas recorrentes
        totalCriadas += processarReceitasRecorrentes(hoje);
        
        if (totalCriadas > 0) {
            LOGGER.info("[RECORRÊNCIAS] Total de " + totalCriadas + " transações criadas automaticamente");
        } else {
            LOGGER.info("[RECORRÊNCIAS] Nenhuma recorrência pendente para hoje");
        }
        
        return totalCriadas;
    }
    
    private int processarGastosRecorrentes(LocalDate hoje) {
        // Busca gastos com recorrência pendente
        List<Gasto> gastosRecorrentes = expenseRepository.buscarGastosComRecorrenciaPendente(hoje);
        int criados = 0;
        
        for (Gasto gastoOriginal : gastosRecorrentes) {
            try {
                LOGGER.info("[RECORRÊNCIAS] Criando recorrência de gasto: " + gastoOriginal.getDescricao());
                
                // Busca categorias do gasto original
                List<Categoria> categorias = categoryRepository.buscarCategoriasDoGasto(gastoOriginal.getIdGasto());
                List<Integer> idsCategorias = new ArrayList<>();
                for (Categoria cat : categorias) {
                    idsCategorias.add(cat.getIdCategoria());
                }
                
                // Cria novo gasto com a data de recorrência
                int novoIdGasto = expenseRepository.cadastrarGasto(
                    gastoOriginal.getDescricao() + " (Recorrência)",
                    gastoOriginal.getValor(),
                    gastoOriginal.getProximaRecorrencia(),
                    gastoOriginal.getFrequencia(),
                    gastoOriginal.getIdUsuario(),
                    idsCategorias,
                    gastoOriginal.getIdConta(),
                    gastoOriginal.getObservacoes()
                );
                
                // Marca como recorrência do original
                expenseRepository.marcarComoRecorrencia(novoIdGasto, gastoOriginal.getIdGasto());
                
                // Copia tags do gasto original
                List<Tag> tags = tagRepository.buscarTagsGasto(gastoOriginal.getIdGasto());
                for (Tag tag : tags) {
                    tagRepository.associarTagTransacao(novoIdGasto, "GASTO", tag.getIdTag());
                }
                
                // Atualiza próxima recorrência
                LocalDate novaRecorrencia = expenseRepository.calcularProximaRecorrencia(
                    gastoOriginal.getProximaRecorrencia(),
                    gastoOriginal.getFrequencia()
                );
                expenseRepository.atualizarProximaRecorrencia(gastoOriginal.getIdGasto(), novaRecorrencia);
                
                criados++;
            } catch (Exception e) {
                LOGGER.warning("Erro ao processar recorrência de gasto ID " + gastoOriginal.getIdGasto() + ": " + e.getMessage());
            }
        }
        
        return criados;
    }
    
    private int processarReceitasRecorrentes(LocalDate hoje) {
        // Busca receitas com recorrência pendente
        List<Receita> receitasRecorrentes = incomeRepository.buscarReceitasComRecorrenciaPendente(hoje);
        int criados = 0;
        
        for (Receita receitaOriginal : receitasRecorrentes) {
            try {
                LOGGER.info("[RECORRÊNCIAS] Criando recorrência de receita: " + receitaOriginal.getDescricao());
                
                // Cria nova receita com a data de recorrência
                int novoIdReceita = incomeRepository.cadastrarReceita(
                    receitaOriginal.getDescricao() + " (Recorrência)",
                    receitaOriginal.getValor(),
                    receitaOriginal.getProximaRecorrencia(),
                    receitaOriginal.getIdUsuario(),
                    receitaOriginal.getIdConta(),
                    receitaOriginal.getObservacoes()
                );
                
                // Marca como recorrência da original
                incomeRepository.marcarComoRecorrencia(novoIdReceita, receitaOriginal.getIdReceita(), 
                    receitaOriginal.getFrequencia(), receitaOriginal.getProximaRecorrencia());
                
                // Copia tags da receita original
                List<Tag> tags = tagRepository.buscarTagsReceita(receitaOriginal.getIdReceita());
                for (Tag tag : tags) {
                    tagRepository.associarTagTransacao(novoIdReceita, "RECEITA", tag.getIdTag());
                }
                
                // Atualiza próxima recorrência
                LocalDate novaRecorrencia = incomeRepository.calcularProximaRecorrencia(
                    receitaOriginal.getProximaRecorrencia(),
                    receitaOriginal.getFrequencia()
                );
                incomeRepository.atualizarProximaRecorrencia(receitaOriginal.getIdReceita(), novaRecorrencia);
                
                criados++;
            } catch (Exception e) {
                LOGGER.warning("Erro ao processar recorrência de receita ID " + receitaOriginal.getIdReceita() + ": " + e.getMessage());
            }
        }
        
        return criados;
    }
}

