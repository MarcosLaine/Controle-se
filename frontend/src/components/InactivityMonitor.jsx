import { useRef, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import { useInactivityTimer } from '../hooks/useInactivityTimer';

/**
 * Componente que monitora a inatividade do usuário e faz logout automático após 15 minutos
 */
export default function InactivityMonitor() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const handleInactivityRef = useRef(null);

  // Atualiza a referência quando o usuário muda
  useEffect(() => {
    handleInactivityRef.current = () => {
      if (user) {
        // Faz logout por inatividade
        logout(true);
        // Redireciona para login com parâmetro de inatividade
        navigate('/login?inactivity=true', { replace: true });
      }
    };
  }, [user, logout, navigate]);

  // Só monitora inatividade se o usuário estiver logado
  useInactivityTimer(() => {
    if (handleInactivityRef.current) {
      handleInactivityRef.current();
    }
  }, 15);

  return null; // Este componente não renderiza nada
}
