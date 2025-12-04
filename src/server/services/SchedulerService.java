package server.services;

import java.util.Calendar;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Logger;

/**
 * Serviço para gerenciar tarefas agendadas (schedulers)
 */
public class SchedulerService {
    private static final Logger LOGGER = Logger.getLogger(SchedulerService.class.getName());
    
    public SchedulerService() {
    }
    
    /**
     * Inicia o scheduler que processa recorrências automáticas
     * Executa diariamente às 00:05 da manhã
     */
    public void iniciarSchedulerRecorrencias() {
        Timer timer = new Timer("RecorrenciasScheduler", true); // daemon=true para não bloquear shutdown
        
        // Calcula o delay até a próxima execução (hoje às 00:05 ou amanhã às 00:05)
        Calendar proximaExecucao = Calendar.getInstance();
        proximaExecucao.set(Calendar.HOUR_OF_DAY, 0);
        proximaExecucao.set(Calendar.MINUTE, 5);
        proximaExecucao.set(Calendar.SECOND, 0);
        proximaExecucao.set(Calendar.MILLISECOND, 0);
        
        // Se já passou da hora hoje, agenda para amanhã
        if (proximaExecucao.getTimeInMillis() < System.currentTimeMillis()) {
            proximaExecucao.add(Calendar.DAY_OF_MONTH, 1);
        }
        
        long delay = proximaExecucao.getTimeInMillis() - System.currentTimeMillis();
        long periodo = 24 * 60 * 60 * 1000; // 24 horas em milissegundos
        
        // Agenda a tarefa para executar diariamente
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    // LOGGER.info("=== SCHEDULER DE RECORRÊNCIAS === Executando processamento automático...");
                    RecurrenceService recurrenceService = new RecurrenceService();
                    int criados = recurrenceService.processarRecorrencias();
                    // LOGGER.info("Total de transações recorrentes criadas: " + criados);
                } catch (Exception e) {
                    System.err.println("Erro ao processar recorrências: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }, delay, periodo);
        
        // Para fins de DEBUG/TESTE: executa imediatamente na inicialização também
        // LOGGER.info("[INICIALIZAÇÃO] Processando recorrências pendentes...");
        try {
            RecurrenceService recurrenceService = new RecurrenceService();
            int criados = recurrenceService.processarRecorrencias();
            if (criados > 0) {
                // LOGGER.info("[INICIALIZAÇÃO] " + criados + " transações recorrentes criadas");
            } else {
                // LOGGER.info("[INICIALIZAÇÃO] Nenhuma recorrência pendente");
            }
        } catch (Exception e) {
            System.err.println("[INICIALIZAÇÃO] Erro ao processar recorrências: " + e.getMessage());
        }
        // LOGGER.fine("Scheduler de recorrências inicializado");
    }
    
    /**
     * Inicia o scheduler que atualiza cotações de investimentos
     * Executa a cada 30 minutos
     */
    public void iniciarSchedulerCotacoes() {
        Timer timer = new Timer("CotacoesScheduler", true); // daemon=true para não bloquear shutdown
        
        // Executa imediatamente e depois a cada 30 minutos
        long periodo = 30 * 60 * 1000; // 30 minutos em milissegundos
        
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    // LOGGER.info("=== ATUALIZAÇÃO DE COTAÇÕES ===");
                    QuoteService quoteService = QuoteService.getInstance();
                    quoteService.cleanExpiredCache();
                    // LOGGER.info("Cache de cotações limpo e atualizado");
                } catch (Exception e) {
                    System.err.println("Erro ao atualizar cotações: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }, 0, periodo);
        
        // LOGGER.info("[INICIALIZAÇÃO] Scheduler de cotações iniciado (atualiza a cada 30 minutos)");
    }
}

