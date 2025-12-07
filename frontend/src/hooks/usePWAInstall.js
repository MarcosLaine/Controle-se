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
      console.log('✅ deferredPrompt encontrado no window.__deferredPrompt (capturado no script inline)');
    }
    
    // Função de diagnóstico para verificar critérios do PWA
    const diagnosePWA = async () => {
      console.log('🔍 Diagnóstico PWA:');
      const diagnostics = {
        https: window.location.protocol === 'https:',
        serviceWorker: 'serviceWorker' in navigator,
        manifest: false,
        icons: false,
        standalone: window.matchMedia('(display-mode: standalone)').matches
      };
      
      try {
        const manifestRes = await fetch('/manifest.webmanifest', { cache: 'no-cache' });
        if (manifestRes.ok) {
          const manifest = await manifestRes.json();
          diagnostics.manifest = !!manifest;
          diagnostics.icons = manifest.icons && manifest.icons.length >= 2;
          console.log('  ✅ Manifest:', manifest.name);
          console.log('  ✅ Ícones:', manifest.icons?.length || 0);
        }
      } catch (e) {
        console.error('  ❌ Erro ao verificar manifest:', e);
      }
      
      if ('serviceWorker' in navigator) {
        const registrations = await navigator.serviceWorker.getRegistrations();
        console.log('  ✅ Service Workers registrados:', registrations.length);
        registrations.forEach((reg, i) => {
          console.log(`    SW ${i + 1}:`, reg.active?.state || 'unknown');
        });
      }
      
      console.log('Critérios atendidos:', diagnostics);
      console.log('Status:', Object.values(diagnostics).filter(v => v).length, 'de', Object.keys(diagnostics).length);
      
      return diagnostics;
    };
    
    // Executa diagnóstico após um delay
    setTimeout(diagnosePWA, 2000);

    const handler = (e) => {
      e.preventDefault();
      currentDeferredPrompt = e;
      deferredPromptRef.current = e;
      setDeferredPrompt(e);
      window.__deferredPrompt = e; // Armazena também no window
      setIsInstallable(true);
      console.log('✅ PWA install prompt disponível - deferredPrompt capturado e salvo');
      console.log('Event details:', {
        platforms: e.platforms,
        userChoice: 'pending'
      });
    };

    // Listener para evento customizado do script inline
    const customHandler = (event) => {
      const e = event.detail;
      handler(e);
    };

    // Adiciona listeners
    window.addEventListener('beforeinstallprompt', handler);
    window.addEventListener('pwa-install-available', customHandler);

    // Verifica se o service worker está registrado e ATIVO
    const checkPWAInstallable = () => {
      if ('serviceWorker' in navigator) {
        navigator.serviceWorker.getRegistrations().then(registrations => {
          if (registrations.length > 0) {
            const registration = registrations[0];
            console.log('Service Worker registrado, PWA disponível');
            console.log('Service Worker state:', registration.active?.state || 'unknown');
            
            // Verifica se o SW está ativo (não apenas registrado)
            if (registration.active && registration.active.state === 'activated') {
              console.log('Service Worker está ATIVO');
            } else {
              console.warn('Service Worker registrado mas não está ativo ainda');
            }
            
            // Verifica se tem manifest válido (mas não bloqueia se falhar)
            fetch('/manifest.webmanifest', { cache: 'no-cache' })
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
                console.log('Manifest icons:', manifest.icons?.length || 0);
                
                // Verifica se os ícones estão acessíveis
                if (manifest.icons && manifest.icons.length > 0) {
                  const icon192 = manifest.icons.find(icon => icon.sizes === '192x192');
                  const icon512 = manifest.icons.find(icon => icon.sizes === '512x512');
                  
                  if (icon192 && icon512) {
                    console.log('Ícones válidos encontrados: 192x192 e 512x512');
                  } else {
                    console.warn('Ícones obrigatórios não encontrados no manifest');
                  }
                }
                
                // Verifica critérios para beforeinstallprompt
                const hasValidStartUrl = manifest.start_url && manifest.start_url.startsWith('/');
                const hasValidScope = manifest.scope && manifest.scope.startsWith('/');
                const hasValidDisplay = manifest.display && ['standalone', 'fullscreen'].includes(manifest.display);
                
                console.log('Critérios PWA:', {
                  start_url: hasValidStartUrl,
                  scope: hasValidScope,
                  display: hasValidDisplay,
                  icons: manifest.icons?.length >= 2
                });
                
                // Mostra botão APENAS se:
                // 1. Tem deferredPrompt (melhor caso - instalação direta)
                // 2. É iOS (instalação manual sempre disponível)
                // NÃO mostra apenas por ter SW + Manifest, pois sem deferredPrompt não podemos instalar
                if (currentDeferredPrompt || isIOS) {
                  setIsInstallable(true);
                  console.log('Botão de instalação ativado - SW + Manifest válidos' + (currentDeferredPrompt ? ' + deferredPrompt disponível' : ' (iOS)'));
                } else {
                  console.log('PWA configurado mas deferredPrompt não disponível - botão não será mostrado');
                  console.log('Dica: O beforeinstallprompt pode não aparecer se:');
                  console.log('  - O usuário já rejeitou o prompt antes');
                  console.log('  - O app já está instalado');
                  console.log('  - O navegador precisa de mais tempo para avaliar o PWA');
                }
              })
              .catch(err => {
                console.warn('Erro ao verificar manifest (não crítico):', err.message);
                // Mesmo com erro no manifest, se tem SW pode tentar
                // O manifest pode não estar acessível, mas o PWA ainda pode funcionar
                // Mas só mostra se tiver deferredPrompt ou for iOS
                if (currentDeferredPrompt || isIOS) {
                  setIsInstallable(true);
                  console.log('Botão de instalação ativado - SW disponível (manifest não verificado)' + (currentDeferredPrompt ? ' + deferredPrompt disponível' : ' (iOS)'));
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
    // Mas só se não estiver instalado
    if (isIOS && !isInStandaloneMode && !isStandalone) {
      setIsInstallable(true);
      console.log('Botão de instalação ativado para iOS');
    }

    // Verifica imediatamente
    checkPWAInstallable();

    // Verifica novamente após delays (o beforeinstallprompt pode demorar)
    // O evento pode levar até 30 segundos para ser disparado em alguns casos
    const timeout1 = setTimeout(() => {
      checkPWAInstallable();
      if (currentDeferredPrompt) {
        console.log('deferredPrompt encontrado após 1s');
      } else {
        console.log('Aguardando beforeinstallprompt... (1s)');
      }
    }, 1000);
    
    const timeout2 = setTimeout(() => {
      checkPWAInstallable();
      if (currentDeferredPrompt) {
        console.log('deferredPrompt encontrado após 3s');
      } else {
        console.log('Aguardando beforeinstallprompt... (3s)');
      }
    }, 3000);
    
    const timeout3 = setTimeout(() => {
      checkPWAInstallable();
      if (currentDeferredPrompt) {
        console.log('deferredPrompt encontrado após 10s');
      } else {
        console.log('deferredPrompt ainda não disponível após 10s');
        console.log('Possíveis causas:');
        console.log('  1. Usuário já rejeitou o prompt antes (tente janela anônima)');
        console.log('  2. App já está instalado');
        console.log('  3. Navegador ainda está avaliando o PWA');
        console.log('  4. Algum critério do PWA não foi atendido');
      }
    }, 10000);

    return () => {
      window.removeEventListener('beforeinstallprompt', handler);
      window.removeEventListener('pwa-install-available', customHandler);
      clearTimeout(timeout1);
      clearTimeout(timeout2);
      clearTimeout(timeout3);
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
        // Chrome/Edge: sem deferredPrompt, não podemos instalar programaticamente
        // Mas podemos dar instruções claras
        const message = 'Para instalar o aplicativo Controle-se:\n\n' +
          'OPÇÃO 1 (Recomendado):\n' +
          '• Procure o ícone de instalação (⊕) na barra de endereços do navegador\n' +
          '• Clique nele para instalar\n\n' +
          'OPÇÃO 2:\n' +
          '• Clique no menu do navegador (⋮ no canto superior direito)\n' +
          '• Procure por "Instalar Controle-se" ou "Instalar aplicativo"\n\n' +
          'NOTA: Se o ícone não aparecer, pode ser que:\n' +
          '• Você já rejeitou a instalação antes (tente em uma janela anônima/privada)\n' +
          '• O aplicativo já está instalado\n' +
          '• O navegador precisa de mais tempo para detectar o PWA\n\n' +
          'Dica: Tente limpar os dados do site e recarregar a página.';
        
        alert(message);
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

