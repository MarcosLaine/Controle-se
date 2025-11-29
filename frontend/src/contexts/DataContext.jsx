import React, { createContext, useContext, useState, useCallback } from 'react';
import api from '../services/api';

const DataContext = createContext(null);

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

  const getCachedData = useCallback((key) => {
    return cache[key];
  }, [cache]);

  const setCachedData = useCallback((key, data) => {
    setCache(prev => ({ ...prev, [key]: data }));
  }, []);

  const fetchData = useCallback(async (key, fetchFn, forceRefresh = false) => {
    // Se já está carregando, não faz nova requisição
    if (loading[key] && !forceRefresh) {
      return cache[key];
    }

    // Se tem cache e não é refresh forçado, retorna cache
    if (cache[key] && !forceRefresh) {
      return cache[key];
    }

    setLoading(prev => ({ ...prev, [key]: true }));

    try {
      const data = await fetchFn();
      setCache(prev => ({ ...prev, [key]: data }));
      return data;
    } catch (error) {
      console.error(`Erro ao carregar ${key}:`, error);
      throw error;
    } finally {
      setLoading(prev => ({ ...prev, [key]: false }));
    }
  }, [cache, loading]);

  const invalidateCache = useCallback((key) => {
    setCache(prev => {
      const newCache = { ...prev };
      if (key) {
        delete newCache[key];
      } else {
        // Se não especificar key, limpa tudo
        return {};
      }
      return newCache;
    });
  }, []);

  const isLoaded = useCallback((key) => {
    return !!cache[key] && !loading[key];
  }, [cache, loading]);

  return (
    <DataContext.Provider value={{
      getCachedData,
      setCachedData,
      fetchData,
      invalidateCache,
      isLoaded,
      loading,
    }}>
      {children}
    </DataContext.Provider>
  );
};

