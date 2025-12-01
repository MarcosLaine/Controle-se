package server.security;

import java.util.concurrent.*;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Rastreia tentativas de login falhas para proteção contra brute force
 */
public class LoginAttemptTracker {
    private static final Logger LOGGER = Logger.getLogger(LoginAttemptTracker.class.getName());
    
    // Configurações
    private static final int MAX_FAILED_ATTEMPTS = 5; // Máximo de tentativas falhas
    private static final long LOCKOUT_DURATION_MS = 15 * 60 * 1000; // 15 minutos de bloqueio
    private static final long ATTEMPT_WINDOW_MS = 60 * 60 * 1000; // Janela de 1 hora para contar tentativas
    
    // Armazena tentativas por IP e por email
    private final ConcurrentHashMap<String, AttemptRecord> attemptsByIp = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AttemptRecord> attemptsByEmail = new ConcurrentHashMap<>();
    
    // Limpa registros expirados periodicamente
    private final ScheduledExecutorService cleanupExecutor = Executors.newScheduledThreadPool(1);
    
    public LoginAttemptTracker() {
        // Limpa registros expirados a cada 10 minutos
        cleanupExecutor.scheduleAtFixedRate(
            this::cleanupExpiredRecords,
            10, 10, TimeUnit.MINUTES
        );
    }
    
    /**
     * Registra uma tentativa de login falha
     * @param ipAddress IP do cliente
     * @param email Email usado na tentativa (opcional)
     */
    public void recordFailedAttempt(String ipAddress, String email) {
        long now = System.currentTimeMillis();
        
        // Registra por IP
        AttemptRecord ipRecord = attemptsByIp.computeIfAbsent(
            ipAddress != null ? ipAddress : "unknown",
            k -> new AttemptRecord()
        );
        ipRecord.addFailedAttempt(now);
        
        // Registra por email se fornecido
        if (email != null && !email.isEmpty()) {
            AttemptRecord emailRecord = attemptsByEmail.computeIfAbsent(
                email.toLowerCase(),
                k -> new AttemptRecord()
            );
            emailRecord.addFailedAttempt(now);
        }
        
        LOGGER.warning(String.format(
            "Tentativa de login falha registrada - IP: %s, Email: %s",
            ipAddress, email != null ? email : "N/A"
        ));
    }
    
    /**
     * Registra uma tentativa de login bem-sucedida (limpa o histórico)
     * @param ipAddress IP do cliente
     * @param email Email usado
     */
    public void recordSuccessfulAttempt(String ipAddress, String email) {
        if (ipAddress != null) {
            attemptsByIp.remove(ipAddress);
        }
        if (email != null && !email.isEmpty()) {
            attemptsByEmail.remove(email.toLowerCase());
        }
    }
    
    /**
     * Verifica se um IP ou email está bloqueado
     * @param ipAddress IP do cliente
     * @param email Email usado (opcional)
     * @return true se bloqueado, false se permitido
     */
    public boolean isBlocked(String ipAddress, String email) {
        long now = System.currentTimeMillis();
        
        // Verifica bloqueio por IP
        if (ipAddress != null) {
            AttemptRecord ipRecord = attemptsByIp.get(ipAddress);
            if (ipRecord != null && ipRecord.isBlocked(now)) {
                LOGGER.warning(String.format("Acesso bloqueado por IP: %s", ipAddress));
                return true;
            }
        }
        
        // Verifica bloqueio por email
        if (email != null && !email.isEmpty()) {
            AttemptRecord emailRecord = attemptsByEmail.get(email.toLowerCase());
            if (emailRecord != null && emailRecord.isBlocked(now)) {
                LOGGER.warning(String.format("Acesso bloqueado por email: %s", email));
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Obtém informações sobre tentativas falhas
     * @param ipAddress IP do cliente
     * @param email Email usado (opcional)
     * @return Informações sobre tentativas
     */
    public AttemptInfo getAttemptInfo(String ipAddress, String email) {
        long now = System.currentTimeMillis();
        
        int ipAttempts = 0;
        long ipUnlockTime = 0;
        boolean ipBlocked = false;
        
        if (ipAddress != null) {
            AttemptRecord ipRecord = attemptsByIp.get(ipAddress);
            if (ipRecord != null) {
                ipAttempts = ipRecord.getFailedAttempts(now);
                ipBlocked = ipRecord.isBlocked(now);
                ipUnlockTime = ipRecord.getUnlockTime(now);
            }
        }
        
        int emailAttempts = 0;
        long emailUnlockTime = 0;
        boolean emailBlocked = false;
        
        if (email != null && !email.isEmpty()) {
            AttemptRecord emailRecord = attemptsByEmail.get(email.toLowerCase());
            if (emailRecord != null) {
                emailAttempts = emailRecord.getFailedAttempts(now);
                emailBlocked = emailRecord.isBlocked(now);
                emailUnlockTime = emailRecord.getUnlockTime(now);
            }
        }
        
        return new AttemptInfo(
            Math.max(ipAttempts, emailAttempts),
            Math.max(ipUnlockTime, emailUnlockTime),
            ipBlocked || emailBlocked
        );
    }
    
    /**
     * Verifica se deve solicitar CAPTCHA
     * @param ipAddress IP do cliente
     * @param email Email usado (opcional)
     * @return true se CAPTCHA deve ser solicitado
     */
    public boolean shouldRequireCaptcha(String ipAddress, String email) {
        long now = System.currentTimeMillis();
        
        // Requer CAPTCHA após 3 tentativas falhas
        int threshold = 3;
        
        if (ipAddress != null) {
            AttemptRecord ipRecord = attemptsByIp.get(ipAddress);
            if (ipRecord != null && ipRecord.getFailedAttempts(now) >= threshold) {
                return true;
            }
        }
        
        if (email != null && !email.isEmpty()) {
            AttemptRecord emailRecord = attemptsByEmail.get(email.toLowerCase());
            if (emailRecord != null && emailRecord.getFailedAttempts(now) >= threshold) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Limpa registros expirados
     */
    private void cleanupExpiredRecords() {
        long now = System.currentTimeMillis();
        
        attemptsByIp.entrySet().removeIf(entry -> {
            AttemptRecord record = entry.getValue();
            return record.isExpired(now);
        });
        
        attemptsByEmail.entrySet().removeIf(entry -> {
            AttemptRecord record = entry.getValue();
            return record.isExpired(now);
        });
    }
    
    /**
     * Classe interna para armazenar tentativas
     */
    private static class AttemptRecord {
        private final ConcurrentLinkedQueue<Long> failedAttempts = new ConcurrentLinkedQueue<>();
        private volatile long lockoutUntil = 0;
        
        synchronized void addFailedAttempt(long timestamp) {
            // Remove tentativas antigas fora da janela
            long windowStart = timestamp - ATTEMPT_WINDOW_MS;
            while (!failedAttempts.isEmpty() && failedAttempts.peek() < windowStart) {
                failedAttempts.poll();
            }
            
            // Adiciona nova tentativa
            failedAttempts.offer(timestamp);
            
            // Se excedeu o limite, bloqueia
            if (failedAttempts.size() >= MAX_FAILED_ATTEMPTS) {
                lockoutUntil = timestamp + LOCKOUT_DURATION_MS;
                LOGGER.severe(String.format(
                    "Bloqueio ativado após %d tentativas falhas. Desbloqueio em %d ms",
                    failedAttempts.size(), LOCKOUT_DURATION_MS
                ));
            }
        }
        
        synchronized int getFailedAttempts(long now) {
            long windowStart = now - ATTEMPT_WINDOW_MS;
            while (!failedAttempts.isEmpty() && failedAttempts.peek() < windowStart) {
                failedAttempts.poll();
            }
            return failedAttempts.size();
        }
        
        synchronized boolean isBlocked(long now) {
            if (lockoutUntil > 0 && now < lockoutUntil) {
                return true;
            }
            // Se passou o tempo de bloqueio, limpa
            if (lockoutUntil > 0 && now >= lockoutUntil) {
                lockoutUntil = 0;
            }
            return false;
        }
        
        synchronized long getUnlockTime(long now) {
            if (lockoutUntil > 0 && now < lockoutUntil) {
                return lockoutUntil;
            }
            return 0;
        }
        
        boolean isExpired(long now) {
            if (lockoutUntil > 0 && now < lockoutUntil) {
                return false; // Ainda bloqueado, não expirar
            }
            return failedAttempts.isEmpty() || 
                   (failedAttempts.peek() != null && (now - failedAttempts.peek()) > ATTEMPT_WINDOW_MS * 2);
        }
    }
    
    /**
     * Informações sobre tentativas
     */
    public static class AttemptInfo {
        public final int failedAttempts;
        public final long unlockTime; // Timestamp em ms quando será desbloqueado (0 se não bloqueado)
        public final boolean isBlocked;
        
        AttemptInfo(int failedAttempts, long unlockTime, boolean isBlocked) {
            this.failedAttempts = failedAttempts;
            this.unlockTime = unlockTime;
            this.isBlocked = isBlocked;
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

