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
      // Se houver token no futuro, adicionar aqui
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
    if (error.response) {
      // Erro da API
      return Promise.reject({
        message: error.response.data?.message || 'Erro na requisição',
        status: error.response.status,
      });
    } else if (error.request) {
      // Erro de rede
      return Promise.reject({
        message: 'Erro de conexão. Verifique se o backend está rodando.',
        status: 0,
      });
    } else {
      // Outro erro
      return Promise.reject({
        message: error.message || 'Erro desconhecido',
        status: 0,
      });
    }
  }
);

export default api;

