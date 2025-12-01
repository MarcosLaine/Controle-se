import axios from 'axios';

const API_BASE_URL = window.location.hostname === 'localhost' 
  ? 'http://localhost:8080/api' 
  : `${window.location.origin}/api`;

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request interceptor para adicionar token se necessário
api.interceptors.request.use(
  (config) => {
    const user = JSON.parse(localStorage.getItem('controle-se-user') || 'null');
    if (user) {
      config.headers.Authorization = `Bearer ${user.token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Response interceptor para tratamento de erros
api.interceptors.response.use(
  (response) => response.data,
  (error) => {
    // Ignora erros de cancelamento (AbortController)
    if (axios.isCancel(error) || error.name === 'CanceledError' || error.name === 'AbortError') {
      return Promise.reject(error);
    }
    
    if (error.response) {
      // Erro da API - passa todos os dados da resposta para preservar requiresCaptcha, blocked, etc.
      return Promise.reject({
        message: error.response.data?.message || 'Erro na requisição',
        status: error.response.status,
        name: error.name,
        requiresCaptcha: error.response.data?.requiresCaptcha,
        blocked: error.response.data?.blocked,
        failedAttempts: error.response.data?.failedAttempts,
        unlockTime: error.response.data?.unlockTime,
        ...error.response.data // Inclui todos os outros campos da resposta
      });
    } else if (error.request) {
      // Erro de rede
      return Promise.reject({
        message: 'Erro de conexão. Verifique se o backend está rodando.',
        status: 0,
        name: error.name,
      });
    } else {
      // Outro erro
      return Promise.reject({
        message: error.message || 'Erro desconhecido',
        status: 0,
        name: error.name,
      });
    }
  }
);

export default api;

