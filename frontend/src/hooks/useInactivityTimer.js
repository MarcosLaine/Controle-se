import { useEffect, useRef } from 'react';

/**
 * Hook que monitora a inatividade do usuário e executa uma callback após um tempo determinado
 * @param {Function} onInactive - Callback executada quando o tempo de inatividade é atingido
 * @param {number} timeoutMinutes - Tempo em minutos antes de considerar inatividade (padrão: 15)
 */
export const useInactivityTimer = (onInactive, timeoutMinutes = 15) => {
  const timeoutRef = useRef(null);
  const timeoutMs = timeoutMinutes * 60 * 1000; // Converte minutos para milissegundos

  const resetTimer = () => {
    // Limpa o timer anterior
    if (timeoutRef.current) {
      clearTimeout(timeoutRef.current);
    }

    // Cria um novo timer
    timeoutRef.current = setTimeout(() => {
      onInactive();
    }, timeoutMs);
  };

  useEffect(() => {
    // Eventos que indicam atividade do usuário
    const events = [
      'mousedown',
      'mousemove',
      'keypress',
      'scroll',
      'touchstart',
      'click',
      'keydown'
    ];

    // Inicia o timer quando o componente monta
    resetTimer();

    // Adiciona listeners para os eventos de atividade
    events.forEach((event) => {
      window.addEventListener(event, resetTimer, true);
    });

    // Cleanup: remove listeners e limpa o timer
    return () => {
      events.forEach((event) => {
        window.removeEventListener(event, resetTimer, true);
      });
      if (timeoutRef.current) {
        clearTimeout(timeoutRef.current);
      }
    };
  }, [onInactive, timeoutMs]);
};
