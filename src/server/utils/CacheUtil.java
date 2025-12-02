package server.utils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utilitário para cache simples com TTL
 */
public class CacheUtil {
    private static final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 30 * 1000; // 30 segundos
    
    private static class CacheEntry {
        final Object data;
        final long timestamp;
        
        CacheEntry(Object data) {
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }
        
        boolean isExpired() {
            return (System.currentTimeMillis() - timestamp) > CACHE_TTL_MS;
        }
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
    
    public static void setCached(String key, Object data) {
        cache.put(key, new CacheEntry(data));
    }
    
    public static void invalidateCache(String pattern) {
        if (pattern == null) {
            cache.clear();
        } else {
            cache.entrySet().removeIf(entry -> entry.getKey().startsWith(pattern));
        }
    }
}

