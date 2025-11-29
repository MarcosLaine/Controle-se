import React, { createContext, useContext, useState, useEffect } from 'react';
import api from '../services/api';
import toast from 'react-hot-toast';

const AuthContext = createContext(null);

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within AuthProvider');
  }
  return context;
};

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    // Verifica se há usuário salvo no localStorage
    const savedUser = localStorage.getItem('controle-se-user');
    if (savedUser) {
      try {
        setUser(JSON.parse(savedUser));
      } catch (error) {
        console.error('Erro ao carregar usuário:', error);
        localStorage.removeItem('controle-se-user');
      }
    }
    setLoading(false);
  }, []);

  const login = async (email, password) => {
    try {
      const response = await api.post('/auth/login', { email, password });
      if (response.success) {
        setUser(response.user);
        localStorage.setItem('controle-se-user', JSON.stringify(response.user));
        toast.success('Login realizado com sucesso!');
        return { success: true };
      } else {
        toast.error(response.message || 'Erro no login');
        return { success: false, message: response.message };
      }
    } catch (error) {
      toast.error(error.message || 'Erro ao fazer login');
      return { success: false, message: error.message };
    }
  };

  const register = async (name, email, password) => {
    try {
      const response = await api.post('/auth/register', { name, email, password });
      if (response.success) {
        toast.success('Cadastro realizado com sucesso!');
        return { success: true };
      } else {
        toast.error(response.message || 'Erro no cadastro');
        return { success: false, message: response.message };
      }
    } catch (error) {
      toast.error(error.message || 'Erro ao fazer cadastro');
      return { success: false, message: error.message };
    }
  };

  const logout = () => {
    setUser(null);
    localStorage.removeItem('controle-se-user');
    toast.success('Logout realizado com sucesso!');
  };

  return (
    <AuthContext.Provider value={{ user, loading, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  );
};

