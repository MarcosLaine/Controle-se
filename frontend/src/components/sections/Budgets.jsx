import React, { useState, useEffect } from 'react';
import { Plus, Edit, Trash2, TrendingUp } from 'lucide-react';
import { useAuth } from '../../contexts/AuthContext';
import api from '../../services/api';
import { formatCurrency } from '../../utils/formatters';
import Modal from '../common/Modal';
import toast from 'react-hot-toast';

export default function Budgets() {
  const { user } = useAuth();
  const [budgets, setBudgets] = useState([]);
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showModal, setShowModal] = useState(false);
  const [editingBudget, setEditingBudget] = useState(null);
  const [formData, setFormData] = useState({ categoryId: '', value: '', period: 'Mensal' });

  useEffect(() => {
    if (user) {
      loadData();
    }
  }, [user]);

  const loadData = async () => {
    setLoading(true);
    try {
      const [budgetsRes, categoriesRes] = await Promise.all([
        api.get(`/budgets?userId=${user.id}`),
        api.get(`/categories?userId=${user.id}`),
      ]);
      if (budgetsRes.success) setBudgets(budgetsRes.data || []);
      if (categoriesRes.success) setCategories(categoriesRes.data || []);
    } catch (error) {
      toast.error('Erro ao carregar orçamentos');
    } finally {
      setLoading(false);
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    try {
      const data = {
        categoryId: parseInt(formData.categoryId),
        value: parseFloat(formData.value),
        period: formData.period,
        userId: user.id,
      };

      if (editingBudget) {
        const response = await api.put(`/budgets/${editingBudget.idOrcamento}`, data);
        if (response.success) {
          toast.success('Orçamento atualizado!');
          loadData();
          handleCloseModal();
        }
      } else {
        const response = await api.post('/budgets', data);
        if (response.success) {
          toast.success('Orçamento criado!');
          loadData();
          handleCloseModal();
        }
      }
    } catch (error) {
      toast.error(error.message || 'Erro ao salvar orçamento');
    }
  };

  const handleDelete = async (id) => {
    if (!confirm('Tem certeza que deseja excluir este orçamento?')) return;
    try {
      const response = await api.delete(`/budgets/${id}?userId=${user.id}`);
      if (response.success) {
        toast.success('Orçamento excluído!');
        loadData();
      }
    } catch (error) {
      toast.error('Erro ao excluir orçamento');
    }
  };

  const handleCloseModal = () => {
    setShowModal(false);
    setEditingBudget(null);
    setFormData({ categoryId: '', value: '', period: 'Mensal' });
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
          <h2 className="text-3xl font-bold text-gray-900 dark:text-white">Orçamentos</h2>
          <p className="text-gray-600 dark:text-gray-400 mt-1">Controle seus gastos planejados</p>
        </div>
        <button onClick={() => setShowModal(true)} className="btn-primary">
          <Plus className="w-4 h-4" />
          Novo Orçamento
        </button>
      </div>

      {budgets.length === 0 ? (
        <div className="card text-center py-12">
          <TrendingUp className="w-16 h-16 text-gray-400 mx-auto mb-4" />
          <p className="text-gray-600 dark:text-gray-400">Nenhum orçamento definido</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {budgets.map((budget) => {
            const valorUsado = budget.valorUsado || 0;
            const valorPlanejado = budget.valorPlanejado || 0;
            const percentage = valorPlanejado > 0 
              ? Math.min((valorUsado / valorPlanejado) * 100, 100) 
              : 0;
            return (
              <div key={budget.idOrcamento} className="card">
                <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-2">
                  {budget.categoryName}
                </h3>
                <p className="text-sm text-gray-600 dark:text-gray-400 mb-4">{budget.periodo}</p>
                <div className="mb-4">
                  <div className="flex justify-between text-sm mb-2">
                    <span className="text-gray-600 dark:text-gray-400">
                      {formatCurrency(valorUsado)} / {formatCurrency(valorPlanejado)}
                    </span>
                    <span className="font-semibold">{percentage.toFixed(1)}%</span>
                  </div>
                  <div className="w-full bg-gray-200 dark:bg-gray-700 rounded-full h-2">
                    <div
                      className={`h-2 rounded-full transition-all ${
                        percentage > 100 ? 'bg-red-500' : percentage > 80 ? 'bg-yellow-500' : 'bg-green-500'
                      }`}
                      style={{ width: `${Math.min(percentage, 100)}%` }}
                    ></div>
                  </div>
                </div>
                <div className="flex gap-2">
                  <button onClick={() => {
                    setEditingBudget(budget);
                    setFormData({
                      categoryId: budget.idCategoria.toString(),
                      value: budget.valorPlanejado.toString(),
                      period: budget.periodo,
                    });
                    setShowModal(true);
                  }} className="btn-secondary flex-1">
                    <Edit className="w-4 h-4" />
                    Editar
                  </button>
                  <button onClick={() => handleDelete(budget.idOrcamento)} className="btn-danger flex-1">
                    <Trash2 className="w-4 h-4" />
                    Excluir
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      )}

      <Modal
        isOpen={showModal}
        onClose={handleCloseModal}
        title={editingBudget ? 'Editar Orçamento' : 'Novo Orçamento'}
      >
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="label">Categoria</label>
            <select
              value={formData.categoryId}
              onChange={(e) => setFormData({ ...formData, categoryId: e.target.value })}
              className="input"
              required
            >
              <option value="">Selecione a categoria</option>
              {categories.map((cat) => (
                <option key={cat.idCategoria} value={cat.idCategoria}>
                  {cat.nome}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className="label">Valor Planejado (R$)</label>
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
            <label className="label">Período</label>
            <select
              value={formData.period}
              onChange={(e) => setFormData({ ...formData, period: e.target.value })}
              className="input"
              required
            >
              <option value="Mensal">Mensal</option>
              <option value="Anual">Anual</option>
            </select>
          </div>
          <div className="flex gap-2 justify-end">
            <button type="button" onClick={handleCloseModal} className="btn-secondary">
              Cancelar
            </button>
            <button type="submit" className="btn-primary">
              {editingBudget ? 'Atualizar' : 'Criar'}
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
}

