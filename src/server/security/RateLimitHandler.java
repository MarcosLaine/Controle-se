package server.security;

import com.sun.net.httpserver.*;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Handler wrapper que aplica rate limiting e circuit breaker
 */
public class RateLimitHandler implements HttpHandler {
    private static final Logger LOGGER = Logger.getLogger(RateLimitHandler.class.getName());
    
    private final HttpHandler delegate;
    private final RateLimiter rateLimiter;
    private final CircuitBreaker circuitBreaker;
    private final String endpoint;
    
    public RateLimitHandler(HttpHandler delegate, RateLimiter rateLimiter, 
                           CircuitBreaker circuitBreaker, String endpoint) {
        this.delegate = delegate;
        this.rateLimiter = rateLimiter;
        this.circuitBreaker = circuitBreaker;
        this.endpoint = endpoint;
    }
    
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String clientIp = getClientIp(exchange);
        String requestPath = exchange.getRequestURI().getPath();
        
        // Verifica circuit breaker
        if (!circuitBreaker.allowRequest()) {
            CircuitBreaker.CircuitBreakerInfo info = circuitBreaker.getInfo();
            LOGGER.warning(String.format(
                "Requisição bloqueada por Circuit Breaker - IP: %s, Endpoint: %s, Estado: %s",
                clientIp, requestPath, info.state
            ));
            
            sendRateLimitResponse(exchange, 503, 
                "Serviço temporariamente indisponível. Tente novamente em alguns instantes.",
                "circuit-breaker-open",
                info.timeUntilRetry
            );
            return;
        }
        
        // Verifica rate limiting
        if (!rateLimiter.allowRequest(clientIp, endpoint)) {
            RateLimiter.RateLimitInfo info = rateLimiter.getRateLimitInfo(clientIp);
            LOGGER.warning(String.format(
                "Requisição bloqueada por Rate Limit - IP: %s, Endpoint: %s",
                clientIp, requestPath
            ));
            
            sendRateLimitResponse(exchange, 429, 
                "Muitas requisições. Tente novamente mais tarde.",
                "rate-limit-exceeded",
                info.resetTime
            );
            return;
        }
        
        // Processa requisição
        // Nota: O circuit breaker monitora apenas erros do sistema (5xx), não erros de negócio (4xx)
        // Para isso, precisaríamos interceptar o código de status, mas HttpExchange não permite isso facilmente
        // Por enquanto, registramos falha apenas em exceções não tratadas (erros do sistema)
        try {
            delegate.handle(exchange);
            // Se chegou aqui sem exceção, assume sucesso
            // Nota: Em uma implementação mais sofisticada, poderíamos usar um wrapper
            // que intercepta o código de status HTTP antes de enviar a resposta
        } catch (Exception e) {
            // Exceções não tratadas indicam falha do sistema
            circuitBreaker.recordFailure();
            LOGGER.log(Level.SEVERE, "Erro ao processar requisição", e);
            throw e;
        }
        // Sucesso implícito - não registramos aqui para evitar falsos positivos
        // O circuit breaker será resetado naturalmente quando não houver mais falhas
    }
    
    /**
     * Extrai o IP do cliente da requisição
     */
    private String getClientIp(HttpExchange exchange) {
        // Tenta obter do header X-Forwarded-For (útil para proxies/load balancers)
        String forwardedFor = exchange.getRequestHeaders().getFirst("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isEmpty()) {
            // Pega o primeiro IP (o IP original do cliente)
            String[] ips = forwardedFor.split(",");
            if (ips.length > 0) {
                return ips[0].trim();
            }
        }
        
        // Tenta obter do header X-Real-IP
        String realIp = exchange.getRequestHeaders().getFirst("X-Real-IP");
        if (realIp != null && !realIp.isEmpty()) {
            return realIp.trim();
        }
        
        // Fallback para o IP remoto da conexão
        InetSocketAddress remoteAddress = exchange.getRemoteAddress();
        if (remoteAddress != null) {
            return remoteAddress.getAddress().getHostAddress();
        }
        
        return "unknown";
    }
    
    /**
     * Envia resposta de rate limit
     */
    private void sendRateLimitResponse(HttpExchange exchange, int statusCode, 
                                      String message, String errorCode, long retryAfter) 
            throws IOException {
        String response = String.format(
            "{\"success\":false,\"error\":\"%s\",\"message\":\"%s\",\"retryAfter\":%d}",
            errorCode, message, retryAfter
        );
        
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Retry-After", String.valueOf((retryAfter / 1000) + 1));
        exchange.sendResponseHeaders(statusCode, response.getBytes().length);
        
        try (var os = exchange.getResponseBody()) {
            os.write(response.getBytes());
        }
    }
}

