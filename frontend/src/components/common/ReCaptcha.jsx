import React, { forwardRef, useEffect, useImperativeHandle, useRef, useState } from 'react';

/**
 * Componente React para Google reCAPTCHA v2
 * 
 * Uso:
 * <ReCaptcha
 *   ref={captchaRef}
 *   siteKey="YOUR_SITE_KEY"
 *   onVerify={(token) => console.log(token)}
 *   onExpire={() => console.log('expired')}
 * />
 */
const ReCaptcha = forwardRef(function ReCaptcha({ siteKey, onVerify, onExpire, onError, theme = 'light', size = 'normal' }, ref) {
  const containerRef = useRef(null);
  const widgetIdRef = useRef(null);
  const [isLoaded, setIsLoaded] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    // Verifica se o script do reCAPTCHA está carregado
    const checkRecaptchaLoaded = () => {
      if (window.grecaptcha && window.grecaptcha.render) {
        setIsLoaded(true);
        return true;
      }
      return false;
    };

    // Função para renderizar o CAPTCHA
    const tryRender = () => {
      if (checkRecaptchaLoaded()) {
        renderCaptcha();
        return true;
      }
      return false;
    };

    // Tenta renderizar imediatamente se já está carregado
    if (tryRender()) {
      return;
    }

    // Aguarda o script carregar
    const interval = setInterval(() => {
      if (tryRender()) {
        clearInterval(interval);
      }
    }, 100);

    // Timeout após 10 segundos
    const timeout = setTimeout(() => {
      clearInterval(interval);
      if (!isLoaded) {
        setError('reCAPTCHA não pôde ser carregado. Verifique sua conexão.');
        if (onError) onError('timeout');
      }
    }, 10000);

    return () => {
      clearInterval(interval);
      clearTimeout(timeout);
    };
  }, []); // Executa apenas na montagem

  // useEffect que verifica quando o container está disponível (após renderização)
  useEffect(() => {
    // Se já tem widget, não precisa renderizar novamente
    if (widgetIdRef.current !== null) {
      return;
    }

    // Aguarda um pouco para garantir que o DOM foi atualizado
    const timer = setTimeout(() => {
      if (containerRef.current && window.grecaptcha && window.grecaptcha.render && !widgetIdRef.current) {
        renderCaptcha();
      }
    }, 100);

    return () => clearTimeout(timer);
  }, [siteKey, theme]); // Executa quando siteKey ou theme mudam, ou na montagem

  const renderCaptcha = () => {
    if (!containerRef.current || !window.grecaptcha || !siteKey) {
      return;
    }

    // Se já existe um widget, não renderiza novamente
    if (widgetIdRef.current !== null) {
      return;
    }

    try {
      // Renderiza o novo widget
      widgetIdRef.current = window.grecaptcha.render(containerRef.current, {
        sitekey: siteKey,
        theme: theme,
        size: size,
        callback: (token) => {
          if (onVerify) {
            onVerify(token);
          }
        },
        'expired-callback': () => {
          if (onExpire) {
            onExpire();
          }
        },
        'error-callback': () => {
          setError('Erro ao carregar reCAPTCHA');
          if (onError) {
            onError('render');
          }
        }
      });
    } catch (e) {
      // setError('Erro ao renderizar reCAPTCHA: ' + e.message);
      if (onError) {
        onError(e);
      }
    }
  };

  const reset = () => {
    if (widgetIdRef.current !== null && window.grecaptcha) {
      try {
        window.grecaptcha.reset(widgetIdRef.current);
        widgetIdRef.current = null; // Limpa a referência para permitir re-renderização
        setError(null);
        // Re-renderiza após reset
        setTimeout(() => {
          if (containerRef.current && window.grecaptcha) {
            renderCaptcha();
          }
        }, 100);
      } catch (e) {
        console.error('Erro ao resetar reCAPTCHA:', e);
        widgetIdRef.current = null; // Limpa mesmo em caso de erro
      }
    } else if (containerRef.current && window.grecaptcha && window.grecaptcha.render) {
      // Se não há widget mas o container está disponível, renderiza
      renderCaptcha();
    }
  };

  // Expõe método reset para uso externo via ref
  useImperativeHandle(ref, () => ({
    reset
  }));

  if (!siteKey) {
    return (
      <div className="text-sm text-gray-500 dark:text-gray-400">
        reCAPTCHA não configurado
      </div>
    );
  }

  return (
    <div>
      <div ref={containerRef} className="flex justify-center"></div>
      {error && (
        <div className="mt-2 text-sm text-red-600 dark:text-red-400">
          {error}
        </div>
      )}
    </div>
  );
});

export default ReCaptcha;

