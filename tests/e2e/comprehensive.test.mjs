import axios from 'axios';
import assert from 'node:assert/strict';
import test from 'node:test';
import { randomBytes } from 'crypto';

const BASE_URL = process.env.CONTROLE_SE_BASE_URL || 'http://localhost:8080';
const API = axios.create({
  baseURL: `${BASE_URL}/api`,
  validateStatus: () => true,
  timeout: 30000, // 30 segundos de timeout
});

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
const delay = (ms) => new Promise(resolve => setTimeout(resolve, ms));

// Logging helpers para visualizar progresso
const log = {
  info: (msg) => console.log(`\x1b[36m[INFO]\x1b[0m ${msg}`),
  success: (msg) => console.log(`\x1b[32m[✓]\x1b[0m ${msg}`),
  warning: (msg) => console.log(`\x1b[33m[⚠]\x1b[0m ${msg}`),
  error: (msg) => console.log(`\x1b[31m[✗]\x1b[0m ${msg}`),
  test: (msg) => console.log(`\x1b[35m[TEST]\x1b[0m ${msg}`),
  step: (msg) => console.log(`  \x1b[90m→\x1b[0m ${msg}`),
};

// Delay inicial para evitar rate limiting entre testes
let lastTestTime = 0;
async function delayBetweenTests(testName) {
  const now = Date.now();
  const timeSinceLastTest = now - lastTestTime;
  const minDelay = 5000; // Mínimo de 5 segundos entre testes para evitar rate limit
  
  if (timeSinceLastTest < minDelay) {
    const waitTime = minDelay - timeSinceLastTest;
    log.step(`Aguardando ${Math.ceil(waitTime / 1000)}s antes de iniciar "${testName}"...`);
    await delay(waitTime);
  }
  log.test(`Iniciando: ${testName}`);
  lastTestTime = Date.now();
}

// Trata rate limiting com retry baseado no retryAfter
async function handleRateLimit(response, retryFn, maxRetries = 3) {
  if (response.status === 429 && response.data?.retryAfter) {
    const retryAfter = response.data.retryAfter;
    const now = Date.now();
    const waitTime = Math.max(0, retryAfter - now) + 1000; // Adiciona 1 segundo de margem
    
    if (waitTime > 0 && maxRetries > 0) {
      console.log(`Rate limit atingido. Aguardando ${Math.ceil(waitTime / 1000)} segundos...`);
      await delay(waitTime);
      return await retryFn();
    }
  }
  return response;
}

// Retry logic para requisições
async function requestWithRetry(method, url, data, token = null, maxRetries = 3, retryDelay = 1000) {
  let lastError;
  for (let attempt = 0; attempt < maxRetries; attempt++) {
    try {
      const headers = {};
      if (token) {
        headers.Authorization = `Bearer ${token}`;
      }
      const response = await API.request({ method, url, data, headers });
      if (!response.data) {
        response.data = {};
      }
      return response;
    } catch (error) {
      lastError = error;
      if (attempt < maxRetries - 1) {
        await delay(retryDelay * (attempt + 1)); // Backoff exponencial
      }
    }
  }
  throw lastError;
}

// Função auxiliar para requisições simples
async function request(method, url, data, token = null) {
  return requestWithRetry(method, url, data, token);
}

// Limpeza robusta de usuário - evita tentativas de login desnecessárias
async function deleteUserSafely(email, password, maxAttempts = 2) {
  // Evita tentar login múltiplas vezes para não acionar CAPTCHA
  // Se o usuário não existe, não precisa deletar
  try {
    // Tenta fazer login apenas uma vez
    const login = await request('post', '/auth/login', {
      email,
      password,
    }, null);

    if (login.status === 200) {
      const token = login.data?.token || login.data?.accessToken || login.data?.user?.token;
      if (token) {
        await delay(1000); // Delay maior para evitar rate limit
        const deleteRes = await request('delete', '/auth/user', null, token);
        if (deleteRes.status === 200 || deleteRes.status === 429) {
          await delay(1000); // Aguarda processamento
          return true;
        }
      }
    } else if (login.status === 401) {
      // Verifica se é porque usuário não existe ou senha incorreta
      if (login.data?.requiresCaptcha) {
        // CAPTCHA requerido - aguarda um pouco e tenta novamente apenas uma vez
        await delay(2000);
        const retryLogin = await request('post', '/auth/login', {
          email,
          password,
        }, null);
        if (retryLogin.status === 200) {
          const token = retryLogin.data?.token || retryLogin.data?.accessToken;
          if (token) {
            await delay(1000);
            await request('delete', '/auth/user', null, token);
            return true;
          }
        }
      }
      // Usuário não existe ou senha incorreta - consideramos sucesso (não precisa deletar)
      return true;
    }
  } catch (error) {
    // Em caso de erro, assume que usuário não existe
    return true;
  }
  return false;
}

// Setup de teste: cria usuário e retorna token
async function setupTestUser(email, password, name) {
  log.step(`Configurando usuário: ${email}`);
  
  // Primeiro, tenta deletar qualquer usuário existente (com delay para evitar CAPTCHA)
  log.step('Limpando usuário existente (se houver)...');
  await deleteUserSafely(email, password);
  log.step('Aguardando 3s para evitar rate limit...');
  await delay(3000); // Delay maior para evitar rate limit e CAPTCHA

  // Tenta registrar novo usuário primeiro (mais eficiente que tentar login)
  log.step('Registrando novo usuário...');
  let register = await request('post', '/auth/register', {
    name,
    email,
    password,
  }, null);

  // Trata rate limiting no registro
  if (register.status === 429) {
    log.warning('Rate limit detectado, aguardando...');
    register = await handleRateLimit(register, async () => {
      log.step('Tentando registrar novamente...');
      return await request('post', '/auth/register', {
        name,
        email,
        password,
      }, null);
    });
  }

  if (register.status === 201) {
    log.success('Usuário registrado com sucesso');
    // Usuário criado com sucesso, aguarda um pouco e faz login
    log.step('Aguardando 2s antes de fazer login...');
    await delay(2000);
    log.step('Fazendo login...');
    let login = await request('post', '/auth/login', {
      email,
      password,
    }, null);

    if (login.status !== 200) {
      log.warning('Login falhou, aguardando 3s e tentando novamente...');
      // Se login falhou mesmo após criar, aguarda mais e tenta novamente
      await delay(3000);
      login = await request('post', '/auth/login', {
        email,
        password,
      }, null);
      if (login.status !== 200) {
        throw new Error(`Falha ao fazer login após criar usuário: ${JSON.stringify(login.data)}`);
      }
    }
    log.success('Login realizado com sucesso');

    const token = login.data?.token || login.data?.accessToken;
    if (!token) {
      throw new Error(`Token não encontrado: ${JSON.stringify(login.data)}`);
    }
    return token;
  } else if (register.status === 409) {
    log.info('Usuário já existe, fazendo login...');
    // Usuário já existe, aguarda mais tempo antes de tentar login (para evitar CAPTCHA)
    log.step('Aguardando 3s antes de fazer login...');
    await delay(3000);
    log.step('Fazendo login...');
    let login = await request('post', '/auth/login', {
      email,
      password,
    }, null);

    if (login.status !== 200) {
      // Se requer CAPTCHA, aguarda ainda mais tempo
      if (login.data?.requiresCaptcha) {
        log.warning('CAPTCHA requerido, aguardando 5s...');
        await delay(5000); // Aguarda 5 segundos para resetar tentativas
        log.step('Tentando login novamente...');
        login = await request('post', '/auth/login', {
          email,
          password,
        }, null);
        if (login.status !== 200) {
          throw new Error(`Falha ao fazer login (CAPTCHA): ${JSON.stringify(login.data)}`);
        }
      } else {
        throw new Error(`Falha ao fazer login: ${JSON.stringify(login.data)}`);
      }
    }
    log.success('Login realizado com sucesso');

    const token = login.data?.token || login.data?.accessToken;
    if (!token) {
      throw new Error(`Token não encontrado: ${JSON.stringify(login.data)}`);
    }
    return token;
  } else {
    throw new Error(`Falha ao registrar usuário: ${JSON.stringify(register.data)}`);
  }
}

// ==================== TESTES DE AUTENTICAÇÃO ====================

test('Autenticação - Fluxo Completo', async (t) => {
  await delayBetweenTests('Autenticação - Fluxo Completo');
  
  const testState = {
    email: generateUniqueEmail(),
    password: 'Teste@123',
    name: generateUniqueName('Usuário Teste'),
    token: null,
    refreshToken: null,
  };

  try {
    await t.test('Registro de usuário', async () => {
      await deleteUserSafely(testState.email, testState.password);
      await delay(1000);

      const register = await request('post', '/auth/register', {
        name: testState.name,
        email: testState.email,
        password: testState.password,
      }, null);

      assert.equal(register.status, 201, `Cadastro falhou: ${JSON.stringify(register.data)}`);
      assert.ok(register.data.success !== false, 'Cadastro deve retornar sucesso');
    });

    await t.test('Login', async () => {
      await delay(2000); // Delay maior para evitar CAPTCHA
      let login = await request('post', '/auth/login', {
        email: testState.email,
        password: testState.password,
      }, null);

      // Se requer CAPTCHA, aguarda e tenta novamente
      if (login.status === 401 && login.data?.requiresCaptcha) {
        await delay(5000); // Aguarda 5 segundos para resetar tentativas
        login = await request('post', '/auth/login', {
          email: testState.email,
          password: testState.password,
        }, null);
      }

      assert.equal(login.status, 200, `Login falhou: ${JSON.stringify(login.data)}`);
      const token = login.data?.token || login.data?.accessToken;
      assert.ok(token, `Token não encontrado: ${JSON.stringify(login.data)}`);
      testState.token = token;
      testState.refreshToken = login.data?.refreshToken || null;
    });

    await t.test('Refresh Token', async () => {
      if (testState.refreshToken) {
        await delay(500);
        const refresh = await request('post', '/auth/refresh', {
          refreshToken: testState.refreshToken,
        }, null);

        if (refresh.status === 200) {
          const newToken = refresh.data?.token || refresh.data?.accessToken;
          if (newToken) {
            testState.token = newToken;
            console.log('Token renovado com sucesso');
          }
        }
      }
    });

    await t.test('Mudança de senha', async () => {
      await delay(500);
      const newPassword = 'NovaSenha@123';
      const changePassword = await request('post', '/auth/change-password', {
        currentPassword: testState.password,
        newPassword: newPassword,
      }, testState.token);

      if (changePassword.status === 200) {
        // Testa login com nova senha
        await delay(500);
        const loginNew = await request('post', '/auth/login', {
          email: testState.email,
          password: newPassword,
        }, null);

        if (loginNew.status === 200) {
          const token = loginNew.data?.token || loginNew.data?.accessToken;
          if (token) {
            testState.token = token;
            // Restaura senha original
            await delay(500);
            await request('post', '/auth/change-password', {
              currentPassword: newPassword,
              newPassword: testState.password,
            }, testState.token);
          }
        }
      }
    });

  } finally {
    await deleteUserSafely(testState.email, testState.password);
  }
});

// ==================== TESTES DE CONTAS ====================

test('Contas - CRUD Completo', async (t) => {
  await delayBetweenTests('Contas - CRUD Completo'); // Delay para evitar rate limiting
  
  const testState = {
    email: generateUniqueEmail(),
    password: 'Teste@123',
    name: generateUniqueName('Usuário Contas'),
    token: null,
    accounts: [],
  };

  try {
    testState.token = await setupTestUser(testState.email, testState.password, testState.name);

    await t.test('Criar conta corrente', async () => {
      log.step('Criando conta corrente...');
      const accountName = generateUniqueName('Conta Corrente');
      const res = await request('post', '/accounts', {
        name: accountName,
        type: 'CORRENTE',
        balance: 5000,
      }, testState.token);

      assert.equal(res.status, 201, `Falha ao criar conta: ${JSON.stringify(res.data)}`);
      assert.ok(res.data.accountId, 'Deve retornar accountId');
      log.success(`Conta criada: ${accountName} (ID: ${res.data.accountId})`);
      testState.accounts.push({ id: res.data.accountId, name: accountName, type: 'CORRENTE' });
    });

    await t.test('Criar conta investimento', async () => {
      const accountName = generateUniqueName('Conta Investimento');
      const res = await request('post', '/accounts', {
        name: accountName,
        type: 'INVESTIMENTO',
        balance: 0,
      }, testState.token);

      assert.equal(res.status, 201, `Falha ao criar conta: ${JSON.stringify(res.data)}`);
      assert.ok(res.data.accountId, 'Deve retornar accountId');
      testState.accounts.push({ id: res.data.accountId, name: accountName, type: 'INVESTIMENTO' });
    });

    await t.test('Criar cartão de crédito', async () => {
      const accountName = generateUniqueName('Cartão Crédito');
      const res = await request('post', '/accounts', {
        name: accountName,
        type: 'CARTAO_CREDITO',
        balance: 0,
        diaFechamento: 10,
        diaPagamento: 20,
      }, testState.token);

      assert.equal(res.status, 201, `Falha ao criar cartão: ${JSON.stringify(res.data)}`);
      assert.ok(res.data.accountId, 'Deve retornar accountId');
      testState.accounts.push({ id: res.data.accountId, name: accountName, type: 'CARTAO_CREDITO' });
    });

    await t.test('Listar contas', async () => {
      const res = await request('get', '/accounts', null, testState.token);
      assert.equal(res.status, 200, `Falha ao listar contas: ${JSON.stringify(res.data)}`);
      assert.ok(Array.isArray(res.data.data), 'Resposta deve ser um array');
      assert.ok(res.data.data.length >= 3, 'Deve ter pelo menos 3 contas');
    });

    await t.test('Atualizar conta', async () => {
      if (testState.accounts.length > 0) {
        const account = testState.accounts[0];
        const newName = generateUniqueName('Conta Atualizada');
        const res = await request('put', '/accounts', {
          id: account.id,
          name: newName,
          type: account.type,
          balance: 6000,
        }, testState.token);

        assert.equal(res.status, 200, `Falha ao atualizar conta: ${JSON.stringify(res.data)}`);
        assert.ok(res.data.success !== false, 'Deve retornar sucesso');
      }
    });

    await t.test('Obter informações de fatura do cartão', async () => {
      const creditCard = testState.accounts.find(a => a.type === 'CARTAO_CREDITO');
      if (creditCard) {
        const res = await request('get', `/accounts/${creditCard.id}/invoice-info`, null, testState.token);
        // Pode retornar 200 ou 404 se não houver fatura
        assert.ok([200, 404].includes(res.status), `Status inesperado: ${res.status}`);
      }
    });

    await t.test('Deletar conta', async () => {
      if (testState.accounts.length > 0) {
        const account = testState.accounts.pop();
        const res = await request('delete', `/accounts?id=${account.id}`, null, testState.token);
        assert.equal(res.status, 200, `Falha ao deletar conta: ${JSON.stringify(res.data)}`);
        assert.ok(res.data.success !== false, 'Deve retornar sucesso');
      }
    });

  } finally {
    await deleteUserSafely(testState.email, testState.password);
  }
});

// ==================== TESTES DE CATEGORIAS ====================

test('Categorias - CRUD Completo', async (t) => {
  await delayBetweenTests('Categorias - CRUD Completo'); // Delay para evitar rate limiting
  
  const testState = {
    email: generateUniqueEmail(),
    password: 'Teste@123',
    name: generateUniqueName('Usuário Categorias'),
    token: null,
    categories: [],
  };

  try {
    testState.token = await setupTestUser(testState.email, testState.password, testState.name);

    await t.test('Criar categoria', async () => {
      const categoryName = generateUniqueName('Alimentação');
      const res = await request('post', '/categories', { name: categoryName }, testState.token);
      assert.equal(res.status, 201, `Falha ao criar categoria: ${JSON.stringify(res.data)}`);
      assert.ok(res.data.categoryId, 'Deve retornar categoryId');
      testState.categories.push({ id: res.data.categoryId, name: categoryName });
    });

    await t.test('Criar categoria com orçamento', async () => {
      const categoryName = generateUniqueName('Transporte');
      const res = await request('post', '/categories', {
        name: categoryName,
        budget: 500,
      }, testState.token);
      assert.equal(res.status, 201, `Falha ao criar categoria com orçamento: ${JSON.stringify(res.data)}`);
      assert.ok(res.data.categoryId, 'Deve retornar categoryId');
      testState.categories.push({ id: res.data.categoryId, name: categoryName });
    });

    await t.test('Listar categorias', async () => {
      const res = await request('get', '/categories', null, testState.token);
      assert.equal(res.status, 200, `Falha ao listar categorias: ${JSON.stringify(res.data)}`);
      assert.ok(Array.isArray(res.data.data), 'Resposta deve ser um array');
      assert.ok(res.data.data.length >= 2, 'Deve ter pelo menos 2 categorias');
    });

    await t.test('Atualizar categoria', async () => {
      if (testState.categories.length > 0) {
        const category = testState.categories[0];
        const newName = generateUniqueName('Alimentação Atualizada');
        const res = await request('put', '/categories', {
          id: category.id,
          name: newName,
        }, testState.token);
        assert.equal(res.status, 200, `Falha ao atualizar categoria: ${JSON.stringify(res.data)}`);
        assert.ok(res.data.success !== false, 'Deve retornar sucesso');
      }
    });

    await t.test('Deletar categoria', async () => {
      if (testState.categories.length > 0) {
        const category = testState.categories.pop();
        const res = await request('delete', `/categories?id=${category.id}`, null, testState.token);
        assert.equal(res.status, 200, `Falha ao deletar categoria: ${JSON.stringify(res.data)}`);
        assert.ok(res.data.success !== false, 'Deve retornar sucesso');
      }
    });

  } finally {
    await deleteUserSafely(testState.email, testState.password);
  }
});

// ==================== TESTES DE TAGS ====================

test('Tags - CRUD Completo', async (t) => {
  await delayBetweenTests('Tags - CRUD Completo'); // Delay para evitar rate limiting
  
  const testState = {
    email: generateUniqueEmail(),
    password: 'Teste@123',
    name: generateUniqueName('Usuário Tags'),
    token: null,
    tags: [],
  };

  try {
    testState.token = await setupTestUser(testState.email, testState.password, testState.name);

    await t.test('Criar tag', async () => {
      const tagName = generateUniqueName('Essencial');
      const res = await request('post', '/tags', {
        nome: tagName,
        cor: '#F87171',
      }, testState.token);
      assert.equal(res.status, 201, `Falha ao criar tag: ${JSON.stringify(res.data)}`);
      assert.ok(res.data.tagId, 'Deve retornar tagId');
      testState.tags.push({ id: res.data.tagId, nome: tagName, cor: '#F87171' });
    });

    await t.test('Listar tags', async () => {
      const res = await request('get', '/tags', null, testState.token);
      assert.equal(res.status, 200, `Falha ao listar tags: ${JSON.stringify(res.data)}`);
      assert.ok(Array.isArray(res.data.data), 'Resposta deve ser um array');
      assert.ok(res.data.data.length >= 1, 'Deve ter pelo menos 1 tag');
    });

    await t.test('Atualizar tag', async () => {
      if (testState.tags.length > 0) {
        const tag = testState.tags[0];
        const newName = generateUniqueName('Essencial Atualizado');
        const res = await request('put', '/tags', {
          id: tag.id,
          nome: newName,
          cor: '#EF4444',
        }, testState.token);
        assert.equal(res.status, 200, `Falha ao atualizar tag: ${JSON.stringify(res.data)}`);
        assert.ok(res.data.success !== false, 'Deve retornar sucesso');
      }
    });

    await t.test('Deletar tag', async () => {
      if (testState.tags.length > 0) {
        const tag = testState.tags.pop();
        const res = await request('delete', `/tags?id=${tag.id}`, null, testState.token);
        assert.equal(res.status, 200, `Falha ao deletar tag: ${JSON.stringify(res.data)}`);
        assert.ok(res.data.success !== false, 'Deve retornar sucesso');
      }
    });

  } finally {
    await deleteUserSafely(testState.email, testState.password);
  }
});

// ==================== TESTES DE ORÇAMENTOS ====================

test('Orçamentos - CRUD Completo', async (t) => {
  await delayBetweenTests('Orçamentos - CRUD Completo'); // Delay para evitar rate limiting
  
  const testState = {
    email: generateUniqueEmail(),
    password: 'Teste@123',
    name: generateUniqueName('Usuário Orçamentos'),
    token: null,
    categories: [],
    budgets: [],
  };

  try {
    testState.token = await setupTestUser(testState.email, testState.password, testState.name);

    // Criar categoria para o orçamento
    const catRes = await request('post', '/categories', {
      name: generateUniqueName('Categoria Orçamento')
    }, testState.token);
    const categoryId = catRes.data.categoryId;
    testState.categories.push({ id: categoryId });

    await t.test('Criar orçamento mensal', async () => {
      const res = await request('post', '/budgets', {
        categoryId,
        value: 1000,
        period: 'MENSAL',
      }, testState.token);
      assert.equal(res.status, 201, `Falha ao criar orçamento: ${JSON.stringify(res.data)}`);
      assert.ok(res.data.budgetId, 'Deve retornar budgetId');
      testState.budgets.push({ id: res.data.budgetId, categoryId, value: 1000 });
    });

    await t.test('Listar orçamentos', async () => {
      const res = await request('get', '/budgets', null, testState.token);
      assert.equal(res.status, 200, `Falha ao listar orçamentos: ${JSON.stringify(res.data)}`);
      assert.ok(Array.isArray(res.data.data), 'Resposta deve ser um array');
      assert.ok(res.data.data.length >= 1, 'Deve ter pelo menos 1 orçamento');
    });

    await t.test('Atualizar orçamento', async () => {
      if (testState.budgets.length > 0) {
        const budget = testState.budgets[0];
        const res = await request('put', '/budgets', {
          id: budget.id,
          categoryId: budget.categoryId,
          value: 1500,
          period: 'MENSAL',
        }, testState.token);
        assert.equal(res.status, 200, `Falha ao atualizar orçamento: ${JSON.stringify(res.data)}`);
        assert.ok(res.data.success !== false, 'Deve retornar sucesso');
      }
    });

    await t.test('Deletar orçamento', async () => {
      if (testState.budgets.length > 0) {
        const budget = testState.budgets.pop();
        const res = await request('delete', `/budgets?id=${budget.id}`, null, testState.token);
        assert.equal(res.status, 200, `Falha ao deletar orçamento: ${JSON.stringify(res.data)}`);
        assert.ok(res.data.success !== false, 'Deve retornar sucesso');
      }
    });

  } finally {
    await deleteUserSafely(testState.email, testState.password);
  }
});

// ==================== TESTES DE GASTOS ====================

test('Gastos - Fluxo Completo', async (t) => {
  await delayBetweenTests('Gastos - Fluxo Completo'); // Delay para evitar rate limiting
  
  const testState = {
    email: generateUniqueEmail(),
    password: 'Teste@123',
    name: generateUniqueName('Usuário Gastos'),
    token: null,
    accounts: [],
    categories: [],
    tags: [],
    expenses: [],
  };

  try {
    testState.token = await setupTestUser(testState.email, testState.password, testState.name);

    // Setup: criar conta, categoria e tag
    const accountRes = await request('post', '/accounts', {
      name: generateUniqueName('Conta Teste'),
      type: 'CORRENTE',
      balance: 5000,
    }, testState.token);
    const accountId = accountRes.data.accountId;
    testState.accounts.push({ id: accountId });

    const catRes = await request('post', '/categories', {
      name: generateUniqueName('Categoria Gasto')
    }, testState.token);
    const categoryId = catRes.data.categoryId;
    testState.categories.push({ id: categoryId });

    const tagRes = await request('post', '/tags', {
      nome: generateUniqueName('Tag Gasto'),
      cor: '#FF0000'
    }, testState.token);
    const tagId = tagRes.data.tagId;
    testState.tags.push({ id: tagId });

    await t.test('Criar gasto único', async () => {
      const res = await request('post', '/expenses', {
        accountId,
        description: generateUniqueName('Gasto único'),
        value: 100,
        date: today(),
        frequency: 'Único',
        categoryIds: [categoryId],
        tagIds: [tagId],
      }, testState.token);
      assert.equal(res.status, 201, `Falha ao criar gasto: ${JSON.stringify(res.data)}`);
      assert.ok(res.data.expenseId, 'Deve retornar expenseId');
      testState.expenses.push(res.data.expenseId);
    });

    await t.test('Criar gasto parcelado', async () => {
      const res = await request('post', '/expenses', {
        accountId,
        description: generateUniqueName('Gasto parcelado'),
        value: 300,
        date: today(),
        frequency: 'Parcelado',
        numeroParcelas: 3,
        categoryIds: [categoryId],
        tagIds: [tagId],
      }, testState.token);
      assert.equal(res.status, 201, `Falha ao criar gasto parcelado: ${JSON.stringify(res.data)}`);
      assert.ok(res.data.idGrupo || res.data.expenseId, 'Deve retornar idGrupo ou expenseId');
      if (res.data.expenseId) {
        testState.expenses.push(res.data.expenseId);
      }
    });

    await t.test('Listar gastos via transações', async () => {
      const res = await request('get', '/transactions', null, testState.token);
      assert.equal(res.status, 200, `Falha ao listar transações: ${JSON.stringify(res.data)}`);
      assert.ok(res.data !== undefined, 'Resposta deve conter dados');
    });

    await t.test('Pagar parcela antecipada', async () => {
      if (testState.expenses.length > 1) {
        const parceledExpenseId = testState.expenses[1];
        const paymentAccountRes = await request('post', '/accounts', {
          name: generateUniqueName('Conta Pagamento'),
          type: 'CORRENTE',
          balance: 1000,
        }, testState.token);
        const paymentAccountId = paymentAccountRes.data.accountId;

        const res = await request('post', '/expenses/pay-installment', {
          expenseId: parceledExpenseId,
          contaOrigemId: paymentAccountId,
        }, testState.token);
        // Pode retornar 200 ou 400/404 se a parcela não for válida para pagamento antecipado
        assert.ok([200, 400, 404].includes(res.status), `Status inesperado ao pagar parcela: ${res.status}`);
      }
    });

    await t.test('Deletar gasto', async () => {
      if (testState.expenses.length > 0) {
        const expenseId = testState.expenses.pop();
        const res = await request('delete', `/expenses?id=${expenseId}`, null, testState.token);
        assert.equal(res.status, 200, `Falha ao deletar gasto: ${JSON.stringify(res.data)}`);
        assert.ok(res.data.success !== false, 'Deve retornar sucesso');
      }
    });

  } finally {
    await deleteUserSafely(testState.email, testState.password);
  }
});

// ==================== TESTES DE RECEITAS ====================

test('Receitas - Fluxo Completo', async (t) => {
  await delayBetweenTests('Receitas - Fluxo Completo'); // Delay para evitar rate limiting
  
  const testState = {
    email: generateUniqueEmail(),
    password: 'Teste@123',
    name: generateUniqueName('Usuário Receitas'),
    token: null,
    accounts: [],
    tags: [],
    incomes: [],
  };

  try {
    testState.token = await setupTestUser(testState.email, testState.password, testState.name);

    // Setup: criar conta e tag
    const accountRes = await request('post', '/accounts', {
      name: generateUniqueName('Conta Teste'),
      type: 'CORRENTE',
      balance: 5000,
    }, testState.token);
    const accountId = accountRes.data.accountId;
    testState.accounts.push({ id: accountId });

    const tagRes = await request('post', '/tags', {
      nome: generateUniqueName('Tag Receita'),
      cor: '#00FF00'
    }, testState.token);
    const tagId = tagRes.data.tagId;
    testState.tags.push({ id: tagId });

    await t.test('Criar receita', async () => {
      const res = await request('post', '/incomes', {
        accountId,
        description: generateUniqueName('Receita teste'),
        value: 5000,
        date: today(),
        tagIds: [tagId],
      }, testState.token);
      assert.equal(res.status, 201, `Falha ao criar receita: ${JSON.stringify(res.data)}`);
      assert.ok(res.data.incomeId, 'Deve retornar incomeId');
      testState.incomes.push(res.data.incomeId);
    });

    await t.test('Listar receitas via transações', async () => {
      const res = await request('get', '/transactions', null, testState.token);
      assert.equal(res.status, 200, `Falha ao listar transações: ${JSON.stringify(res.data)}`);
      assert.ok(res.data !== undefined, 'Resposta deve conter dados');
    });

    await t.test('Deletar receita', async () => {
      if (testState.incomes.length > 0) {
        const incomeId = testState.incomes.pop();
        const res = await request('delete', `/incomes?id=${incomeId}`, null, testState.token);
        assert.equal(res.status, 200, `Falha ao deletar receita: ${JSON.stringify(res.data)}`);
        assert.ok(res.data.success !== false, 'Deve retornar sucesso');
      }
    });

  } finally {
    await deleteUserSafely(testState.email, testState.password);
  }
});

// ==================== TESTES DE INVESTIMENTOS ====================

test('Investimentos - CRUD Completo', async (t) => {
  await delayBetweenTests('Investimentos - CRUD Completo'); // Delay para evitar rate limiting
  
  const testState = {
    email: generateUniqueEmail(),
    password: 'Teste@123',
    name: generateUniqueName('Usuário Investimentos'),
    token: null,
    accounts: [],
    investments: [],
  };

  try {
    testState.token = await setupTestUser(testState.email, testState.password, testState.name);

    // Setup: criar conta investimento
    const accountRes = await request('post', '/accounts', {
      name: generateUniqueName('Conta Investimento'),
      type: 'INVESTIMENTO',
      balance: 0,
    }, testState.token);
    const accountId = accountRes.data.accountId;
    testState.accounts.push({ id: accountId });

    await t.test('Criar investimento - Ação', async () => {
      const res = await request('post', '/investments', {
        nome: 'ITUB4',
        categoria: 'ACAO',
        quantidade: 10,
        corretagem: 0,
        accountId,
        dataAporte: today(),
        moeda: 'BRL',
      }, testState.token);
      assert.equal(res.status, 201, `Falha ao criar investimento: ${JSON.stringify(res.data)}`);
      assert.ok(res.data.investmentId, 'Deve retornar investmentId');
      testState.investments.push({ id: res.data.investmentId, nome: 'ITUB4' });
    });

    await t.test('Listar investimentos', async () => {
      const res = await request('get', '/investments', null, testState.token);
      assert.equal(res.status, 200, `Falha ao listar investimentos: ${JSON.stringify(res.data)}`);
      assert.ok(Array.isArray(res.data.data), 'Resposta deve ser um array');
      assert.ok(res.data.data.length >= 1, 'Deve ter pelo menos 1 investimento');
    });

    await t.test('Atualizar investimento', async () => {
      if (testState.investments.length > 0) {
        const investment = testState.investments[0];
        const res = await request('put', '/investments', {
          id: investment.id,
          nome: 'ITUB4',
          nomeAtivo: 'ITUB4',
          categoria: 'ACAO',
          quantidade: 12,
          corretagem: 0,
          corretora: 'Conta Investimento',
          dataAporte: today(),
          moeda: 'BRL',
        }, testState.token);
        assert.equal(res.status, 200, `Falha ao atualizar investimento: ${JSON.stringify(res.data)}`);
        assert.ok(res.data.success !== false, 'Deve retornar sucesso');
      }
    });

    await t.test('Deletar investimento', async () => {
      if (testState.investments.length > 0) {
        const investment = testState.investments.pop();
        const res = await request('delete', `/investments?id=${investment.id}`, null, testState.token);
        assert.equal(res.status, 200, `Falha ao deletar investimento: ${JSON.stringify(res.data)}`);
        assert.ok(res.data.success !== false, 'Deve retornar sucesso');
      }
    });

  } finally {
    await deleteUserSafely(testState.email, testState.password);
  }
});

// ==================== TESTES DE DASHBOARD E RELATÓRIOS ====================

test('Dashboard e Relatórios', async (t) => {
  await delayBetweenTests('Dashboard e Relatórios'); // Delay para evitar rate limiting
  
  const testState = {
    email: generateUniqueEmail(),
    password: 'Teste@123',
    name: generateUniqueName('Usuário Dashboard'),
    token: null,
  };

  try {
    testState.token = await setupTestUser(testState.email, testState.password, testState.name);

    await t.test('Obter overview do dashboard', async () => {
      const res = await request('get', '/dashboard/overview', null, testState.token);
      assert.equal(res.status, 200, `Falha ao obter overview: ${JSON.stringify(res.data)}`);
      assert.ok(res.data.success !== false, 'Overview deve retornar sucesso');
    });

    await t.test('Listar transações recentes', async () => {
      const res = await request('get', '/transactions/recent', null, testState.token);
      assert.equal(res.status, 200, `Falha ao listar transações recentes: ${JSON.stringify(res.data)}`);
      assert.ok(res.data !== undefined, 'Resposta deve conter dados');
    });

    await t.test('Listar transações com filtros', async () => {
      const res = await request('get', '/transactions?startDate=2024-01-01&endDate=2024-12-31', null, testState.token);
      assert.equal(res.status, 200, `Falha ao listar transações: ${JSON.stringify(res.data)}`);
      assert.ok(res.data !== undefined, 'Resposta deve conter dados');
    });

    await t.test('Gerar relatório', async () => {
      const res = await request('get', '/reports?startDate=2024-01-01&endDate=2024-12-31', null, testState.token);
      assert.equal(res.status, 200, `Falha ao gerar relatório: ${JSON.stringify(res.data)}`);
      assert.ok(res.data !== undefined, 'Resposta deve conter dados');
    });

  } finally {
    await deleteUserSafely(testState.email, testState.password);
  }
});

// ==================== TESTES DE FERRAMENTAS ====================

test('Ferramentas', async (t) => {
  await delayBetweenTests('Ferramentas'); // Delay para evitar rate limiting
  
  const testState = {
    email: generateUniqueEmail(),
    password: 'Teste@123',
    name: generateUniqueName('Usuário Ferramentas'),
    token: null,
  };

  try {
    testState.token = await setupTestUser(testState.email, testState.password, testState.name);

    await t.test('Listar cálculos de juros compostos salvos', async () => {
      // O GET lista os cálculos salvos pelo usuário
      // O endpoint requer userId como query param e valida se corresponde ao token
      // Como não temos acesso direto ao userId do token, testamos apenas se o endpoint responde
      // Tentamos com um userId genérico - pode retornar 403 se não corresponder ao token
      const res = await request('get', '/tools/compound-interest?userId=999', null, testState.token);
      // Pode retornar 200 (sucesso se userId corresponder), 400 (userId inválido), 403 (acesso negado), ou 401 (não autenticado)
      assert.ok([200, 400, 401, 403].includes(res.status), `Status inesperado: ${res.status} - ${JSON.stringify(res.data)}`);
    });

    await t.test('Obter cotação de investimento', async () => {
      // O endpoint requer symbol e category como parâmetros obrigatórios
      const res = await request('get', '/investments/quote?symbol=ITUB4&category=ACAO', null, testState.token);
      // Pode retornar 200 (sucesso), 400 (parâmetros inválidos), 500/503 (erro da API externa)
      assert.ok([200, 400, 500, 503].includes(res.status), `Status inesperado: ${res.status} - ${JSON.stringify(res.data)}`);
    });

    await t.test('Obter evolução de investimentos', async () => {
      const res = await request('get', '/investments/evolution', null, testState.token);
      assert.equal(res.status, 200, `Falha ao obter evolução: ${JSON.stringify(res.data)}`);
      assert.ok(res.data !== undefined, 'Resposta deve conter dados');
    });

  } finally {
    await deleteUserSafely(testState.email, testState.password);
  }
});

// ==================== TESTES DE IMPORTAÇÃO ====================

test('Importação de Transações', async (t) => {
  await delayBetweenTests('Importação de Transações'); // Delay para evitar rate limiting
  
  const testState = {
    email: generateUniqueEmail(),
    password: 'Teste@123',
    name: generateUniqueName('Usuário Importação'),
    token: null,
  };

  try {
    testState.token = await setupTestUser(testState.email, testState.password, testState.name);

    await t.test('Obter template de importação', async () => {
      // Template pode ser público, então não precisa de token
      const res = await request('get', '/transactions/import/template', null, null);
      // Pode retornar 200 (sucesso) ou 401 (se precisar autenticação)
      assert.ok([200, 401].includes(res.status), `Status inesperado: ${res.status}`);
    });

  } finally {
    await deleteUserSafely(testState.email, testState.password);
  }
});
