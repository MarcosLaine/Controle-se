// ===== CONTROLE-SE FRONTEND APPLICATION =====
class ControleSeApp {
    constructor() {
        this.currentUser = null;
        this.apiBaseUrl = 'http://localhost:8080/api'; // Backend API URL
        this.categoriesCache = []; // Cache de categorias
        this.accountsCache = []; // Cache de contas
        this.init();
    }

    init() {
        this.hideLoadingScreen();
        this.setupEventListeners();
        this.checkAuthStatus();
    }

    // ===== LOADING SCREEN =====
    hideLoadingScreen() {
        setTimeout(() => {
            const loadingScreen = document.getElementById('loading-screen');
            loadingScreen.classList.add('hidden');
            setTimeout(() => {
                loadingScreen.style.display = 'none';
            }, 350);
        }, 1500);
    }

    // ===== EVENT LISTENERS =====
    setupEventListeners() {
        // Auth forms
        document.getElementById('login-form').addEventListener('submit', (e) => this.handleLogin(e));
        document.getElementById('register-form').addEventListener('submit', (e) => this.handleRegister(e));
        
        // Auth tabs
        document.querySelectorAll('.tab-btn').forEach(btn => {
            btn.addEventListener('click', (e) => this.switchAuthTab(e));
        });

        // Navigation
        document.querySelectorAll('.nav-item').forEach(item => {
            item.addEventListener('click', (e) => this.navigateToSection(e));
        });

        // Logout
        document.getElementById('logout-btn').addEventListener('click', () => this.logout());

        // Modal
        document.querySelector('.modal-close').addEventListener('click', () => this.closeModal());
        document.getElementById('modal-overlay').addEventListener('click', (e) => {
            if (e.target.id === 'modal-overlay') this.closeModal();
        });

        // Add buttons
        document.getElementById('add-category-btn').addEventListener('click', () => this.showAddCategoryModal());
        document.getElementById('add-account-btn').addEventListener('click', () => this.showAddAccountModal());
        document.getElementById('add-expense-btn').addEventListener('click', () => this.showAddExpenseModal());
        document.getElementById('add-income-btn').addEventListener('click', () => this.showAddIncomeModal());
        document.getElementById('add-budget-btn').addEventListener('click', () => this.showAddBudgetModal());

        // Filters
        document.getElementById('category-filter').addEventListener('change', () => this.filterTransactions());
        document.getElementById('date-filter').addEventListener('change', () => this.filterTransactions());
        document.getElementById('clear-filters').addEventListener('click', () => this.clearFilters());

        // Period select
        document.getElementById('period-select').addEventListener('change', () => this.updateOverview());
    }

    // ===== AUTHENTICATION =====
    switchAuthTab(e) {
        e.preventDefault();
        const tab = e.target.dataset.tab;
        
        // Update tab buttons
        document.querySelectorAll('.tab-btn').forEach(btn => btn.classList.remove('active'));
        e.target.classList.add('active');
        
        // Update forms
        document.querySelectorAll('.auth-form').forEach(form => form.classList.remove('active'));
        document.getElementById(`${tab}-form`).classList.add('active');
    }

    async handleLogin(e) {
        e.preventDefault();
        const email = document.getElementById('login-email').value;
        const password = document.getElementById('login-password').value;

        try {
            const response = await this.apiCall('/auth/login', 'POST', { email, password });
            if (response.success) {
                this.currentUser = response.user;
                this.showDashboard();
                this.showToast('Login realizado com sucesso!', 'success');
            } else {
                this.showToast(response.message || 'Erro no login', 'error');
            }
        } catch (error) {
            this.showToast('Erro de conexão. Verifique se o backend está rodando.', 'error');
            console.error('Login error:', error);
        }
    }

    async handleRegister(e) {
        e.preventDefault();
        const name = document.getElementById('register-name').value;
        const email = document.getElementById('register-email').value;
        const password = document.getElementById('register-password').value;

        try {
            const response = await this.apiCall('/auth/register', 'POST', { name, email, password });
            if (response.success) {
                this.showToast('Cadastro realizado com sucesso!', 'success');
                // Switch to login tab
                document.querySelector('[data-tab="login"]').click();
            } else {
                this.showToast(response.message || 'Erro no cadastro', 'error');
            }
        } catch (error) {
            this.showToast('Erro de conexão. Verifique se o backend está rodando.', 'error');
            console.error('Register error:', error);
        }
    }

    logout() {
        this.currentUser = null;
        this.showAuthScreen();
        this.showToast('Logout realizado com sucesso!', 'info');
    }

    checkAuthStatus() {
        // Check if user is already logged in (from localStorage)
        const savedUser = localStorage.getItem('controle-se-user');
        if (savedUser) {
            this.currentUser = JSON.parse(savedUser);
            this.showDashboard();
        } else {
            this.showAuthScreen();
        }
    }

    // ===== UI STATE MANAGEMENT =====
    showAuthScreen() {
        document.getElementById('auth-screen').style.display = 'flex';
        document.getElementById('dashboard').style.display = 'none';
        document.getElementById('user-info').style.display = 'none';
    }

    showDashboard() {
        document.getElementById('auth-screen').style.display = 'none';
        document.getElementById('dashboard').style.display = 'flex';
        document.getElementById('user-info').style.display = 'flex';
        
        // Update user info
        document.getElementById('user-name').textContent = this.currentUser.name;
        
        // Save user to localStorage
        localStorage.setItem('controle-se-user', JSON.stringify(this.currentUser));
        
        // Load dashboard data
        this.loadDashboardData();
    }

    navigateToSection(e) {
        e.preventDefault();
        const section = e.target.closest('.nav-item').dataset.section;
        
        // Update navigation
        document.querySelectorAll('.nav-item').forEach(item => item.classList.remove('active'));
        e.target.closest('.nav-item').classList.add('active');
        
        // Update content sections
        document.querySelectorAll('.content-section').forEach(section => section.classList.remove('active'));
        document.getElementById(`${section}-section`).classList.add('active');
        
        // Load section data
        this.loadSectionData(section);
    }

    // ===== DATA LOADING =====
    async loadDashboardData() {
        try {
            await Promise.all([
                this.updateOverview(),
                this.loadRecentTransactions(),
                this.loadCategories(),
                this.loadAccounts(),
                this.loadBudgets()
            ]);
        } catch (error) {
            console.error('Error loading dashboard data:', error);
            this.showToast('Erro ao carregar dados do dashboard', 'error');
        }
    }

    async loadSectionData(section) {
        switch (section) {
            case 'overview':
                await this.updateOverview();
                break;
            case 'categories':
                await this.loadCategories();
                break;
            case 'accounts':
                await this.loadAccounts();
                break;
            case 'transactions':
                await this.loadTransactions();
                break;
            case 'budgets':
                await this.loadBudgets();
                break;
            case 'reports':
                await this.loadReports();
                break;
        }
    }

    async updateOverview() {
        try {
            const response = await this.apiCall('/dashboard/overview', 'GET');
            if (response.success) {
                this.updateSummaryCards(response.data);
                this.updateCategoryChart(response.data.categoryBreakdown);
            }
        } catch (error) {
            console.error('Error updating overview:', error);
        }
    }

    updateSummaryCards(data) {
        document.getElementById('total-income').textContent = this.formatCurrency(data.totalIncome);
        document.getElementById('total-expense').textContent = this.formatCurrency(data.totalExpense);
        document.getElementById('total-balance').textContent = this.formatCurrency(data.balance);
        document.getElementById('total-accounts').textContent = this.formatCurrency(data.totalAccounts);
    }

    async loadRecentTransactions() {
        try {
            const response = await this.apiCall('/transactions/recent', 'GET');
            if (response.success) {
                this.renderRecentTransactions(response.data);
            }
        } catch (error) {
            console.error('Error loading recent transactions:', error);
        }
    }

    renderRecentTransactions(transactions) {
        const container = document.getElementById('recent-transactions-list');
        container.innerHTML = '';

        if (transactions.length === 0) {
            container.innerHTML = '<p class="text-center">Nenhuma transação recente</p>';
            return;
        }

        transactions.forEach(transaction => {
            const item = document.createElement('div');
            item.className = 'transaction-item';
            item.innerHTML = `
                <div class="transaction-info">
                    <div class="transaction-icon ${transaction.type}">
                        <i class="fas ${transaction.type === 'income' ? 'fa-arrow-up' : 'fa-arrow-down'}"></i>
                    </div>
                    <div class="transaction-details">
                        <h4>${transaction.description}</h4>
                        <p>${this.formatDate(transaction.date)}</p>
                    </div>
                </div>
                <div class="transaction-amount ${transaction.type}">
                    ${transaction.type === 'income' ? '+' : '-'}${this.formatCurrency(transaction.value)}
                </div>
            `;
            container.appendChild(item);
        });
    }

    async loadCategories() {
        try {
            const userId = this.currentUser ? this.currentUser.id : 1;
            const response = await this.apiCall(`/categories?userId=${userId}`, 'GET');
            if (response.success) {
                this.categoriesCache = response.data; // Atualiza o cache
                this.renderCategories(response.data);
                this.updateCategoryFilter(response.data);
            }
        } catch (error) {
            console.error('Error loading categories:', error);
        }
    }

    renderCategories(categories) {
        const container = document.getElementById('categories-list');
        container.innerHTML = '';

        if (categories.length === 0) {
            container.innerHTML = '<p class="text-center">Nenhuma categoria cadastrada</p>';
            return;
        }

        categories.forEach(category => {
            const card = document.createElement('div');
            card.className = 'category-card';
            card.innerHTML = `
                <h3>${category.nome}</h3>
                <p>ID: ${category.idCategoria}</p>
                <div class="card-actions">
                    <button class="btn-secondary" onclick="app.editCategory(${category.idCategoria})">
                        <i class="fas fa-edit"></i> Editar
                    </button>
                    <button class="btn-secondary" onclick="app.deleteCategory(${category.idCategoria})">
                        <i class="fas fa-trash"></i> Excluir
                    </button>
                </div>
            `;
            container.appendChild(card);
        });
    }

    async loadAccounts() {
        try {
            const response = await this.apiCall('/accounts', 'GET');
            if (response.success) {
                this.accountsCache = response.data; // Atualiza o cache
                this.renderAccounts(response.data);
            }
        } catch (error) {
            console.error('Error loading accounts:', error);
        }
    }

    renderAccounts(accounts) {
        const container = document.getElementById('accounts-list');
        container.innerHTML = '';

        if (accounts.length === 0) {
            container.innerHTML = '<p class="text-center">Nenhuma conta cadastrada</p>';
            return;
        }

        accounts.forEach(account => {
            const card = document.createElement('div');
            card.className = 'account-card';
            card.innerHTML = `
                <h3>${account.nome}</h3>
                <p class="account-type">${account.tipo}</p>
                <p class="account-balance">${this.formatCurrency(account.saldoAtual)}</p>
                <div class="card-actions">
                    <button class="btn-secondary" onclick="app.editAccount(${account.idConta})">
                        <i class="fas fa-edit"></i> Editar
                    </button>
                    <button class="btn-secondary" onclick="app.deleteAccount(${account.idConta})">
                        <i class="fas fa-trash"></i> Excluir
                    </button>
                </div>
            `;
            container.appendChild(card);
        });
    }

    async loadTransactions(categoryId = null, date = null) {
        try {
            let url = '/transactions?userId=1';
            if (categoryId) {
                url += `&categoryId=${categoryId}`;
            }
            if (date) {
                url += `&date=${date}`;
            }
            
            const response = await this.apiCall(url, 'GET');
            if (response.success) {
                this.renderTransactions(response.data);
            }
        } catch (error) {
            console.error('Error loading transactions:', error);
        }
    }

    renderTransactions(transactions) {
        const container = document.getElementById('transactions-list');
        container.innerHTML = '';

        if (transactions.length === 0) {
            container.innerHTML = '<p class="text-center">Nenhuma transação encontrada</p>';
            return;
        }

        transactions.forEach(transaction => {
            const item = document.createElement('div');
            item.className = 'transaction-item';
            item.innerHTML = `
                <div class="transaction-info">
                    <div class="transaction-icon ${transaction.type}">
                        <i class="fas ${transaction.type === 'income' ? 'fa-arrow-up' : 'fa-arrow-down'}"></i>
                    </div>
                    <div class="transaction-details">
                        <h4>${transaction.description}</h4>
                        <p>${this.formatDate(transaction.date)} - ${transaction.category}</p>
                    </div>
                </div>
                <div class="transaction-amount ${transaction.type}">
                    ${transaction.type === 'income' ? '+' : '-'}${this.formatCurrency(transaction.value)}
                </div>
            `;
            container.appendChild(item);
        });
    }

    async loadBudgets() {
        try {
            const response = await this.apiCall('/budgets', 'GET');
            if (response.success) {
                this.renderBudgets(response.data);
            }
        } catch (error) {
            console.error('Error loading budgets:', error);
        }
    }

    renderBudgets(budgets) {
        const container = document.getElementById('budgets-list');
        container.innerHTML = '';

        if (budgets.length === 0) {
            container.innerHTML = '<p class="text-center">Nenhum orçamento definido</p>';
            return;
        }

        budgets.forEach(budget => {
            const card = document.createElement('div');
            card.className = 'budget-card';
            card.innerHTML = `
                <h3>${budget.categoryName}</h3>
                <p class="budget-period">${budget.periodo}</p>
                <p class="budget-amount">${this.formatCurrency(budget.valorPlanejado)}</p>
                <div class="budget-progress">
                    <div class="progress-bar">
                        <div class="progress-fill" style="width: ${budget.percentageUsed}%"></div>
                    </div>
                    <span class="progress-text">${budget.percentageUsed}% usado</span>
                </div>
                <div class="card-actions">
                    <button class="btn-secondary" onclick="app.editBudget(${budget.idOrcamento})">
                        <i class="fas fa-edit"></i> Editar
                    </button>
                    <button class="btn-secondary" onclick="app.deleteBudget(${budget.idOrcamento})">
                        <i class="fas fa-trash"></i> Excluir
                    </button>
                </div>
            `;
            container.appendChild(card);
        });
    }

    // ===== MODAL MANAGEMENT =====
    showModal(title, content) {
        document.getElementById('modal-title').textContent = title;
        document.getElementById('modal-body').innerHTML = content;
        document.getElementById('modal-overlay').classList.add('active');
    }

    closeModal() {
        document.getElementById('modal-overlay').classList.remove('active');
    }

    showAddCategoryModal() {
        const content = `
            <form id="add-category-form">
                <div class="form-group">
                    <label for="category-name">Nome da Categoria</label>
                    <input type="text" id="category-name" required>
                </div>
                <div class="form-actions">
                    <button type="button" class="btn-secondary" onclick="app.closeModal()">Cancelar</button>
                    <button type="submit" class="btn-primary">Salvar</button>
                </div>
            </form>
        `;
        this.showModal('Nova Categoria', content);
        
        document.getElementById('add-category-form').addEventListener('submit', (e) => {
            e.preventDefault();
            this.addCategory();
        });
    }

    showAddAccountModal() {
        const content = `
            <form id="add-account-form">
                <div class="form-group">
                    <label for="account-name">Nome da Conta</label>
                    <input type="text" id="account-name" required>
                </div>
                <div class="form-group">
                    <label for="account-type">Tipo</label>
                    <select id="account-type" required>
                        <option value="">Selecione o tipo</option>
                        <option value="Corrente">Conta Corrente</option>
                        <option value="Poupança">Poupança</option>
                        <option value="Cartão">Cartão de Crédito</option>
                        <option value="Investimento">Investimento</option>
                    </select>
                </div>
                <div class="form-group">
                    <label for="account-balance">Saldo Atual</label>
                    <input type="number" id="account-balance" step="0.01" required>
                </div>
                <div class="form-actions">
                    <button type="button" class="btn-secondary" onclick="app.closeModal()">Cancelar</button>
                    <button type="submit" class="btn-primary">Salvar</button>
                </div>
            </form>
        `;
        this.showModal('Nova Conta', content);
        
        document.getElementById('add-account-form').addEventListener('submit', (e) => {
            e.preventDefault();
            this.addAccount();
        });
    }

    showAddExpenseModal() {
        Promise.all([this.loadCategories(), this.loadAccounts()]).then(() => {
            if (this.categoriesCache.length === 0) {
                this.showToast('Você precisa criar pelo menos uma categoria antes de registrar um gasto!', 'warning');
                this.closeModal();
                setTimeout(() => {
                    document.querySelector('[data-section="categories"]')?.click();
                }, 1500);
                return;
            }
            
            if (this.accountsCache.length === 0) {
                this.showToast('Você precisa criar pelo menos uma conta antes de registrar um gasto!', 'warning');
                this.closeModal();
                setTimeout(() => {
                    document.querySelector('[data-section="accounts"]')?.click();
                }, 1500);
                return;
            }
            
            const accounts = this.getAccountsForSelect();
            const categoryCheckboxes = this.getCategoriesForCheckbox();
            const content = `
                <form id="add-expense-form">
                    <div class="form-group">
                        <label for="expense-description">Descrição</label>
                        <input type="text" id="expense-description" required>
                    </div>
                    <div class="form-group">
                        <label for="expense-value">Valor</label>
                        <input type="number" id="expense-value" step="0.01" required>
                    </div>
                    <div class="form-group">
                        <label for="expense-date">Data</label>
                        <input type="date" id="expense-date" required>
                    </div>
                    <div class="form-group">
                        <label>Categorias</label>
                        <div id="expense-categories" class="checkbox-group">
                            ${categoryCheckboxes}
                        </div>
                    </div>
                    <div class="form-group">
                        <label for="expense-account">Conta</label>
                        <select id="expense-account" required>
                            <option value="">Selecione a conta</option>
                            ${accounts}
                        </select>
                    </div>
                    <div class="form-group">
                        <label for="expense-frequency">Frequência</label>
                        <select id="expense-frequency" required>
                            <option value="Único">Único</option>
                            <option value="Semanal">Semanal</option>
                            <option value="Mensal">Mensal</option>
                            <option value="Anual">Anual</option>
                        </select>
                    </div>
                    <div class="form-actions">
                        <button type="button" class="btn-secondary" onclick="app.closeModal()">Cancelar</button>
                        <button type="submit" class="btn-primary">Salvar</button>
                    </div>
                </form>
            `;
            this.showModal('Novo Gasto', content);
            
            document.getElementById('add-expense-form').addEventListener('submit', (e) => {
                e.preventDefault();
                this.addExpense();
            });
        });
    }

    showAddIncomeModal() {
        this.loadAccounts().then(() => {
            if (this.accountsCache.length === 0) {
                this.showToast('Você precisa criar pelo menos uma conta antes de registrar uma receita!', 'warning');
                this.closeModal();
                setTimeout(() => {
                    document.querySelector('[data-section="accounts"]')?.click();
                }, 1500);
                return;
            }
            
            const accounts = this.getAccountsForSelect();
            const content = `
                <form id="add-income-form">
                    <div class="form-group">
                        <label for="income-description">Descrição</label>
                        <input type="text" id="income-description" placeholder="Ex: Salário, Freelance, etc." required>
                    </div>
                    <div class="form-group">
                        <label for="income-value">Valor</label>
                        <input type="number" id="income-value" step="0.01" required>
                    </div>
                    <div class="form-group">
                        <label for="income-date">Data</label>
                        <input type="date" id="income-date" required>
                    </div>
                    <div class="form-group">
                        <label for="income-account">Conta</label>
                        <select id="income-account" required>
                            <option value="">Selecione a conta</option>
                            ${accounts}
                        </select>
                    </div>
                    <div class="form-actions">
                        <button type="button" class="btn-secondary" onclick="app.closeModal()">Cancelar</button>
                        <button type="submit" class="btn-primary">Salvar</button>
                    </div>
                </form>
            `;
            this.showModal('Nova Receita', content);
            
            document.getElementById('add-income-form').addEventListener('submit', (e) => {
                e.preventDefault();
                this.addIncome();
            });
        });
    }

    showAddBudgetModal() {
        this.loadCategories().then(() => {
            // Verifica se há categorias disponíveis
            if (this.categoriesCache.length === 0) {
                this.showToast('Você precisa criar pelo menos uma categoria antes de definir um orçamento!', 'warning');
                this.closeModal();
                // Navega para a seção de categorias
                setTimeout(() => {
                    const categoriesNavItem = document.querySelector('[data-section="categories"]');
                    if (categoriesNavItem) {
                        categoriesNavItem.click();
                    }
                }, 1500);
                return;
            }
            
            const categories = this.getCategoriesForSelect();
            const content = `
                <form id="add-budget-form">
                    <div class="form-group">
                        <label for="budget-category">Categoria</label>
                        <select id="budget-category" required>
                            <option value="">Selecione a categoria</option>
                            ${categories}
                        </select>
                    </div>
                    <div class="form-group">
                        <label for="budget-value">Valor Planejado</label>
                        <input type="number" id="budget-value" step="0.01" required>
                    </div>
                    <div class="form-group">
                        <label for="budget-period">Período</label>
                        <select id="budget-period" required>
                            <option value="Mensal">Mensal</option>
                            <option value="Anual">Anual</option>
                        </select>
                    </div>
                    <div class="form-actions">
                        <button type="button" class="btn-secondary" onclick="app.closeModal()">Cancelar</button>
                        <button type="submit" class="btn-primary">Salvar</button>
                    </div>
                </form>
            `;
            this.showModal('Novo Orçamento', content);
            
            document.getElementById('add-budget-form').addEventListener('submit', (e) => {
                e.preventDefault();
                this.addBudget();
            });
        });
    }

    // ===== API CALLS =====
    async addCategory() {
        const name = document.getElementById('category-name').value;
        try {
            const userId = this.currentUser ? this.currentUser.id : 1;
            const response = await this.apiCall('/categories', 'POST', { name, userId });
            if (response.success) {
                this.showToast('Categoria adicionada com sucesso!', 'success');
                this.closeModal();
                // Recarrega as categorias para atualizar o cache
                await this.loadCategories();
            } else {
                this.showToast(response.message || 'Erro ao adicionar categoria', 'error');
            }
        } catch (error) {
            this.showToast('Erro de conexão', 'error');
            console.error('Add category error:', error);
        }
    }

    async addAccount() {
        const name = document.getElementById('account-name').value;
        const type = document.getElementById('account-type').value;
        const balance = parseFloat(document.getElementById('account-balance').value);
        
        try {
            const response = await this.apiCall('/accounts', 'POST', { name, type, balance });
            if (response.success) {
                this.showToast('Conta adicionada com sucesso!', 'success');
                this.closeModal();
                this.loadAccounts();
            } else {
                this.showToast(response.message || 'Erro ao adicionar conta', 'error');
            }
        } catch (error) {
            this.showToast('Erro de conexão', 'error');
            console.error('Add account error:', error);
        }
    }

    async addExpense() {
        const description = document.getElementById('expense-description').value;
        const value = parseFloat(document.getElementById('expense-value').value);
        const date = document.getElementById('expense-date').value;
        const accountId = parseInt(document.getElementById('expense-account').value);
        const frequency = document.getElementById('expense-frequency').value;
        
        // Obtém todas as categorias marcadas (checkboxes)
        const checkedBoxes = document.querySelectorAll('#expense-categories input[type="checkbox"]:checked');
        const categoryIds = Array.from(checkedBoxes).map(checkbox => parseInt(checkbox.value));
        
        // Valida se pelo menos uma categoria foi selecionada
        if (categoryIds.length === 0) {
            this.showToast('Selecione pelo menos uma categoria', 'error');
            return;
        }
        
        try {
            const response = await this.apiCall('/expenses', 'POST', {
                description, value, date, categoryIds, accountId, frequency
            });
            if (response.success) {
                this.showToast('Gasto adicionado com sucesso!', 'success');
                this.closeModal();
                this.loadTransactions();
                this.loadAccounts(); // Recarrega contas para atualizar saldo
                this.updateOverview();
            } else {
                this.showToast(response.message || 'Erro ao adicionar gasto', 'error');
            }
        } catch (error) {
            this.showToast('Erro de conexão', 'error');
            console.error('Add expense error:', error);
        }
    }

    async addIncome() {
        const description = document.getElementById('income-description').value;
        const value = parseFloat(document.getElementById('income-value').value);
        const date = document.getElementById('income-date').value;
        const accountId = parseInt(document.getElementById('income-account').value);
        
        try {
            const response = await this.apiCall('/incomes', 'POST', { description, value, date, accountId });
            if (response.success) {
                this.showToast('Receita adicionada com sucesso!', 'success');
                this.closeModal();
                this.loadTransactions();
                this.loadAccounts(); // Recarrega contas para atualizar saldo
                this.updateOverview();
            } else {
                this.showToast(response.message || 'Erro ao adicionar receita', 'error');
            }
        } catch (error) {
            this.showToast('Erro de conexão', 'error');
            console.error('Add income error:', error);
        }
    }

    async addBudget() {
        const categoryId = parseInt(document.getElementById('budget-category').value);
        const value = parseFloat(document.getElementById('budget-value').value);
        const period = document.getElementById('budget-period').value;
        
        try {
            const response = await this.apiCall('/budgets', 'POST', {
                categoryId, value, period
            });
            if (response.success) {
                this.showToast('Orçamento adicionado com sucesso!', 'success');
                this.closeModal();
                this.loadBudgets();
            } else {
                this.showToast(response.message || 'Erro ao adicionar orçamento', 'error');
            }
        } catch (error) {
            this.showToast('Erro de conexão', 'error');
            console.error('Add budget error:', error);
        }
    }

    // ===== UTILITY METHODS =====
    async apiCall(endpoint, method = 'GET', data = null) {
        const url = `${this.apiBaseUrl}${endpoint}`;
        const options = {
            method,
            headers: {
                'Content-Type': 'application/json',
                'Authorization': this.currentUser ? `Bearer ${this.currentUser.token}` : ''
            }
        };

        if (data && method !== 'GET') {
            options.body = JSON.stringify(data);
        }

        const response = await fetch(url, options);
        return await response.json();
    }

    formatCurrency(value) {
        return new Intl.NumberFormat('pt-BR', {
            style: 'currency',
            currency: 'BRL'
        }).format(value);
    }

    formatDate(dateString) {
        // Parse da data sem considerar timezone para evitar bug de dia anterior
        const [year, month, day] = dateString.split('-');
        const date = new Date(year, month - 1, day); // month é 0-indexed
        return date.toLocaleDateString('pt-BR');
    }

    getCategoriesForSelect() {
        if (this.categoriesCache.length === 0) {
            return '<option value="">Nenhuma categoria disponível</option>';
        }
        
        let options = '';
        this.categoriesCache.forEach(category => {
            options += `<option value="${category.idCategoria}">${category.nome}</option>`;
        });
        return options;
    }

    getCategoriesForCheckbox() {
        if (this.categoriesCache.length === 0) {
            return '<p class="no-data">Nenhuma categoria disponível</p>';
        }
        
        let checkboxes = '';
        this.categoriesCache.forEach(category => {
            checkboxes += `
                <div class="checkbox-item">
                    <input type="checkbox" id="cat-${category.idCategoria}" name="category" value="${category.idCategoria}">
                    <label for="cat-${category.idCategoria}">${category.nome}</label>
                </div>
            `;
        });
        return checkboxes;
    }

    getAccountsForSelect() {
        if (this.accountsCache.length === 0) {
            return '<option value="">Nenhuma conta disponível</option>';
        }
        
        let options = '';
        this.accountsCache.forEach(account => {
            options += `<option value="${account.idConta}">${account.nome}</option>`;
        });
        return options;
    }

    updateCategoryFilter(categories) {
        const select = document.getElementById('category-filter');
        select.innerHTML = '<option value="">Todas as Categorias</option>';
        categories.forEach(category => {
            select.innerHTML += `<option value="${category.idCategoria}">${category.nome}</option>`;
        });
    }

    filterTransactions() {
        const categoryId = document.getElementById('category-filter').value;
        const date = document.getElementById('date-filter').value;
        
        // Converte valores vazios para null
        const categoryFilter = categoryId ? parseInt(categoryId) : null;
        const dateFilter = date || null;
        
        console.log('Aplicando filtros:', { categoryFilter, dateFilter });
        this.loadTransactions(categoryFilter, dateFilter);
    }

    clearFilters() {
        document.getElementById('category-filter').value = '';
        document.getElementById('date-filter').value = '';
        this.loadTransactions();
    }

    updateCategoryChart(data) {
        const chartContainer = document.querySelector('.chart-container');
        if (!chartContainer) return;

        // Remove o canvas e cria um container para barras
        const oldCanvas = document.getElementById('category-chart');
        if (oldCanvas) {
            oldCanvas.remove();
        }

        // Cria um novo container para as barras
        let barsContainer = document.getElementById('category-bars');
        if (!barsContainer) {
            barsContainer = document.createElement('div');
            barsContainer.id = 'category-bars';
            barsContainer.className = 'category-bars';
            chartContainer.appendChild(barsContainer);
        }

        // Limpa o container
        barsContainer.innerHTML = '';

        // Se não houver dados, mostra mensagem
        if (!data || data.length === 0) {
            barsContainer.innerHTML = '<p class="no-data">Nenhum gasto registrado ainda</p>';
            return;
        }

        // Filtra apenas categorias com valor > 0
        const filteredData = data.filter(item => item.value > 0);
        
        if (filteredData.length === 0) {
            barsContainer.innerHTML = '<p class="no-data">Nenhum gasto registrado ainda</p>';
            return;
        }

        // Encontra o valor máximo para calcular as porcentagens
        const maxValue = Math.max(...filteredData.map(item => item.value));

        // Ordena por valor decrescente
        filteredData.sort((a, b) => b.value - a.value);

        // Define cores para as categorias
        const colors = [
            '#FF6B6B', '#4ECDC4', '#45B7D1', '#FFA07A', 
            '#98D8C8', '#F7DC6F', '#BB8FCE', '#85C1E2',
            '#F8B739', '#52B788', '#D62828', '#5A189A'
        ];

        // Cria as barras
        filteredData.forEach((item, index) => {
            const percentage = (item.value / maxValue) * 100;
            const color = colors[index % colors.length];

            const barItem = document.createElement('div');
            barItem.className = 'category-bar-item';
            barItem.innerHTML = `
                <div class="category-bar-header">
                    <span class="category-name">${item.name}</span>
                    <span class="category-value">${this.formatCurrency(item.value)}</span>
                </div>
                <div class="category-bar-track">
                    <div class="category-bar-fill" style="width: ${percentage}%; background-color: ${color};"></div>
                </div>
            `;
            barsContainer.appendChild(barItem);
        });
    }

    showToast(message, type = 'info') {
        const container = document.getElementById('toast-container');
        const toast = document.createElement('div');
        toast.className = `toast ${type}`;
        toast.innerHTML = `
            <div class="toast-content">
                <i class="fas ${this.getToastIcon(type)}"></i>
                <span>${message}</span>
            </div>
        `;
        
        container.appendChild(toast);
        
        // Show toast
        setTimeout(() => toast.classList.add('show'), 100);
        
        // Remove toast after 5 seconds
        setTimeout(() => {
            toast.classList.remove('show');
            setTimeout(() => container.removeChild(toast), 300);
        }, 5000);
    }

    getToastIcon(type) {
        const icons = {
            success: 'fa-check-circle',
            error: 'fa-exclamation-circle',
            warning: 'fa-exclamation-triangle',
            info: 'fa-info-circle'
        };
        return icons[type] || icons.info;
    }
    
    // ===== FUNÇÕES DE EDIÇÃO =====
    
    editCategory(categoryId) {
        const categoria = this.categoriesCache.find(c => c.idCategoria === categoryId);
        if (!categoria) return;
        
        const content = `
            <form id="edit-category-form">
                <div class="form-group">
                    <label for="edit-category-name">Nome da Categoria</label>
                    <input type="text" id="edit-category-name" value="${categoria.nome}" required>
                </div>
                <div class="form-actions">
                    <button type="button" class="btn-secondary" onclick="app.closeModal()">Cancelar</button>
                    <button type="submit" class="btn-primary">Salvar Alterações</button>
                </div>
            </form>
        `;
        this.showModal('Editar Categoria', content);
        
        document.getElementById('edit-category-form').addEventListener('submit', async (e) => {
            e.preventDefault();
            const newName = document.getElementById('edit-category-name').value;
            try {
                const response = await this.apiCall('/categories', 'PUT', { id: categoryId, name: newName });
                if (response.success) {
                    this.showToast('Categoria atualizada com sucesso!', 'success');
                    this.closeModal();
                    await this.loadCategories();
                } else {
                    this.showToast(response.message || 'Erro ao atualizar categoria', 'error');
                }
            } catch (error) {
                this.showToast('Erro de conexão', 'error');
                console.error('Edit category error:', error);
            }
        });
    }
    
    editAccount(accountId) {
        this.loadAccounts().then(() => {
            // Busca a conta atual (seria melhor ter um cache)
            const content = `
                <form id="edit-account-form">
                    <div class="form-group">
                        <label for="edit-account-name">Nome da Conta</label>
                        <input type="text" id="edit-account-name" required>
                    </div>
                    <div class="form-group">
                        <label for="edit-account-type">Tipo</label>
                        <select id="edit-account-type" required>
                            <option value="Corrente">Conta Corrente</option>
                            <option value="Poupança">Poupança</option>
                            <option value="Cartão">Cartão de Crédito</option>
                            <option value="Investimento">Investimento</option>
                        </select>
                    </div>
                    <div class="form-group">
                        <label for="edit-account-balance">Saldo Atual</label>
                        <input type="number" id="edit-account-balance" step="0.01" required>
                    </div>
                    <div class="form-actions">
                        <button type="button" class="btn-secondary" onclick="app.closeModal()">Cancelar</button>
                        <button type="submit" class="btn-primary">Salvar Alterações</button>
                    </div>
                </form>
            `;
            this.showModal('Editar Conta', content);
            
            document.getElementById('edit-account-form').addEventListener('submit', async (e) => {
                e.preventDefault();
                const name = document.getElementById('edit-account-name').value;
                const type = document.getElementById('edit-account-type').value;
                const balance = parseFloat(document.getElementById('edit-account-balance').value);
                
                try {
                    const response = await this.apiCall('/accounts', 'PUT', { id: accountId, name, type, balance });
                    if (response.success) {
                        this.showToast('Conta atualizada com sucesso!', 'success');
                        this.closeModal();
                        await this.loadAccounts();
                    } else {
                        this.showToast(response.message || 'Erro ao atualizar conta', 'error');
                    }
                } catch (error) {
                    this.showToast('Erro de conexão', 'error');
                    console.error('Edit account error:', error);
                }
            });
        });
    }
    
    editBudget(budgetId) {
        const content = `
            <form id="edit-budget-form">
                <div class="form-group">
                    <label for="edit-budget-value">Valor Planejado</label>
                    <input type="number" id="edit-budget-value" step="0.01" required>
                </div>
                <div class="form-group">
                    <label for="edit-budget-period">Período</label>
                    <select id="edit-budget-period" required>
                        <option value="Mensal">Mensal</option>
                        <option value="Anual">Anual</option>
                    </select>
                </div>
                <div class="form-actions">
                    <button type="button" class="btn-secondary" onclick="app.closeModal()">Cancelar</button>
                    <button type="submit" class="btn-primary">Salvar Alterações</button>
                </div>
            </form>
        `;
        this.showModal('Editar Orçamento', content);
        
        document.getElementById('edit-budget-form').addEventListener('submit', async (e) => {
            e.preventDefault();
            const value = parseFloat(document.getElementById('edit-budget-value').value);
            const period = document.getElementById('edit-budget-period').value;
            
            try {
                const response = await this.apiCall('/budgets', 'PUT', { id: budgetId, value, period });
                if (response.success) {
                    this.showToast('Orçamento atualizado com sucesso!', 'success');
                    this.closeModal();
                    await this.loadBudgets();
                } else {
                    this.showToast(response.message || 'Erro ao atualizar orçamento', 'error');
                }
            } catch (error) {
                this.showToast('Erro de conexão', 'error');
                console.error('Edit budget error:', error);
            }
        });
    }
    
    // ===== FUNÇÕES DE EXCLUSÃO COM CONFIRMAÇÃO =====
    
    deleteCategory(categoryId) {
        if (!confirm('Tem certeza que deseja excluir esta categoria? Esta ação não pode ser desfeita.')) {
            return;
        }
        
        this.apiCall(`/categories?id=${categoryId}`, 'DELETE').then(response => {
            if (response.success) {
                this.showToast('Categoria excluída com sucesso!', 'success');
                this.loadCategories();
            } else {
                this.showToast(response.message || 'Erro ao excluir categoria', 'error');
            }
        }).catch(error => {
            this.showToast('Erro de conexão', 'error');
            console.error('Delete category error:', error);
        });
    }
    
    deleteAccount(accountId) {
        if (!confirm('Tem certeza que deseja excluir esta conta? Esta ação não pode ser desfeita.')) {
            return;
        }
        
        this.apiCall(`/accounts?id=${accountId}`, 'DELETE').then(response => {
            if (response.success) {
                this.showToast('Conta excluída com sucesso!', 'success');
                this.loadAccounts();
            } else {
                this.showToast(response.message || 'Erro ao excluir conta', 'error');
            }
        }).catch(error => {
            this.showToast('Erro de conexão', 'error');
            console.error('Delete account error:', error);
        });
    }
    
    deleteBudget(budgetId) {
        if (!confirm('Tem certeza que deseja excluir este orçamento? Esta ação não pode ser desfeita.')) {
            return;
        }
        
        this.apiCall(`/budgets?id=${budgetId}`, 'DELETE').then(response => {
            if (response.success) {
                this.showToast('Orçamento excluído com sucesso!', 'success');
                this.loadBudgets();
            } else {
                this.showToast(response.message || 'Erro ao excluir orçamento', 'error');
            }
        }).catch(error => {
            this.showToast('Erro de conexão', 'error');
            console.error('Delete budget error:', error);
        });
    }
}

// ===== INITIALIZE APP =====
const app = new ControleSeApp();

// ===== CHART.JS INTEGRATION =====
// This would be loaded from CDN: https://cdn.jsdelivr.net/npm/chart.js
// For now, we'll just log chart updates
