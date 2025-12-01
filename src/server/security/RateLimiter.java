package server.security;

import java.util.concurrent.*;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Rate Limiter para proteção contra DDoS e requisições excessivas
 * Implementa algoritmo Token Bucket para controle de taxa
 */
public class RateLimiter {
    private static final Logger LOGGER = Logger.getLogger(RateLimiter.class.getName());
    
    // Configurações padrão
    private static final int DEFAULT_MAX_REQUESTS = 100; // Requisições por janela
    private static final long DEFAULT_WINDOW_MS = 60 * 1000; // 1 minuto
    private static final int DEFAULT_MAX_REQUESTS_AUTH = 5; // Login/Register: 5 por minuto
    private static final long DEFAULT_WINDOW_MS_AUTH = 60 * 1000; // 1 minuto
    
    // Limites mais restritivos para endpoints de autenticação
    // Aumentado para permitir que o CAPTCHA apareça (após 3 tentativas) antes de bloquear
    private static final int MAX_REQUESTS_LOGIN = 10; // 10 tentativas por minuto (permite CAPTCHA aparecer)
    private static final int MAX_REQUESTS_REGISTER = 5; // 5 registros por minuto
    
    // Armazena requisições por IP
    private final ConcurrentHashMap<String, RequestWindow> requestWindows = new ConcurrentHashMap<>();
    
    // Limpa janelas expiradas periodicamente
    private final ScheduledExecutorService cleanupExecutor = Executors.newScheduledThreadPool(1);
    
    public RateLimiter() {
        // Limpa janelas expiradas a cada 5 minutos
        cleanupExecutor.scheduleAtFixedRate(
            this::cleanupExpiredWindows,
            5, 5, TimeUnit.MINUTES
        );
    }
    
    /**
     * Verifica se uma requisição deve ser permitida
     * @param ipAddress Endereço IP do cliente
     * @param endpoint Endpoint sendo acessado (para limites específicos)
     * @return true se permitido, false se excedeu o limite
     */
    public boolean allowRequest(String ipAddress, String endpoint) {
        if (ipAddress == null || ipAddress.isEmpty()) {
            ipAddress = "unknown";
        }
        
        // Determina limites baseado no endpoint
        int maxRequests;
        long windowMs;
        
        if (endpoint != null) {
            if (endpoint.contains("/auth/login")) {
                maxRequests = MAX_REQUESTS_LOGIN;
                windowMs = DEFAULT_WINDOW_MS_AUTH;
            } else if (endpoint.contains("/auth/register")) {
                maxRequests = MAX_REQUESTS_REGISTER;
                windowMs = DEFAULT_WINDOW_MS_AUTH;
            } else if (endpoint.contains("/auth/")) {
                maxRequests = DEFAULT_MAX_REQUESTS_AUTH;
                windowMs = DEFAULT_WINDOW_MS_AUTH;
            } else {
                maxRequests = DEFAULT_MAX_REQUESTS;
                windowMs = DEFAULT_WINDOW_MS;
            }
        } else {
            maxRequests = DEFAULT_MAX_REQUESTS;
            windowMs = DEFAULT_WINDOW_MS;
        }
        
        RequestWindow window = requestWindows.computeIfAbsent(
            ipAddress,
            k -> new RequestWindow(maxRequests, windowMs)
        );
        
        // Atualiza limites se necessário (caso endpoint mude)
        window.updateLimits(maxRequests, windowMs);
        
        boolean allowed = window.allowRequest();
        
        if (!allowed) {
            LOGGER.warning(String.format(
                "Rate limit excedido para IP %s no endpoint %s (limite: %d req/%dms)",
                ipAddress, endpoint, maxRequests, windowMs
            ));
        }
        
        return allowed;
    }
    
    /**
     * Obtém informações sobre o rate limit atual para um IP
     * @param ipAddress Endereço IP
     * @return Informações sobre requisições restantes
     */
    public RateLimitInfo getRateLimitInfo(String ipAddress) {
        if (ipAddress == null || ipAddress.isEmpty()) {
            ipAddress = "unknown";
        }
        
        RequestWindow window = requestWindows.get(ipAddress);
        if (window == null) {
            return new RateLimitInfo(0, 0, 0);
        }
        
        return window.getInfo();
    }
    
    /**
     * Limpa janelas expiradas para liberar memória
     */
    private void cleanupExpiredWindows() {
        long now = System.currentTimeMillis();
        requestWindows.entrySet().removeIf(entry -> {
            RequestWindow window = entry.getValue();
            return window.isExpired(now);
        });
    }
    
    /**
     * Limpa todas as janelas (útil para testes)
     */
    public void clear() {
        requestWindows.clear();
    }
    
    /**
     * Classe interna para gerenciar janela de requisições
     */
    private static class RequestWindow {
        private int maxRequests;
        private long windowMs;
        private final ConcurrentLinkedQueue<Long> requests = new ConcurrentLinkedQueue<>();
        
        RequestWindow(int maxRequests, long windowMs) {
            this.maxRequests = maxRequests;
            this.windowMs = windowMs;
        }
        
        void updateLimits(int maxRequests, long windowMs) {
            this.maxRequests = maxRequests;
            this.windowMs = windowMs;
        }
        
        synchronized boolean allowRequest() {
            long now = System.currentTimeMillis();
            long windowStart = now - windowMs;
            
            // Remove requisições antigas fora da janela
            while (!requests.isEmpty() && requests.peek() < windowStart) {
                requests.poll();
            }
            
            // Verifica se ainda há espaço na janela
            if (requests.size() >= maxRequests) {
                return false;
            }
            
            // Adiciona nova requisição
            requests.offer(now);
            return true;
        }
        
        synchronized RateLimitInfo getInfo() {
            long now = System.currentTimeMillis();
            long windowStart = now - windowMs;
            
            // Remove requisições antigas
            while (!requests.isEmpty() && requests.peek() < windowStart) {
                requests.poll();
            }
            
            int remaining = Math.max(0, maxRequests - requests.size());
            long resetTime = requests.isEmpty() ? 0 : requests.peek() + windowMs;
            
            return new RateLimitInfo(requests.size(), remaining, resetTime);
        }
        
        boolean isExpired(long now) {
            if (requests.isEmpty()) {
                return true;
            }
            Long oldestRequest = requests.peek();
            return oldestRequest != null && (now - oldestRequest) > windowMs * 2; // 2x a janela para segurança
        }
    }
    
    /**
     * Informações sobre rate limit
     */
    public static class RateLimitInfo {
        public final int used;
        public final int remaining;
        public final long resetTime; // Timestamp em ms quando o limite será resetado
        
        RateLimitInfo(int used, int remaining, long resetTime) {
            this.used = used;
            this.remaining = remaining;
            this.resetTime = resetTime;
        }
    }
    
    /**
     * Shutdown graceful
     */
    public void shutdown() {
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

