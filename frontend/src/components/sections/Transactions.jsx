import React, { useState, useEffect } from 'react';
import { Plus, Minus, Trash2, ArrowUp, ArrowDown, Filter } from 'lucide-react';
import { useAuth } from '../../contexts/AuthContext';
import { useData } from '../../contexts/DataContext';
import api from '../../services/api';
import { formatCurrency, formatDate } from '../../utils/formatters';
import Modal from '../common/Modal';
import toast from 'react-hot-toast';
import SkeletonSection from '../common/SkeletonSection';

export default function Transactions() {
  const { user } = useAuth();
  const { invalidateCache } = useData();
  const [transactions, setTransactions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showExpenseModal, setShowExpenseModal] = useState(false);
  const [showIncomeModal, setShowIncomeModal] = useState(false);
  const [categories, setCategories] = useState([]);
  const [accounts, setAccounts] = useState([]);
  const [tags, setTags] = useState([]);
  const [filters, setFilters] = useState({ category: '', tag: '', date: '' });

  useEffect(() => {
    if (user) {
      loadData();
    }
  }, [user, filters]);

  const loadData = async () => {
    setLoading(true);
    try {
      // Constrói URL com parâmetros de filtro
      let transactionsUrl = `/transactions?userId=${user.id}`;
      if (filters.category) {
        transactionsUrl += `&categoryId=${filters.category}`;
      }
      if (filters.date) {
        transactionsUrl += `&date=${filters.date}`;
      }

      const [transRes, catRes, accRes, tagRes] = await Promise.all([
        api.get(transactionsUrl),
        api.get(`/categories?userId=${user.id}`),
        api.get(`/accounts?userId=${user.id}`),
        api.get(`/tags?userId=${user.id}`),
      ]);

      if (transRes.success) {
        let filtered = transRes.data || [];
        // Filtro de tag ainda é feito no cliente, pois o backend não suporta filtro por tag diretamente
        if (filters.tag) {
          filtered = filtered.filter((t) =>
            t.tags?.some((tag) => tag.idTag === parseInt(filters.tag))
          );
        }
        setTransactions(filtered);
      }
      if (catRes.success) setCategories(catRes.data || []);
      if (accRes.success) setAccounts(accRes.data || []);
      if (tagRes.success) setTags(tagRes.data || []);
    } catch (error) {
      toast.error('Erro ao carregar transações');
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async (id, type) => {
    if (!confirm('Tem certeza que deseja excluir esta transação?')) return;

    try {
      const endpoint = type === 'income' ? '/incomes' : '/expenses';
      const response = await api.delete(`${endpoint}?id=${id}`);
      if (response.success) {
        toast.success('Transação excluída!');
        
        // Invalidate cache for overview and recent transactions
        invalidateCache(`overview-${user.id}-month`);
        invalidateCache(`overview-${user.id}-year`);
        invalidateCache(`overview-${user.id}-all`);
        invalidateCache(`recent-transactions-${user.id}`);
        
        loadData();
      }
    } catch (error) {
      toast.error('Erro ao excluir transação');
    }
  };

  if (loading) {
    return <SkeletonSection type="transactions" />;
  }

  return (
    <div className="space-y-6 animate-fade-in">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-3xl font-bold text-gray-900 dark:text-white">
            Transações
          </h2>
          <p className="text-gray-600 dark:text-gray-400 mt-1">
            Gerencie suas receitas e gastos
          </p>
        </div>
        <div className="flex gap-2 w-full sm:w-auto">
          <button onClick={() => setShowExpenseModal(true)} className="flex-1 sm:flex-none btn-secondary justify-center">
            <Minus className="w-4 h-4" />
            Novo Gasto
          </button>
          <button onClick={() => setShowIncomeModal(true)} className="flex-1 sm:flex-none btn-primary justify-center">
            <Plus className="w-4 h-4" />
            Nova Receita
          </button>
        </div>
      </div>

      {/* Filters */}
      <div className="card">
        <div className="flex items-center gap-2 mb-4">
          <Filter className="w-5 h-5 text-gray-500" />
          <h3 className="font-semibold text-gray-900 dark:text-white">Filtros</h3>
        </div>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div>
            <label className="label">Categoria</label>
            <select
              value={filters.category}
              onChange={(e) => setFilters({ ...filters, category: e.target.value })}
              className="input"
            >
              <option value="">Todas as Categorias</option>
              {categories.map((cat) => (
                <option key={cat.idCategoria} value={cat.idCategoria}>
                  {cat.nome}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className="label">Tag</label>
            <select
              value={filters.tag}
              onChange={(e) => setFilters({ ...filters, tag: e.target.value })}
              className="input"
            >
              <option value="">Todas as Tags</option>
              {tags.map((tag) => (
                <option key={tag.idTag} value={tag.idTag}>
                  {tag.nome}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className="label">Data</label>
            <input
              type="date"
              value={filters.date}
              onChange={(e) => setFilters({ ...filters, date: e.target.value })}
              className="input"
            />
          </div>
        </div>
        <button
          onClick={() => setFilters({ category: '', tag: '', date: '' })}
          className="btn-secondary mt-4"
        >
          Limpar Filtros
        </button>
      </div>

      {/* Transactions List */}
      {transactions.length === 0 ? (
        <div className="card text-center py-12">
          <p className="text-gray-600 dark:text-gray-400">
            Nenhuma transação encontrada
          </p>
        </div>
      ) : (
        <div className="space-y-3">
          {transactions.map((transaction) => (
            <div
              key={transaction.id}
              className="card flex flex-col sm:flex-row sm:items-center justify-between gap-4"
            >
              <div className="flex items-center gap-4 w-full sm:w-auto">
                <div
                  className={`w-12 h-12 rounded-xl flex items-center justify-center shrink-0 ${
                    transaction.type === 'income'
                      ? 'bg-green-100 dark:bg-green-900/30 text-green-600'
                      : 'bg-red-100 dark:bg-red-900/30 text-red-600'
                  }`}
                >
                  {transaction.type === 'income' ? (
                    <ArrowUp className="w-6 h-6" />
                  ) : (
                    <ArrowDown className="w-6 h-6" />
                  )}
                </div>
                <div className="min-w-0">
                  <p className="font-semibold text-gray-900 dark:text-white truncate">
                    {transaction.description}
                  </p>
                  <p className="text-sm text-gray-500 dark:text-gray-400 truncate">
                    {formatDate(transaction.date)} - {transaction.category}
                  </p>
                  {transaction.tags && transaction.tags.length > 0 && (
                    <div className="flex flex-wrap gap-2 mt-2">
                      {transaction.tags.map((tag) => (
                        <span
                          key={tag.idTag}
                          className="text-xs px-2 py-1 rounded-full text-white"
                          style={{ backgroundColor: tag.cor }}
                        >
                          {tag.nome}
                        </span>
                      ))}
                    </div>
                  )}
                  {transaction.observacoes && transaction.observacoes.length > 0 && (
                    <div className="mt-2">
                      <p className="text-xs text-gray-500 dark:text-gray-400 font-medium">Observações:</p>
                      <ul className="text-xs text-gray-600 dark:text-gray-300 list-disc list-inside mt-1">
                        {Array.isArray(transaction.observacoes) 
                          ? transaction.observacoes.map((obs, idx) => (
                              <li key={idx}>{obs}</li>
                            ))
                          : <li>{transaction.observacoes}</li>
                        }
                      </ul>
                    </div>
                  )}
                </div>
              </div>
              <div className="flex items-center justify-between sm:justify-end gap-4 w-full sm:w-auto border-t sm:border-t-0 pt-4 sm:pt-0 border-gray-100 dark:border-gray-700">
                <span
                  className={`text-lg font-bold ${
                    transaction.type === 'income'
                      ? 'text-green-600 dark:text-green-400'
                      : 'text-red-600 dark:text-red-400'
                  }`}
                >
                  {transaction.type === 'income' ? '+' : '-'}
                  {formatCurrency(transaction.value || 0)}
                </span>
                <button
                  onClick={() => handleDelete(transaction.id, transaction.type)}
                  className="btn-danger shrink-0"
                >
                  <Trash2 className="w-4 h-4" />
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Expense Modal - Simplified */}
      <TransactionModal
        isOpen={showExpenseModal}
        onClose={() => setShowExpenseModal(false)}
        type="expense"
        categories={categories}
        accounts={accounts}
        tags={tags}
        onSuccess={() => {
          loadData();
          // Invalidate cache
          invalidateCache(`overview-${user.id}-month`);
          invalidateCache(`overview-${user.id}-year`);
          invalidateCache(`overview-${user.id}-all`);
          invalidateCache(`recent-transactions-${user.id}`);
        }}
        user={user}
      />

      {/* Income Modal - Simplified */}
      <TransactionModal
        isOpen={showIncomeModal}
        onClose={() => setShowIncomeModal(false)}
        type="income"
        categories={categories}
        accounts={accounts}
        tags={tags}
        onSuccess={() => {
          loadData();
          // Invalidate cache
          invalidateCache(`overview-${user.id}-month`);
          invalidateCache(`overview-${user.id}-year`);
          invalidateCache(`overview-${user.id}-all`);
          invalidateCache(`recent-transactions-${user.id}`);
        }}
        user={user}
      />
    </div>
  );
}

function TransactionModal({ isOpen, onClose, type, categories, accounts, tags, onSuccess, user }) {
  const [formData, setFormData] = useState({
    description: '',
    value: '',
    date: new Date().toISOString().split('T')[0],
    accountId: '',
    categoryIds: [],
    tagIds: [],
    observacoes: '',
    pagamentoFatura: false,
  });
  const [invoiceInfo, setInvoiceInfo] = useState(null);

  const formatAccountType = (tipo) => {
    if (!tipo) return '';
    const tipoLower = tipo.toLowerCase();
    if (tipoLower.includes('cartão') || tipoLower.includes('cartao') || tipoLower.includes('cartao_credito') || tipoLower.includes('credito') || tipoLower.includes('crédito')) {
      return 'Cartão de Crédito';
    }
    if (tipoLower.includes('investimento')) {
      return 'Investimento';
    }
    if (tipoLower.includes('corrente')) {
      return 'Conta Corrente';
    }
    if (tipoLower.includes('poupança') || tipoLower.includes('poupanca')) {
      return 'Poupança';
    }
    if (tipoLower.includes('dinheiro')) {
      return 'Dinheiro';
    }
    // Retorna o tipo original formatado (primeira letra maiúscula, resto minúscula)
    return tipo.split('_').map(word => 
      word.charAt(0).toUpperCase() + word.slice(1).toLowerCase()
    ).join(' ');
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    
    // Validação no frontend: se for receita em cartão de crédito, verifica valor
    if (type === 'income' && formData.pagamentoFatura && invoiceInfo) {
      const valorInformado = parseFloat(formData.value);
      const valorDisponivel = invoiceInfo.valorDisponivel || 0;
      
      if (valorInformado > valorDisponivel + 0.01) {
        let mensagem = '';
        if (invoiceInfo.valorFatura <= 0) {
          mensagem = `Não há fatura pendente para pagar. A fatura atual está em ${formatCurrency(invoiceInfo.valorFatura)}.`;
        } else if (invoiceInfo.valorJaPago >= invoiceInfo.valorFatura) {
          mensagem = `A fatura já foi totalmente paga. Valor da fatura: ${formatCurrency(invoiceInfo.valorFatura)}. Valor já pago: ${formatCurrency(invoiceInfo.valorJaPago)}.`;
        } else {
          mensagem = `O valor informado (${formatCurrency(valorInformado)}) excede o valor disponível para pagamento (${formatCurrency(valorDisponivel)}).`;
        }
        toast.error(mensagem);
        return;
      }
    }
    
    try {
      const data = {
        description: formData.description,
        value: parseFloat(formData.value),
        date: formData.date,
        accountId: parseInt(formData.accountId),
        userId: user.id,
      };

      if (type === 'expense') {
        // Categoria não é mais obrigatória - gastos sem categoria serão associados à categoria "Sem Categoria"
        data.categoryIds = formData.categoryIds.length > 0 ? formData.categoryIds.map(Number) : [];
      }

      if (formData.tagIds.length > 0) {
        data.tagIds = formData.tagIds.map(Number);
      }

      if (formData.observacoes && formData.observacoes.trim()) {
        data.observacoes = formData.observacoes.trim();
      }

      // Se for receita, sempre envia flag de pagamento de fatura (mesmo que false)
      if (type === 'income') {
        data.pagamentoFatura = formData.pagamentoFatura || false;
      }

      const endpoint = type === 'expense' ? '/expenses' : '/incomes';
      const response = await api.post(endpoint, data);
      if (response.success) {
        toast.success(`${type === 'expense' ? 'Gasto Adicionado' : 'Receita Adicionada'}!`);
        onSuccess();
        onClose();
        setFormData({
          description: '',
          value: '',
          date: new Date().toISOString().split('T')[0],
          accountId: '',
          categoryIds: [],
          tagIds: [],
          observacoes: '',
          pagamentoFatura: false,
        });
      }
    } catch (error) {
      toast.error(error.message || 'Erro ao salvar transação');
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={type === 'expense' ? 'Novo Gasto' : 'Nova Receita'}
    >
      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label className="label">Descrição</label>
          <input
            type="text"
            value={formData.description}
            onChange={(e) => setFormData({ ...formData, description: e.target.value })}
            className="input"
            required
          />
        </div>
        <div>
          <label className="label">Valor</label>
          <input
            type="number"
            step="0.01"
            value={formData.value}
            onChange={(e) => setFormData({ ...formData, value: e.target.value })}
            className="input"
            required
          />
        </div>
        <div>
          <label className="label">Data</label>
          <input
            type="date"
            value={formData.date}
            onChange={(e) => setFormData({ ...formData, date: e.target.value })}
            className="input"
            required
          />
        </div>
        {type === 'expense' && (
          <div>
            <label className="label">Categorias</label>
            <div className="space-y-2 max-h-32 overflow-y-auto">
              {categories.map((cat) => (
                <label key={cat.idCategoria} className="flex items-center gap-2">
                  <input
                    type="checkbox"
                    checked={formData.categoryIds.includes(cat.idCategoria.toString())}
                    onChange={(e) => {
                      if (e.target.checked) {
                        setFormData({
                          ...formData,
                          categoryIds: [...formData.categoryIds, cat.idCategoria.toString()],
                        });
                      } else {
                        setFormData({
                          ...formData,
                          categoryIds: formData.categoryIds.filter((id) => id !== cat.idCategoria.toString()),
                        });
                      }
                    }}
                  />
                  <span>{cat.nome}</span>
                </label>
              ))}
            </div>
          </div>
        )}
        <div className="relative">
          <label className="label">Conta</label>
          <select
            value={formData.accountId}
            onChange={async (e) => {
              const selectedAccount = accounts.find(acc => acc.idConta.toString() === e.target.value);
              const isCreditCard = selectedAccount?.tipo?.toLowerCase().includes('cartao') || 
                                   selectedAccount?.tipo?.toLowerCase().includes('cartão') ||
                                   selectedAccount?.tipo?.toLowerCase().includes('credito') ||
                                   selectedAccount?.tipo?.toLowerCase().includes('crédito');
              setFormData({ 
                ...formData, 
                accountId: e.target.value,
                pagamentoFatura: false // Reseta quando muda a conta
              });
              setInvoiceInfo(null); // Reseta informações da fatura

              // Se for receita e cartão de crédito, carrega informações da fatura
              if (type === 'income' && isCreditCard && user) {
                try {
                  const response = await api.get(`/accounts/${e.target.value}/invoice-info?userId=${user.id}`);
                  if (response.success && response.data) {
                    setInvoiceInfo({
                      valorFatura: response.data.valorFatura || 0,
                      valorJaPago: response.data.valorJaPago || 0,
                      valorDisponivel: response.data.valorDisponivelPagamento || 0
                    });
                  } else {
                    setInvoiceInfo(null);
                  }
                } catch (error) {
                  console.error('Erro ao carregar informações da fatura:', error);
                  setInvoiceInfo(null);
                }
              }
            }}
            className="input"
            required
          >
            <option value="">Selecione a conta</option>
            {accounts
              .filter((acc) => acc.tipo?.toLowerCase() !== 'investimento')
              .map((acc) => (
                <option key={acc.idConta} value={acc.idConta}>
                  {acc.nome} {acc.tipo ? `(${formatAccountType(acc.tipo)})` : ''}
                </option>
              ))}
          </select>
        </div>
        {type === 'income' && (() => {
          const selectedAccount = accounts.find(acc => acc.idConta.toString() === formData.accountId);
          const isCreditCard = selectedAccount?.tipo?.toLowerCase().includes('cartao') || 
                               selectedAccount?.tipo?.toLowerCase().includes('cartão') ||
                               selectedAccount?.tipo?.toLowerCase().includes('credito') ||
                               selectedAccount?.tipo?.toLowerCase().includes('crédito');
          
          if (isCreditCard) {
            return (
              <div className="space-y-2">
                <label className="flex items-center gap-2 cursor-pointer">
                  <input
                    type="checkbox"
                    checked={formData.pagamentoFatura}
                    onChange={async (e) => {
                      const checked = e.target.checked;
                      setFormData({ ...formData, pagamentoFatura: checked });
                      
                      // Se marcou o checkbox e ainda não tem invoiceInfo, carrega
                      if (checked && !invoiceInfo && user && formData.accountId) {
                        try {
                          const response = await api.get(`/accounts/${formData.accountId}/invoice-info?userId=${user.id}`);
                          if (response.success && response.data) {
                            setInvoiceInfo({
                              valorFatura: response.data.valorFatura || 0,
                              valorJaPago: response.data.valorJaPago || 0,
                              valorDisponivel: response.data.valorDisponivelPagamento || 0
                            });
                          }
                        } catch (error) {
                          console.error('Erro ao carregar informações da fatura:', error);
                        }
                      }
                    }}
                    className="w-4 h-4 text-primary-600 rounded focus:ring-primary-500"
                    required
                  />
                  <span className="label">Esta receita é um pagamento de fatura de cartão de crédito</span>
                </label>
                {!formData.pagamentoFatura && (
                  <p className="text-sm text-red-600 dark:text-red-400 mt-1">
                    Você deve marcar esta opção para cadastrar receitas em cartão de crédito
                  </p>
                )}
                {formData.pagamentoFatura && invoiceInfo && (
                  <div className="bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-lg p-3 mt-2">
                    <p className="text-sm text-blue-800 dark:text-blue-200 font-medium mb-2">
                      Informações da Fatura:
                    </p>
                    <div className="text-xs text-blue-700 dark:text-blue-300 space-y-1">
                      <div className="flex justify-between">
                        <span>Valor da fatura:</span>
                        <span className="font-semibold">{formatCurrency(invoiceInfo.valorFatura || 0)}</span>
                      </div>
                      <div className="flex justify-between">
                        <span>Valor já pago:</span>
                        <span className="font-semibold">{formatCurrency(invoiceInfo.valorJaPago || 0)}</span>
                      </div>
                      <div className="flex justify-between pt-1 border-t border-blue-200 dark:border-blue-700">
                        <span className="font-medium">Disponível para pagamento:</span>
                        <span className={`font-bold ${
                          (invoiceInfo.valorDisponivel || 0) <= 0 
                            ? 'text-red-600 dark:text-red-400' 
                            : 'text-blue-900 dark:text-blue-100'
                        }`}>
                          {formatCurrency(invoiceInfo.valorDisponivel || 0)}
                        </span>
                      </div>
                    </div>
                    {formData.value && parseFloat(formData.value) > (invoiceInfo.valorDisponivel || 0) + 0.01 && (
                      <p className="text-xs text-red-600 dark:text-red-400 mt-2 font-medium">
                        ⚠️ O valor informado excede o valor disponível!
                      </p>
                    )}
                  </div>
                )}
              </div>
            );
          }
          return null;
        })()}
        {tags.length > 0 && (
          <div>
            <label className="label">Tags (opcional)</label>
            <div className="space-y-2 max-h-32 overflow-y-auto">
              {tags.map((tag) => (
                <label key={tag.idTag} className="flex items-center gap-2">
                  <input
                    type="checkbox"
                    checked={formData.tagIds.includes(tag.idTag.toString())}
                    onChange={(e) => {
                      if (e.target.checked) {
                        setFormData({
                          ...formData,
                          tagIds: [...formData.tagIds, tag.idTag.toString()],
                        });
                      } else {
                        setFormData({
                          ...formData,
                          tagIds: formData.tagIds.filter((id) => id !== tag.idTag.toString()),
                        });
                      }
                    }}
                  />
                  <span
                    className="text-xs px-2 py-1 rounded-full text-white"
                    style={{ backgroundColor: tag.cor }}
                  >
                    {tag.nome}
                  </span>
                </label>
              ))}
            </div>
          </div>
        )}
        <div>
          <label className="label">Observações (opcional)</label>
          <textarea
            value={formData.observacoes}
            onChange={(e) => setFormData({ ...formData, observacoes: e.target.value })}
            className="input"
            rows="3"
            placeholder="Digite observações separadas por vírgula ou quebra de linha..."
          />
        </div>
        <div className="flex gap-2 justify-end">
          <button type="button" onClick={onClose} className="btn-secondary">
            Cancelar
          </button>
          <button type="submit" className="btn-primary">
            Salvar
          </button>
        </div>
      </form>
    </Modal>
  );
}

