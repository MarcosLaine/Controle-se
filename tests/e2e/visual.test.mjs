import { test, expect } from '@playwright/test';
import { randomBytes } from 'crypto';

const BASE_URL = process.env.CONTROLE_SE_BASE_URL || 'http://localhost:8080';

// Gera email único para cada execução de teste
const generateUniqueEmail = () => {
  const timestamp = Date.now();
  const random = randomBytes(4).toString('hex');
  return `teste-${timestamp}-${random}@teste.com`;
};

const generateUniqueName = (prefix) => {
  const timestamp = Date.now();
  const random = randomBytes(2).toString('hex');
  return `${prefix} ${timestamp}-${random}`;
};

const today = () => new Date().toISOString().slice(0, 10);

// Configuração para ver os testes acontecendo
test.use({
  headless: false, // Mostra o navegador
  slowMo: 500, // Delay de 500ms entre ações para ver melhor
  video: 'on', // Grava vídeo dos testes
  screenshot: 'on', // Tira screenshots
  viewport: { width: 1280, height: 720 },
});

test.describe('Testes Visuais E2E - Controle-se', () => {
  let testState = {
    email: null,
    password: 'Teste@123',
    name: null,
  };

  test.beforeAll(async () => {
    testState.email = generateUniqueEmail();
    testState.name = generateUniqueName('Usuário Teste');
  });

  test('Fluxo Completo Visual - Do Registro ao Dashboard', async ({ page }) => {
    // 1. REGISTRO
    await page.goto(`${BASE_URL}/login`);
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(1000); // Aguarda página carregar completamente
    
    console.log('📝 Preenchendo formulário de registro...');
    // Muda para aba de registro - busca pelo botão de tab
    const registerTab = page.locator('button:has-text("Register"), button:has-text("Registrar")').filter({ hasNotText: /login|entrar/i });
    await registerTab.click();
    await page.waitForTimeout(1000); // Aguarda formulário aparecer
    
    // Verifica se o formulário de registro está visível
    const registerForm = page.locator('form').filter({ has: page.locator('input[type="text"], input[name="name"]') });
    await registerForm.waitFor({ state: 'visible', timeout: 5000 });
    
    // Preenche formulário - busca inputs dentro do formulário de registro
    const nameInput = registerForm.locator('input[type="text"], input[name="name"]').first();
    await nameInput.waitFor({ state: 'visible' });
    await nameInput.fill(testState.name);
    console.log(`  → Nome preenchido: ${testState.name}`);
    
    const emailInput = registerForm.locator('input[type="email"]').first();
    await emailInput.fill(testState.email);
    console.log(`  → Email preenchido: ${testState.email}`);
    
    const passwordInput = registerForm.locator('input[type="password"]').first();
    await passwordInput.fill(testState.password);
    console.log('  → Senha preenchida');
    
    // Verifica se há CAPTCHA necessário
    const captchaVisible = await page.locator('[data-testid="recaptcha"], iframe[src*="recaptcha"]').isVisible().catch(() => false);
    if (captchaVisible) {
      console.log('⚠️ CAPTCHA detectado - aguardando resolução manual ou automática...');
      await page.waitForTimeout(5000); // Aguarda CAPTCHA ser resolvido
    }
    
    console.log('✅ Clicando em registrar...');
    // Clica no botão de submit do formulário de registro
    const submitButton = registerForm.locator('button[type="submit"]').first();
    
    // Aguarda a resposta do registro
    const [response] = await Promise.all([
      page.waitForResponse(resp => resp.url().includes('/auth/register') && resp.status() === 201, { timeout: 15000 }).catch(() => null),
      submitButton.click()
    ]);
    
    if (response) {
      console.log('✅ Registro enviado com sucesso!');
    } else {
      console.log('⚠️ Aguardando resposta do servidor...');
    }
    
    // Aguarda redirecionamento - verifica tanto URL quanto elementos do dashboard
    console.log('⏳ Aguardando redirecionamento...');
    await page.waitForTimeout(2000); // Aguarda processamento
    
    try {
      // Tenta múltiplas formas de detectar o dashboard
      await Promise.race([
        page.waitForURL('**/dashboard', { timeout: 15000 }),
        page.waitForURL('**/', { timeout: 15000 }), // Pode redirecionar para /
        page.waitForSelector('nav, aside', { timeout: 15000 }),
        page.waitForSelector('h2, [data-section]', { timeout: 15000 }),
        page.waitForFunction(() => {
          return window.location.pathname === '/dashboard' || 
                 window.location.pathname === '/' ||
                 document.querySelector('nav, aside') !== null;
        }, { timeout: 15000 })
      ]);
      console.log('🎉 Registro concluído! Redirecionado para dashboard.');
      console.log(`📍 URL atual: ${page.url()}`);
    } catch (error) {
      // Se falhar, tira screenshot e mostra o que está na página
      await page.screenshot({ path: 'test-results/register-failed.png', fullPage: true });
      const currentUrl = page.url();
      const pageTitle = await page.title();
      const hasError = await page.locator('text=/error|erro|falha/i').isVisible().catch(() => false);
      const hasCaptcha = await page.locator('[data-testid="recaptcha"], iframe[src*="recaptcha"]').isVisible().catch(() => false);
      
      console.error(`❌ Falha ao redirecionar para dashboard.`);
      console.error(`   URL atual: ${currentUrl}`);
      console.error(`   Título: ${pageTitle}`);
      console.error(`   Tem erro visível: ${hasError}`);
      console.error(`   Tem CAPTCHA: ${hasCaptcha}`);
      
      if (hasCaptcha) {
        throw new Error('CAPTCHA requerido - não é possível continuar automaticamente');
      }
      
      throw new Error(`Falha ao redirecionar para dashboard. URL atual: ${currentUrl}`);
    }

    // 2. DASHBOARD - Ver overview
    await page.waitForLoadState('networkidle');
    console.log('📊 Visualizando dashboard...');
    await page.waitForTimeout(2000); // Pausa para ver o dashboard

    // 3. CRIAR CONTA
    console.log('💰 Navegando para contas...');
    // O sidebar usa botões com texto traduzido - busca por texto ou ícone Building2
    await page.click('nav button:has-text("Accounts"), nav button:has-text("Contas"), button:has([data-section="accounts"])');
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(2000); // Aguarda carregar a seção

    console.log('➕ Criando nova conta...');
    // Aguarda a seção de contas carregar completamente
    await page.waitForSelector('h2:has-text("Accounts"), h2:has-text("Contas"), h2:has-text("Conta")', { timeout: 10000 });
    await page.waitForTimeout(1000); // Aguarda renderização completa
    
    // Busca pelo botão de adicionar conta - mais específico, próximo ao título
    // O botão está no mesmo container que o h2, então busca por proximidade
    const accountsSection = page.locator('div:has(h2:has-text("Accounts")), div:has(h2:has-text("Contas"))');
    const addAccountButton = accountsSection.locator('button.btn-primary').first();
    
    await addAccountButton.waitFor({ state: 'visible', timeout: 10000 });
    console.log('  → Botão encontrado, clicando...');
    await addAccountButton.click();
    
    // Aguarda modal abrir - o modal é uma div com fixed inset-0 e bg-black bg-opacity-50
    console.log('  → Aguardando modal abrir...');
    await page.waitForSelector('div.fixed.inset-0:has(input[name="nome"]), div.fixed.inset-0:has(input[name="name"]), div:has(input[name="nome"]):has(button[type="submit"])', { timeout: 10000 });
    await page.waitForTimeout(1000); // Aguarda animação do modal
    
    const accountName = generateUniqueName('Conta Teste');
    console.log(`  → Preenchendo formulário: ${accountName}`);
    
    // Preenche o formulário - busca dentro do modal (div com fixed inset-0 que contém o formulário)
    const modal = page.locator('div.fixed.inset-0').filter({ has: page.locator('input[name="nome"], input[name="name"]') }).first();
    const accountNameInput = modal.locator('input[name="nome"], input[name="name"]').first();
    await accountNameInput.waitFor({ state: 'visible' });
    await accountNameInput.fill(accountName);
    
    // Seleciona tipo de conta
    const typeSelect = modal.locator('select[name="tipo"], select[name="type"]').first();
    if (await typeSelect.isVisible({ timeout: 2000 }).catch(() => false)) {
      await typeSelect.selectOption({ index: 0 }); // Primeira opção geralmente é Corrente
      console.log('  → Tipo de conta selecionado');
    }
    
    // Preenche saldo - busca pelo input de saldo
    const balanceInput = modal.locator('input[name="saldoInicial"], input[name="balance"], input[placeholder*="0"]').first();
    await balanceInput.waitFor({ state: 'visible', timeout: 5000 });
    await balanceInput.fill('5000');
    console.log('  → Saldo preenchido: 5000');
    
    console.log('✅ Salvando conta...');
    // Busca o botão de submit dentro do modal - geralmente é o último botão ou o que não é cancel
    const saveButton = modal.locator('button[type="submit"], button.btn-primary:has-text("Salvar"), button.btn-primary:has-text("Save"), button.btn-primary:has-text("Criar"), button.btn-primary:has-text("Create")').first();
    await saveButton.waitFor({ state: 'visible', timeout: 5000 });
    await saveButton.click();
    await page.waitForTimeout(2000);
    console.log(`✅ Conta "${accountName}" criada!`);

    // 4. CRIAR CATEGORIA
    console.log('📁 Navegando para categorias...');
    await page.click('nav button:has-text("Categories"), nav button:has-text("Categorias"), button:has([data-section="categories"])');
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(2000);

    console.log('➕ Criando nova categoria...');
    // Busca pelo botão de adicionar categoria
    await page.click('button:has-text("New"), button:has-text("Nova"), button.btn-primary');
    await page.waitForTimeout(1000);
    
    const categoryName = generateUniqueName('Categoria Teste');
    await page.fill('input[name="name"], input[placeholder*="nome" i], input[placeholder*="name" i]', categoryName);
    
    console.log('✅ Salvando categoria...');
    await page.click('button:has-text("Save"), button:has-text("Salvar"), button[type="submit"]:not([disabled])');
    await page.waitForTimeout(2000);
    console.log(`✅ Categoria "${categoryName}" criada!`);

    // 5. CRIAR GASTO
    console.log('💸 Navegando para transações...');
    await page.click('nav button:has-text("Transactions"), nav button:has-text("Transações"), button:has([data-section="transactions"])');
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(2000);

    console.log('➕ Criando novo gasto...');
    // Aguarda seção carregar
    await page.waitForSelector('h2:has-text("Transactions"), h2:has-text("Transações")', { timeout: 10000 });
    await page.waitForTimeout(1000);
    
    // Busca pelo botão de nova despesa - dentro da seção de transações
    const transactionsSection = page.locator('div:has(h2:has-text("Transactions")), div:has(h2:has-text("Transações"))');
    const addExpenseButton = transactionsSection.locator('button:has-text("New Expense"), button:has-text("Nova Despesa"), button.btn-secondary').first();
    await addExpenseButton.waitFor({ state: 'visible', timeout: 10000 });
    await addExpenseButton.click();
    
    // Aguarda modal abrir
    await page.waitForSelector('div.fixed.inset-0:has(input[name="description"])', { timeout: 10000 });
    await page.waitForTimeout(1000);
    
    const expenseModal = page.locator('div.fixed.inset-0').filter({ has: page.locator('input[name="description"]') }).first();
    await expenseModal.locator('input[name="description"]').first().fill('Gasto Teste Visual');
    await expenseModal.locator('input[name="value"], input[type="number"]').first().fill('100');
    await expenseModal.locator('input[type="date"]').first().fill(today());
    
    console.log('✅ Salvando gasto...');
    await expenseModal.locator('button[type="submit"], button.btn-primary:has-text("Salvar")').first().click();
    await page.waitForTimeout(2000);
    console.log('✅ Gasto criado!');

    // 6. CRIAR RECEITA
    console.log('💰 Criando receita...');
    // Busca pelo botão de nova receita - dentro da seção de transações
    const addIncomeButton = transactionsSection.locator('button:has-text("New Income"), button:has-text("Nova Receita"), button.btn-primary').first();
    await addIncomeButton.waitFor({ state: 'visible', timeout: 10000 });
    await addIncomeButton.click();
    
    // Aguarda modal abrir
    await page.waitForSelector('div.fixed.inset-0:has(input[name="description"])', { timeout: 10000 });
    await page.waitForTimeout(1000);
    
    const incomeModal = page.locator('div.fixed.inset-0').filter({ has: page.locator('input[name="description"]') }).first();
    await incomeModal.locator('input[name="description"]').first().fill('Receita Teste Visual');
    await incomeModal.locator('input[name="value"], input[type="number"]').first().fill('5000');
    await incomeModal.locator('input[type="date"]').first().fill(today());
    
    console.log('✅ Salvando receita...');
    await incomeModal.locator('button[type="submit"], button.btn-primary:has-text("Salvar")').first().click();
    await page.waitForTimeout(2000);
    console.log('✅ Receita criada!');

    // 7. VER INVESTIMENTOS
    console.log('📈 Navegando para investimentos...');
    await page.click('nav button:has-text("Investments"), nav button:has-text("Investimentos"), button:has([data-section="investments"])');
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(2000);
    console.log('✅ Página de investimentos carregada!');

    // 8. VER RELATÓRIOS
    console.log('📊 Navegando para relatórios...');
    await page.click('nav button:has-text("Reports"), nav button:has-text("Relatórios"), button:has([data-section="reports"])');
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(2000);
    console.log('✅ Página de relatórios carregada!');

    // 9. VOLTAR AO DASHBOARD
    console.log('🏠 Voltando ao dashboard...');
    await page.click('nav button:has-text("Overview"), nav button:has-text("Início"), button:has([data-section="overview"])');
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(3000);
    console.log('✅ Dashboard visualizado novamente!');

    console.log('🎉 Teste visual completo!');
  });

  test('Criar e Editar Conta - Visual', async ({ page }) => {
    await page.goto(`${BASE_URL}/login`);
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(1000);
    
    // Login
    console.log('🔐 Fazendo login...');
    const loginForm = page.locator('form').filter({ has: page.locator('input[type="email"]') });
    await loginForm.locator('input[type="email"]').fill(testState.email);
    await loginForm.locator('input[type="password"]').fill(testState.password);
    
    // Verifica se há CAPTCHA necessário
    const captchaVisible = await page.locator('[data-testid="recaptcha"], iframe[src*="recaptcha"]').isVisible().catch(() => false);
    if (captchaVisible) {
      console.log('⚠️ CAPTCHA detectado - aguardando resolução...');
      await page.waitForTimeout(5000);
    }
    
    // Aguarda resposta do login
    const [loginResponse] = await Promise.all([
      page.waitForResponse(resp => resp.url().includes('/auth/login') && resp.status() === 200, { timeout: 15000 }).catch(() => null),
      loginForm.locator('button[type="submit"]').click()
    ]);
    
    if (loginResponse) {
      console.log('✅ Login enviado com sucesso!');
    }
    
    // Aguarda redirecionamento
    console.log('⏳ Aguardando redirecionamento após login...');
    await page.waitForTimeout(2000);
    
    try {
      await Promise.race([
        page.waitForURL('**/dashboard', { timeout: 15000 }),
        page.waitForURL('**/', { timeout: 15000 }),
        page.waitForSelector('nav, aside', { timeout: 15000 }),
        page.waitForFunction(() => {
          return window.location.pathname === '/dashboard' || 
                 window.location.pathname === '/' ||
                 document.querySelector('nav, aside') !== null;
        }, { timeout: 15000 })
      ]);
      console.log('✅ Login realizado com sucesso!');
      console.log(`📍 URL atual: ${page.url()}`);
    } catch (error) {
      await page.screenshot({ path: 'test-results/login-failed.png', fullPage: true });
      const currentUrl = page.url();
      const hasCaptcha = await page.locator('[data-testid="recaptcha"], iframe[src*="recaptcha"]').isVisible().catch(() => false);
      
      if (hasCaptcha) {
        throw new Error('CAPTCHA requerido - não é possível continuar automaticamente');
      }
      
      throw new Error(`Falha ao fazer login. URL atual: ${currentUrl}`);
    }
    
    // Navegar para contas
    await page.click('nav button:has-text("Accounts"), nav button:has-text("Contas")');
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(2000);

    // Criar conta
    console.log('➕ Criando conta para editar...');
    await page.waitForSelector('h2:has-text("Accounts"), h2:has-text("Contas")', { timeout: 10000 });
    await page.waitForTimeout(1000);
    
    const accountsSection = page.locator('div:has(h2:has-text("Accounts")), div:has(h2:has-text("Contas"))');
    const addAccountButton = accountsSection.locator('button.btn-primary').first();
    await addAccountButton.waitFor({ state: 'visible', timeout: 10000 });
    await addAccountButton.click();
    
    // Aguarda modal abrir
    console.log('  → Aguardando modal abrir...');
    await page.waitForSelector('div.fixed.inset-0:has(input[name="nome"]), div.fixed.inset-0:has(input[name="name"])', { timeout: 10000 });
    await page.waitForTimeout(1000);
    
    const modal = page.locator('div.fixed.inset-0').filter({ has: page.locator('input[name="nome"], input[name="name"]') }).first();
    const accountName = generateUniqueName('Conta Editar');
    await modal.locator('input[name="nome"], input[name="name"]').first().fill(accountName);
    
    const typeSelect = modal.locator('select[name="tipo"], select[name="type"]').first();
    if (await typeSelect.isVisible({ timeout: 2000 }).catch(() => false)) {
      await typeSelect.selectOption({ index: 0 });
    }
    
    await modal.locator('input[name="saldoInicial"], input[name="balance"]').first().fill('3000');
    await modal.locator('button[type="submit"], button.btn-primary:has-text("Salvar")').first().click();
    await page.waitForTimeout(2000);

    // Editar conta - clica no card da conta ou botão de editar
    console.log('✏️ Editando conta...');
    await page.click(`text=${accountName}`, { timeout: 5000 }).catch(() => {
      // Se não encontrar pelo texto, tenta pelo botão de editar
      return page.click(`button:has-text("${accountName}") + button, [aria-label*="Edit"], button:has(svg)`);
    });
    await page.waitForTimeout(1000);
    await page.fill('input[name="nome"], input[name="name"]:visible', `${accountName} - Editada`);
    await page.fill('input[name="saldoInicial"], input[name="balance"]:visible', '4000');
    await page.click('button:has-text("Save"), button:has-text("Salvar"), button[type="submit"]:not([disabled])');
    await page.waitForTimeout(2000);
    console.log('✅ Conta editada!');
  });

  test('Criar Gasto Parcelado - Visual', async ({ page }) => {
    await page.goto(`${BASE_URL}/login`);
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(1000);
    
    // Login
    console.log('🔐 Fazendo login...');
    const loginForm = page.locator('form').filter({ has: page.locator('input[type="email"]') });
    await loginForm.locator('input[type="email"]').fill(testState.email);
    await loginForm.locator('input[type="password"]').fill(testState.password);
    
    // Verifica se há CAPTCHA necessário
    const captchaVisible = await page.locator('[data-testid="recaptcha"], iframe[src*="recaptcha"]').isVisible().catch(() => false);
    if (captchaVisible) {
      console.log('⚠️ CAPTCHA detectado - aguardando resolução...');
      await page.waitForTimeout(5000);
    }
    
    // Aguarda resposta do login
    const [loginResponse] = await Promise.all([
      page.waitForResponse(resp => resp.url().includes('/auth/login') && resp.status() === 200, { timeout: 15000 }).catch(() => null),
      loginForm.locator('button[type="submit"]').click()
    ]);
    
    if (loginResponse) {
      console.log('✅ Login enviado com sucesso!');
    }
    
    // Aguarda redirecionamento
    console.log('⏳ Aguardando redirecionamento após login...');
    await page.waitForTimeout(2000);
    
    try {
      await Promise.race([
        page.waitForURL('**/dashboard', { timeout: 15000 }),
        page.waitForURL('**/', { timeout: 15000 }),
        page.waitForSelector('nav, aside', { timeout: 15000 }),
        page.waitForFunction(() => {
          return window.location.pathname === '/dashboard' || 
                 window.location.pathname === '/' ||
                 document.querySelector('nav, aside') !== null;
        }, { timeout: 15000 })
      ]);
      console.log('✅ Login realizado com sucesso!');
      console.log(`📍 URL atual: ${page.url()}`);
    } catch (error) {
      await page.screenshot({ path: 'test-results/login-failed.png', fullPage: true });
      const currentUrl = page.url();
      const hasCaptcha = await page.locator('[data-testid="recaptcha"], iframe[src*="recaptcha"]').isVisible().catch(() => false);
      
      if (hasCaptcha) {
        throw new Error('CAPTCHA requerido - não é possível continuar automaticamente');
      }
      
      throw new Error(`Falha ao fazer login. URL atual: ${currentUrl}`);
    }
    
    // Navegar para transações
    await page.click('nav button:has-text("Transactions"), nav button:has-text("Transações")');
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(2000);

    // Criar gasto parcelado
    console.log('💳 Criando gasto parcelado...');
    await page.click('button:has-text("New Expense"), button:has-text("Nova Despesa"), button.btn-secondary:has(svg)');
    await page.waitForTimeout(1500);
    
    await page.fill('input[name="description"], input[placeholder*="descrição" i]', 'Compra Parcelada Teste');
    await page.fill('input[name="value"], input[type="number"]', '1200');
    await page.fill('input[type="date"]', today());
    
    // Selecionar parcelado
    await page.selectOption('select[name="frequency"], select[name="frequencia"]', { index: 1 }); // Geralmente a segunda opção é Parcelado
    await page.waitForTimeout(1000);
    await page.fill('input[name="installments"], input[name="numeroParcelas"], input[name="parcelas"]', '3');
    
    console.log('✅ Salvando gasto parcelado...');
    await page.click('button:has-text("Save"), button:has-text("Salvar"), button[type="submit"]:not([disabled])');
    await page.waitForTimeout(3000);
    console.log('✅ Gasto parcelado criado com 3 parcelas!');
  });
});

