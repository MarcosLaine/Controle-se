// ===== CONTROLE-SE FRONTEND APPLICATION =====
class ControleSeApp {
    constructor() {
        this.currentUser = null;
        // Detecta automaticamente a URL da API (produção ou desenvolvimento)
        this.apiBaseUrl = window.location.hostname === 'localhost' 
            ? 'http://localhost:8080/api' 
            : `${window.location.origin}/api`;
        this.categoriesCache = []; // Cache de categorias
        this.accountsCache = []; // Cache de contas
        this.tagsCache = []; // Cache de tags
        this.transactionsCache = []; // Cache de transações
        this._loadingAccounts = false; // Flag para evitar loops infinitos ao carregar contas
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
        document.getElementById('add-tag-btn').addEventListener('click', () => this.showAddTagModal());
        document.getElementById('add-investment-btn').addEventListener('click', () => this.showAddInvestmentModal());

        // Filters
        document.getElementById('category-filter').addEventListener('change', () => this.filterTransactions());
        document.getElementById('tag-filter').addEventListener('change', () => this.filterTransactions());
        document.getElementById('date-filter').addEventListener('change', () => this.filterTransactions());
        document.getElementById('clear-filters').addEventListener('click', () => this.clearFilters());

        // Period select
        document.getElementById('period-select').addEventListener('change', () => this.updateOverview());
        
        // Reports
        document.getElementById('report-period').addEventListener('change', () => this.handleReportPeriodChange());
        document.getElementById('export-report-btn').addEventListener('click', () => this.handleExport());
        document.getElementById('export-format').addEventListener('change', () => this.updateExportButtonIcon());
        document.getElementById('start-date').addEventListener('change', () => this.loadReports());
        document.getElementById('end-date').addEventListener('change', () => this.loadReports());
        
        // Inicializar ícone do botão de exportação
        this.updateExportButtonIcon();
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
        
        // Limpa o cache para garantir que os dados sejam recarregados com o userId correto
        this.categoriesCache = [];
        this.accountsCache = [];
        this.tagsCache = [];
        this.transactionsCache = [];
        
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
                this.loadBudgets(),
                this.loadTags()
            ]);
        } catch (error) {
            console.error('Error loading dashboard data:', error);
            this.showToast('Erro ao carregar dados do dashboard', 'error');
        }
    }

    async loadSectionData(section) {
        switch (section) {
            case 'overview':
                await Promise.all([
                    this.updateOverview(),
                    this.loadRecentTransactions()
                ]);
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
            case 'tags':
                await this.loadTags();
                break;
            case 'reports':
                await this.loadReports();
                break;
            case 'investments':
                await this.loadInvestments();
                break;
        }
    }

    async updateOverview() {
        try {
            const userId = this.currentUser ? this.currentUser.id : 1;
            const response = await this.apiCall(`/dashboard/overview?userId=${userId}`, 'GET');
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
        
        const netWorthElement = document.getElementById('total-net-worth');
        if (netWorthElement) {
            netWorthElement.textContent = this.formatCurrency(data.netWorth);
        }
    }

    showSaldoInfo(event) {
        if (event) event.stopPropagation();
        this.showModal('Informação: Saldo', `
            <div style="padding: 10px;">
                <p style="margin-bottom: 10px;">O <strong>Saldo</strong> representa a soma de todas as suas contas disponíveis para uso imediato (Corrente, Poupança, Dinheiro, etc.).</p>
                <p>Contas do tipo <strong>Investimento</strong> não são contabilizadas neste valor.</p>
            </div>
        `);
    }

    showPatrimonioInfo(event) {
        if (event) event.stopPropagation();
        this.showModal('Informação: Patrimônio', `
            <div style="padding: 10px;">
                <p style="margin-bottom: 10px;">O <strong>Patrimônio</strong> representa o valor total acumulado em todas as suas contas cadastrada no sistema.</p>
                <p>Este valor inclui o saldo de todas as contas, inclusive as de <strong>Investimento</strong>.</p>
            </div>
        `);
    }

    async loadRecentTransactions() {
        try {
            const userId = this.currentUser ? this.currentUser.id : 1;
            const response = await this.apiCall(`/transactions/recent?userId=${userId}`, 'GET');
            if (response && response.success) {
                this.renderRecentTransactions(response.data || []);
            } else {
                console.warn('loadRecentTransactions: response not successful', response);
                this.renderRecentTransactions([]);
            }
        } catch (error) {
            console.error('Error loading recent transactions:', error);
            this.renderRecentTransactions([]);
        }
    }

    renderRecentTransactions(transactions) {
        const container = document.getElementById('recent-transactions-list');
        if (!container) {
            console.error('recent-transactions-list container not found');
            return;
        }
        
        container.innerHTML = '';

        if (!transactions || !Array.isArray(transactions) || transactions.length === 0) {
            container.innerHTML = '<p class="text-center">Nenhuma transação recente</p>';
            return;
        }

        transactions.forEach(transaction => {
            if (!transaction) return;
            
            const item = document.createElement('div');
            item.className = 'transaction-item';
            item.innerHTML = `
                <div class="transaction-info">
                    <div class="transaction-icon ${transaction.type || 'expense'}">
                        <i class="fas ${transaction.type === 'income' ? 'fa-arrow-up' : 'fa-arrow-down'}"></i>
                    </div>
                    <div class="transaction-details">
                        <h4>${transaction.description || 'Sem descrição'}</h4>
                        <p>${transaction.date ? this.formatDate(transaction.date) : ''}</p>
                    </div>
                </div>
                <div class="transaction-amount ${transaction.type || 'expense'}">
                    ${transaction.type === 'income' ? '+' : '-'}${this.formatCurrency(transaction.value || 0)}
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
            const userId = this.currentUser ? this.currentUser.id : 1;
            const response = await this.apiCall(`/accounts?userId=${userId}`, 'GET');
            if (response.success) {
                this.accountsCache = response.data || []; // Atualiza o cache, garantindo que seja um array
                this.renderAccounts(this.accountsCache);
            } else {
                // Se a resposta não foi bem-sucedida, inicializa como array vazio
                this.accountsCache = [];
            }
        } catch (error) {
            console.error('Error loading accounts:', error);
            // Em caso de erro, inicializa como array vazio para evitar loops
            this.accountsCache = [];
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

    async loadTransactions(categoryId = null, date = null, tagId = null) {
        try {
            const userId = this.currentUser ? this.currentUser.id : 1;
            let url = `/transactions?userId=${userId}`;
            if (categoryId) {
                url += `&categoryId=${categoryId}`;
            }
            if (date) {
                url += `&date=${date}`;
            }
            
            const response = await this.apiCall(url, 'GET');
            if (response.success) {
                this.transactionsCache = response.data;
                
                // Aplica filtro de tag se houver (client-side)
                let filteredTransactions = response.data;
                if (tagId) {
                    filteredTransactions = response.data.filter(transaction => 
                        transaction.tags && transaction.tags.some(tag => tag.idTag === parseInt(tagId))
                    );
                }
                
                this.renderTransactions(filteredTransactions);
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
            
            // Renderiza tags se houver
            let tagsHtml = '';
            if (transaction.tags && transaction.tags.length > 0) {
                tagsHtml = '<div class="transaction-tags">' + 
                    transaction.tags.map(tag => 
                        `<span class="tag-badge-small" style="background-color: ${tag.cor};">${tag.nome}</span>`
                    ).join('') + 
                    '</div>';
            }
            
            // Renderiza observações se houver
            let observationsHtml = '';
            if (transaction.observacoes && transaction.observacoes.length > 0) {
                const observationsList = transaction.observacoes.map(obs => `<li>${obs}</li>`).join('');
                observationsHtml = `
                    <div class="transaction-observations">
                        <strong>Observações:</strong>
                        <ul>${observationsList}</ul>
                    </div>
                `;
            }
            
            item.innerHTML = `
                <div class="transaction-info">
                    <div class="transaction-icon ${transaction.type}">
                        <i class="fas ${transaction.type === 'income' ? 'fa-arrow-up' : 'fa-arrow-down'}"></i>
                    </div>
                    <div class="transaction-details">
                        <h4>${transaction.description}</h4>
                        <p>${this.formatDate(transaction.date)} - ${transaction.category}</p>
                        ${tagsHtml}
                        ${observationsHtml}
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
            const userId = this.currentUser ? this.currentUser.id : 1;
            const response = await this.apiCall(`/budgets?userId=${userId}`, 'GET');
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
                    <span class="progress-text">${parseFloat(budget.percentageUsed).toFixed(2)}% usado</span>
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

    // ===== TAGS MANAGEMENT =====
    async loadTags() {
        try {
            const userId = this.currentUser ? this.currentUser.id : 1;
            const response = await this.apiCall(`/tags?userId=${userId}`, 'GET');
            if (response.success) {
                this.tagsCache = response.data;
                this.renderTags(response.data);
                this.updateTagFilter(response.data);
            }
        } catch (error) {
            console.error('Error loading tags:', error);
        }
    }

    renderTags(tags) {
        const container = document.getElementById('tags-list');
        container.innerHTML = '';

        if (tags.length === 0) {
            container.innerHTML = '<p class="text-center">Nenhuma tag criada. Crie tags para organizar suas transações!</p>';
            return;
        }

        tags.forEach(tag => {
            const card = document.createElement('div');
            card.className = 'tag-card';
            card.innerHTML = `
                <div class="tag-header">
                    <span class="tag-badge" style="background-color: ${tag.cor};">
                        <i class="fas fa-tag"></i>
                        ${tag.nome}
                    </span>
                </div>
                <div class="card-actions">
                    <button class="btn-secondary" onclick="app.editTag(${tag.idTag}, '${tag.nome}', '${tag.cor}')">
                        <i class="fas fa-edit"></i> Editar
                    </button>
                    <button class="btn-secondary" onclick="app.deleteTag(${tag.idTag})">
                        <i class="fas fa-trash"></i> Excluir
                    </button>
                </div>
            `;
            container.appendChild(card);
        });
    }

    getTagsForSelect() {
        return this.tagsCache.map(tag => 
            `<div class="checkbox-item">
                <input type="checkbox" id="tag-${tag.idTag}" value="${tag.idTag}">
                <label for="tag-${tag.idTag}">
                    <span class="tag-badge-small" style="background-color: ${tag.cor};">${tag.nome}</span>
                </label>
            </div>`
        ).join('');
    }

    // ===== MODAL MANAGEMENT =====
    showModal(title, content) {
        const modalTitle = document.getElementById('modal-title');
        const modalBody = document.getElementById('modal-body');
        const modalOverlay = document.getElementById('modal-overlay');
        
        if (!modalTitle || !modalBody || !modalOverlay) {
            console.error('Elementos do modal não encontrados');
            return;
        }
        
        if (title) {
            modalTitle.textContent = title;
        } else {
            modalTitle.textContent = '';
        }
        
        if (content) {
            modalBody.innerHTML = content;
        } else {
            modalBody.innerHTML = '';
        }
        
        modalOverlay.classList.add('active');
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
                
                <div class="form-divider">
                    <hr>
                    <span>Orçamento (Opcional)</span>
                    <hr>
                </div>
                
                <div class="form-group">
                    <label>
                        <input type="checkbox" id="add-budget-checkbox" onchange="app.toggleBudgetFields()">
                        Definir orçamento para esta categoria
                    </label>
                </div>
                
                <div id="budget-fields" style="display: none;">
                    <div class="form-group">
                        <label for="category-budget-value">Valor Planejado (R$)</label>
                        <input type="number" id="category-budget-value" step="0.01" min="0" placeholder="0.00">
                    </div>
                    <div class="form-group">
                        <label for="category-budget-period">Período</label>
                        <select id="category-budget-period">
                            <option value="Mensal">Mensal</option>
                            <option value="Anual">Anual</option>
                        </select>
                    </div>
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
    
    toggleBudgetFields() {
        const checkbox = document.getElementById('add-budget-checkbox');
        const budgetFields = document.getElementById('budget-fields');
        const budgetValue = document.getElementById('category-budget-value');
        
        if (checkbox.checked) {
            budgetFields.style.display = 'block';
            budgetValue.required = true;
        } else {
            budgetFields.style.display = 'none';
            budgetValue.required = false;
            budgetValue.value = '';
        }
    }

    showAddAccountModal() {
        this.showAddAccountModalInternal();
    }
    
    showAddAccountModalFromInvestment() {
        this.showAddAccountModalInternal('Investimento', true);
    }
    
    showAddAccountModalInternal(preselectedType = null, returnToInvestment = false) {
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
                        <option value="Investimento" ${preselectedType === 'Investimento' ? 'selected' : ''}>Investimento (Corretora)</option>
                    </select>
                    <small id="account-type-help" style="color: var(--neutral-600); margin-top: 4px; display: ${preselectedType === 'Investimento' ? 'block' : 'none'};">
                        <i class="fas fa-info-circle"></i> Esta conta será usada como corretora ao criar investimentos
                    </small>
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
        
        // Adiciona listener para mostrar/ocultar texto de ajuda quando tipo for selecionado
        const accountTypeSelect = document.getElementById('account-type');
        const accountTypeHelp = document.getElementById('account-type-help');
        
        if (accountTypeSelect && accountTypeHelp) {
            accountTypeSelect.addEventListener('change', () => {
                if (accountTypeSelect.value === 'Investimento') {
                    accountTypeHelp.style.display = 'block';
                } else {
                    accountTypeHelp.style.display = 'none';
                }
            });
        }
        
        document.getElementById('add-account-form').addEventListener('submit', async (e) => {
            e.preventDefault();
            const newAccountId = await this.addAccount(returnToInvestment);
            if (returnToInvestment && newAccountId) {
                // Atualiza o select de contas no modal de investimento (que ainda está aberto)
                setTimeout(() => {
                    const accountSelect = document.getElementById('investment-account');
                    if (accountSelect) {
                        // Limpa o select
                        accountSelect.innerHTML = '<option value="">Selecione uma conta de investimento...</option>';
                        
                        // Recarrega as contas do cache atualizado
                        const investmentAccounts = this.accountsCache.filter(acc => 
                            acc.tipo && acc.tipo.toLowerCase() === 'investimento'
                        );
                        
                        investmentAccounts.forEach(account => {
                            const option = document.createElement('option');
                            option.value = account.idConta;
                            option.textContent = `${account.nome} (${account.tipo})`;
                            accountSelect.appendChild(option);
                        });
                        
                        // Adiciona opção para criar nova conta novamente
                        const newAccountOption = document.createElement('option');
                        newAccountOption.value = '__new__';
                        newAccountOption.textContent = '➕ Criar nova conta de investimento...';
                        newAccountOption.style.fontWeight = 'bold';
                        newAccountOption.style.color = 'var(--primary-color)';
                        accountSelect.appendChild(newAccountOption);
                        
                        // Seleciona a nova conta
                        accountSelect.value = newAccountId;
                        
                        // Re-adiciona o event listener para criar nova conta
                        accountSelect.addEventListener('change', () => {
                            if (accountSelect.value === '__new__') {
                                const investmentFormData = {
                                    category: document.getElementById('investment-category')?.value || '',
                                    name: document.getElementById('investment-name')?.value || '',
                                    quantity: document.getElementById('investment-quantity')?.value || '',
                                    date: document.getElementById('investment-date')?.value || '',
                                    brokerage: document.getElementById('investment-brokerage')?.value || '0',
                                    currency: document.getElementById('investment-currency')?.value || 'BRL'
                                };
                                this._pendingInvestmentData = investmentFormData;
                                this.showAddAccountModalFromInvestment();
                            }
                        });
                    }
                }, 200);
            }
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
                        <button type="button" class="btn-link" onclick="app.toggleNewCategoryInExpense()">
                            <i class="fas fa-plus-circle"></i> Nova Categoria
                        </button>
                        <div id="new-category-inline" style="display: none; margin-top: 10px;">
                            <input type="text" id="new-category-name-expense" placeholder="Nome da nova categoria" style="width: 100%; padding: 8px; border: 1px solid var(--neutral-300); border-radius: 4px;">
                            <button type="button" class="btn-primary" onclick="app.createCategoryInline()" style="margin-top: 8px; width: 100%;">
                                Criar Categoria
                            </button>
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
                    <div class="form-group">
                        <label>Tags (opcional)</label>
                        <div id="expense-tags" class="checkbox-group">
                            ${this.getTagsForSelect()}
                        </div>
                    </div>
                    <div class="form-group">
                        <label for="expense-observations">Observações (opcional)</label>
                        <textarea id="expense-observations" rows="3" placeholder="Digite observações separadas por vírgula ou quebra de linha..."></textarea>
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
                    <div class="form-group">
                        <label>Tags (opcional)</label>
                        <div id="income-tags" class="checkbox-group">
                            ${this.getTagsForSelect()}
                        </div>
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
            const categories = this.getCategoriesForSelect();
            const content = `
                <form id="add-budget-form">
                    <div class="form-group">
                        <label for="budget-category">Categoria</label>
                        <select id="budget-category" required onchange="app.toggleNewCategoryField()">
                            <option value="">Selecione a categoria</option>
                            ${categories}
                            <option value="__new__" style="font-weight: bold; color: var(--primary-color);">➕ Nova Categoria...</option>
                        </select>
                    </div>
                    
                    <div id="new-category-field" style="display: none;">
                        <div class="form-group">
                            <label for="new-category-name">Nome da Nova Categoria</label>
                            <input type="text" id="new-category-name" placeholder="Ex: Transporte, Educação...">
                        </div>
                    </div>
                    
                    <div class="form-group">
                        <label for="budget-value">Valor Planejado (R$)</label>
                        <input type="number" id="budget-value" step="0.01" min="0" required>
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
    
    toggleNewCategoryField() {
        const categorySelect = document.getElementById('budget-category');
        const newCategoryField = document.getElementById('new-category-field');
        const newCategoryInput = document.getElementById('new-category-name');
        
        if (categorySelect && categorySelect.value === '__new__') {
            newCategoryField.style.display = 'block';
            newCategoryInput.required = true;
            newCategoryInput.focus();
        } else {
            newCategoryField.style.display = 'none';
            newCategoryInput.required = false;
            newCategoryInput.value = '';
        }
    }

    toggleNewCategoryInExpense() {
        const newCategoryDiv = document.getElementById('new-category-inline');
        const newCategoryInput = document.getElementById('new-category-name-expense');
        
        if (newCategoryDiv.style.display === 'none') {
            newCategoryDiv.style.display = 'block';
            newCategoryInput.focus();
        } else {
            newCategoryDiv.style.display = 'none';
            newCategoryInput.value = '';
        }
    }

    async createCategoryInline() {
        const nameInput = document.getElementById('new-category-name-expense');
        const name = nameInput.value.trim();
        
        if (!name) {
            this.showToast('Por favor, informe o nome da categoria', 'warning');
            return;
        }
        
        const userId = this.currentUser ? this.currentUser.id : 1;
        
        try {
            const response = await this.apiCall('/categories', 'POST', { name, userId });
            if (response.success) {
                this.showToast('Categoria criada com sucesso!', 'success');
                
                // Atualiza o cache
                await this.loadCategories();
                
                // Adiciona a nova categoria à lista de checkboxes e marca ela
                const newCategory = {
                    idCategoria: response.categoryId,
                    nome: name
                };
                
                const checkboxContainer = document.getElementById('expense-categories');
                const checkboxDiv = document.createElement('div');
                checkboxDiv.className = 'checkbox-item';
                checkboxDiv.innerHTML = `
                    <input type="checkbox" id="cat-${newCategory.idCategoria}" value="${newCategory.idCategoria}" checked>
                    <label for="cat-${newCategory.idCategoria}">${newCategory.nome}</label>
                `;
                checkboxContainer.appendChild(checkboxDiv);
                
                // Limpa e esconde o campo
                nameInput.value = '';
                document.getElementById('new-category-inline').style.display = 'none';
            } else {
                this.showToast(response.message || 'Erro ao criar categoria', 'error');
            }
        } catch (error) {
            this.showToast('Erro de conexão', 'error');
            console.error('Create category inline error:', error);
        }
    }

    // ===== API CALLS =====
    async addCategory() {
        const name = document.getElementById('category-name').value;
        const userId = this.currentUser ? this.currentUser.id : 1;
        
        // Verifica se deve criar orçamento junto
        const addBudgetCheckbox = document.getElementById('add-budget-checkbox');
        const createBudget = addBudgetCheckbox && addBudgetCheckbox.checked;
        
        const payload = { name, userId };
        
        if (createBudget) {
            const budgetValue = parseFloat(document.getElementById('category-budget-value').value);
            const budgetPeriod = document.getElementById('category-budget-period').value;
            
            if (!budgetValue || budgetValue <= 0) {
                this.showToast('Por favor, informe um valor válido para o orçamento', 'warning');
                return;
            }
            
            payload.budget = {
                value: budgetValue,
                period: budgetPeriod
            };
        }
        
        try {
            const response = await this.apiCall('/categories', 'POST', payload);
            if (response.success) {
                if (createBudget) {
                    this.showToast('Categoria e orçamento adicionados com sucesso!', 'success');
                } else {
                    this.showToast('Categoria adicionada com sucesso!', 'success');
                }
                this.closeModal();
                // Recarrega as categorias para atualizar o cache
                await this.loadCategories();
                // Se criou orçamento, recarrega também
                if (createBudget) {
                    await this.loadBudgets();
                }
            } else {
                this.showToast(response.message || 'Erro ao adicionar categoria', 'error');
            }
        } catch (error) {
            this.showToast('Erro de conexão', 'error');
            console.error('Add category error:', error);
        }
    }

    async addAccount(returnToInvestment = false) {
        const name = document.getElementById('account-name').value;
        const type = document.getElementById('account-type').value;
        const balance = parseFloat(document.getElementById('account-balance').value);
        
        try {
            const userId = this.currentUser ? this.currentUser.id : 1;
            const response = await this.apiCall('/accounts', 'POST', { name, type, balance, userId });
            if (response.success) {
                this.showToast('Conta adicionada com sucesso!', 'success');
                this.closeModal();
                await this.loadAccounts();
                
                // Retorna o ID da conta criada se solicitado
                if (returnToInvestment) {
                    return response.accountId;
                }
            } else {
                this.showToast(response.message || 'Erro ao adicionar conta', 'error');
                return null;
            }
        } catch (error) {
            this.showToast('Erro de conexão', 'error');
            console.error('Add account error:', error);
            return null;
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
        
        // Obtém todas as tags marcadas (checkboxes)
        const checkedTags = document.querySelectorAll('#expense-tags input[type="checkbox"]:checked');
        const tagIds = Array.from(checkedTags).map(checkbox => parseInt(checkbox.value));
        
        // Obtém observações
        const observations = document.getElementById('expense-observations').value.trim();
        
        // Valida se pelo menos uma categoria foi selecionada
        if (categoryIds.length === 0) {
            this.showToast('Selecione pelo menos uma categoria', 'error');
            return;
        }
        
        try {
            const userId = this.currentUser ? this.currentUser.id : 1;
            const response = await this.apiCall('/expenses', 'POST', {
                description, value, date, categoryIds, accountId, frequency, tagIds, observacoes: observations, userId
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
        
        // Obtém todas as tags marcadas (checkboxes)
        const checkedTags = document.querySelectorAll('#income-tags input[type="checkbox"]:checked');
        const tagIds = Array.from(checkedTags).map(checkbox => parseInt(checkbox.value));
        
        try {
            const userId = this.currentUser ? this.currentUser.id : 1;
            const response = await this.apiCall('/incomes', 'POST', { description, value, date, accountId, tagIds, userId });
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
        const categorySelectValue = document.getElementById('budget-category').value;
        const value = parseFloat(document.getElementById('budget-value').value);
        const period = document.getElementById('budget-period').value;
        const userId = this.currentUser ? this.currentUser.id : 1;
        
        try {
            let categoryId;
            let isNewCategory = false;
            
            // Verifica se está criando nova categoria
            if (categorySelectValue === '__new__') {
                const newCategoryName = document.getElementById('new-category-name').value.trim();
                
                if (!newCategoryName) {
                    this.showToast('Por favor, informe o nome da nova categoria', 'warning');
                    return;
                }
                
                // Cria a categoria primeiro
                const categoryResponse = await this.apiCall('/categories', 'POST', {
                    name: newCategoryName,
                    userId: userId
                });
                
                if (!categoryResponse.success) {
                    this.showToast(categoryResponse.message || 'Erro ao criar categoria', 'error');
                    return;
                }
                
                categoryId = categoryResponse.categoryId;
                isNewCategory = true;
            } else {
                categoryId = parseInt(categorySelectValue);
            }
            
            // Agora cria o orçamento com a categoria (nova ou existente)
            const response = await this.apiCall('/budgets', 'POST', {
                categoryId, value, period, userId
            });
            
            if (response.success) {
                if (isNewCategory) {
                    this.showToast('Categoria e orçamento criados com sucesso!', 'success');
                } else {
                    this.showToast('Orçamento adicionado com sucesso!', 'success');
                }
                this.closeModal();
                
                // Recarrega ambas as listas
                await this.loadBudgets();
                if (isNewCategory) {
                    await this.loadCategories();
                }
            } else {
                this.showToast(response.message || 'Erro ao adicionar orçamento', 'error');
            }
        } catch (error) {
            this.showToast('Erro de conexão', 'error');
            console.error('Add budget error:', error);
        }
    }

    showAddTagModal() {
        const content = `
            <form id="add-tag-form">
                <div class="form-group">
                    <label for="tag-name">Nome da Tag</label>
                    <input type="text" id="tag-name" placeholder="Ex: Urgente, Pessoal, Trabalho..." required>
                </div>
                <div class="form-group">
                    <label for="tag-color">Cor</label>
                    <div class="color-picker">
                        <input type="color" id="tag-color" value="#3498db" required>
                        <span id="color-preview" style="background-color: #3498db;"></span>
                    </div>
                </div>
                <div class="form-actions">
                    <button type="button" class="btn-secondary" onclick="app.closeModal()">Cancelar</button>
                    <button type="submit" class="btn-primary">Salvar</button>
                </div>
            </form>
        `;
        this.showModal('Nova Tag', content);
        
        // Color preview update
        const colorInput = document.getElementById('tag-color');
        const colorPreview = document.getElementById('color-preview');
        colorInput.addEventListener('input', (e) => {
            colorPreview.style.backgroundColor = e.target.value;
        });
        
        document.getElementById('add-tag-form').addEventListener('submit', (e) => {
            e.preventDefault();
            this.addTag();
        });
    }

    async addTag() {
        const name = document.getElementById('tag-name').value.trim();
        const color = document.getElementById('tag-color').value;
        const userId = this.currentUser ? this.currentUser.id : 1;

        try {
            const response = await this.apiCall('/tags', 'POST', { nome: name, cor: color, userId });
            if (response.success) {
                this.showToast('Tag criada com sucesso!', 'success');
                this.closeModal();
                await this.loadTags();
            } else {
                this.showToast(response.message || 'Erro ao criar tag', 'error');
            }
        } catch (error) {
            this.showToast('Erro de conexão', 'error');
            console.error('Add tag error:', error);
        }
    }

    editTag(tagId, currentName, currentColor) {
        const content = `
            <form id="edit-tag-form">
                <div class="form-group">
                    <label for="tag-name">Nome da Tag</label>
                    <input type="text" id="tag-name" value="${currentName}" required>
                </div>
                <div class="form-group">
                    <label for="tag-color">Cor</label>
                    <div class="color-picker">
                        <input type="color" id="tag-color" value="${currentColor}" required>
                        <span id="color-preview" style="background-color: ${currentColor};"></span>
                    </div>
                </div>
                <div class="form-actions">
                    <button type="button" class="btn-secondary" onclick="app.closeModal()">Cancelar</button>
                    <button type="submit" class="btn-primary">Salvar</button>
                </div>
            </form>
        `;
        this.showModal('Editar Tag', content);
        
        // Color preview update
        const colorInput = document.getElementById('tag-color');
        const colorPreview = document.getElementById('color-preview');
        colorInput.addEventListener('input', (e) => {
            colorPreview.style.backgroundColor = e.target.value;
        });
        
        document.getElementById('edit-tag-form').addEventListener('submit', async (e) => {
            e.preventDefault();
            const name = document.getElementById('tag-name').value.trim();
            const color = document.getElementById('tag-color').value;
            
            try {
                const response = await this.apiCall(`/tags/${tagId}`, 'PUT', { id: tagId, nome: name, cor: color });
                if (response.success) {
                    this.showToast('Tag atualizada com sucesso!', 'success');
                    this.closeModal();
                    await this.loadTags();
                } else {
                    this.showToast(response.message || 'Erro ao atualizar tag', 'error');
                }
            } catch (error) {
                this.showToast('Erro de conexão', 'error');
                console.error('Edit tag error:', error);
            }
        });
    }

    async deleteTag(tagId) {
        if (!confirm('Tem certeza que deseja excluir esta tag?')) {
            return;
        }

        try {
            const response = await this.apiCall(`/tags/${tagId}`, 'DELETE');
            if (response.success) {
                this.showToast('Tag excluída com sucesso!', 'success');
                await this.loadTags();
            } else {
                this.showToast(response.message || 'Erro ao excluir tag', 'error');
            }
        } catch (error) {
            this.showToast('Erro de conexão', 'error');
            console.error('Delete tag error:', error);
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

    updateTagFilter(tags) {
        const select = document.getElementById('tag-filter');
        select.innerHTML = '<option value="">Todas as Tags</option>';
        tags.forEach(tag => {
            select.innerHTML += `<option value="${tag.idTag}">${tag.nome}</option>`;
        });
    }

    filterTransactions() {
        const categoryId = document.getElementById('category-filter').value;
        const tagId = document.getElementById('tag-filter').value;
        const date = document.getElementById('date-filter').value;
        
        // Converte valores vazios para null
        const categoryFilter = categoryId ? parseInt(categoryId) : null;
        const tagFilter = tagId ? parseInt(tagId) : null;
        const dateFilter = date || null;
        
        console.log('Aplicando filtros:', { categoryFilter, tagFilter, dateFilter });
        this.loadTransactions(categoryFilter, dateFilter, tagFilter);
    }

    clearFilters() {
        document.getElementById('category-filter').value = '';
        document.getElementById('tag-filter').value = '';
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
    
    // ===== REPORTS FUNCTIONALITY =====
    
    async loadReports() {
        try {
            const period = document.getElementById('report-period').value;
            let startDate, endDate;
            
            if (period === 'custom') {
                startDate = document.getElementById('start-date').value;
                endDate = document.getElementById('end-date').value;
                
                if (!startDate || !endDate) {
                    this.showToast('Por favor, selecione as datas de início e fim', 'warning');
                    return;
                }
            }
            
            const userId = this.currentUser ? this.currentUser.id : 1;
            let url = `/reports?userId=${userId}&period=${period}`;
            
            if (startDate && endDate) {
                url += `&startDate=${startDate}&endDate=${endDate}`;
            }
            
            const response = await this.apiCall(url, 'GET');
            if (response.success) {
                this.renderReports(response.data);
            } else {
                this.showToast('Erro ao carregar relatórios', 'error');
            }
        } catch (error) {
            console.error('Error loading reports:', error);
            this.showToast('Erro ao carregar relatórios', 'error');
        }
    }
    
    renderReports(data) {
        // Update summary cards
        document.getElementById('report-total-income').textContent = this.formatCurrency(data.totalIncomes);
        document.getElementById('report-total-expense').textContent = this.formatCurrency(data.totalExpenses);
        document.getElementById('report-balance').textContent = this.formatCurrency(data.balance);
        document.getElementById('report-income-count').textContent = `${data.incomeCount} transações`;
        document.getElementById('report-expense-count').textContent = `${data.expenseCount} transações`;
        document.getElementById('report-period-range').textContent = `${data.startDate} a ${data.endDate}`;
        
        // Render charts
        this.renderCategoryChart(data.categoryAnalysis);
        this.renderMonthlyChart(data.monthlyAnalysis);
        this.renderAccountChart(data.accountAnalysis);
        this.renderTopExpenses(data.topExpenses);
    }
    
    renderCategoryChart(categoryData) {
        const ctx = document.getElementById('report-category-chart');
        if (!ctx) return;
        
        // Destroy existing chart if it exists
        if (this.reportCategoryChart) {
            this.reportCategoryChart.destroy();
        }
        
        const labels = Object.keys(categoryData);
        const values = Object.values(categoryData);
        
        if (labels.length === 0) {
            ctx.parentElement.innerHTML = '<div class="chart-no-data">Nenhum gasto por categoria no período</div>';
            return;
        }
        
        this.reportCategoryChart = new Chart(ctx, {
            type: 'doughnut',
            data: {
                labels: labels,
                datasets: [{
                    data: values,
                    backgroundColor: [
                        '#FF6B6B', '#4ECDC4', '#45B7D1', '#FFA07A', 
                        '#98D8C8', '#F7DC6F', '#BB8FCE', '#85C1E2',
                        '#F8B739', '#52B788', '#D62828', '#5A189A'
                    ],
                    borderWidth: 2,
                    borderColor: '#fff'
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: {
                        position: 'bottom',
                        labels: {
                            padding: 20,
                            usePointStyle: true
                        }
                    },
                    tooltip: {
                        callbacks: {
                            label: (context) => {
                                const value = context.parsed;
                                const total = context.dataset.data.reduce((a, b) => a + b, 0);
                                const percentage = ((value / total) * 100).toFixed(1);
                                return `${context.label}: ${this.formatCurrency(value)} (${percentage}%)`;
                            }
                        }
                    }
                }
            }
        });
    }
    
    renderMonthlyChart(monthlyData) {
        const ctx = document.getElementById('report-monthly-chart');
        if (!ctx) return;
        
        // Destroy existing chart if it exists
        if (this.reportMonthlyChart) {
            this.reportMonthlyChart.destroy();
        }
        
        const labels = monthlyData.map(item => {
            const monthNames = ['Jan', 'Fev', 'Mar', 'Abr', 'Mai', 'Jun', 
                              'Jul', 'Ago', 'Set', 'Out', 'Nov', 'Dez'];
            return `${monthNames[item.month - 1]}/${item.year}`;
        });
        const expenses = monthlyData.map(item => item.expenses);
        const incomes = monthlyData.map(item => item.incomes);
        
        this.reportMonthlyChart = new Chart(ctx, {
            type: 'line',
            data: {
                labels: labels,
                datasets: [
                    {
                        label: 'Gastos',
                        data: expenses,
                        borderColor: '#dc2626',
                        backgroundColor: 'rgba(220, 38, 38, 0.1)',
                        tension: 0.4,
                        fill: false
                    },
                    {
                        label: 'Receitas',
                        data: incomes,
                        borderColor: '#059669',
                        backgroundColor: 'rgba(5, 150, 105, 0.1)',
                        tension: 0.4,
                        fill: false
                    }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: {
                        position: 'top',
                    },
                    tooltip: {
                        callbacks: {
                            label: (context) => {
                                return `${context.dataset.label}: ${this.formatCurrency(context.parsed.y)}`;
                            }
                        }
                    }
                },
                scales: {
                    y: {
                        beginAtZero: true,
                        ticks: {
                            callback: (value) => this.formatCurrency(value)
                        }
                    }
                }
            }
        });
    }
    
    renderAccountChart(accountData) {
        const ctx = document.getElementById('report-account-chart');
        if (!ctx) return;
        
        // Destroy existing chart if it exists
        if (this.reportAccountChart) {
            this.reportAccountChart.destroy();
        }
        
        const labels = Object.keys(accountData);
        const values = Object.values(accountData);
        
        if (labels.length === 0) {
            ctx.parentElement.innerHTML = '<div class="chart-no-data">Nenhum gasto por conta no período</div>';
            return;
        }
        
        this.reportAccountChart = new Chart(ctx, {
            type: 'bar',
            data: {
                labels: labels,
                datasets: [{
                    label: 'Gastos por Conta',
                    data: values,
                    backgroundColor: '#2563eb',
                    borderColor: '#1d4ed8',
                    borderWidth: 1
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: {
                        display: false
                    },
                    tooltip: {
                        callbacks: {
                            label: (context) => {
                                return `${context.label}: ${this.formatCurrency(context.parsed.y)}`;
                            }
                        }
                    }
                },
                scales: {
                    y: {
                        beginAtZero: true,
                        ticks: {
                            callback: (value) => this.formatCurrency(value)
                        }
                    }
                }
            }
        });
    }
    
    renderTopExpenses(topExpenses) {
        const container = document.getElementById('top-expenses-list');
        container.innerHTML = '';
        
        if (topExpenses.length === 0) {
            container.innerHTML = '<div class="chart-no-data">Nenhum gasto registrado no período</div>';
            return;
        }
        
        topExpenses.forEach((expense, index) => {
            const item = document.createElement('div');
            item.className = 'top-expense-item';
            item.innerHTML = `
                <div class="top-expense-info">
                    <div class="top-expense-description">${expense.description}</div>
                    <div class="top-expense-details">${expense.category} • ${this.formatDate(expense.date)}</div>
                </div>
                <div class="top-expense-value">${this.formatCurrency(expense.value)}</div>
            `;
            container.appendChild(item);
        });
    }
    
    handleReportPeriodChange() {
        const period = document.getElementById('report-period').value;
        const customDateRange = document.getElementById('custom-date-range');
        
        if (period === 'custom') {
            customDateRange.style.display = 'flex';
            // Set default dates (last 30 days)
            const endDate = new Date();
            const startDate = new Date();
            startDate.setDate(startDate.getDate() - 30);
            
            document.getElementById('start-date').value = startDate.toISOString().split('T')[0];
            document.getElementById('end-date').value = endDate.toISOString().split('T')[0];
        } else {
            customDateRange.style.display = 'none';
        }
        
        this.loadReports();
    }
    
    handleExport() {
        const format = document.getElementById('export-format').value;
        
        if (format === 'pdf') {
            this.exportPDF();
        } else {
            this.exportReport();
        }
    }

    updateExportButtonIcon() {
        const format = document.getElementById('export-format').value;
        const button = document.getElementById('export-report-btn');
        const icon = button.querySelector('i');
        
        // Remover todas as classes de ícone
        icon.className = 'fas';
        
        switch (format) {
            case 'csv':
                icon.classList.add('fa-file-csv');
                break;
            case 'xlsx':
                icon.classList.add('fa-file-excel');
                break;
            case 'pdf':
                icon.classList.add('fa-file-pdf');
                break;
            default:
                icon.classList.add('fa-download');
        }
    }

    async exportReport() {
        try {
            const period = document.getElementById('report-period').value;
            const format = document.getElementById('export-format').value;
            let startDate, endDate;
            
            if (period === 'custom') {
                startDate = document.getElementById('start-date').value;
                endDate = document.getElementById('end-date').value;
                
                if (!startDate || !endDate) {
                    this.showToast('Por favor, selecione as datas de início e fim', 'warning');
                    return;
                }
            }
            
            const userId = this.currentUser ? this.currentUser.id : 1;
            const requestData = {
                userId: userId,
                format: format,
                period: period
            };
            
            if (startDate && endDate) {
                requestData.startDate = startDate;
                requestData.endDate = endDate;
            }
            
            const response = await fetch(`${this.apiBaseUrl}/reports`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': this.currentUser ? `Bearer ${this.currentUser.token}` : ''
                },
                body: JSON.stringify(requestData)
            });
            
            if (response.ok) {
                const blob = await response.blob();
                const url = window.URL.createObjectURL(blob);
                const a = document.createElement('a');
                a.href = url;
                a.download = `relatorio_${new Date().toISOString().split('T')[0]}.${format}`;
                document.body.appendChild(a);
                a.click();
                window.URL.revokeObjectURL(url);
                document.body.removeChild(a);
                
                this.showToast(`Relatório exportado como ${format.toUpperCase()} com sucesso!`, 'success');
            } else {
                this.showToast('Erro ao exportar relatório', 'error');
            }
        } catch (error) {
            console.error('Error exporting report:', error);
            this.showToast('Erro ao exportar relatório', 'error');
        }
    }

    async exportPDF() {
        try {
            this.showToast('Gerando PDF com gráficos...', 'info');
            
            // Verificação rápida das bibliotecas
            const jsPDFLoaded = typeof window.jsPDF !== 'undefined';
            const html2canvasLoaded = typeof window.html2canvas !== 'undefined';
            
            console.log('Verificando bibliotecas para PDF:', {
                jsPDF: jsPDFLoaded,
                html2canvas: html2canvasLoaded
            });
            
            if (!jsPDFLoaded || !html2canvasLoaded) {
                console.log('Bibliotecas não disponíveis, usando método alternativo...');
                await this.exportPDFAlternative();
                return;
            }

            // Criar novo documento PDF
            const { jsPDF } = window.jsPDF;
            const pdf = new jsPDF('p', 'mm', 'a4');
            
            // Configurações
            const pageWidth = pdf.internal.pageSize.getWidth();
            const pageHeight = pdf.internal.pageSize.getHeight();
            const margin = 20;
            let yPosition = margin;
            
            // Título
            pdf.setFontSize(20);
            pdf.setFont('helvetica', 'bold');
            pdf.text('Relatório Financeiro - Controle-se', pageWidth / 2, yPosition, { align: 'center' });
            yPosition += 15;
            
            // Data de geração
            pdf.setFontSize(10);
            pdf.setFont('helvetica', 'normal');
            pdf.text(`Gerado em: ${new Date().toLocaleDateString('pt-BR')}`, pageWidth / 2, yPosition, { align: 'center' });
            yPosition += 20;
            
            // Capturar e adicionar cards de resumo
            const summaryCards = document.querySelector('.report-summary-cards');
            if (summaryCards) {
                const summaryCanvas = await html2canvas(summaryCards, {
                    backgroundColor: '#ffffff',
                    scale: 2
                });
                
                const summaryImg = summaryCanvas.toDataURL('image/png');
                const summaryWidth = pageWidth - 2 * margin;
                const summaryHeight = (summaryCanvas.height * summaryWidth) / summaryCanvas.width;
                
                pdf.addImage(summaryImg, 'PNG', margin, yPosition, summaryWidth, summaryHeight);
                yPosition += summaryHeight + 10;
            }
            
            // Capturar e adicionar gráfico de categorias
            const categoryChart = document.getElementById('report-category-chart');
            if (categoryChart && categoryChart.chart) {
                const categoryCanvas = await html2canvas(categoryChart, {
                    backgroundColor: '#ffffff',
                    scale: 2
                });
                
                const categoryImg = categoryCanvas.toDataURL('image/png');
                const chartWidth = (pageWidth - 3 * margin) / 2;
                const chartHeight = (categoryCanvas.height * chartWidth) / categoryCanvas.width;
                
                pdf.addImage(categoryImg, 'PNG', margin, yPosition, chartWidth, chartHeight);
            }
            
            // Capturar e adicionar gráfico mensal
            const monthlyChart = document.getElementById('report-monthly-chart');
            if (monthlyChart && monthlyChart.chart) {
                const monthlyCanvas = await html2canvas(monthlyChart, {
                    backgroundColor: '#ffffff',
                    scale: 2
                });
                
                const monthlyImg = monthlyCanvas.toDataURL('image/png');
                const chartWidth = (pageWidth - 3 * margin) / 2;
                const chartHeight = (monthlyCanvas.height * chartWidth) / monthlyCanvas.width;
                
                pdf.addImage(monthlyImg, 'PNG', pageWidth / 2 + margin / 2, yPosition, chartWidth, chartHeight);
                yPosition += chartHeight + 10;
            }
            
            // Nova página para gráfico de contas e top gastos
            pdf.addPage();
            yPosition = margin;
            
            // Capturar e adicionar gráfico de contas
            const accountChart = document.getElementById('report-account-chart');
            if (accountChart && accountChart.chart) {
                const accountCanvas = await html2canvas(accountChart, {
                    backgroundColor: '#ffffff',
                    scale: 2
                });
                
                const accountImg = accountCanvas.toDataURL('image/png');
                const chartWidth = (pageWidth - 3 * margin) / 2;
                const chartHeight = (accountCanvas.height * chartWidth) / accountCanvas.width;
                
                pdf.addImage(accountImg, 'PNG', margin, yPosition, chartWidth, chartHeight);
            }
            
            // Capturar e adicionar top gastos
            const topExpenses = document.getElementById('top-expenses');
            if (topExpenses) {
                const expensesCanvas = await html2canvas(topExpenses, {
                    backgroundColor: '#ffffff',
                    scale: 2
                });
                
                const expensesImg = expensesCanvas.toDataURL('image/png');
                const chartWidth = (pageWidth - 3 * margin) / 2;
                const chartHeight = (expensesCanvas.height * chartWidth) / expensesCanvas.width;
                
                pdf.addImage(expensesImg, 'PNG', pageWidth / 2 + margin / 2, yPosition, chartWidth, chartHeight);
            }
            
            // Salvar o PDF
            const fileName = `relatorio_completo_${new Date().toISOString().split('T')[0]}.pdf`;
            pdf.save(fileName);
            
            this.showToast('PDF gerado com sucesso!', 'success');
            
        } catch (error) {
            console.error('Error generating PDF:', error);
            this.showToast('Erro ao gerar PDF', 'error');
        }
    }

    async loadJSPDF() {
        return new Promise((resolve, reject) => {
            if (typeof window.jsPDF !== 'undefined') {
                resolve();
                return;
            }
            
            const script = document.createElement('script');
            script.src = 'https://unpkg.com/jspdf@2.5.1/dist/jspdf.umd.min.js';
            script.onload = () => {
                console.log('jsPDF carregado dinamicamente');
                resolve();
            };
            script.onerror = () => {
                console.error('Erro ao carregar jsPDF');
                reject(new Error('Erro ao carregar jsPDF'));
            };
            document.head.appendChild(script);
        });
    }

    async loadHtml2Canvas() {
        return new Promise((resolve, reject) => {
            if (typeof window.html2canvas !== 'undefined') {
                resolve();
                return;
            }
            
            const script = document.createElement('script');
            script.src = 'https://unpkg.com/html2canvas@1.4.1/dist/html2canvas.min.js';
            script.onload = () => {
                console.log('html2canvas carregado dinamicamente');
                resolve();
            };
            script.onerror = () => {
                console.error('Erro ao carregar html2canvas');
                reject(new Error('Erro ao carregar html2canvas'));
            };
            document.head.appendChild(script);
        });
    }

    async waitForLibraries(timeoutMs = 15000) {
        return new Promise((resolve) => {
            const startTime = Date.now();
            
            const checkLibraries = () => {
                const jsPDFLoaded = typeof window.jsPDF !== 'undefined';
                const html2canvasLoaded = typeof window.html2canvas !== 'undefined';
                
                // Verificar se as bibliotecas estão realmente funcionais
                let jsPDFFunctional = false;
                let html2canvasFunctional = false;
                
                if (jsPDFLoaded) {
                    try {
                        const { jsPDF } = window.jsPDF;
                        const testPdf = new jsPDF();
                        jsPDFFunctional = testPdf && typeof testPdf.save === 'function';
                    } catch (e) {
                        console.error('jsPDF carregado mas não funcional:', e);
                    }
                }
                
                if (html2canvasLoaded) {
                    try {
                        html2canvasFunctional = typeof window.html2canvas === 'function';
                    } catch (e) {
                        console.error('html2canvas carregado mas não funcional:', e);
                    }
                }
                
                console.log('Verificando bibliotecas:', {
                    jsPDF: jsPDFLoaded,
                    html2canvas: html2canvasLoaded,
                    jsPDFFunctional: jsPDFFunctional,
                    html2canvasFunctional: html2canvasFunctional,
                    elapsed: Date.now() - startTime
                });
                
                if (jsPDFFunctional && html2canvasFunctional) {
                    console.log('Todas as bibliotecas carregadas e funcionais!');
                    resolve(true);
                    return;
                }
                
                if (Date.now() - startTime > timeoutMs) {
                    console.error('Timeout: Bibliotecas não carregaram em', timeoutMs, 'ms');
                    resolve(false);
                    return;
                }
                
                // Verificar novamente em 500ms
                setTimeout(checkLibraries, 500);
            };
            
            checkLibraries();
        });
    }


    async exportPDFAlternative() {
        try {
            this.showToast('Gerando PDF com gráficos...', 'info');
            
            // Aguardar um pouco para garantir que os gráficos estão renderizados
            await new Promise(resolve => setTimeout(resolve, 1000));
            
            // Usar a API nativa do navegador para imprimir/gerar PDF
            const printWindow = window.open('', '_blank');
            
            // Obter dados dos relatórios
            const period = document.getElementById('report-period').value;
            const userId = this.currentUser ? this.currentUser.id : 1;
            
            let startDate, endDate;
            if (period === 'custom') {
                startDate = document.getElementById('start-date').value;
                endDate = document.getElementById('end-date').value;
            }
            
            // Buscar dados dos relatórios
            const reportData = await this.fetchReportData(userId, period, startDate, endDate);
            
            // Capturar os gráficos como imagens
            const chartImages = await this.captureCharts();
            
            // Criar HTML para o PDF
            const htmlContent = this.generatePDFHTMLWithCharts(reportData, period, chartImages);
            
            printWindow.document.write(htmlContent);
            printWindow.document.close();
            
            // Aguardar o carregamento e imprimir
            printWindow.onload = function() {
                printWindow.print();
                printWindow.close();
            };
            
            this.showToast('PDF com gráficos gerado! Use Ctrl+P para salvar como PDF.', 'success');
            
        } catch (error) {
            console.error('Error generating alternative PDF:', error);
            this.showToast('Erro ao gerar PDF alternativo', 'error');
        }
    }

    async captureCharts() {
        const charts = {};
        
        try {
            // Capturar gráfico de categorias
            const categoryChart = document.getElementById('report-category-chart');
            if (categoryChart) {
                charts.category = await this.captureElementAsImage(categoryChart);
            }
            
            // Capturar gráfico mensal
            const monthlyChart = document.getElementById('report-monthly-chart');
            if (monthlyChart) {
                charts.monthly = await this.captureElementAsImage(monthlyChart);
            }
            
            // Capturar gráfico de contas
            const accountChart = document.getElementById('report-account-chart');
            if (accountChart) {
                charts.account = await this.captureElementAsImage(accountChart);
            }
            
            // Capturar top gastos
            const topExpenses = document.getElementById('top-expenses');
            if (topExpenses) {
                charts.topExpenses = await this.captureElementAsImage(topExpenses);
            }
            
            // Capturar cards de resumo
            const summaryCards = document.querySelector('.report-summary-cards');
            if (summaryCards) {
                charts.summary = await this.captureElementAsImage(summaryCards);
            }
            
        } catch (error) {
            console.error('Erro ao capturar gráficos:', error);
        }
        
        return charts;
    }

    async captureElementAsImage(element) {
        return new Promise((resolve) => {
            // Usar html2canvas se disponível, senão usar método alternativo
            if (typeof window.html2canvas !== 'undefined') {
                html2canvas(element, {
                    backgroundColor: '#ffffff',
                    scale: 2,
                    useCORS: true,
                    allowTaint: true
                }).then(canvas => {
                    resolve(canvas.toDataURL('image/png'));
                }).catch(() => {
                    resolve(null);
                });
            } else {
                // Método alternativo usando canvas nativo
                try {
                    const canvas = document.createElement('canvas');
                    const ctx = canvas.getContext('2d');
                    const rect = element.getBoundingClientRect();
                    
                    canvas.width = rect.width * 2;
                    canvas.height = rect.height * 2;
                    
                    // Criar uma imagem simples com o conteúdo do elemento
                    ctx.fillStyle = '#ffffff';
                    ctx.fillRect(0, 0, canvas.width, canvas.height);
                    
                    ctx.fillStyle = '#333333';
                    ctx.font = '16px Arial';
                    ctx.textAlign = 'center';
                    ctx.fillText('Gráfico não disponível', canvas.width / 2, canvas.height / 2);
                    
                    resolve(canvas.toDataURL('image/png'));
                } catch (error) {
                    console.error('Erro ao criar canvas:', error);
                    resolve(null);
                }
            }
        });
    }

    async fetchReportData(userId, period, startDate, endDate) {
        const params = new URLSearchParams({
            userId: userId,
            period: period
        });
        
        if (startDate && endDate) {
            params.append('startDate', startDate);
            params.append('endDate', endDate);
        }
        
        const response = await fetch(`${this.apiBaseUrl}/reports?${params}`);
        const data = await response.json();
        return data.data;
    }

    generatePDFHTMLWithCharts(data, period, chartImages) {
        const periodText = period === 'month' ? 'Este Mês' : 
                          period === 'year' ? 'Este Ano' : 'Período Personalizado';
        
        return `
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Relatório Financeiro - Controle-se</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 20px; }
        .header { text-align: center; margin-bottom: 30px; }
        .summary-cards { display: flex; justify-content: space-around; margin: 20px 0; }
        .card { background: #f5f5f5; padding: 15px; border-radius: 8px; text-align: center; }
        .card h3 { margin: 0 0 10px 0; color: #333; }
        .card .amount { font-size: 24px; font-weight: bold; margin: 5px 0; }
        .card.income .amount { color: #27ae60; }
        .card.expense .amount { color: #e74c3c; }
        .card.balance .amount { color: #3498db; }
        .section { margin: 30px 0; }
        .section h2 { color: #2c3e50; border-bottom: 2px solid #3498db; padding-bottom: 5px; }
        .top-expenses { list-style: none; padding: 0; }
        .top-expenses li { background: #f8f9fa; margin: 5px 0; padding: 10px; border-radius: 5px; }
        .chart-placeholder { background: #f0f0f0; padding: 40px; text-align: center; border-radius: 8px; margin: 20px 0; }
        .chart-image { border: 1px solid #ddd; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
        .summary-section { text-align: center; margin: 20px 0; }
        @media print { 
            body { margin: 0; }
            .chart-image { border: none; box-shadow: none; }
        }
    </style>
</head>
<body>
    <div class="header">
        <h1>Relatório Financeiro - Controle-se</h1>
        <p>Período: ${periodText}</p>
        <p>Gerado em: ${new Date().toLocaleDateString('pt-BR')}</p>
    </div>
    
        ${chartImages.summary ? 
            `<div class="summary-section">
                <img src="${chartImages.summary}" alt="Cards de Resumo" class="chart-image" style="width: 100%; max-width: 800px; height: auto; margin: 20px 0;">
            </div>` :
        `<div class="summary-cards">
            <div class="card income">
                <h3>Total de Receitas</h3>
                <div class="amount">R$ ${data.totalIncomes.toFixed(2).replace('.', ',')}</div>
                <p>${data.incomeCount} transações</p>
            </div>
            <div class="card expense">
                <h3>Total de Gastos</h3>
                <div class="amount">R$ ${data.totalExpenses.toFixed(2).replace('.', ',')}</div>
                <p>${data.expenseCount} transações</p>
            </div>
            <div class="card balance">
                <h3>Saldo</h3>
                <div class="amount">R$ ${data.balance.toFixed(2).replace('.', ',')}</div>
            </div>
        </div>`
    }
    
    <div class="section">
        <h2>Análise por Categoria</h2>
        ${chartImages.category ? 
            `<img src="${chartImages.category}" alt="Gráfico de Categorias" class="chart-image" style="width: 100%; max-width: 600px; height: auto; margin: 20px 0;">` :
            `<div class="chart-placeholder">
                <p>Gráfico de Pizza - Gastos por Categoria</p>
                <p>Dados: ${Object.keys(data.categoryAnalysis).map(cat => `${cat}: R$ ${data.categoryAnalysis[cat].toFixed(2)}`).join(', ')}</p>
            </div>`
        }
    </div>
    
    <div class="section">
        <h2>Evolução Mensal</h2>
        ${chartImages.monthly ? 
            `<img src="${chartImages.monthly}" alt="Gráfico Mensal" class="chart-image" style="width: 100%; max-width: 600px; height: auto; margin: 20px 0;">` :
            `<div class="chart-placeholder">
                <p>Gráfico de Linha - Evolução de Receitas vs Gastos</p>
                <p>Últimos 12 meses de dados disponíveis</p>
            </div>`
        }
    </div>
    
    <div class="section">
        <h2>Análise por Conta</h2>
        ${chartImages.account ? 
            `<img src="${chartImages.account}" alt="Gráfico de Contas" class="chart-image" style="width: 100%; max-width: 600px; height: auto; margin: 20px 0;">` :
            `<div class="chart-placeholder">
                <p>Gráfico de Barras - Gastos por Conta</p>
                <p>Dados: ${Object.keys(data.accountAnalysis).map(acc => `${acc}: R$ ${data.accountAnalysis[acc].toFixed(2)}`).join(', ')}</p>
            </div>`
        }
    </div>
    
    <div class="section">
        <h2>Top 5 Maiores Gastos</h2>
        ${chartImages.topExpenses ? 
            `<img src="${chartImages.topExpenses}" alt="Top Gastos" class="chart-image" style="width: 100%; max-width: 600px; height: auto; margin: 20px 0;">` :
            `<ul class="top-expenses">
                ${data.topExpenses.map(expense => `
                    <li>
                        <strong>${expense.description}</strong> - 
                        R$ ${expense.value.toFixed(2).replace('.', ',')} 
                        (${expense.category}) - 
                        ${new Date(expense.date).toLocaleDateString('pt-BR')}
                    </li>
                `).join('')}
            </ul>`
        }
    </div>
</body>
</html>`;
    }

    generatePDFHTML(data, period) {
        const periodText = period === 'month' ? 'Este Mês' : 
                          period === 'year' ? 'Este Ano' : 'Período Personalizado';
        
        return `
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Relatório Financeiro - Controle-se</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 20px; }
        .header { text-align: center; margin-bottom: 30px; }
        .summary-cards { display: flex; justify-content: space-around; margin: 20px 0; }
        .card { background: #f5f5f5; padding: 15px; border-radius: 8px; text-align: center; }
        .card h3 { margin: 0 0 10px 0; color: #333; }
        .card .amount { font-size: 24px; font-weight: bold; margin: 5px 0; }
        .card.income .amount { color: #27ae60; }
        .card.expense .amount { color: #e74c3c; }
        .card.balance .amount { color: #3498db; }
        .section { margin: 30px 0; }
        .section h2 { color: #2c3e50; border-bottom: 2px solid #3498db; padding-bottom: 5px; }
        .top-expenses { list-style: none; padding: 0; }
        .top-expenses li { background: #f8f9fa; margin: 5px 0; padding: 10px; border-radius: 5px; }
        .chart-placeholder { background: #f0f0f0; padding: 40px; text-align: center; border-radius: 8px; margin: 20px 0; }
        @media print { body { margin: 0; } }
    </style>
</head>
<body>
    <div class="header">
        <h1>Relatório Financeiro - Controle-se</h1>
        <p>Período: ${periodText}</p>
        <p>Gerado em: ${new Date().toLocaleDateString('pt-BR')}</p>
    </div>
    
    <div class="summary-cards">
        <div class="card income">
            <h3>Total de Receitas</h3>
            <div class="amount">R$ ${data.totalIncomes.toFixed(2).replace('.', ',')}</div>
            <p>${data.incomeCount} transações</p>
        </div>
        <div class="card expense">
            <h3>Total de Gastos</h3>
            <div class="amount">R$ ${data.totalExpenses.toFixed(2).replace('.', ',')}</div>
            <p>${data.expenseCount} transações</p>
        </div>
        <div class="card balance">
            <h3>Saldo</h3>
            <div class="amount">R$ ${data.balance.toFixed(2).replace('.', ',')}</div>
        </div>
    </div>
    
    <div class="section">
        <h2>Análise por Categoria</h2>
        <div class="chart-placeholder">
            <p>Gráfico de Pizza - Gastos por Categoria</p>
            <p>Dados: ${Object.keys(data.categoryAnalysis).map(cat => `${cat}: R$ ${data.categoryAnalysis[cat].toFixed(2)}`).join(', ')}</p>
        </div>
    </div>
    
    <div class="section">
        <h2>Análise por Conta</h2>
        <div class="chart-placeholder">
            <p>Gráfico de Barras - Gastos por Conta</p>
            <p>Dados: ${Object.keys(data.accountAnalysis).map(acc => `${acc}: R$ ${data.accountAnalysis[acc].toFixed(2)}`).join(', ')}</p>
        </div>
    </div>
    
    <div class="section">
        <h2>Top 5 Maiores Gastos</h2>
        <ul class="top-expenses">
            ${data.topExpenses.map(expense => `
                <li>
                    <strong>${expense.description}</strong> - 
                    R$ ${expense.value.toFixed(2).replace('.', ',')} 
                    (${expense.category}) - 
                    ${new Date(expense.date).toLocaleDateString('pt-BR')}
                </li>
            `).join('')}
        </ul>
    </div>
    
    <div class="section">
        <h2>Evolução Mensal</h2>
        <div class="chart-placeholder">
            <p>Gráfico de Linha - Evolução de Receitas vs Gastos</p>
            <p>Últimos 12 meses de dados disponíveis</p>
        </div>
    </div>
</body>
</html>`;
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
            // Busca a conta atual do cache
            const account = this.accountsCache.find(acc => acc.idConta === accountId);
            if (!account) {
                this.showToast('Conta não encontrada', 'error');
                return;
            }
            
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
                            <option value="Investimento">Investimento (Corretora)</option>
                        </select>
                        <small id="edit-account-type-help" style="color: var(--neutral-600); margin-top: 4px; display: none;">
                            <i class="fas fa-info-circle"></i> Esta conta será usada como corretora ao criar investimentos
                        </small>
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
            
            // Adiciona listener para mostrar/ocultar texto de ajuda quando tipo for selecionado
            const editAccountTypeSelect = document.getElementById('edit-account-type');
            const editAccountTypeHelp = document.getElementById('edit-account-type-help');
            
            // Preenche os campos com os dados da conta
            document.getElementById('edit-account-name').value = account.nome || '';
            document.getElementById('edit-account-type').value = account.tipo || '';
            document.getElementById('edit-account-balance').value = account.saldo || 0;
            
            if (editAccountTypeSelect && editAccountTypeHelp) {
                // Verifica o valor inicial e mostra ajuda se necessário
                if (editAccountTypeSelect.value === 'Investimento') {
                    editAccountTypeHelp.style.display = 'block';
                }
                
                editAccountTypeSelect.addEventListener('change', () => {
                    if (editAccountTypeSelect.value === 'Investimento') {
                        editAccountTypeHelp.style.display = 'block';
                    } else {
                        editAccountTypeHelp.style.display = 'none';
                    }
                });
            }
            
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
    
    // ===== INVESTMENTS SECTION =====
    
    async loadInvestments() {
        try {
            const userId = this.currentUser ? this.currentUser.id : 1;
            const response = await this.apiCall(`/investments?userId=${userId}`, 'GET');
            if (response.success) {
                this.renderInvestments(response.data, response.summary);
                this.updateInvestmentsChart(response.data);
                this.updateAssetsAllocationChart(response.data);
            }
        } catch (error) {
            console.error('Error loading investments:', error);
        }
    }
    
    renderInvestments(investments, summary) {
        const container = document.getElementById('investments-list');
        container.innerHTML = '';
        
        // Atualiza cards de resumo
        if (summary) {
            document.getElementById('total-invested').textContent = this.formatCurrency(summary.totalInvested);
            document.getElementById('total-current').textContent = this.formatCurrency(summary.totalCurrent);
            document.getElementById('total-return').textContent = this.formatCurrency(summary.totalReturn);
            document.getElementById('total-return-percent').textContent = 
                `${summary.totalReturnPercent >= 0 ? '+' : ''}${summary.totalReturnPercent.toFixed(2)}%`;
            
            // Atualiza cor do card de retorno
            const returnCard = document.getElementById('return-card');
            if (summary.totalReturn >= 0) {
                returnCard.className = 'summary-card income';
            } else {
                returnCard.className = 'summary-card expense';
            }
        }
        
        if (!investments || investments.length === 0) {
            container.innerHTML = '<p class="text-center">Nenhum investimento cadastrado</p>';
            return;
        }
        
        // Agrupa investimentos por categoria
        const investmentsByCategory = {};
        investments.forEach(inv => {
            const categoria = inv.categoria || 'OUTROS';
            if (!investmentsByCategory[categoria]) {
                investmentsByCategory[categoria] = [];
            }
            investmentsByCategory[categoria].push(inv);
        });
        
        // Nomes amigáveis para as categorias
        const categoryNames = {
            'ACAO': 'Ações (B3)',
            'STOCK': 'Stocks (NYSE/NASDAQ)',
            'CRYPTO': 'Criptomoedas',
            'FII': 'Fundos Imobiliários',
            'RENDA_FIXA': 'Renda Fixa',
            'OUTROS': 'Outros'
        };
        
        // Ícones para cada categoria
        const categoryIcons = {
            'ACAO': 'fa-chart-line',
            'STOCK': 'fa-globe',
            'CRYPTO': 'fa-coins',
            'FII': 'fa-building',
            'RENDA_FIXA': 'fa-piggy-bank',
            'OUTROS': 'fa-wallet'
        };
        
        // Ordena categorias por valor total (maior para menor)
        const sortedCategories = Object.keys(investmentsByCategory).sort((a, b) => {
            const totalA = investmentsByCategory[a].reduce((sum, inv) => sum + (parseFloat(inv.valorAtual) || 0), 0);
            const totalB = investmentsByCategory[b].reduce((sum, inv) => sum + (parseFloat(inv.valorAtual) || 0), 0);
            return totalB - totalA;
        });
        
        // Cria dropdown para cada categoria
        sortedCategories.forEach(categoria => {
            const categoryInvestments = investmentsByCategory[categoria];
            const categoryTotal = categoryInvestments.reduce((sum, inv) => sum + (parseFloat(inv.valorAtual) || 0), 0);
            const categoryName = categoryNames[categoria] || categoria;
            const categoryIcon = categoryIcons[categoria] || 'fa-wallet';
            
            const categorySection = document.createElement('div');
            categorySection.className = 'investment-category-section';
            categorySection.dataset.category = categoria;
            
            categorySection.innerHTML = `
                <div class="category-header" data-category-toggle="${categoria}">
                    <div class="category-header-left">
                        <i class="fas ${categoryIcon}"></i>
                        <div>
                            <h4>${categoryName}</h4>
                            <span class="category-count">${categoryInvestments.length} investimento${categoryInvestments.length !== 1 ? 's' : ''}</span>
                        </div>
                    </div>
                    <div class="category-header-right">
                        <span class="category-total">${this.formatCurrency(categoryTotal)}</span>
                        <i class="fas fa-chevron-down category-chevron"></i>
                    </div>
                </div>
                <div class="category-content" id="category-content-${categoria}" style="display: none;">
                    <div class="investments-grid" id="investments-grid-${categoria}">
                        <!-- Investimentos serão inseridos aqui -->
                    </div>
                    ${categoryInvestments.length > 5 ? `
                        <div class="show-more-container">
                            <button class="btn-show-more" data-category="${categoria}" data-shown="5" data-total="${categoryInvestments.length}">
                                Mostrar mais (${categoryInvestments.length - 5} restantes)
                            </button>
                        </div>
                    ` : ''}
                </div>
            `;
            
            container.appendChild(categorySection);
            
            // Renderiza os primeiros 5 investimentos
            const gridContainer = document.getElementById(`investments-grid-${categoria}`);
            this.renderCategoryInvestments(gridContainer, categoryInvestments.slice(0, 5), categoria);
        });
        
        // Adiciona event listeners para dropdowns
        container.querySelectorAll('[data-category-toggle]').forEach(header => {
            header.addEventListener('click', () => {
                const categoria = header.dataset.categoryToggle;
                const content = document.getElementById(`category-content-${categoria}`);
                const chevron = header.querySelector('.category-chevron');
                
                if (content.style.display === 'none') {
                    content.style.display = 'block';
                    chevron.classList.add('rotated');
                } else {
                    content.style.display = 'none';
                    chevron.classList.remove('rotated');
                }
            });
        });
        
        // Adiciona event listeners para botões "mostrar mais"
        container.querySelectorAll('.btn-show-more').forEach(btn => {
            btn.addEventListener('click', () => {
                const categoria = btn.dataset.category;
                const shown = parseInt(btn.dataset.shown);
                const total = parseInt(btn.dataset.total);
                const gridContainer = document.getElementById(`investments-grid-${categoria}`);
                const categoryInvestments = investmentsByCategory[categoria];
                
                // Mostra mais 5 itens
                const nextBatch = categoryInvestments.slice(shown, shown + 5);
                this.renderCategoryInvestments(gridContainer, nextBatch, categoria, true);
                
                const newShown = shown + nextBatch.length;
                btn.dataset.shown = newShown;
                
                if (newShown >= total) {
                    btn.remove();
                } else {
                    btn.textContent = `Mostrar mais (${total - newShown} restantes)`;
                }
            });
        });
    }
    
    renderCategoryInvestments(container, investments, categoria, append = false) {
        if (!append) {
            container.innerHTML = '';
        }
        
        investments.forEach(inv => {
            const card = document.createElement('div');
            card.className = 'investment-card';
            const returnClass = inv.retorno >= 0 ? 'positive' : 'negative';
            const displayName = inv.nomeAtivo 
                ? `${inv.nome} - ${inv.nomeAtivo}` 
                : inv.nome;
            
            card.innerHTML = `
                <div class="investment-header">
                    <h3>${displayName}</h3>
                    <span class="investment-badge ${returnClass}">${inv.retornoPercent >= 0 ? '+' : ''}${inv.retornoPercent.toFixed(2)}%</span>
                </div>
                <div class="investment-details">
                    <div class="investment-row">
                        <span>Quantidade:</span>
                        <strong>${parseFloat(inv.quantidade).toLocaleString('pt-BR', { minimumFractionDigits: 0, maximumFractionDigits: 6 })}</strong>
                    </div>
                    <div class="investment-row">
                        <span>Preço Aporte:</span>
                        <strong>${this.formatCurrency(inv.precoAporte)}</strong>
                    </div>
                    <div class="investment-row">
                        <span>Preço Atual:</span>
                        <strong>${this.formatCurrency(inv.precoAtual)}</strong>
                    </div>
                    <div class="investment-row">
                        <span>Valor Investido:</span>
                        <strong>${this.formatCurrency(inv.valorAporte)}</strong>
                    </div>
                    <div class="investment-row">
                        <span>Valor Atual:</span>
                        <strong>${this.formatCurrency(inv.valorAtual)}</strong>
                    </div>
                    <div class="investment-row return ${returnClass}">
                        <span>Retorno:</span>
                        <strong>${inv.retorno >= 0 ? '+' : ''}${this.formatCurrency(inv.retorno)}</strong>
                    </div>
                    <div class="investment-row">
                        <span>Conta/Corretora:</span>
                        <strong>${inv.corretora || 'N/A'}</strong>
                    </div>
                    <div class="investment-row">
                        <span>Data Aporte:</span>
                        <strong>${this.formatDate(inv.dataAporte)}</strong>
                    </div>
                </div>
                <div class="card-actions">
                    <button class="btn-secondary btn-edit-investment" data-investment-id="${inv.idInvestimento}">
                        <i class="fas fa-edit"></i> Editar
                    </button>
                    <button class="btn-secondary btn-delete-investment" data-investment-id="${inv.idInvestimento}">
                        <i class="fas fa-trash"></i> Excluir
                    </button>
                </div>
            `;
            container.appendChild(card);
        });
        
        // Adiciona event listeners para os botões de editar e excluir
        container.querySelectorAll('.btn-edit-investment').forEach(btn => {
            btn.addEventListener('click', (e) => {
                const investmentId = parseInt(e.target.closest('.btn-edit-investment').dataset.investmentId);
                this.editInvestment(investmentId);
            });
        });
        
        container.querySelectorAll('.btn-delete-investment').forEach(btn => {
            btn.addEventListener('click', (e) => {
                const investmentId = parseInt(e.target.closest('.btn-delete-investment').dataset.investmentId);
                this.deleteInvestment(investmentId);
            });
        });
    }
    
    updateInvestmentsChart(investments) {
        const ctx = document.getElementById('investments-chart');
        if (!ctx) return;
        
        // Se não houver investimentos, não renderiza o gráfico
        if (!investments || investments.length === 0) {
            if (window.investmentsChart) {
                window.investmentsChart.destroy();
                window.investmentsChart = null;
            }
            return;
        }
        
        // Agrupa por data de aporte para mostrar evolução
        const sortedInvestments = [...investments].sort((a, b) => 
            new Date(a.dataAporte) - new Date(b.dataAporte)
        );
        
        const labels = sortedInvestments.map(inv => this.formatDate(inv.dataAporte));
        const investedData = [];
        const currentData = [];
        let cumulativeInvested = 0;
        let cumulativeCurrent = 0;
        
        sortedInvestments.forEach(inv => {
            const valorAporte = parseFloat(inv.valorAporte) || 0;
            const valorAtual = parseFloat(inv.valorAtual) || 0;
            
            cumulativeInvested += valorAporte;
            cumulativeCurrent += valorAtual;
            
            // Garante que os valores são números válidos
            investedData.push(isNaN(cumulativeInvested) || !isFinite(cumulativeInvested) ? 0 : cumulativeInvested);
            currentData.push(isNaN(cumulativeCurrent) || !isFinite(cumulativeCurrent) ? 0 : cumulativeCurrent);
        });
        
        // Calcula valores mínimo e máximo para a escala
        const allValues = [...investedData, ...currentData].filter(v => 
            !isNaN(v) && isFinite(v) && v !== null && v !== undefined
        );
        
        if (allValues.length === 0) {
            if (window.investmentsChart) {
                window.investmentsChart.destroy();
                window.investmentsChart = null;
            }
            return;
        }
        
        const minValue = Math.min(...allValues);
        const maxValue = Math.max(...allValues);
        
        // Calcula padding baseado no range, mas garante valores válidos
        const range = maxValue - minValue;
        const padding = range > 0 ? range * 0.1 : maxValue * 0.1;
        
        // Define limites da escala Y
        const yMin = minValue >= 0 ? 0 : Math.max(0, minValue - padding);
        const yMax = maxValue > 0 ? maxValue + padding : 100; // Se tudo for zero, mostra até 100
        
        if (window.investmentsChart) {
            window.investmentsChart.destroy();
        }
        
        window.investmentsChart = new Chart(ctx, {
            type: 'line',
            data: {
                labels: labels,
                datasets: [{
                    label: 'Valor Investido',
                    data: investedData,
                    borderColor: 'rgb(75, 192, 192)',
                    backgroundColor: 'rgba(75, 192, 192, 0.2)',
                    tension: 0.1,
                    fill: false
                }, {
                    label: 'Valor Atual',
                    data: currentData,
                    borderColor: 'rgb(255, 99, 132)',
                    backgroundColor: 'rgba(255, 99, 132, 0.2)',
                    tension: 0.1,
                    fill: false
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: true,
                interaction: {
                    intersect: false,
                    mode: 'index'
                },
                plugins: {
                    legend: {
                        display: true,
                        position: 'top'
                    },
                    tooltip: {
                        callbacks: {
                            label: function(context) {
                                const value = context.parsed.y;
                                if (isNaN(value) || !isFinite(value)) return context.dataset.label + ': R$ 0,00';
                                return context.dataset.label + ': R$ ' + 
                                    value.toLocaleString('pt-BR', {
                                        minimumFractionDigits: 2,
                                        maximumFractionDigits: 2
                                    });
                            }
                        }
                    }
                },
                scales: {
                    y: {
                        beginAtZero: minValue >= 0,
                        min: yMin,
                        max: yMax,
                        ticks: {
                            callback: function(value) {
                                if (isNaN(value) || !isFinite(value) || value < 0) return '';
                                return 'R$ ' + value.toLocaleString('pt-BR', {
                                    minimumFractionDigits: 2,
                                    maximumFractionDigits: 2
                                });
                            }
                        },
                        grid: {
                            display: true
                        }
                    },
                    x: {
                        display: true,
                        grid: {
                            display: false
                        }
                    }
                }
            }
        });
    }
    
    updateAssetsAllocationChart(investments) {
        const ctx = document.getElementById('assets-allocation-chart');
        if (!ctx) return;
        
        // Se não houver investimentos, não renderiza o gráfico
        if (!investments || investments.length === 0) {
            if (window.assetsAllocationChart) {
                window.assetsAllocationChart.destroy();
                window.assetsAllocationChart = null;
            }
            return;
        }
        
        // Agrupa investimentos por categoria e calcula valores totais
        const allocationByCategory = {};
        
        investments.forEach(inv => {
            const categoria = inv.categoria || 'OUTROS';
            const valorAtual = parseFloat(inv.valorAtual) || 0;
            
            if (!allocationByCategory[categoria]) {
                allocationByCategory[categoria] = {
                    categoria: categoria,
                    valorTotal: 0,
                    quantidade: 0
                };
            }
            
            allocationByCategory[categoria].valorTotal += valorAtual;
            allocationByCategory[categoria].quantidade += 1;
        });
        
        // Converte para arrays para o gráfico
        const labels = [];
        const data = [];
        const backgroundColors = [];
        const borderColors = [];
        
        // Cores para cada categoria
        const categoryColors = {
            'ACAO': { bg: 'rgba(54, 162, 235, 0.8)', border: 'rgb(54, 162, 235)' },
            'STOCK': { bg: 'rgba(255, 99, 132, 0.8)', border: 'rgb(255, 99, 132)' },
            'CRYPTO': { bg: 'rgba(255, 206, 86, 0.8)', border: 'rgb(255, 206, 86)' },
            'FII': { bg: 'rgba(75, 192, 192, 0.8)', border: 'rgb(75, 192, 192)' },
            'RENDA_FIXA': { bg: 'rgba(153, 102, 255, 0.8)', border: 'rgb(153, 102, 255)' },
            'OUTROS': { bg: 'rgba(201, 203, 207, 0.8)', border: 'rgb(201, 203, 207)' }
        };
        
        // Nomes amigáveis para as categorias
        const categoryNames = {
            'ACAO': 'Ações (B3)',
            'STOCK': 'Stocks (NYSE/NASDAQ)',
            'CRYPTO': 'Criptomoedas',
            'FII': 'Fundos Imobiliários',
            'RENDA_FIXA': 'Renda Fixa',
            'OUTROS': 'Outros'
        };
        
        // Ordena por valor total (maior para menor)
        const sortedCategories = Object.values(allocationByCategory)
            .filter(item => item.valorTotal > 0)
            .sort((a, b) => b.valorTotal - a.valorTotal);
        
        sortedCategories.forEach(item => {
            const categoria = item.categoria;
            labels.push(categoryNames[categoria] || categoria);
            data.push(item.valorTotal);
            
            const colors = categoryColors[categoria] || categoryColors['OUTROS'];
            backgroundColors.push(colors.bg);
            borderColors.push(colors.border);
        });
        
        if (data.length === 0) {
            if (window.assetsAllocationChart) {
                window.assetsAllocationChart.destroy();
                window.assetsAllocationChart = null;
            }
            return;
        }
        
        // Calcula total para porcentagens
        const total = data.reduce((sum, val) => sum + val, 0);
        
        if (window.assetsAllocationChart) {
            window.assetsAllocationChart.destroy();
        }
        
        window.assetsAllocationChart = new Chart(ctx, {
            type: 'doughnut',
            data: {
                labels: labels,
                datasets: [{
                    label: 'Valor Investido por Categoria',
                    data: data,
                    backgroundColor: backgroundColors,
                    borderColor: borderColors,
                    borderWidth: 2
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: true,
                plugins: {
                    legend: {
                        display: true,
                        position: 'right'
                    },
                    tooltip: {
                        callbacks: {
                            label: function(context) {
                                const label = context.label || '';
                                const value = context.parsed || 0;
                                const percentage = total > 0 ? ((value / total) * 100).toFixed(2) : 0;
                                return `${label}: R$ ${value.toLocaleString('pt-BR', {
                                    minimumFractionDigits: 2,
                                    maximumFractionDigits: 2
                                })} (${percentage}%)`;
                            }
                        }
                    }
                }
            }
        });
    }
    
    showAddInvestmentModal() {
        // Garante que as contas estão carregadas
        if (!this.accountsCache || this.accountsCache.length === 0) {
            // Evita loop infinito: verifica se já está carregando
            if (this._loadingAccounts) {
                return;
            }
            
            this._loadingAccounts = true;
            this.loadAccounts().then(() => {
                this._loadingAccounts = false;
                // Verifica se há contas do tipo "Investimento"
                const investmentAccounts = this.accountsCache.filter(acc => 
                    acc.tipo && acc.tipo.toLowerCase() === 'investimento'
                );
                
                if (investmentAccounts.length === 0) {
                    this.showToast('Você precisa criar pelo menos uma conta do tipo "Investimento" antes de registrar um investimento!', 'warning');
                    setTimeout(() => {
                        document.querySelector('[data-section="accounts"]')?.click();
                    }, 2000);
                    return;
                }
                // Se houver contas de investimento, abre o modal
                this.showAddInvestmentModal();
            }).catch(() => {
                this._loadingAccounts = false;
                this.showToast('Erro ao carregar contas', 'error');
            });
            return;
        }
        
        // Filtra apenas contas do tipo "Investimento"
        const investmentAccounts = this.accountsCache.filter(acc => 
            acc.tipo && acc.tipo.toLowerCase() === 'investimento'
        );
        
        if (investmentAccounts.length === 0) {
            this.showToast('Você precisa criar pelo menos uma conta do tipo "Investimento" antes de registrar um investimento!', 'warning');
            setTimeout(() => {
                document.querySelector('[data-section="accounts"]')?.click();
            }, 2000);
            return;
        }
        
        const content = `
            <form id="investment-form">
                <div class="form-group">
                    <label for="investment-name">Nome/Símbolo</label>
                    <input type="text" id="investment-name" placeholder="Ex: ITUB4, AAPL, BTC" required>
                    <small style="color: var(--neutral-600); margin-top: 4px; display: block;">
                        Digite o símbolo do investimento. O preço será obtido automaticamente com base no fechamento do dia através de nossa base de dados.
                    </small>
                </div>
                <div class="form-group">
                    <label for="investment-category">Categoria</label>
                    <select id="investment-category" required>
                        <option value="">Selecione...</option>
                        <option value="ACAO">Ação (B3)</option>
                        <option value="STOCK">Stock (NYSE/NASDAQ)</option>
                        <option value="CRYPTO">Criptomoeda</option>
                        <option value="FII">FII</option>
                        <option value="RENDA_FIXA">Renda Fixa</option>
                    </select>
                </div>
                <div class="form-group">
                    <label for="investment-quantity">Quantidade</label>
                    <input type="number" id="investment-quantity" min="0.000001" step="0.000001" required>
                    <small style="color: var(--neutral-600); margin-top: 4px; display: block;">
                       <!-- Você pode inserir quantidades fracionadas (ex: 0.003 BTC, 1.4 ações) -->
                    </small>
                </div>
                <div class="form-group">
                    <label for="investment-date">Data do Aporte</label>
                    <input type="date" id="investment-date" required>
                    <small style="color: var(--neutral-600); margin-top: 4px; display: block;">
                        <i class="fas fa-info-circle"></i> O preço será calculado com base no <strong>fechamento da ação no dia selecionado</strong>
                    </small>
                </div>
                <div class="form-group">
                    <label for="investment-brokerage">Corretagem</label>
                    <input type="number" id="investment-brokerage" min="0" step="0.01" value="0">
                </div>
                <div class="form-group">
                    <label for="investment-account">Conta de Investimento (Corretora)</label>
                    <select id="investment-account" required>
                        <option value="">Selecione uma conta de investimento...</option>
                    </select>
                    <small style="color: var(--neutral-600); margin-top: 4px; display: block;">
                        <i class="fas fa-info-circle"></i> A conta selecionada será usada como corretora. Apenas contas do tipo "Investimento" podem ser utilizadas.
                    </small>
                </div>
                <div class="form-group">
                    <label for="investment-currency">Moeda</label>
                    <select id="investment-currency">
                        <option value="BRL">BRL (Real)</option>
                        <option value="USD">USD (Dólar)</option>
                        <option value="EUR">EUR (Euro)</option>
                    </select>
                </div>
                <div class="form-actions">
                    <button type="button" class="btn-secondary" onclick="app.closeModal()">Cancelar</button>
                    <button type="submit" class="btn-primary">Salvar</button>
                </div>
            </form>
        `;
        
        this.showModal('Novo Investimento', content);
        
        // Aguarda um pouco para garantir que o DOM foi atualizado
        setTimeout(() => {
            // Preenche select de contas - apenas contas do tipo "Investimento"
            const accountSelect = document.getElementById('investment-account');
            if (accountSelect && this.accountsCache) {
                // Filtra apenas contas do tipo "Investimento"
                const investmentAccounts = this.accountsCache.filter(acc => 
                    acc.tipo && acc.tipo.toLowerCase() === 'investimento'
                );
                
                if (investmentAccounts.length === 0) {
                    const option = document.createElement('option');
                    option.value = '';
                    option.textContent = 'Nenhuma conta de investimento disponível';
                    option.disabled = true;
                    accountSelect.appendChild(option);
                } else {
                    investmentAccounts.forEach(account => {
                        const option = document.createElement('option');
                        option.value = account.idConta;
                        option.textContent = `${account.nome} (${account.tipo})`;
                        accountSelect.appendChild(option);
                    });
                }
                
                // Adiciona opção para criar nova conta
                const newAccountOption = document.createElement('option');
                newAccountOption.value = '__new__';
                newAccountOption.textContent = '➕ Criar nova conta de investimento...';
                newAccountOption.style.fontWeight = 'bold';
                newAccountOption.style.color = 'var(--primary-color)';
                accountSelect.appendChild(newAccountOption);
                
                // Event listener para quando selecionar "Criar nova conta"
                accountSelect.addEventListener('change', () => {
                    if (accountSelect.value === '__new__') {
                        // Salva os dados do formulário antes de abrir o modal de criação de conta
                        const investmentFormData = {
                            category: document.getElementById('investment-category')?.value || '',
                            name: document.getElementById('investment-name')?.value || '',
                            quantity: document.getElementById('investment-quantity')?.value || '',
                            date: document.getElementById('investment-date')?.value || '',
                            brokerage: document.getElementById('investment-brokerage')?.value || '0',
                            currency: document.getElementById('investment-currency')?.value || 'BRL'
                        };
                        
                        // Armazena temporariamente os dados
                        this._pendingInvestmentData = investmentFormData;
                        
                        this.showAddAccountModalFromInvestment();
                    }
                });
            }
            
            // Define data padrão como hoje
            const dateInput = document.getElementById('investment-date');
            if (dateInput) {
                const today = new Date();
                const year = today.getFullYear();
                const month = String(today.getMonth() + 1).padStart(2, '0');
                const day = String(today.getDate()).padStart(2, '0');
                dateInput.value = `${year}-${month}-${day}`;
            }
            
            // Event listener para o formulário
            const form = document.getElementById('investment-form');
            if (form) {
                // Remove listener anterior se existir
                const newForm = form.cloneNode(true);
                form.parentNode.replaceChild(newForm, form);
                newForm.addEventListener('submit', (e) => {
                    this.handleCreateInvestment(e);
                });
            }
        }, 100);
    }
    
    async handleCreateInvestment(e) {
        e.preventDefault();
        
        const category = document.getElementById('investment-category').value;
        const name = document.getElementById('investment-name').value.toUpperCase();
        const quantity = parseFloat(document.getElementById('investment-quantity').value);
        const date = document.getElementById('investment-date').value;
        
        // Valida quantidade mínima
        if (isNaN(quantity) || quantity <= 0) {
            this.showToast('A quantidade deve ser maior que zero', 'error');
            return;
        }
        const brokerage = parseFloat(document.getElementById('investment-brokerage').value) || 0;
        const accountIdValue = document.getElementById('investment-account').value;
        const currency = document.getElementById('investment-currency').value;
        
        // Valida se não está tentando criar uma nova conta
        if (accountIdValue === '__new__') {
            this.showToast('Por favor, selecione uma conta de investimento ou crie uma nova conta primeiro', 'warning');
            return;
        }
        
        const accountId = parseInt(accountIdValue);
        
        // Valida se a conta selecionada é do tipo "Investimento"
        let selectedAccount = null;
        if (accountId) {
            selectedAccount = this.accountsCache.find(acc => acc.idConta === accountId);
            if (!selectedAccount || !selectedAccount.tipo || selectedAccount.tipo.toLowerCase() !== 'investimento') {
                this.showToast('Apenas contas do tipo "Investimento" podem ser utilizadas para criar investimentos!', 'error');
                return;
            }
        }
        
        // A corretora é o nome da conta de investimento selecionada
        const broker = selectedAccount ? selectedAccount.nome : '';
        
        try {
            // Primeiro verifica se o símbolo existe buscando a cotação
            const quoteResponse = await this.apiCall(
                `/investments/quote?symbol=${name}&category=${category}&date=${date}`, 
                'GET'
            );
            
            if (!quoteResponse.success) {
                // Se a API não encontrou o investimento, abre modal para inserção manual
                this.showManualPriceModal(name, category, quantity, date, brokerage, accountId, currency, broker, quoteResponse.message);
                return;
            }
            
            const userId = this.currentUser ? this.currentUser.id : 1;
            const response = await this.apiCall('/investments', 'POST', {
                nome: name,
                categoria: category,
                quantidade: quantity,
                corretagem: brokerage,
                corretora: broker,
                dataAporte: date,
                userId: userId,
                accountId: accountId,
                moeda: currency
            });
            
            if (response.success) {
                this.showToast('Investimento cadastrado com sucesso!', 'success');
                this.closeModal();
                this.loadInvestments();
            } else {
                this.showToast(response.message || 'Erro ao cadastrar investimento', 'error');
            }
        } catch (error) {
            this.showToast('Erro de conexão', 'error');
            console.error('Create investment error:', error);
        }
    }
    
    showManualPriceModal(name, category, quantity, date, brokerage, accountId, currency, broker, errorMessage) {
        const content = `
            <div style="margin-bottom: var(--spacing-4); padding: var(--spacing-4); background: var(--warning-light); border-radius: var(--radius-md); border-left: 4px solid var(--warning-color);">
                <p style="margin: 0; color: var(--warning-color); font-weight: 600;">
                    <i class="fas fa-exclamation-triangle"></i> Investimento não encontrado nas bases de dados
                </p>
                <p style="margin: var(--spacing-2) 0 0 0; color: var(--neutral-700); font-size: var(--font-size-sm);">
                    ${errorMessage || 'Não foi possível encontrar este investimento automaticamente. Por favor, preencha as informações manualmente.'}
                </p>
            </div>
            <form id="manual-investment-form">
                <div class="form-group">
                    <label for="manual-investment-name">Nome/Símbolo do Investimento</label>
                    <input type="text" id="manual-investment-name" value="${name}" readonly style="background: var(--neutral-200);">
                </div>
                <div class="form-group">
                    <label for="manual-investment-asset-name">Nome Completo do Ativo (opcional)</label>
                    <input type="text" id="manual-investment-asset-name" placeholder="Ex: Itaú Unibanco, Apple Inc., Bitcoin">
                    <small style="color: var(--neutral-600); margin-top: 4px; display: block;">
                        Nome completo do ativo para melhor identificação
                    </small>
                </div>
                <div class="form-group">
                    <label for="manual-investment-price">Preço Unitário no Dia do Aporte</label>
                    <input type="number" id="manual-investment-price" min="0.01" step="0.01" required>
                    <small style="color: var(--neutral-600); margin-top: 4px; display: block;">
                        <i class="fas fa-info-circle"></i> Informe o preço de fechamento do ativo no dia ${date ? this.formatDate(date) : 'selecionado'}
                    </small>
                </div>
                <div class="form-group">
                    <label for="manual-investment-quantity">Quantidade</label>
                    <input type="number" id="manual-investment-quantity" value="${quantity}" min="0.000001" step="0.000001" required readonly style="background: var(--neutral-200);">
                </div>
                <div class="form-group">
                    <label for="manual-investment-date">Data do Aporte</label>
                    <input type="date" id="manual-investment-date" value="${date}" required readonly style="background: var(--neutral-200);">
                </div>
                <div class="form-group">
                    <label for="manual-investment-brokerage">Corretagem</label>
                    <input type="number" id="manual-investment-brokerage" value="${brokerage}" min="0" step="0.01" readonly style="background: var(--neutral-200);">
                </div>
                <div class="form-group">
                    <label for="manual-investment-currency">Moeda</label>
                    <select id="manual-investment-currency" required>
                        <option value="BRL" ${currency === 'BRL' ? 'selected' : ''}>BRL (Real)</option>
                        <option value="USD" ${currency === 'USD' ? 'selected' : ''}>USD (Dólar)</option>
                        <option value="EUR" ${currency === 'EUR' ? 'selected' : ''}>EUR (Euro)</option>
                    </select>
                </div>
                <div class="form-actions">
                    <button type="button" class="btn-secondary" onclick="app.closeModal()">Cancelar</button>
                    <button type="submit" class="btn-primary">Salvar Investimento</button>
                </div>
            </form>
        `;
        
        this.showModal('Inserir Investimento Manualmente', content);
        
        // Event listener para o formulário
        setTimeout(() => {
            const form = document.getElementById('manual-investment-form');
            if (form) {
                const newForm = form.cloneNode(true);
                form.parentNode.replaceChild(newForm, form);
                newForm.addEventListener('submit', (e) => {
                    this.handleManualInvestment(e, accountId, broker, category);
                });
            }
        }, 100);
    }
    
    async handleManualInvestment(e, accountId, broker, category) {
        e.preventDefault();
        
        const name = document.getElementById('manual-investment-name').value.toUpperCase();
        const assetName = document.getElementById('manual-investment-asset-name').value.trim() || null;
        const price = parseFloat(document.getElementById('manual-investment-price').value);
        const quantity = parseFloat(document.getElementById('manual-investment-quantity').value);
        const date = document.getElementById('manual-investment-date').value;
        const brokerage = parseFloat(document.getElementById('manual-investment-brokerage').value) || 0;
        const currency = document.getElementById('manual-investment-currency').value;
        
        // Validações
        if (isNaN(price) || price <= 0) {
            this.showToast('O preço deve ser maior que zero', 'error');
            return;
        }
        
        if (isNaN(quantity) || quantity <= 0) {
            this.showToast('A quantidade deve ser maior que zero', 'error');
            return;
        }
        
        try {
            const userId = this.currentUser ? this.currentUser.id : 1;
            const response = await this.apiCall('/investments', 'POST', {
                nome: name,
                nomeAtivo: assetName,
                categoria: category,
                quantidade: quantity,
                precoAporte: price,
                corretagem: brokerage,
                corretora: broker,
                dataAporte: date,
                userId: userId,
                accountId: accountId,
                moeda: currency
            });
            
            if (response.success) {
                this.showToast('Investimento cadastrado com sucesso!', 'success');
                this.closeModal();
                this.loadInvestments();
            } else {
                this.showToast(response.message || 'Erro ao cadastrar investimento', 'error');
            }
        } catch (error) {
            this.showToast('Erro de conexão', 'error');
            console.error('Create manual investment error:', error);
        }
    }
    
    editInvestment(investmentId) {
        // Implementar edição de investimento
        this.showToast('Funcionalidade de edição em desenvolvimento', 'info');
    }
    
    deleteInvestment(investmentId) {
        if (!confirm('Tem certeza que deseja excluir este investimento? Esta ação não pode ser desfeita.')) {
            return;
        }
        
        this.apiCall(`/investments?id=${investmentId}`, 'DELETE').then(response => {
            if (response.success) {
                this.showToast('Investimento excluído com sucesso!', 'success');
                this.loadInvestments();
            } else {
                this.showToast(response.message || 'Erro ao excluir investimento', 'error');
            }
        }).catch(error => {
            this.showToast('Erro de conexão', 'error');
            console.error('Delete investment error:', error);
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
