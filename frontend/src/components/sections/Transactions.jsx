import React, { useState, useEffect } from 'react';
import { Plus, Minus, Trash2, ArrowUp, ArrowDown, Filter } from 'lucide-react';
import { useAuth } from '../../contexts/AuthContext';
import { useData } from '../../contexts/DataContext';
import api from '../../services/api';
import { formatCurrency, formatDate } from '../../utils/formatters';
import Modal from '../common/Modal';
import toast from 'react-hot-toast';

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
      const [transRes, catRes, accRes, tagRes] = await Promise.all([
        api.get(`/transactions?userId=${user.id}`),
        api.get(`/categories?userId=${user.id}`),
        api.get(`/accounts?userId=${user.id}`),
        api.get(`/tags?userId=${user.id}`),
      ]);

      if (transRes.success) {
        let filtered = transRes.data || [];
        if (filters.category) {
          filtered = filtered.filter((t) => t.categoryId === parseInt(filters.category));
        }
        if (filters.tag) {
          filtered = filtered.filter((t) =>
            t.tags?.some((tag) => tag.idTag === parseInt(filters.tag))
          );
        }
        if (filters.date) {
          filtered = filtered.filter((t) => t.date?.startsWith(filters.date));
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
    return (
      <div className="flex items-center justify-center h-64">
        <div className="animate-spin rounded-full h-12 w-12 border-4 border-primary-600 border-t-transparent"></div>
      </div>
    );
  }

  return (
    <div className="space-y-6 animate-fade-in">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-3xl font-bold text-gray-900 dark:text-white">
            Transações
          </h2>
          <p className="text-gray-600 dark:text-gray-400 mt-1">
            Gerencie suas receitas e gastos
          </p>
        </div>
        <div className="flex gap-2">
          <button onClick={() => setShowExpenseModal(true)} className="btn-secondary">
            <Minus className="w-4 h-4" />
            Novo Gasto
          </button>
          <button onClick={() => setShowIncomeModal(true)} className="btn-primary">
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
              className="card flex items-center justify-between"
            >
              <div className="flex items-center gap-4">
                <div
                  className={`w-12 h-12 rounded-xl flex items-center justify-center ${
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
                <div>
                  <p className="font-semibold text-gray-900 dark:text-white">
                    {transaction.description}
                  </p>
                  <p className="text-sm text-gray-500 dark:text-gray-400">
                    {formatDate(transaction.date)} - {transaction.category}
                  </p>
                  {transaction.tags && transaction.tags.length > 0 && (
                    <div className="flex gap-2 mt-2">
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
              <div className="flex items-center gap-4">
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
                  className="btn-danger"
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
  });

  const handleSubmit = async (e) => {
    e.preventDefault();
    try {
      const data = {
        description: formData.description,
        value: parseFloat(formData.value),
        date: formData.date,
        accountId: parseInt(formData.accountId),
        userId: user.id,
      };

      if (type === 'expense') {
        data.categoryIds = formData.categoryIds.map(Number);
        if (data.categoryIds.length === 0) {
          toast.error('Selecione pelo menos uma categoria');
          return;
        }
      }

      if (formData.tagIds.length > 0) {
        data.tagIds = formData.tagIds.map(Number);
      }

      if (formData.observacoes && formData.observacoes.trim()) {
        data.observacoes = formData.observacoes.trim();
      }

      const endpoint = type === 'expense' ? '/expenses' : '/incomes';
      const response = await api.post(endpoint, data);
      if (response.success) {
        toast.success(`${type === 'expense' ? 'Gasto' : 'Receita'} adicionada!`);
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
        <div>
          <label className="label">Conta</label>
          <select
            value={formData.accountId}
            onChange={(e) => setFormData({ ...formData, accountId: e.target.value })}
            className="input"
            required
          >
            <option value="">Selecione a conta</option>
            {accounts
              .filter((acc) => acc.tipo?.toLowerCase() !== 'investimento')
              .map((acc) => (
                <option key={acc.idConta} value={acc.idConta}>
                  {acc.nome} ({acc.tipo})
                </option>
              ))}
          </select>
        </div>
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

