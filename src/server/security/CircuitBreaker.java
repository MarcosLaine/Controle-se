package server.security;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Circuit Breaker para proteção contra sobrecarga do sistema
 * Implementa padrão Circuit Breaker para detectar e prevenir falhas em cascata
 */
public class CircuitBreaker {
    private static final Logger LOGGER = Logger.getLogger(CircuitBreaker.class.getName());
    
    // Estados do circuit breaker
    public enum State {
        CLOSED,    // Normal, permitindo requisições
        OPEN,      // Bloqueado, rejeitando requisições
        HALF_OPEN  // Testando se o serviço recuperou
    }
    
    // Configurações padrão
    private static final int DEFAULT_FAILURE_THRESHOLD = 5; // Abre após 5 falhas
    private static final long DEFAULT_TIMEOUT_MS = 60 * 1000; // 1 minuto em estado OPEN
    private static final int DEFAULT_SUCCESS_THRESHOLD = 2; // Fecha após 2 sucessos em HALF_OPEN
    
    private final String name;
    private final int failureThreshold;
    private final long timeoutMs;
    private final int successThreshold;
    
    // Contadores atômicos para thread-safety
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicLong lastFailureTime = new AtomicLong(0);
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    
    public CircuitBreaker(String name) {
        this(name, DEFAULT_FAILURE_THRESHOLD, DEFAULT_TIMEOUT_MS, DEFAULT_SUCCESS_THRESHOLD);
    }
    
    public CircuitBreaker(String name, int failureThreshold, long timeoutMs, int successThreshold) {
        this.name = name;
        this.failureThreshold = failureThreshold;
        this.timeoutMs = timeoutMs;
        this.successThreshold = successThreshold;
    }
    
    /**
     * Verifica se uma requisição pode ser processada
     * @return true se permitido, false se bloqueado
     */
    public boolean allowRequest() {
        State currentState = state.get();
        long now = System.currentTimeMillis();
        
        switch (currentState) {
            case CLOSED:
                return true;
                
            case OPEN:
                // Verifica se passou tempo suficiente para tentar novamente
                if (now - lastFailureTime.get() >= timeoutMs) {
                    // Transiciona para HALF_OPEN
                    if (state.compareAndSet(State.OPEN, State.HALF_OPEN)) {
                        successCount.set(0);
                        LOGGER.info(String.format("Circuit Breaker '%s' transicionou para HALF_OPEN", name));
                    }
                    return true;
                }
                return false;
                
            case HALF_OPEN:
                return true;
                
            default:
                return false;
        }
    }
    
    /**
     * Registra uma requisição bem-sucedida
     */
    public void recordSuccess() {
        State currentState = state.get();
        
        if (currentState == State.HALF_OPEN) {
            int successes = successCount.incrementAndGet();
            if (successes >= successThreshold) {
                // Transiciona para CLOSED
                if (state.compareAndSet(State.HALF_OPEN, State.CLOSED)) {
                    failureCount.set(0);
                    successCount.set(0);
                    LOGGER.info(String.format("Circuit Breaker '%s' transicionou para CLOSED (recuperado)", name));
                }
            }
        } else if (currentState == State.CLOSED) {
            // Reset contador de falhas em caso de sucesso
            failureCount.set(0);
        }
    }
    
    /**
     * Registra uma requisição falha
     */
    public void recordFailure() {
        State currentState = state.get();
        long now = System.currentTimeMillis();
        
        if (currentState == State.HALF_OPEN) {
            // Qualquer falha em HALF_OPEN volta para OPEN
            if (state.compareAndSet(State.HALF_OPEN, State.OPEN)) {
                lastFailureTime.set(now);
                failureCount.set(1);
                successCount.set(0);
                LOGGER.warning(String.format("Circuit Breaker '%s' transicionou para OPEN (falha em HALF_OPEN)", name));
            }
        } else if (currentState == State.CLOSED) {
            int failures = failureCount.incrementAndGet();
            lastFailureTime.set(now);
            
            if (failures >= failureThreshold) {
                // Transiciona para OPEN
                if (state.compareAndSet(State.CLOSED, State.OPEN)) {
                    LOGGER.severe(String.format(
                        "Circuit Breaker '%s' transicionou para OPEN após %d falhas",
                        name, failures
                    ));
                }
            }
        }
    }
    
    /**
     * Obtém o estado atual
     */
    public State getState() {
        return state.get();
    }
    
    /**
     * Obtém informações sobre o circuit breaker
     */
    public CircuitBreakerInfo getInfo() {
        State currentState = state.get();
        long now = System.currentTimeMillis();
        
        long timeUntilRetry = 0;
        if (currentState == State.OPEN) {
            long elapsed = now - lastFailureTime.get();
            timeUntilRetry = Math.max(0, timeoutMs - elapsed);
        }
        
        return new CircuitBreakerInfo(
            currentState,
            failureCount.get(),
            successCount.get(),
            timeUntilRetry
        );
    }
    
    /**
     * Reseta o circuit breaker manualmente (útil para testes)
     */
    public void reset() {
        state.set(State.CLOSED);
        failureCount.set(0);
        successCount.set(0);
        lastFailureTime.set(0);
        LOGGER.info(String.format("Circuit Breaker '%s' foi resetado manualmente", name));
    }
    
    /**
     * Informações sobre o circuit breaker
     */
    public static class CircuitBreakerInfo {
        public final State state;
        public final int failureCount;
        public final int successCount;
        public final long timeUntilRetry; // ms até poder tentar novamente (0 se não aplicável)
        
        CircuitBreakerInfo(State state, int failureCount, int successCount, long timeUntilRetry) {
            this.state = state;
            this.failureCount = failureCount;
            this.successCount = successCount;
            this.timeUntilRetry = timeUntilRetry;
        }
    }
}

