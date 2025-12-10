package server.utils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utilitário para cache simples com TTL configurável
 * Suporta TTLs diferentes baseados no tipo de dado
 */
public class CacheUtil {
    private static final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    
    // TTLs por tipo de dado (em milissegundos)
    private static final long TTL_DYNAMIC = 30 * 1000; // 30 segundos - dados dinâmicos (overview, totais)
    private static final long TTL_SEMI_STATIC = 2 * 60 * 1000; // 2 minutos - dados semi-estáticos (contas)
    private static final long TTL_STATIC = 5 * 60 * 1000; // 5 minutos - dados estáticos (categorias, tags)
    
    private static class CacheEntry {
        final Object data;
        final long timestamp;
        final long ttl;
        
        CacheEntry(Object data, long ttl) {
            this.data = data;
            this.timestamp = System.currentTimeMillis();
            this.ttl = ttl;
        }
        
        boolean isExpired() {
            return (System.currentTimeMillis() - timestamp) > ttl;
        }
    }
    
    /**
     * Determina o TTL apropriado baseado na chave do cache
     */
    private static long getTTLForKey(String key) {
        // Dados estáticos (categorias, tags)
        if (key.startsWith("categories_") || key.startsWith("tags_")) {
            return TTL_STATIC;
        }
        // Dados semi-estáticos (contas)
        if (key.startsWith("accounts_")) {
            return TTL_SEMI_STATIC;
        }
        // Dados dinâmicos (overview, totais, investimentos)
        return TTL_DYNAMIC;
    }
    
    @SuppressWarnings("unchecked")
    public static <T> T getCached(String key) {
        CacheEntry entry = cache.get(key);
        if (entry != null && !entry.isExpired()) {
            return (T) entry.data;
        }
        if (entry != null) {
            cache.remove(key); // Remove entrada expirada
        }
        return null;
    }
    
    /**
     * Armazena dados no cache com TTL automático baseado no tipo de dado
     */
    public static void setCached(String key, Object data) {
        long ttl = getTTLForKey(key);
        cache.put(key, new CacheEntry(data, ttl));
    }
    
    /**
     * Armazena dados no cache com TTL customizado (em milissegundos)
     */
    public static void setCached(String key, Object data, long ttlMs) {
        cache.put(key, new CacheEntry(data, ttlMs));
    }
    
    /**
     * Armazena dados estáticos no cache (TTL de 5 minutos)
     */
    public static void setCachedStatic(String key, Object data) {
        cache.put(key, new CacheEntry(data, TTL_STATIC));
    }
    
    /**
     * Armazena dados semi-estáticos no cache (TTL de 2 minutos)
     */
    public static void setCachedSemiStatic(String key, Object data) {
        cache.put(key, new CacheEntry(data, TTL_SEMI_STATIC));
    }
    
    /**
     * Armazena dados dinâmicos no cache (TTL de 30 segundos)
     */
    public static void setCachedDynamic(String key, Object data) {
        cache.put(key, new CacheEntry(data, TTL_DYNAMIC));
    }
    
    public static void invalidateCache(String pattern) {
        if (pattern == null) {
            cache.clear();
        } else {
            cache.entrySet().removeIf(entry -> entry.getKey().startsWith(pattern));
        }
    }
}

