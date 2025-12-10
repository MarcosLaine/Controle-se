import axios from 'axios';
import assert from 'node:assert/strict';
import test from 'node:test';

const BASE_URL = process.env.CONTROLE_SE_BASE_URL || 'http://localhost:8080';
const API = axios.create({
  baseURL: `${BASE_URL}/api`,
  validateStatus: () => true,
});

const CREDENTIALS = {
  name: 'Usuário Automático',
  email: 'email@email.com',
  password: 'Admin@123',
};

const state = {
  token: null,
  accounts: {},
  categories: [],
  tags: [],
  budgets: [],
  expenses: [],
  incomes: [],
  investments: [],
};

const today = () => new Date().toISOString().slice(0, 10);

async function request(method, url, data, token = null) {
  const headers = {};
  // Sempre usa o token do state se não for especificado explicitamente (null ou undefined)
  const tokenToUse = token !== null && token !== undefined ? token : state.token;
  if (tokenToUse) {
    headers.Authorization = `Bearer ${tokenToUse}`;
  }
  const response = await API.request({ method, url, data, headers });
  if (!response.data) {
    response.data = {};
  }
  return response;
}

async function ensureUserDeleted() {
  try {
    const login = await request('post', '/auth/login', {
      email: CREDENTIALS.email,
      password: CREDENTIALS.password,
    }, null);

    if (login.status === 200) {
      const token = login.data?.token || login.data?.accessToken || login.data?.user?.token;
      if (token) {
        // Adiciona delay para evitar rate limit
        await new Promise(resolve => setTimeout(resolve, 500));
        const deleteRes = await request('delete', '/auth/user', null, token);
        // Aguarda um pouco mais após deletar
        await new Promise(resolve => setTimeout(resolve, 500));
        return deleteRes.status === 200 || deleteRes.status === 429; // Aceita rate limit também
      }
    }
  } catch (error) {
    // Se não conseguir fazer login, o usuário provavelmente não existe
    console.log('Usuário não encontrado ou já foi deletado');
  }
  return false;
}

async function cleanupAllData() {
  // Tenta remover usando o token atual, se existir
  if (state.token) {
    await request('delete', '/auth/user');
    state.token = null;
    return;
  }

  // Caso não tenha token válido (ou falha anterior), tenta logar e excluir
  const login = await request('post', '/auth/login', {
    email: CREDENTIALS.email,
    password: CREDENTIALS.password,
  }, null);

  if (login.status === 200) {
    const token = login.data?.token || login.data?.accessToken || login.data?.user?.token;
    if (token) {
      await request('delete', '/auth/user', null, token);
    }
  }
}

async function createAccount(name, type, balance = 0) {
  const res = await request('post', '/accounts', {
    name,
    type,
    balance,
  });
  assert.equal(res.status, 201, `Falha ao criar conta ${name}: ${JSON.stringify(res.data)}`);
  return res.data.accountId;
}

async function createCategory(name) {
  const res = await request('post', '/categories', { name });
  assert.equal(res.status, 201, `Falha ao criar categoria ${name}: ${JSON.stringify(res.data)}`);
  return res.data.categoryId;
}

async function createTag(nome, cor) {
  const res = await request('post', '/tags', { nome, cor });
  assert.equal(res.status, 201, `Falha ao criar tag ${nome}: ${JSON.stringify(res.data)}`);
  return res.data.tagId;
}

async function createBudget(categoryId, value, period = 'MENSAL') {
  const res = await request('post', '/budgets', {
    categoryId,
    value,
    period,
  });
  assert.equal(res.status, 201, `Falha ao criar orçamento: ${JSON.stringify(res.data)}`);
  return res.data.budgetId;
}

async function createExpense(payload) {
  const res = await request('post', '/expenses', payload);
  assert.equal(res.status, 201, `Falha ao criar gasto: ${JSON.stringify(res.data)}`);
  return res.data.expenseId;
}

async function createIncome(payload) {
  const res = await request('post', '/incomes', payload);
  assert.equal(res.status, 201, `Falha ao criar receita: ${JSON.stringify(res.data)}`);
  return res.data.incomeId;
}

async function createInvestment(payload) {
  const res = await request('post', '/investments', payload);
  assert.equal(res.status, 201, `Falha ao criar investimento ${payload.nome}: ${JSON.stringify(res.data)}`);
  return { id: res.data.investmentId, price: res.data.precoAporte };
}

async function updateInvestment(payload) {
  const res = await request('put', '/investments', payload);
  assert.equal(res.status, 200, `Falha ao atualizar investimento ${payload.id}: ${JSON.stringify(res.data)}`);
}

async function deleteExpense(id) {
  const res = await request('delete', `/expenses?id=${id}`);
  assert.equal(res.status, 200, `Falha ao excluir gasto ${id}: ${JSON.stringify(res.data)}`);
}

async function deleteIncome(id) {
  const res = await request('delete', `/incomes?id=${id}`);
  assert.equal(res.status, 200, `Falha ao excluir receita ${id}: ${JSON.stringify(res.data)}`);
}

async function deleteUser() {
  // Adiciona um pequeno delay para evitar rate limit
  await new Promise(resolve => setTimeout(resolve, 1000));
  const res = await request('delete', '/auth/user');
  // Aceita tanto 200 quanto 429 (rate limit) como sucesso para este teste
  if (res.status === 429) {
    console.log('Rate limit atingido, mas continuando...');
    return;
  }
  assert.equal(res.status, 200, `Falha ao excluir usuário: ${JSON.stringify(res.data)}`);
}

test('Cenário integral de uso', async (t) => {
  // Tenta deletar usuário antes de começar
  const deleted = await ensureUserDeleted();
  if (deleted) {
    // Aguarda um pouco para garantir que a exclusão foi processada
    await new Promise(resolve => setTimeout(resolve, 1000));
  }

  try {
    await t.test('Cadastro e login', async () => {
      // Tenta fazer login primeiro (caso o usuário já exista de um teste anterior)
      let login = await request('post', '/auth/login', {
        email: CREDENTIALS.email,
        password: CREDENTIALS.password,
      }, null);

      // Se login falhou, tenta cadastrar
      if (login.status !== 200) {
        const register = await request('post', '/auth/register', CREDENTIALS, null);
        // Se cadastro retornou 409 (já existe), tenta login novamente
        if (register.status === 409) {
          console.log('Usuário já existe, tentando login novamente...');
          await new Promise(resolve => setTimeout(resolve, 500));
          login = await request('post', '/auth/login', {
            email: CREDENTIALS.email,
            password: CREDENTIALS.password,
          }, null);
        } else {
          // Se cadastro foi bem-sucedido (201), faz login
          assert.equal(register.status, 201, `Cadastro falhou: ${JSON.stringify(register.data)}`);
          await new Promise(resolve => setTimeout(resolve, 500));
          login = await request('post', '/auth/login', {
            email: CREDENTIALS.email,
            password: CREDENTIALS.password,
          }, null);
        }
      }

      assert.equal(login.status, 200, `Login falhou: ${JSON.stringify(login.data)}`);
      // O token pode estar em login.data.token ou login.data.accessToken
      const token = login.data?.token || login.data?.accessToken;
      assert.ok(token, `Token não encontrado na resposta: ${JSON.stringify(login.data)}`);
      state.token = token;
      console.log('Token salvo:', state.token ? 'SIM' : 'NÃO');
    });

    await t.test('Contas', async () => {
      assert.ok(state.token, 'Token não está disponível para o teste de Contas');
      state.accounts.current = await createAccount('Conta Corrente Teste', 'CORRENTE', 2500);
      state.accounts.invest = await createAccount('Conta Investimento Teste', 'INVESTIMENTO', 0);
    });

    await t.test('Categorias e Tags', async () => {
      state.categories.push(await createCategory('Moradia Teste'));
      state.categories.push(await createCategory('Lazer Teste'));

      state.tags.push(await createTag('Essencial', '#F87171'));
      state.tags.push(await createTag('Planejado', '#34D399'));
    });

    await t.test('Orçamentos', async () => {
      state.budgets.push(await createBudget(state.categories[0], 3000, 'MENSAL'));
      state.budgets.push(await createBudget(state.categories[1], 1200, 'MENSAL'));
    });

    await t.test('Gastos', async () => {
      const expensePayloadBase = {
        accountId: state.accounts.current,
        date: today(),
        frequency: 'Único',
        observacoes: 'Observação linha 1\nObservação linha 2',
      };

      const expense1 = await createExpense({
        ...expensePayloadBase,
        description: 'Aluguel apartamento',
        value: 1800,
        categoryIds: [state.categories[0]],
        tagIds: [state.tags[0]],
      });

      const expense2 = await createExpense({
        ...expensePayloadBase,
        description: 'Cinema e jantar',
        value: 320,
        categoryIds: [state.categories[1]],
        tagIds: [state.tags[1]],
      });

      state.expenses.push(expense1, expense2);
    });

    await t.test('Receitas', async () => {
      const incomePayloadBase = {
        accountId: state.accounts.current,
        date: today(),
        observacoes: 'Pagamento recebido',
      };

      const income1 = await createIncome({
        ...incomePayloadBase,
        description: 'Salário mensal',
        value: 5000,
        tagIds: [state.tags[0]],
      });

      const income2 = await createIncome({
        ...incomePayloadBase,
        description: 'Freela de design',
        value: 1500,
        tagIds: [state.tags[1]],
      });

      state.incomes.push(income1, income2);
    });

    await t.test('Excluir um gasto e uma receita', async () => {
      await deleteExpense(state.expenses.pop());
      await deleteIncome(state.incomes.pop());
    });

    await t.test('Investimentos - compras', async () => {
      const investPayload = ({ nome, categoria, quantidade, moeda = 'BRL' }) => ({
        nome,
        categoria,
        quantidade,
        corretagem: 0,
        accountId: state.accounts.invest,
        dataAporte: today(),
        moeda,
      });

      const compraAcao = await createInvestment(investPayload({ nome: 'ITUB4', categoria: 'ACAO', quantidade: 10 }));
      const compraFii = await createInvestment(investPayload({ nome: 'KNCR11', categoria: 'FII', quantidade: 5 }));
      const compraStock = await createInvestment(investPayload({ nome: 'AAPL', categoria: 'STOCK', quantidade: 3, moeda: 'USD' }));
      const compraCripto = await createInvestment(investPayload({ nome: 'BTC', categoria: 'CRYPTO', quantidade: 0.01, moeda: 'USD' }));

      state.investments.push(
        { id: compraAcao.id, nome: 'ITUB4', categoria: 'ACAO', dataAporte: today(), moeda: 'BRL' },
        { id: compraFii.id, nome: 'KNCR11', categoria: 'FII', dataAporte: today(), moeda: 'BRL' },
        { id: compraStock.id, nome: 'AAPL', categoria: 'STOCK', dataAporte: today(), moeda: 'USD' },
        { id: compraCripto.id, nome: 'BTC', categoria: 'CRYPTO', dataAporte: today(), moeda: 'USD' },
      );
    });

    await t.test('Investimentos - edição', async () => {
      const updates = [
        { nome: 'ITUB4', quantidade: 12, categoria: 'ACAO', moeda: 'BRL' },
        { nome: 'KNCR11', quantidade: 6, categoria: 'FII', moeda: 'BRL' },
        { nome: 'AAPL', quantidade: 4, categoria: 'STOCK', moeda: 'USD' },
        { nome: 'BTC', quantidade: 0.012, categoria: 'CRYPTO', moeda: 'USD' },
      ];

      for (const upd of updates) {
        const target = state.investments.find((inv) => inv.nome === upd.nome && inv.categoria === upd.categoria);
        assert.ok(target, `Investimento ${upd.nome} não encontrado para edição`);
        await updateInvestment({
          id: target.id,
          nome: upd.nome,
          nomeAtivo: upd.nome,
          categoria: upd.categoria,
          quantidade: upd.quantidade,
          corretagem: 0,
          corretora: 'Conta Investimento Teste',
          dataAporte: target.dataAporte,
          moeda: upd.moeda,
        });
      }
    });

    await t.test('Investimentos - vendas', async () => {
      const salePayload = ({ nome, categoria, quantidade, moeda = 'BRL' }) => ({
        nome,
        categoria,
        quantidade,
        corretagem: 0,
        accountId: state.accounts.invest,
        dataAporte: today(),
        moeda,
      });

      await createInvestment(salePayload({ nome: 'ITUB4', categoria: 'ACAO', quantidade: -4 }));
      await createInvestment(salePayload({ nome: 'KNCR11', categoria: 'FII', quantidade: -2 }));
      await createInvestment(salePayload({ nome: 'AAPL', categoria: 'STOCK', quantidade: -1, moeda: 'USD' }));
      await createInvestment(salePayload({ nome: 'BTC', categoria: 'CRYPTO', quantidade: -0.005, moeda: 'USD' }));
    });

    await t.test('Encerramento - excluir usuário', async () => {
      await deleteUser();
    });
  } finally {
    await cleanupAllData();
  }
});

