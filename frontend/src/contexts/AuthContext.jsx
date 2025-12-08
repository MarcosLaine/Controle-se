import React, { createContext, useContext, useState, useEffect } from 'react';
import api from '../services/api';
import toast from 'react-hot-toast';
import { useLanguage } from './LanguageContext';

const AuthContext = createContext(null);

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within AuthProvider');
  }
  return context;
};

export const AuthProvider = ({ children }) => {
  const { t } = useLanguage();
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
        // Combina dados do usuário com tokens
        const userData = {
          ...response.user,
          token: response.token || response.accessToken, // Mantém compatibilidade
          accessToken: response.accessToken || response.token,
          refreshToken: response.refreshToken
        };
        setUser(userData);
        localStorage.setItem('controle-se-user', JSON.stringify(userData));
        toast.success(t('auth.loginSuccess'));
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
        toast.error(response.message || t('common.errorLogin'));
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
      toast.error(error.message || t('common.errorDoingLogin'));
      return { success: false, message: error.message };
    }
  };

  const register = async (name, email, password) => {
    try {
      const response = await api.post('/auth/register', { name, email, password });
      if (response.success) {
        // Faz login automático após cadastro bem-sucedido
        if (response.user) {
          // Combina dados do usuário com tokens se disponíveis
          const userData = {
            ...response.user,
            token: response.token || response.accessToken,
            accessToken: response.accessToken || response.token,
            refreshToken: response.refreshToken
          };
          setUser(userData);
          localStorage.setItem('controle-se-user', JSON.stringify(userData));
        }
        toast.success(t('auth.registerSuccess'));
        return { success: true };
      } else {
        toast.error(response.message || t('common.errorRegister'));
        return { success: false, message: response.message };
      }
    } catch (error) {
      toast.error(error.message || t('common.errorDoingRegister'));
      return { success: false, message: error.message };
    }
  };

  const logout = () => {
    setUser(null);
    localStorage.removeItem('controle-se-user');
    toast.success(t('auth.logoutSuccess'));
  };

  const changePassword = async (currentPassword, newPassword) => {
    try {
      const response = await api.post('/auth/change-password', { currentPassword, newPassword });
      toast.success(response.message || t('common.passwordUpdated'));
      return { success: true };
    } catch (error) {
      toast.error(error.message || t('common.errorUpdatingPassword'));
      return { success: false, message: error.message };
    }
  };

  return (
    <AuthContext.Provider value={{ user, loading, login, register, logout, changePassword }}>
      {children}
    </AuthContext.Provider>
  );
};

