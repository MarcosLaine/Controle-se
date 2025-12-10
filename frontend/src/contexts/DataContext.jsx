import React, { createContext, useContext, useState, useCallback, useEffect, useRef } from 'react';
import api from '../services/api';

const DataContext = createContext(null);

// TTLs em milissegundos
const TTL_STATIC = 5 * 60 * 1000; // 5 minutos - dados estáticos (categorias, tags)
const TTL_SEMI_STATIC = 2 * 60 * 1000; // 2 minutos - dados semi-estáticos (contas, orçamentos)
const TTL_DYNAMIC = 30 * 1000; // 30 segundos - dados dinâmicos (transações, overview)

const getTTL = (key) => {
  if (key.startsWith('categories_') || key.startsWith('tags_')) {
    return TTL_STATIC;
  }
  if (key.startsWith('accounts_') || key.startsWith('budgets_')) {
    return TTL_SEMI_STATIC;
  }
  return TTL_DYNAMIC;
};

export const useData = () => {
  const context = useContext(DataContext);
  if (!context) {
    throw new Error('useData must be used within DataProvider');
  }
  return context;
};

export const DataProvider = ({ children }) => {
  const [cache, setCache] = useState({});
  const [loading, setLoading] = useState({});
  const cacheTimestamps = useRef({});

  // Limpa cache expirado periodicamente
  useEffect(() => {
    const interval = setInterval(() => {
      setCache(prev => {
        const now = Date.now();
        const newCache = { ...prev };
        let hasChanges = false;

        Object.keys(newCache).forEach(key => {
          const timestamp = cacheTimestamps.current[key];
          if (timestamp) {
            const ttl = getTTL(key);
            if (now - timestamp > ttl) {
              delete newCache[key];
              delete cacheTimestamps.current[key];
              hasChanges = true;
            }
          }
        });

        return hasChanges ? { ...newCache } : prev;
      });
    }, 10000); // Verifica a cada 10 segundos

    return () => clearInterval(interval);
  }, []);

  const getCachedData = useCallback((key) => {
    const timestamp = cacheTimestamps.current[key];
    if (timestamp) {
      const ttl = getTTL(key);
      if (Date.now() - timestamp > ttl) {
        // Cache expirado, remove
        setCache(prev => {
          const newCache = { ...prev };
          delete newCache[key];
          return newCache;
        });
        delete cacheTimestamps.current[key];
        return undefined;
      }
    }
    return cache[key];
  }, [cache]);

  const setCachedData = useCallback((key, data) => {
    setCache(prev => ({ ...prev, [key]: data }));
    cacheTimestamps.current[key] = Date.now();
  }, []);

  const fetchData = useCallback(async (key, fetchFn, forceRefresh = false) => {
    // Se já está carregando, não faz nova requisição
    if (loading[key] && !forceRefresh) {
      return cache[key];
    }

    // Verifica cache com TTL
    if (!forceRefresh) {
      const cached = getCachedData(key);
      if (cached !== undefined) {
        return cached;
      }
    }

    setLoading(prev => ({ ...prev, [key]: true }));

    try {
      const data = await fetchFn();
      // Só atualiza cache se não foi cancelado (verifica se é um erro de cancelamento)
      if (data !== undefined && !(data instanceof Error)) {
        setCachedData(key, data);
      }
      return data;
    } catch (error) {
      // Se for erro de cancelamento, não loga como erro
      if (error.name === 'CanceledError' || error.name === 'AbortError' || (error.message && error.message.includes('canceled'))) {
        return undefined;
      }
      console.error(`Erro ao carregar ${key}:`, error);
      throw error;
    } finally {
      setLoading(prev => ({ ...prev, [key]: false }));
    }
  }, [cache, loading, getCachedData, setCachedData]);

  // Métodos auxiliares para dados comuns
  const getCategories = useCallback((userId) => {
    return getCachedData(`categories_${userId}`);
  }, [getCachedData]);

  const getAccounts = useCallback((userId) => {
    return getCachedData(`accounts_${userId}`);
  }, [getCachedData]);

  const getTags = useCallback((userId) => {
    return getCachedData(`tags_${userId}`);
  }, [getCachedData]);

  const fetchCategories = useCallback(async (userId, forceRefresh = false) => {
    return fetchData(
      `categories_${userId}`,
      async () => {
        const res = await api.get(`/categories?userId=${userId}`);
        return res.success ? (res.data || []) : [];
      },
      forceRefresh
    );
  }, [fetchData]);

  const fetchAccounts = useCallback(async (userId, forceRefresh = false) => {
    return fetchData(
      `accounts_${userId}`,
      async () => {
        const res = await api.get(`/accounts?userId=${userId}`);
        return res.success ? (res.data || []) : [];
      },
      forceRefresh
    );
  }, [fetchData]);

  const fetchTags = useCallback(async (userId, forceRefresh = false) => {
    return fetchData(
      `tags_${userId}`,
      async () => {
        const res = await api.get(`/tags?userId=${userId}`);
        return res.success ? (res.data || []) : [];
      },
      forceRefresh
    );
  }, [fetchData]);

  const invalidateCache = useCallback((key) => {
    setCache(prev => {
      const newCache = { ...prev };
      if (key) {
        delete newCache[key];
        delete cacheTimestamps.current[key];
      } else {
        // Se não especificar key, limpa tudo
        cacheTimestamps.current = {};
        return {};
      }
      return newCache;
    });
  }, []);

  const isLoaded = useCallback((key) => {
    return !!getCachedData(key) && !loading[key];
  }, [getCachedData, loading]);

  return (
    <DataContext.Provider value={{
      getCachedData,
      setCachedData,
      fetchData,
      invalidateCache,
      isLoaded,
      loading,
      // Métodos auxiliares para dados comuns
      getCategories,
      getAccounts,
      getTags,
      fetchCategories,
      fetchAccounts,
      fetchTags,
    }}>
      {children}
    </DataContext.Provider>
  );
};

