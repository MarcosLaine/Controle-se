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

// Flag para evitar loops infinitos de refresh
let isRefreshing = false;
let failedQueue = [];

const processQueue = (error, token = null) => {
  failedQueue.forEach(prom => {
    if (error) {
      prom.reject(error);
    } else {
      prom.resolve(token);
    }
  });
  
  failedQueue = [];
};

// Valida se o token tem formato JWT válido (3 partes separadas por ponto)
const isValidJWT = (token) => {
  if (!token || typeof token !== 'string') return false;
  const parts = token.split('.');
  return parts.length === 3 && parts.every(part => part.length > 0);
};

// Request interceptor para adicionar token se necessário
api.interceptors.request.use(
  (config) => {
    // Não adiciona token para endpoints de autenticação (login, register, refresh)
    const isAuthEndpoint = config.url?.includes('/auth/login') || 
                          config.url?.includes('/auth/register') || 
                          config.url?.includes('/auth/refresh');
    
    if (!isAuthEndpoint) {
      try {
        const userStr = localStorage.getItem('controle-se-user');
        if (userStr) {
          const user = JSON.parse(userStr);
          // Prioriza accessToken, mas mantém compatibilidade com token antigo
          const token = user.accessToken || user.token;
          // Só adiciona o token se ele for válido
          if (token && isValidJWT(token)) {
            config.headers.Authorization = `Bearer ${token}`;
          } else if (token) {
            // Token inválido - remove do localStorage para evitar tentativas futuras
            console.warn('Token inválido encontrado, removendo do localStorage');
            localStorage.removeItem('controle-se-user');
          }
        }
      } catch (error) {
        // Se houver erro ao parsear, remove o item corrompido
        console.error('Erro ao parsear usuário do localStorage:', error);
        localStorage.removeItem('controle-se-user');
      }
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Response interceptor para tratamento de erros e refresh automático
api.interceptors.response.use(
  (response) => response.data,
  async (error) => {
    // Ignora erros de cancelamento (AbortController)
    if (axios.isCancel(error) || error.name === 'CanceledError' || error.name === 'AbortError') {
      return Promise.reject(error);
    }
    
    const originalRequest = error.config;
    
    // Se for erro 401 (não autorizado) e não for um retry, tenta refresh token
    if (error.response?.status === 401 && !originalRequest._retry) {
      if (isRefreshing) {
        // Se já está fazendo refresh, adiciona à fila
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject });
        })
          .then(token => {
            originalRequest.headers.Authorization = `Bearer ${token}`;
            return api(originalRequest);
          })
          .catch(err => {
            return Promise.reject(err);
          });
      }
      
      originalRequest._retry = true;
      isRefreshing = true;
      
      const user = JSON.parse(localStorage.getItem('controle-se-user') || 'null');
      const refreshToken = user?.refreshToken;
      
      if (!refreshToken) {
        isRefreshing = false;
        processQueue({ message: 'Refresh token não encontrado' }, null);
        // Remove dados do usuário e redireciona para login
        localStorage.removeItem('controle-se-user');
        return Promise.reject({
          message: 'Sessão expirada. Faça login novamente.',
          status: 401,
          name: error.name,
        });
      }
      
      try {
        // Tenta renovar o token
        const response = await axios.post(`${API_BASE_URL}/auth/refresh`, {
          refreshToken: refreshToken
        });
        
        const { accessToken, refreshToken: newRefreshToken, user: userData } = response.data;
        
        // Atualiza os tokens no localStorage
        const updatedUser = {
          ...user,
          token: accessToken, // Mantém compatibilidade
          accessToken: accessToken,
          refreshToken: newRefreshToken,
          ...userData
        };
        localStorage.setItem('controle-se-user', JSON.stringify(updatedUser));
        
        // Atualiza o header da requisição original
        originalRequest.headers.Authorization = `Bearer ${accessToken}`;
        
        isRefreshing = false;
        processQueue(null, accessToken);
        
        // Retenta a requisição original
        return api(originalRequest);
      } catch (refreshError) {
        isRefreshing = false;
        processQueue({ message: 'Erro ao renovar token' }, null);
        
        // Remove dados do usuário em caso de falha no refresh
        localStorage.removeItem('controle-se-user');
        
        return Promise.reject({
          message: 'Sessão expirada. Faça login novamente.',
          status: 401,
          name: error.name,
        });
      }
    }
    
    // Tratamento de outros erros
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

