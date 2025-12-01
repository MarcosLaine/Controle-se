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

  const login = async (email, password, captchaToken = null) => {
    try {
      const payload = { email, password };
      if (captchaToken) {
        payload.captchaToken = captchaToken;
      }
      
      const response = await api.post('/auth/login', payload);
      if (response.success) {
        setUser(response.user);
        localStorage.setItem('controle-se-user', JSON.stringify(response.user));
        toast.success('Login realizado com sucesso!');
        return { success: true };
      } else {
        // Verifica se precisa de CAPTCHA
        if (response.requiresCaptcha) {
          // Não mostra toast de erro quando CAPTCHA é requerido
          return { 
            success: false, 
            message: response.message, 
            requiresCaptcha: true,
            failedAttempts: response.failedAttempts 
          };
        }
        toast.error(response.message || 'Erro no login');
        return { success: false, message: response.message, requiresCaptcha: response.requiresCaptcha };
      }
    } catch (error) {
      // Verifica se o erro indica necessidade de CAPTCHA
      if (error.requiresCaptcha) {
        // Não mostra toast de erro quando CAPTCHA é requerido
        return { 
          success: false, 
          message: error.message, 
          requiresCaptcha: true,
          failedAttempts: error.failedAttempts
        };
      }
      toast.error(error.message || 'Erro ao fazer login');
      return { success: false, message: error.message };
    }
  };

  const register = async (name, email, password) => {
    try {
      const response = await api.post('/auth/register', { name, email, password });
      if (response.success) {
        // Faz login automático após cadastro bem-sucedido
        if (response.user) {
          setUser(response.user);
          localStorage.setItem('controle-se-user', JSON.stringify(response.user));
        }
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

  const changePassword = async (currentPassword, newPassword) => {
    try {
      const response = await api.post('/auth/change-password', { currentPassword, newPassword });
      toast.success(response.message || 'Senha atualizada com sucesso!');
      return { success: true };
    } catch (error) {
      toast.error(error.message || 'Erro ao atualizar senha');
      return { success: false, message: error.message };
    }
  };

  return (
    <AuthContext.Provider value={{ user, loading, login, register, logout, changePassword }}>
      {children}
    </AuthContext.Provider>
  );
};

