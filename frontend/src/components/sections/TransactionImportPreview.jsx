import React, { useState, useEffect } from 'react';
import { Trash2, Check, ArrowLeft } from 'lucide-react';
import { useLanguage } from '../../contexts/LanguageContext';
import { useAuth } from '../../contexts/AuthContext';
import { formatCurrency, formatDate } from '../../utils/formatters';
import Modal from '../common/Modal';
import toast from 'react-hot-toast';
import api from '../../services/api';
import { TransactionModal } from './Transactions';

export default function TransactionImportPreview({
  isOpen,
  onClose,
  transactions: initialTransactions,
  errors,
  onConfirm,
  onBack,
  loading,
}) {
  const { t } = useLanguage();
  const { user } = useAuth();
  const [transactions, setTransactions] = useState(initialTransactions || []);
  const [categories, setCategories] = useState([]);
  const [accounts, setAccounts] = useState([]);
  const [tags, setTags] = useState([]);
  const [editingIndex, setEditingIndex] = useState(null);
  const [editingTransaction, setEditingTransaction] = useState(null);

  // Carrega categorias, contas e tags
  useEffect(() => {
    if (isOpen && user) {
      loadData();
    }
  }, [isOpen, user]);

  const loadData = async () => {
    try {
      const [catRes, accRes, tagRes] = await Promise.all([
        api.get(`/categories?userId=${user.id}`),
        api.get(`/accounts?userId=${user.id}`),
        api.get(`/tags?userId=${user.id}`),
      ]);

      if (catRes.success) setCategories(catRes.data || []);
      if (accRes.success) setAccounts(accRes.data || []);
      if (tagRes.success) setTags(tagRes.data || []);
    } catch (error) {
      console.error('Erro ao carregar dados:', error);
    }
  };

  const handleEdit = (index) => {
    const transaction = transactions[index];
    setEditingIndex(index);
    setEditingTransaction(transaction);
  };

  const handleSaveEdit = (updatedTransaction) => {
    if (editingIndex === null) return;

    const updated = [...transactions];
    updated[editingIndex] = updatedTransaction;
    setTransactions(updated);
    setEditingIndex(null);
    setEditingTransaction(null);
    toast.success(t('import.editSaved') || 'Alterações salvas');
  };

  const handleCancelEdit = () => {
    setEditingIndex(null);
    setEditingTransaction(null);
  };

  const handleDelete = (index) => {
    if (!confirm(t('import.deleteConfirm') || 'Deseja realmente excluir esta transação?')) {
      return;
    }
    const updated = transactions.filter((_, i) => i !== index);
    setTransactions(updated);
    toast.success(t('import.deleted') || 'Transação excluída');
  };

  const handleConfirm = () => {
    if (transactions.length === 0) {
      toast.error(t('import.noTransactions') || 'Nenhuma transação para importar');
      return;
    }
    onConfirm(transactions);
  };

  const getTypeLabel = (type) => {
    if (type === 'gasto') {
      const label = t('transactions.expense');
      return (label && label !== 'transactions.expense') ? label : 'Gasto';
    } else {
      const label = t('transactions.income');
      return (label && label !== 'transactions.income') ? label : 'Receita';
    }
  };

  const getTypeColor = (type) => {
    return type === 'gasto'
      ? 'bg-red-100 dark:bg-red-900/30 text-red-600 dark:text-red-400'
      : 'bg-green-100 dark:bg-green-900/30 text-green-600 dark:text-green-400';
  };

  // Converte transação do formato de importação para o formato do modal
  const convertTransactionForModal = (transaction) => {
    // Trata observações corretamente
    let observacoesStr = '';
    if (transaction.observacoes) {
      if (Array.isArray(transaction.observacoes)) {
        observacoesStr = transaction.observacoes.filter(obs => obs && obs.trim()).join('; ');
      } else if (typeof transaction.observacoes === 'string') {
        observacoesStr = transaction.observacoes;
      } else {
        // Se for um objeto estranho (como array Java), ignora
        observacoesStr = '';
      }
    }
    
    return {
      description: transaction.description || '',
      value: transaction.value?.toString() || '',
      date: transaction.date || new Date().toISOString().split('T')[0],
      accountId: transaction.accountId?.toString() || '',
      categoryIds: (transaction.categoryIds || []).map(id => id.toString()),
      tagIds: (transaction.tagIds || []).map(id => id.toString()),
      observacoes: observacoesStr,
      pagamentoFatura: transaction.pagamentoFatura || false,
      isParcelado: transaction.isParcelado || (transaction.numeroParcelas && transaction.numeroParcelas > 1),
      numeroParcelas: transaction.numeroParcelas?.toString() || '',
      intervaloDias: transaction.intervaloDias || 30,
      contaOrigemId: transaction.contaOrigemId?.toString() || '',
      compraRetida: transaction.compraRetida || !!transaction.dataEntradaFatura,
      dataEntradaFatura: transaction.dataEntradaFatura || '',
      frequency: transaction.frequency || 'UNICA',
    };
  };

  // Converte transação do formato do modal para o formato de importação
  const convertTransactionFromModal = (formData, originalTransaction) => {
    // Trata observações corretamente
    let observacoesArray = [];
    if (formData.observacoes && formData.observacoes.trim()) {
      observacoesArray = formData.observacoes.split(';').map(o => o.trim()).filter(o => o);
    }
    
    return {
      ...originalTransaction,
      description: formData.description,
      value: parseFloat(formData.value) || 0,
      date: formData.date,
      accountId: parseInt(formData.accountId) || 0,
      categoryIds: (formData.categoryIds || []).map(id => parseInt(id)),
      tagIds: (formData.tagIds || []).map(id => parseInt(id)),
      observacoes: observacoesArray,
      pagamentoFatura: formData.pagamentoFatura || false,
      isParcelado: formData.isParcelado || false,
      numeroParcelas: formData.isParcelado ? parseInt(formData.numeroParcelas) : undefined,
      intervaloDias: formData.isParcelado ? parseInt(formData.intervaloDias) : 30,
      contaOrigemId: formData.contaOrigemId ? parseInt(formData.contaOrigemId) : undefined,
      compraRetida: formData.compraRetida || false,
      dataEntradaFatura: formData.compraRetida && formData.dataEntradaFatura ? formData.dataEntradaFatura : undefined,
      frequency: formData.frequency || 'UNICA',
    };
  };

  if (!isOpen) return null;

  return (
    <>
      <Modal
        isOpen={isOpen}
        onClose={onClose}
        title={t('import.previewTitle') || 'Revisar Transações Importadas'}
        size="xl"
      >
        <div className="space-y-4">
          {/* Errors */}
          {errors && errors.length > 0 && (
            <div className="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg p-4">
              <h4 className="font-semibold text-red-900 dark:text-red-100 mb-2">
                {t('import.errors') || 'Erros encontrados:'}
              </h4>
              <ul className="text-sm text-red-800 dark:text-red-200 space-y-1 list-disc list-inside">
                {errors.map((error, idx) => (
                  <li key={idx}>{error}</li>
                ))}
              </ul>
            </div>
          )}

          {/* Summary */}
          <div className="bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-lg p-4">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm text-blue-800 dark:text-blue-200">
                  {t('import.totalTransactions') || 'Total de transações:'} <strong>{transactions.length}</strong>
                </p>
                <p className="text-xs text-blue-700 dark:text-blue-300 mt-1">
                  {t('import.editInstructions') || 'Você pode editar ou excluir transações antes de confirmar'}
                </p>
              </div>
              <button
                onClick={onBack}
                className="btn-secondary flex items-center gap-2 text-sm"
              >
                <ArrowLeft className="w-4 h-4" />
                {t('import.back') || 'Voltar'}
              </button>
            </div>
          </div>

          {/* Transactions List */}
          <div className="space-y-3 max-h-96 overflow-y-auto">
            {transactions.length === 0 ? (
              <div className="text-center py-8 text-gray-500 dark:text-gray-400">
                {t('import.noTransactions') || 'Nenhuma transação para importar'}
              </div>
            ) : (
              transactions.map((transaction, index) => (
                <div
                  key={index}
                  className="card border-l-4 border-l-primary-500"
                >
                  <div className="flex items-start justify-between">
                    <div className="flex-1">
                      <div className="flex items-center gap-2 mb-2">
                        <span className={`px-2 py-1 rounded-full text-xs font-medium ${getTypeColor(transaction.type)}`}>
                          {getTypeLabel(transaction.type)}
                        </span>
                        {transaction.isParcelado && transaction.numeroParcelas && (
                          <span className="px-2 py-1 rounded-full text-xs font-medium bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300">
                            {t('transactions.installmentPurchase') || 'Parcelado'} ({transaction.numeroParcelas}x)
                          </span>
                        )}
                        {transaction.frequency && transaction.frequency !== 'UNICA' && (
                          <span className="px-2 py-1 rounded-full text-xs font-medium bg-purple-100 dark:bg-purple-900/30 text-purple-700 dark:text-purple-300">
                            {transaction.frequency}
                          </span>
                        )}
                      </div>
                      <h4 className="font-semibold text-gray-900 dark:text-white">
                        {transaction.description}
                      </h4>
                      <div className="text-sm text-gray-600 dark:text-gray-400 mt-1 space-y-1">
                        <p>
                          {t('transactions.date') || 'Data'}: {formatDate(transaction.date)}
                        </p>
                        {transaction.categoryIds && transaction.categoryIds.length > 0 && (
                          <p>
                            {t('transactions.categories') || 'Categorias'}: {transaction.categoryIds.length}
                          </p>
                        )}
                        {transaction.tagIds && transaction.tagIds.length > 0 && (
                          <p>
                            {t('transactions.tags') || 'Tags'}: {transaction.tagIds.length}
                          </p>
                        )}
                        {transaction.dataEntradaFatura && (
                          <p className="text-xs text-blue-600 dark:text-blue-400">
                            {t('transactions.invoiceEntryDate') || 'Data entrada fatura'}: {formatDate(transaction.dataEntradaFatura)}
                          </p>
                        )}
                      </div>
                    </div>
                    <div className="text-right ml-4">
                      <p className={`text-lg font-bold ${
                        transaction.type === 'receita'
                          ? 'text-green-600 dark:text-green-400'
                          : 'text-red-600 dark:text-red-400'
                      }`}>
                        {transaction.type === 'receita' ? '+' : '-'}
                        {formatCurrency(transaction.value || 0)}
                      </p>
                      <div className="flex gap-2 mt-2">
                        <button
                          onClick={() => handleEdit(index)}
                          className="btn-secondary p-1.5"
                          title={t('common.edit') || 'Editar'}
                        >
                          <span className="text-xs">{t('common.edit') || 'Editar'}</span>
                        </button>
                        <button
                          onClick={() => handleDelete(index)}
                          className="btn-danger p-1.5"
                          title={t('common.delete') || 'Excluir'}
                        >
                          <Trash2 className="w-4 h-4" />
                        </button>
                      </div>
                    </div>
                  </div>
                </div>
              ))
            )}
          </div>

          {/* Actions */}
          <div className="flex gap-2 justify-end pt-4 border-t border-gray-200 dark:border-gray-700">
            <button
              onClick={onClose}
              className="btn-secondary"
              disabled={loading}
            >
              {t('common.cancel') || 'Cancelar'}
            </button>
            <button
              onClick={handleConfirm}
              className="btn-primary flex items-center gap-2"
              disabled={loading || transactions.length === 0}
            >
              {loading ? (
                <>
                  <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-white"></div>
                  {t('import.importing') || 'Importando...'}
                </>
              ) : (
                <>
                  <Check className="w-4 h-4" />
                  {t('import.confirm') || `Confirmar Importação (${transactions.length})`}
                </>
              )}
            </button>
          </div>
        </div>
      </Modal>

      {/* Edit Modal */}
      {editingTransaction && editingIndex !== null && (
        <EditTransactionModal
          isOpen={editingIndex !== null}
          onClose={handleCancelEdit}
          transaction={editingTransaction}
          type={editingTransaction.type}
          categories={categories}
          accounts={accounts}
          tags={tags}
          onSave={(formData) => {
            const updated = convertTransactionFromModal(formData, editingTransaction);
            handleSaveEdit(updated);
          }}
          user={user}
        />
      )}
    </>
  );
}

// Componente modal de edição que usa o TransactionModal
function EditTransactionModal({ isOpen, onClose, transaction, type, categories, accounts, tags, onSave, user }) {
  // Trata observações corretamente
  let observacoesStr = '';
  if (transaction.observacoes) {
    if (Array.isArray(transaction.observacoes)) {
      observacoesStr = transaction.observacoes.filter(obs => obs && obs.trim && obs.trim()).join('; ');
    } else if (typeof transaction.observacoes === 'string') {
      observacoesStr = transaction.observacoes;
    } else {
      // Se for um objeto estranho (como array Java), ignora
      observacoesStr = '';
    }
  }
  
  // Converte a transação para o formato do formulário
  const initialData = {
    description: transaction.description || '',
    value: transaction.value?.toString() || '',
    date: transaction.date || new Date().toISOString().split('T')[0],
    accountId: transaction.accountId?.toString() || '',
    categoryIds: (transaction.categoryIds || []).map(id => id.toString()),
    tagIds: (transaction.tagIds || []).map(id => id.toString()),
    observacoes: observacoesStr,
    pagamentoFatura: transaction.pagamentoFatura || false,
    isParcelado: transaction.isParcelado || (transaction.numeroParcelas && transaction.numeroParcelas > 1),
    numeroParcelas: transaction.numeroParcelas?.toString() || '',
    intervaloDias: transaction.intervaloDias || 30,
    contaOrigemId: transaction.contaOrigemId?.toString() || '',
    compraRetida: transaction.compraRetida || !!transaction.dataEntradaFatura,
    dataEntradaFatura: transaction.dataEntradaFatura || '',
    frequency: transaction.frequency || 'UNICA',
  };

  // Reutiliza o TransactionModal do componente Transactions
  return (
    <TransactionModal
      isOpen={isOpen}
      onClose={onClose}
      type={type === 'gasto' ? 'expense' : 'income'}
      categories={categories}
      accounts={accounts}
      tags={tags}
      onSuccess={(formData) => {
        onSave(formData);
      }}
      user={user}
      initialData={initialData}
      isEditMode={true}
    />
  );
}
