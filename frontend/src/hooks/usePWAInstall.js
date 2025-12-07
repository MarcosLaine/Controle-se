import { useState, useEffect, useRef } from 'react';

export function usePWAInstall() {
  const [deferredPrompt, setDeferredPrompt] = useState(null);
  const [isInstallable, setIsInstallable] = useState(false);
  const deferredPromptRef = useRef(null);

  useEffect(() => {
    // Verifica se já está instalado
    const isStandalone = window.matchMedia('(display-mode: standalone)').matches;
    const isIOS = /iPad|iPhone|iPod/.test(navigator.userAgent) && !window.MSStream;
    const isInStandaloneMode = ('standalone' in window.navigator) && window.navigator.standalone;

    if (isStandalone || isInStandaloneMode) {
      console.log('PWA já está instalado');
      setIsInstallable(false);
      return;
    }

    let currentDeferredPrompt = null;

    // Verifica se já foi capturado no script inline
    if (window.__deferredPrompt) {
      currentDeferredPrompt = window.__deferredPrompt;
      deferredPromptRef.current = window.__deferredPrompt;
      setDeferredPrompt(window.__deferredPrompt);
      setIsInstallable(true);
      console.log('deferredPrompt encontrado no window.__deferredPrompt (capturado no script inline)');
    }

    const handler = (e) => {
      e.preventDefault();
      currentDeferredPrompt = e;
      deferredPromptRef.current = e;
      setDeferredPrompt(e);
      window.__deferredPrompt = e; // Armazena também no window
      setIsInstallable(true);
      console.log('PWA install prompt disponível - deferredPrompt capturado e salvo');
    };

    // Listener para evento customizado do script inline
    const customHandler = (event) => {
      const e = event.detail;
      handler(e);
    };

    // Adiciona listeners
    window.addEventListener('beforeinstallprompt', handler);
    window.addEventListener('pwa-install-available', customHandler);

    // Verifica se o service worker está registrado
    const checkPWAInstallable = () => {
      if ('serviceWorker' in navigator) {
        navigator.serviceWorker.getRegistrations().then(registrations => {
          if (registrations.length > 0) {
            console.log('Service Worker registrado, PWA disponível');
            // Verifica se tem manifest válido (mas não bloqueia se falhar)
            fetch('/manifest.webmanifest')
              .then(res => {
                if (res.ok) {
                  const contentType = res.headers.get('content-type');
                  // Verifica se é JSON ou manifest
                  if (contentType && (contentType.includes('json') || contentType.includes('manifest'))) {
                    return res.json();
                  } else {
                    // Se não for JSON, pode ser HTML (erro do servidor)
                    throw new Error('Servidor retornou HTML em vez de manifest');
                  }
                }
                throw new Error('Manifest não encontrado ou inválido');
              })
              .then(manifest => {
                console.log('Manifest válido encontrado:', manifest.name);
                // Se tem service worker E manifest, pode ser instalável
                // Mostra botão mesmo sem deferredPrompt (o navegador pode mostrar prompt ao clicar)
                const isChrome = /Chrome/.test(navigator.userAgent) && /Google Inc/.test(navigator.vendor);
                const isEdge = /Edg/.test(navigator.userAgent);
                
                // Mostra botão se:
                // 1. Tem deferredPrompt (melhor caso)
                // 2. É iOS (instalação manual)
                // 3. É Chrome/Edge (pode ter prompt na barra ou podemos tentar)
                if (currentDeferredPrompt || isIOS || isChrome || isEdge) {
                  setIsInstallable(true);
                  console.log('Botão de instalação ativado - SW + Manifest válidos');
                }
              })
              .catch(err => {
                console.warn('Erro ao verificar manifest (não crítico):', err.message);
                // Mesmo com erro no manifest, se tem SW pode tentar
                // O manifest pode não estar acessível, mas o PWA ainda pode funcionar
                const isChrome = /Chrome/.test(navigator.userAgent) && /Google Inc/.test(navigator.vendor);
                const isEdge = /Edg/.test(navigator.userAgent);
                if (currentDeferredPrompt || isIOS || isChrome || isEdge) {
                  setIsInstallable(true);
                  console.log('Botão de instalação ativado - SW disponível (manifest não verificado)');
                }
              });
          } else {
            console.log('Service Worker não registrado ainda');
          }
        }).catch(err => {
          console.error('Erro ao verificar service worker:', err);
        });
      } else {
        console.log('Service Worker não suportado neste navegador');
      }
    };

    // Para iOS, sempre mostra o botão (instalação manual)
    if (isIOS && !isInStandaloneMode) {
      setIsInstallable(true);
    }

    // Verifica imediatamente
    checkPWAInstallable();

    // Verifica novamente após delays (o beforeinstallprompt pode demorar)
    const timeout1 = setTimeout(() => {
      checkPWAInstallable();
      if (currentDeferredPrompt) {
        console.log('deferredPrompt encontrado após 1s');
      }
    }, 1000);
    
    const timeout2 = setTimeout(() => {
      checkPWAInstallable();
      if (currentDeferredPrompt) {
        console.log('deferredPrompt encontrado após 3s');
      } else {
        console.log('deferredPrompt ainda não disponível após 3s');
      }
    }, 3000);

    return () => {
      window.removeEventListener('beforeinstallprompt', handler);
      window.removeEventListener('pwa-install-available', customHandler);
      clearTimeout(timeout1);
      clearTimeout(timeout2);
    };
  }, []);

  const handleInstall = async () => {
    // Usa o ref como fallback caso o state não esteja atualizado
    const prompt = deferredPrompt || deferredPromptRef.current;
    
    console.log('handleInstall chamado');
    console.log('deferredPrompt (state):', deferredPrompt ? 'disponível' : 'não disponível');
    console.log('deferredPromptRef.current:', deferredPromptRef.current ? 'disponível' : 'não disponível');
    console.log('prompt final:', prompt ? 'disponível' : 'não disponível');
    
    if (prompt) {
      try {
        console.log('Chamando prompt.prompt()...');
        // Usa o prompt nativo se disponível
        await prompt.prompt();
        console.log('Prompt exibido, aguardando escolha do usuário...');
        const { outcome } = await prompt.userChoice;
        
        console.log('Resultado da instalação:', outcome);
        if (outcome === 'accepted') {
          setIsInstallable(false);
          console.log('PWA instalado com sucesso!');
          // Limpa o prompt após instalação
          setDeferredPrompt(null);
          deferredPromptRef.current = null;
        } else {
          console.log('Usuário cancelou a instalação');
        }
      } catch (error) {
        console.error('Erro ao chamar prompt.prompt():', error);
        console.error('Detalhes do erro:', error.message, error.stack);
        // Se o prompt falhar, tenta métodos alternativos
        tryAlternativeInstall();
      }
    } else {
      console.log('Nenhum prompt disponível, tentando verificar se pode instalar diretamente...');
      
      // Tenta verificar se o navegador pode instalar mesmo sem deferredPrompt
      const isChrome = /Chrome/.test(navigator.userAgent) && /Google Inc/.test(navigator.vendor);
      const isEdge = /Edg/.test(navigator.userAgent);
      const isIOS = /iPad|iPhone|iPod/.test(navigator.userAgent) && !window.MSStream;
      
      if (isIOS) {
        tryAlternativeInstall();
      } else if (isChrome || isEdge) {
        // Chrome/Edge: tenta verificar se há uma forma de instalar
        // Verifica se o app pode ser instalado verificando o manifest
        fetch('/manifest.webmanifest')
          .then(res => res.json())
          .then(manifest => {
            // Tenta uma última verificação se o evento pode ser disparado
            // Infelizmente, sem deferredPrompt, não podemos forçar
            // Mas podemos dar instruções mais específicas
            const message = 'Para instalar o aplicativo Controle-se:\n\n' +
              'OPÇÃO 1 (Recomendado):\n' +
              '• Procure o ícone de instalação (⊕) na barra de endereços do navegador\n' +
              '• Clique nele para instalar\n\n' +
              'OPÇÃO 2:\n' +
              '• Clique no menu do navegador (⋮ no canto superior direito)\n' +
              '• Procure por "Instalar Controle-se" ou "Instalar aplicativo"\n\n' +
              'NOTA: Se o ícone não aparecer, pode ser que:\n' +
              '• Você já rejeitou a instalação antes (tente em uma janela anônima)\n' +
              '• O aplicativo já está instalado\n' +
              '• O navegador precisa de mais tempo para detectar o PWA';
            
            if (confirm(message + '\n\nDeseja abrir uma nova janela anônima para tentar novamente?')) {
              window.open(window.location.href, '_blank');
            }
          })
          .catch(() => {
            alert('Para instalar:\n1. Clique no ícone de instalação (⊕) na barra de endereços\n2. Ou use o menu (⋮) > "Instalar Controle-se"');
          });
      } else {
        tryAlternativeInstall();
      }
    }
  };

  const checkIfCanInstall = () => {
    // Verifica se tem service worker e manifest
    const hasServiceWorker = 'serviceWorker' in navigator;
    const isStandalone = window.matchMedia('(display-mode: standalone)').matches;
    const isInStandaloneMode = ('standalone' in window.navigator) && window.navigator.standalone;
    
    return hasServiceWorker && !isStandalone && !isInStandaloneMode;
  };

  const tryAlternativeInstall = () => {
    const isIOS = /iPad|iPhone|iPod/.test(navigator.userAgent) && !window.MSStream;
    const isChrome = /Chrome/.test(navigator.userAgent) && /Google Inc/.test(navigator.vendor);
    const isEdge = /Edg/.test(navigator.userAgent);
    const isFirefox = /Firefox/.test(navigator.userAgent);
    const isSafari = /Safari/.test(navigator.userAgent) && !isChrome;

    if (isIOS) {
      // iOS: mostra instruções
      alert('Para instalar no iOS:\n1. Toque no botão de compartilhar (□↑)\n2. Selecione "Adicionar à Tela de Início"');
    } else if (isChrome || isEdge) {
      // Chrome/Edge: tenta abrir o menu de instalação
      // Infelizmente não há API direta, mas podemos mostrar instruções
      alert('Para instalar:\n1. Clique no ícone de instalação (⊕) na barra de endereços\n2. Ou use o menu (⋮) > "Instalar Controle-se"');
    } else if (isFirefox) {
      // Firefox: mostra instruções
      alert('Para instalar no Firefox:\n1. Clique no menu (☰)\n2. Selecione "Instalar" ou "Adicionar à Tela Inicial"');
    } else if (isSafari) {
      // Safari: mostra instruções
      alert('Para instalar no Safari:\n1. Clique em Compartilhar\n2. Selecione "Adicionar à Tela de Início"');
    } else {
      // Navegador desconhecido
      alert('Use o menu do navegador para instalar o aplicativo');
    }
  };

  return { isInstallable, handleInstall };
}

