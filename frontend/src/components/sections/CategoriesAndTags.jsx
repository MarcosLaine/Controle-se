import React, { useState, useEffect, useRef, useCallback } from 'react';
import { Plus, Edit, Trash2, Tag, TrendingUp, Info } from 'lucide-react';
import { useAuth } from '../../contexts/AuthContext';
import api from '../../services/api';
import axios from 'axios';
import { formatCurrency } from '../../utils/formatters';
import Modal from '../common/Modal';
import toast from 'react-hot-toast';
import SkeletonSection from '../common/SkeletonSection';
import Spinner from '../common/Spinner';

export default function CategoriesAndTags() {
  const { user } = useAuth();
  const [categories, setCategories] = useState([]);
  const [tags, setTags] = useState([]);
  const [budgets, setBudgets] = useState([]);
  const [loading, setLoading] = useState(true);
  
  // Ref para armazenar o AbortController da requisição atual
  const abortControllerRef = useRef(null);
  
  // Modals
  const [showCategoryModal, setShowCategoryModal] = useState(false);
  const [showTagModal, setShowTagModal] = useState(false);
  const [showBudgetModal, setShowBudgetModal] = useState(false);
  
  // Editing states
  const [editingCategory, setEditingCategory] = useState(null);
  const [editingTag, setEditingTag] = useState(null);
  const [editingBudget, setEditingBudget] = useState(null);
  
  // Form data
  const [categoryFormData, setCategoryFormData] = useState({ nome: '', budget: '' });
  const [tagFormData, setTagFormData] = useState({ nome: '', cor: '#3498db' });
  const [budgetFormData, setBudgetFormData] = useState({ categoryId: '', value: '', period: 'MENSAL' });
  
  // Loading states
  const [categoryLoading, setCategoryLoading] = useState(false);
  const [tagLoading, setTagLoading] = useState(false);
  const [budgetLoading, setBudgetLoading] = useState(false);
  const [deletingCategoryIds, setDeletingCategoryIds] = useState(new Set());
  const [deletingTagIds, setDeletingTagIds] = useState(new Set());
  const [deletingBudgetIds, setDeletingBudgetIds] = useState(new Set());

  const loadData = useCallback(async () => {
    if (!user) return;
    
    // Cancela a requisição anterior se ainda estiver em andamento
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
    }

    // Cria um novo AbortController para esta requisição
    const abortController = new AbortController();
    abortControllerRef.current = abortController;

    setLoading(true);
    try {
      const [categoriesRes, tagsRes, budgetsRes] = await Promise.all([
        api.get(`/categories?userId=${user.id}`, { signal: abortController.signal }),
        api.get(`/tags?userId=${user.id}`, { signal: abortController.signal }),
        api.get(`/budgets?userId=${user.id}`, { signal: abortController.signal }),
      ]);
      
      // Verifica se a requisição foi cancelada antes de processar a resposta
      if (abortController.signal.aborted) {
        return;
      }
      
      if (categoriesRes.success) setCategories(categoriesRes.data || []);
      if (tagsRes.success) setTags(tagsRes.data || []);
      if (budgetsRes.success) setBudgets(budgetsRes.data || []);
    } catch (error) {
      // Ignora erros de cancelamento
      if (axios.isCancel && axios.isCancel(error)) {
        return;
      }
      if (error.name === 'CanceledError' || error.name === 'AbortError') {
        return;
      }
      
      // Verifica se a requisição foi cancelada antes de processar o erro
      if (abortController.signal.aborted) {
        return;
      }
      
      toast.error('Erro ao carregar dados');
    } finally {
      // Só atualiza o loading se a requisição não foi cancelada
      if (!abortController.signal.aborted) {
        setLoading(false);
      }
      // Limpa a referência se esta ainda é a requisição atual
      if (abortControllerRef.current === abortController) {
        abortControllerRef.current = null;
      }
    }
  }, [user]);

  useEffect(() => {
    if (user) {
      loadData();
    }
    
    // Cleanup: cancela requisições quando o componente é desmontado
    return () => {
      if (abortControllerRef.current) {
        abortControllerRef.current.abort();
        abortControllerRef.current = null;
      }
    };
  }, [loadData]);

  // Helper para encontrar orçamento de uma categoria
  const getBudgetForCategory = (categoryId) => {
    return budgets.find(b => b.idCategoria === categoryId);
  };

  // ========== CATEGORIAS ==========
  const handleCategorySubmit = async (e) => {
    e.preventDefault();
    if (!categoryFormData.nome.trim()) {
      toast.error('Nome da categoria é obrigatório');
      return;
    }

    setCategoryLoading(true);
    try {
      if (editingCategory) {
        const response = await api.put(`/categories/${editingCategory.idCategoria}`, {
          nome: categoryFormData.nome,
          userId: user.id,
        });
        if (response.success) {
          toast.success('Categoria atualizada!');
          loadData();
          handleCloseCategoryModal();
        }
      } else {
        const response = await api.post('/categories', {
          nome: categoryFormData.nome,
          userId: user.id,
          budget: categoryFormData.budget ? parseFloat(categoryFormData.budget) : null
        });
        if (response.success) {
          toast.success('Categoria criada!');
          loadData();
          handleCloseCategoryModal();
        }
      }
    } catch (error) {
      toast.error(error.message || 'Erro ao salvar categoria');
    } finally {
      setCategoryLoading(false);
    }
  };

  const handleEditCategory = (category) => {
    setEditingCategory(category);
    setCategoryFormData({ nome: category.nome, budget: '' });
    setShowCategoryModal(true);
  };

  const handleDeleteCategory = async (id) => {
    if (!confirm('Tem certeza que deseja excluir esta categoria?')) return;
    setDeletingCategoryIds(prev => new Set(prev).add(id));
    try {
      const response = await api.delete(`/categories/${id}?userId=${user.id}`);
      if (response.success) {
        toast.success('Categoria excluída!');
        loadData();
      }
    } catch (error) {
      toast.error(error.message || 'Erro ao excluir categoria');
    } finally {
      setDeletingCategoryIds(prev => {
        const next = new Set(prev);
        next.delete(id);
        return next;
      });
    }
  };

  const handleCloseCategoryModal = () => {
    setShowCategoryModal(false);
    setEditingCategory(null);
    setCategoryFormData({ nome: '', budget: '' });
  };

  // ========== TAGS ==========
  const handleTagSubmit = async (e) => {
    e.preventDefault();
    setTagLoading(true);
    try {
      const data = { nome: tagFormData.nome, cor: tagFormData.cor, userId: user.id };
      if (editingTag) {
        const response = await api.put(`/tags/${editingTag.idTag}`, { ...data, id: editingTag.idTag });
        if (response.success) {
          toast.success('Tag atualizada!');
          loadData();
          handleCloseTagModal();
        }
      } else {
        const response = await api.post('/tags', data);
        if (response.success) {
          toast.success('Tag criada!');
          loadData();
          handleCloseTagModal();
        }
      }
    } catch (error) {
      toast.error(error.message || 'Erro ao salvar tag');
    } finally {
      setTagLoading(false);
    }
  };

  const handleEditTag = (tag) => {
    setEditingTag(tag);
    setTagFormData({ nome: tag.nome, cor: tag.cor });
    setShowTagModal(true);
  };

  const handleDeleteTag = async (id) => {
    if (!confirm('Tem certeza que deseja excluir esta tag?')) return;
    setDeletingTagIds(prev => new Set(prev).add(id));
    try {
      const response = await api.delete(`/tags/${id}`);
      if (response.success) {
        toast.success('Tag excluída!');
        loadData();
      }
    } catch (error) {
      toast.error('Erro ao excluir tag');
    } finally {
      setDeletingTagIds(prev => {
        const next = new Set(prev);
        next.delete(id);
        return next;
      });
    }
  };

  const handleCloseTagModal = () => {
    setShowTagModal(false);
    setEditingTag(null);
    setTagFormData({ nome: '', cor: '#3498db' });
  };

  // ========== ORÇAMENTOS ==========
  const handleBudgetSubmit = async (e) => {
    e.preventDefault();
    setBudgetLoading(true);
    try {
      const data = {
        categoryId: parseInt(budgetFormData.categoryId),
        value: parseFloat(budgetFormData.value),
        period: budgetFormData.period,
        userId: user.id,
      };

      if (editingBudget) {
        const response = await api.put(`/budgets/${editingBudget.idOrcamento}`, data);
        if (response.success) {
          toast.success('Orçamento atualizado!');
          loadData();
          handleCloseBudgetModal();
        }
      } else {
        const response = await api.post('/budgets', data);
        if (response.success) {
          toast.success('Orçamento criado!');
          loadData();
          handleCloseBudgetModal();
        }
      }
    } catch (error) {
      toast.error(error.message || 'Erro ao salvar orçamento');
    } finally {
      setBudgetLoading(false);
    }
  };

  const handleEditBudget = (budget) => {
    setEditingBudget(budget);
    setBudgetFormData({
      categoryId: budget.idCategoria.toString(),
      value: budget.valorPlanejado.toString(),
      period: budget.periodo,
    });
    setShowBudgetModal(true);
  };

  const handleDeleteBudget = async (id) => {
    if (!confirm('Tem certeza que deseja excluir este orçamento?')) return;
    setDeletingBudgetIds(prev => new Set(prev).add(id));
    try {
      const response = await api.delete(`/budgets/${id}?userId=${user.id}`);
      if (response.success) {
        toast.success('Orçamento excluído!');
        loadData();
      }
    } catch (error) {
      toast.error('Erro ao excluir orçamento');
    } finally {
      setDeletingBudgetIds(prev => {
        const next = new Set(prev);
        next.delete(id);
        return next;
      });
    }
  };

  const handleCloseBudgetModal = () => {
    setShowBudgetModal(false);
    setEditingBudget(null);
    setBudgetFormData({ categoryId: '', value: '', period: 'MENSAL' });
  };

  if (loading) {
    return <SkeletonSection type="categories" />;
  }

  return (
    <div className="space-y-8 animate-fade-in">
      {/* ========== SEÇÃO DE CATEGORIAS ========== */}
      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <div>
            <div className="flex items-center gap-3 mb-2">
              <Tag className="w-6 h-6 text-primary-600 dark:text-primary-400" />
              <h2 className="text-3xl font-bold text-gray-900 dark:text-white">
                Categorias
              </h2>
            </div>
            <div className="flex items-start gap-2 text-sm text-gray-600 dark:text-gray-400">
              <Info className="w-4 h-4 mt-0.5 flex-shrink-0" />
              <p>
                Organize suas transações por categorias. Cada categoria pode ter um orçamento mensal para controlar seus gastos.
              </p>
            </div>
          </div>
          <button onClick={() => setShowCategoryModal(true)} className="btn-primary">
            <Plus className="w-4 h-4" />
            Nova Categoria
          </button>
        </div>

        {/* Divisória visual */}
        <div className="border-t border-gray-200 dark:border-gray-700 my-6"></div>

        {categories.length === 0 ? (
          <div className="card text-center py-12">
            <Tag className="w-16 h-16 text-gray-400 mx-auto mb-4" />
            <p className="text-gray-600 dark:text-gray-400">
              Nenhuma categoria cadastrada
            </p>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {categories.map((category) => {
              const budget = getBudgetForCategory(category.idCategoria);
              const valorUsado = budget?.valorUsado || 0;
              const valorPlanejado = budget?.valorPlanejado || 0;
              const percentage = valorPlanejado > 0 
                ? Math.min((valorUsado / valorPlanejado) * 100, 100) 
                : 0;

              return (
                <div key={category.idCategoria} className="card">
                  <div className="flex items-center justify-between mb-4">
                    <h3 className="text-lg font-semibold text-gray-900 dark:text-white">
                      {category.nome}
                    </h3>
                  </div>

                  {/* Exibe orçamento se existir */}
                  {budget && (
                    <div className="mb-4 p-3 bg-blue-50 dark:bg-blue-900/20 rounded-lg border border-blue-200 dark:border-blue-800">
                      <div className="flex items-center justify-between mb-2">
                        <span className="text-xs font-medium text-blue-800 dark:text-blue-200">
                          Orçamento {budget.periodo}
                        </span>
                        <span className="text-xs font-semibold text-blue-900 dark:text-blue-100">
                          {percentage.toFixed(1)}%
                        </span>
                      </div>
                      <div className="flex justify-between text-sm mb-2">
                        <span className="text-blue-700 dark:text-blue-300">
                          {formatCurrency(valorUsado)} / {formatCurrency(valorPlanejado)}
                        </span>
                      </div>
                      <div className="w-full bg-blue-200 dark:bg-blue-800 rounded-full h-2">
                        <div
                          className={`h-2 rounded-full transition-all ${
                            percentage > 100 ? 'bg-red-500' : percentage > 80 ? 'bg-yellow-500' : 'bg-green-500'
                          }`}
                          style={{ width: `${Math.min(percentage, 100)}%` }}
                        ></div>
                      </div>
                      <div className="flex gap-2 mt-2">
                        <button
                          onClick={() => handleEditBudget(budget)}
                          className="text-xs px-2 py-1 bg-blue-100 dark:bg-blue-800 text-blue-700 dark:text-blue-300 rounded hover:bg-blue-200 dark:hover:bg-blue-700 transition-colors"
                        >
                          Editar Orçamento
                        </button>
                        <button
                          onClick={() => handleDeleteBudget(budget.idOrcamento)}
                          className="text-xs px-2 py-1 bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-red-300 rounded hover:bg-red-200 dark:hover:bg-red-900/50 transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                          disabled={deletingBudgetIds.has(budget.idOrcamento)}
                        >
                          {deletingBudgetIds.has(budget.idOrcamento) ? (
                            <Spinner size={12} className="text-red-700 dark:text-red-300" />
                          ) : (
                            'Remover'
                          )}
                        </button>
                      </div>
                    </div>
                  )}

                  {/* Botão para adicionar orçamento se não existir */}
                  {!budget && (
                    <div className="mb-4">
                      <button
                        onClick={() => {
                          setBudgetFormData({
                            categoryId: category.idCategoria.toString(),
                            value: '',
                            period: 'MENSAL',
                          });
                          setShowBudgetModal(true);
                        }}
                        className="w-full text-sm px-3 py-2 bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-300 rounded-lg hover:bg-gray-200 dark:hover:bg-gray-600 transition-colors flex items-center justify-center gap-2"
                      >
                        <TrendingUp className="w-4 h-4" />
                        Adicionar Orçamento
                      </button>
                    </div>
                  )}

                  <div className="flex gap-2">
                    <button
                      onClick={() => handleEditCategory(category)}
                      className="btn-secondary flex-1"
                    >
                      <Edit className="w-4 h-4" />
                      Editar
                    </button>
                    <button
                      onClick={() => handleDeleteCategory(category.idCategoria)}
                      className="btn-danger flex-1"
                      disabled={deletingCategoryIds.has(category.idCategoria)}
                    >
                      {deletingCategoryIds.has(category.idCategoria) ? (
                        <Spinner size={16} className="text-white" />
                      ) : (
                        <>
                          <Trash2 className="w-4 h-4" />
                          Excluir
                        </>
                      )}
                    </button>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* Divisória entre seções */}
      <div className="border-t-2 border-gray-300 dark:border-gray-600 my-8"></div>

      {/* ========== SEÇÃO DE TAGS ========== */}
      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <div>
            <div className="flex items-center gap-3 mb-2">
              <Tag className="w-6 h-6 text-purple-600 dark:text-purple-400" />
              <h2 className="text-3xl font-bold text-gray-900 dark:text-white">
                Tags
              </h2>
            </div>
            <div className="flex items-start gap-2 text-sm text-gray-600 dark:text-gray-400">
              <Info className="w-4 h-4 mt-0.5 flex-shrink-0" />
              <p>
                Use tags coloridas para marcar e filtrar suas transações de forma personalizada. Tags ajudam a identificar características específicas como "Urgente", "Pessoal", "Trabalho", etc.
              </p>
            </div>
          </div>
          <button onClick={() => setShowTagModal(true)} className="btn-primary">
            <Plus className="w-4 h-4" />
            Nova Tag
          </button>
        </div>

        {tags.length === 0 ? (
          <div className="card text-center py-12">
            <Tag className="w-16 h-16 text-gray-400 mx-auto mb-4" />
            <p className="text-gray-600 dark:text-gray-400">Nenhuma tag criada</p>
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {tags.map((tag) => (
              <div key={tag.idTag} className="card">
                <div className="flex items-center gap-3 mb-4">
                  <span
                    className="px-4 py-2 rounded-lg text-white font-medium"
                    style={{ backgroundColor: tag.cor }}
                  >
                    <Tag className="w-4 h-4 inline mr-2" />
                    {tag.nome}
                  </span>
                </div>
                <div className="flex gap-2">
                  <button
                    onClick={() => handleEditTag(tag)}
                    className="btn-secondary flex-1"
                  >
                    <Edit className="w-4 h-4" />
                    Editar
                  </button>
                  <button
                    onClick={() => handleDeleteTag(tag.idTag)}
                    className="btn-danger flex-1"
                    disabled={deletingTagIds.has(tag.idTag)}
                  >
                    {deletingTagIds.has(tag.idTag) ? (
                      <Spinner size={16} className="text-white" />
                    ) : (
                      <>
                        <Trash2 className="w-4 h-4" />
                        Excluir
                      </>
                    )}
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* ========== MODAL DE CATEGORIA ========== */}
      <Modal
        isOpen={showCategoryModal}
        onClose={handleCloseCategoryModal}
        title={editingCategory ? 'Editar Categoria' : 'Nova Categoria'}
      >
        <form onSubmit={handleCategorySubmit} className="space-y-4">
          <div>
            <label className="label">Nome da Categoria</label>
            <input
              type="text"
              value={categoryFormData.nome}
              onChange={(e) => setCategoryFormData({ ...categoryFormData, nome: e.target.value })}
              className="input"
              placeholder="Ex: Alimentação, Transporte..."
              required
            />
          </div>

          {!editingCategory && (
            <div>
              <label className="label">Orçamento Mensal (Opcional)</label>
              <div className="relative">
                <span className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-500">
                  R$
                </span>
                <input
                  type="number"
                  step="0.01"
                  min="0"
                  value={categoryFormData.budget}
                  onChange={(e) => setCategoryFormData({ ...categoryFormData, budget: e.target.value })}
                  className="input pl-10"
                  placeholder="0,00"
                />
              </div>
              <p className="text-xs text-gray-500 dark:text-gray-400 mt-1">
                Você pode adicionar ou editar o orçamento depois de criar a categoria
              </p>
            </div>
          )}

          <div className="flex gap-2 justify-end">
            <button
              type="button"
              onClick={handleCloseCategoryModal}
              className="btn-secondary"
            >
              Cancelar
            </button>
            <button type="submit" className="btn-primary" disabled={categoryLoading}>
              {categoryLoading ? (
                <>
                  <Spinner size={16} className="text-white mr-2" />
                  {editingCategory ? 'Atualizando...' : 'Criando...'}
                </>
              ) : (
                editingCategory ? 'Atualizar' : 'Criar'
              )}
            </button>
          </div>
        </form>
      </Modal>

      {/* ========== MODAL DE TAG ========== */}
      <Modal
        isOpen={showTagModal}
        onClose={handleCloseTagModal}
        title={editingTag ? 'Editar Tag' : 'Nova Tag'}
      >
        <form onSubmit={handleTagSubmit} className="space-y-4">
          <div>
            <label className="label">Nome da Tag</label>
            <input
              type="text"
              value={tagFormData.nome}
              onChange={(e) => setTagFormData({ ...tagFormData, nome: e.target.value })}
              className="input"
              placeholder="Ex: Urgente, Pessoal..."
              required
            />
          </div>
          <div>
            <label className="label">Cor</label>
            <div className="flex items-center gap-4">
              <input
                type="color"
                value={tagFormData.cor}
                onChange={(e) => setTagFormData({ ...tagFormData, cor: e.target.value })}
                className="w-20 h-12 rounded-lg cursor-pointer"
              />
              <div
                className="px-4 py-2 rounded-lg text-white font-medium"
                style={{ backgroundColor: tagFormData.cor }}
              >
                Preview
              </div>
            </div>
          </div>
          <div className="flex gap-2 justify-end">
            <button type="button" onClick={handleCloseTagModal} className="btn-secondary">
              Cancelar
            </button>
            <button type="submit" className="btn-primary" disabled={tagLoading}>
              {tagLoading ? (
                <>
                  <Spinner size={16} className="text-white mr-2" />
                  {editingTag ? 'Atualizando...' : 'Criando...'}
                </>
              ) : (
                editingTag ? 'Atualizar' : 'Criar'
              )}
            </button>
          </div>
        </form>
      </Modal>

      {/* ========== MODAL DE ORÇAMENTO ========== */}
      <Modal
        isOpen={showBudgetModal}
        onClose={handleCloseBudgetModal}
        title={editingBudget ? 'Editar Orçamento' : 'Novo Orçamento'}
      >
        <form onSubmit={handleBudgetSubmit} className="space-y-4">
          <div>
            <label className="label">Categoria</label>
            <select
              value={budgetFormData.categoryId}
              onChange={(e) => setBudgetFormData({ ...budgetFormData, categoryId: e.target.value })}
              className="input"
              required
              disabled={!!editingBudget}
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
              min="0"
              value={budgetFormData.value}
              onChange={(e) => setBudgetFormData({ ...budgetFormData, value: e.target.value })}
              className="input"
              required
            />
          </div>
          <div>
            <label className="label">Período</label>
            <select
              value={budgetFormData.period}
              onChange={(e) => setBudgetFormData({ ...budgetFormData, period: e.target.value })}
              className="input"
              required
            >
              <option value="MENSAL">Mensal</option>
              <option value="ANUAL">Anual</option>
            </select>
          </div>
          <div className="flex gap-2 justify-end">
            <button type="button" onClick={handleCloseBudgetModal} className="btn-secondary">
              Cancelar
            </button>
            <button type="submit" className="btn-primary" disabled={budgetLoading}>
              {budgetLoading ? (
                <>
                  <Spinner size={16} className="text-white mr-2" />
                  {editingBudget ? 'Atualizando...' : 'Criando...'}
                </>
              ) : (
                editingBudget ? 'Atualizar' : 'Criar'
              )}
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
}



