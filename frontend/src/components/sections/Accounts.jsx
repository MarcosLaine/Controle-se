import { Building2, Edit, Plus, Trash2 } from 'lucide-react';
import React, { useEffect, useState, useMemo, useRef, useCallback } from 'react';
import toast from 'react-hot-toast';
import { useAuth } from '../../contexts/AuthContext';
import { useLanguage } from '../../contexts/LanguageContext';
import api from '../../services/api';
import axios from 'axios';
import { formatCurrency, parseFloatBrazilian, formatDate } from '../../utils/formatters';
import Modal from '../common/Modal';
import SkeletonSection from '../common/SkeletonSection';
import Spinner from '../common/Spinner';

export default function Accounts() {
  const { user } = useAuth();
  const { t } = useLanguage();
  const [accounts, setAccounts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showModal, setShowModal] = useState(false);
  const [editingAccount, setEditingAccount] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [deletingIds, setDeletingIds] = useState(new Set());
  const [formData, setFormData] = useState({
    nome: '',
    tipo: 'Corrente',
    saldoInicial: '',
    diaFechamento: '',
    diaPagamento: '',
  });
  
  // Ref para armazenar o AbortController da requisição atual
  const abortControllerRef = useRef(null);

  const formatAccountType = (tipo) => {
    if (!tipo) return '';
    const tipoLower = tipo.toLowerCase();
    if (tipoLower.includes('cartão') || tipoLower.includes('cartao') || tipoLower.includes('cartao_credito')) {
      return t('accounts.creditCard');
    }
    if (tipoLower.includes('investimento')) {
      return t('accounts.investment');
    }
    if (tipoLower.includes('corrente')) {
      return t('accounts.current');
    }
    if (tipoLower.includes('poupança') || tipoLower.includes('poupanca')) {
      return t('accounts.savings');
    }
    if (tipoLower.includes('dinheiro')) {
      return t('accounts.cash');
    }
    // Retorna o tipo original com primeira letra maiúscula
    return tipo.charAt(0).toUpperCase() + tipo.slice(1).toLowerCase();
  };

  const loadAccounts = useCallback(async () => {
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
      const response = await api.get(`/accounts?userId=${user.id}`, {
        signal: abortController.signal
      });
      
      // Verifica se a requisição foi cancelada antes de processar a resposta
      if (abortController.signal.aborted) {
        return;
      }
      
      if (response.success) {
        setAccounts(response.data || []);
      }
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
      
      toast.error(t('accounts.errorLoading'));
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
    loadAccounts();
    
    // Listener para recarregar contas quando um investimento é atualizado
    const handleInvestmentUpdate = () => {
      loadAccounts();
    };
    
    window.addEventListener('investmentUpdated', handleInvestmentUpdate);
    
    return () => {
      window.removeEventListener('investmentUpdated', handleInvestmentUpdate);
      // Cleanup: cancela requisições quando o componente é desmontado
      if (abortControllerRef.current) {
        abortControllerRef.current.abort();
        abortControllerRef.current = null;
      }
    };
  }, [loadAccounts]);



  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!formData.nome.trim()) {
      toast.error(t('accounts.nameRequired'));
      return;
    }

    setSubmitting(true);
    try {
      // Normaliza o tipo: se for "Investimento (Corretora)" ou qualquer variação, salva como "Investimento"
      let tipo = formData.tipo;
      if (tipo && tipo.toLowerCase().includes('investimento')) {
        tipo = 'Investimento';
      }
      // Normaliza cartão de crédito: se contém "cartão" ou "cartao", salva como "Cartão de Crédito"
      if (tipo && (tipo.toLowerCase().includes('cartão') || tipo.toLowerCase().includes('cartao'))) {
        tipo = 'Cartão de Crédito';
      }
      
      const data = {
        nome: formData.nome,
        tipo: tipo,
        saldoInicial: parseFloatBrazilian(formData.saldoInicial) || 0,
        userId: user.id,
      };
      
      // Adiciona campos de cartão de crédito se for cartão
      // Sempre envia os campos, mesmo que vazios, para que o backend possa preservar valores existentes
      if (tipo && (tipo.toLowerCase().includes('cartão') || tipo.toLowerCase().includes('cartao'))) {
        if (formData.diaFechamento && formData.diaFechamento.trim() !== '') {
          data.diaFechamento = parseInt(formData.diaFechamento);
        } else {
          data.diaFechamento = null;
        }
        if (formData.diaPagamento && formData.diaPagamento.trim() !== '') {
          data.diaPagamento = parseInt(formData.diaPagamento);
        } else {
          data.diaPagamento = null;
        }
      }

      setSubmitting(true);
      if (editingAccount) {
        const response = await api.put(`/accounts/${editingAccount.idConta}`, data);
        if (response.success) {
          toast.success(t('accounts.updatedSuccess'));
          loadAccounts();
          handleCloseModal();
        }
      } else {
        const response = await api.post('/accounts', data);
        if (response.success) {
          toast.success(t('accounts.createdSuccess'));
          loadAccounts();
          handleCloseModal();
        }
      }
    } catch (error) {
      toast.error(error.message || t('accounts.errorSaving'));
    } finally {
      setSubmitting(false);
    }
  };

  const handleEdit = (account) => {
    setEditingAccount(account);
    // Normaliza o tipo: se for "INVESTIMENTO (CORRETORA)" ou qualquer variação, mostra como "Investimento"
    let accountType = account.tipo || '';
    if (accountType && accountType.toLowerCase().includes('investimento')) {
      accountType = 'Investimento';
    }
    // Normaliza cartão de crédito: se contém "cartão" ou "cartao", mostra como "Cartão de Crédito"
    if (accountType && (accountType.toLowerCase().includes('cartão') || accountType.toLowerCase().includes('cartao') || accountType.toLowerCase().includes('cartao_credito'))) {
      accountType = 'Cartão de Crédito';
    }
    setFormData({
      nome: account.nome,
      tipo: accountType,
      saldoInicial: account.saldoAtual?.toString() || '',
      diaFechamento: account.diaFechamento?.toString() || '',
      diaPagamento: account.diaPagamento?.toString() || '',
    });
    setShowModal(true);
  };

  const handleDelete = async (id) => {
    if (!confirm(t('accounts.deleteConfirm'))) return;

    setDeletingIds(prev => new Set(prev).add(id));
    try {
      const response = await api.delete(`/accounts/${id}?userId=${user.id}`);
      if (response.success) {
        toast.success(t('accounts.deletedSuccess'));
        loadAccounts();
        // Dispara evento para recarregar investimentos se necessário
        window.dispatchEvent(new CustomEvent('accountDeleted'));
      }
    } catch (error) {
      toast.error(error.message || t('accounts.errorDeleting'));
    } finally {
      setDeletingIds(prev => {
        const next = new Set(prev);
        next.delete(id);
        return next;
      });
    }
  };

  const handleCloseModal = () => {
    setShowModal(false);
    setEditingAccount(null);
    setFormData({ nome: '', tipo: 'Corrente', saldoInicial: '', diaFechamento: '', diaPagamento: '' });
  };

  if (loading) {
    return <SkeletonSection type="accounts" />;
  }

  return (
    <div className="space-y-6 animate-fade-in">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-3xl font-bold text-gray-900 dark:text-white">
            {t('accounts.title')}
          </h2>
          <p className="text-gray-600 dark:text-gray-400 mt-1">
            {t('accounts.subtitle')}
          </p>
        </div>
        <button onClick={() => setShowModal(true)} className="btn-primary">
          <Plus className="w-4 h-4" />
          {t('accounts.newAccount')}
        </button>
      </div>

      {accounts.length === 0 ? (
        <div className="card text-center py-12">
          <Building2 className="w-16 h-16 text-gray-400 mx-auto mb-4" />
          <p className="text-gray-600 dark:text-gray-400">
            {t('accounts.noAccountsRegistered')}
          </p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {accounts.map((account) => {
            const isCartao = account.tipo && (account.tipo.toLowerCase().includes('cartão') || account.tipo.toLowerCase().includes('cartao'));
            
            return (
              <div key={account.idConta} className="card flex flex-col h-full">
                <div className="flex-1">
                  <div className="flex items-center gap-2 mb-2">
                    <h3 className="text-lg font-semibold text-gray-900 dark:text-white">
                      {account.nome}
                    </h3>
                    <span className="text-xs text-gray-500 dark:text-gray-500 font-normal">
                      {formatAccountType(account.tipo)}
                    </span>
                  </div>
                  {isCartao && (
                    <p className="text-xs text-gray-500 dark:text-gray-500 mb-1">
                      {t('accounts.creditAvailable')}
                    </p>
                  )}
                  <p className={`text-2xl font-bold mb-2 ${isCartao ? 'text-orange-600 dark:text-orange-400' : 'text-primary-600 dark:text-primary-400'}`}>
                    {formatCurrency(account.saldoAtual || 0)}
                  </p>
                  {isCartao && (
                    <div className="text-sm text-gray-600 dark:text-gray-400 mb-2 space-y-2">
                      {account.diaFechamento && account.diaPagamento && (
                        <div className="flex items-center gap-2 text-xs">
                          <span className="text-gray-500 dark:text-gray-500">{t('accounts.closingDay', { day: account.diaFechamento })}</span>
                          <span className="text-gray-400">•</span>
                          <span className="text-gray-500 dark:text-gray-500">{t('accounts.paymentDay', { day: account.diaPagamento })}</span>
                        </div>
                      )}
                      {account.faturaInfo && (
                        <div className="mt-2 pt-2 border-t border-gray-200 dark:border-gray-700 space-y-2">
                          <div className="text-xs">
                            <span className="text-gray-500 dark:text-gray-500">{t('accounts.nextClosing')}</span>{' '}
                            <span className="text-gray-700 dark:text-gray-300 font-medium">
                              {formatDate(account.faturaInfo.proximoFechamento)}
                            </span>
                          </div>
                          <div className={`text-xs ${
                            account.faturaInfo.diasAtePagamento <= 7 
                              ? 'text-red-600 dark:text-red-400' 
                              : account.faturaInfo.diasAtePagamento <= 15
                              ? 'text-yellow-600 dark:text-yellow-400'
                              : 'text-gray-700 dark:text-gray-300'
                          }`}>
                            <span className="text-gray-500 dark:text-gray-500">{t('accounts.nextPayment')}</span>{' '}
                            <span className="font-medium">
                              {formatDate(account.faturaInfo.proximoPagamento)}
                            </span>
                          </div>
                          {account.faturaInfo.faturaAberta && (
                            <div className="flex items-center gap-1 text-green-600 dark:text-green-400 font-medium text-xs">
                              <span>✓</span>
                              <span>{t('accounts.openInvoice')}</span>
                            </div>
                          )}
                        </div>
                      )}
                    </div>
                  )}
                </div>
                <div className="flex gap-2 mt-auto pt-4">
                  <button
                    onClick={() => handleEdit(account)}
                    className="btn-secondary flex-1"
                  >
                    <Edit className="w-4 h-4" />
                    {t('common.edit')}
                  </button>
                <button
                  onClick={() => handleDelete(account.idConta)}
                  className="btn-danger flex-1"
                  disabled={deletingIds.has(account.idConta)}
                >
                  {deletingIds.has(account.idConta) ? (
                    <Spinner size={16} className="text-white" />
                  ) : (
                    <>
                      <Trash2 className="w-4 h-4" />
                      {t('common.delete')}
                    </>
                  )}
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
        title={editingAccount ? t('accounts.editAccount') : t('accounts.addAccount')}
      >
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="label">{t('accounts.accountName')}</label>
            <input
              type="text"
              value={formData.nome}
              onChange={(e) => setFormData({ ...formData, nome: e.target.value })}
              className="input"
              placeholder="Ex: Itaú, Caixa, Binance, Avenue..."
              required
            />
          </div>
          <div>
            <label className="label">{t('accounts.accountType')}</label>
            <select
              value={formData.tipo}
              onChange={(e) => setFormData({ ...formData, tipo: e.target.value })}
              className="input"
            >
              <option value="Corrente">{t('accounts.current')}</option>
              <option value="Poupança">{t('accounts.savings')}</option>
              <option value="Investimento">{t('accounts.investmentBrokerage')}</option>
              <option value="Dinheiro">{t('accounts.cash')}</option>
              <option value="Cartão de Crédito">{t('accounts.creditCard')}</option>
            </select>
          </div>
          <div>
            <label className="label">{t('accounts.initialBalance')}</label>
            <input
              type="text"
              value={formData.saldoInicial}
              onChange={(e) => {
                // Permite apenas números, vírgula e ponto
                const value = e.target.value.replace(/[^\d,.-]/g, '');
                // Permite apenas uma vírgula ou um ponto como separador decimal
                const parts = value.split(/[,.]/);
                if (parts.length <= 2) {
                  setFormData({ ...formData, saldoInicial: value });
                }
              }}
              className="input"
              placeholder="0,00"
            />
            {(formData.tipo && (formData.tipo.toLowerCase().includes('cartão') || formData.tipo.toLowerCase().includes('cartao'))) && (
              <p className="text-xs text-gray-500 dark:text-gray-400 mt-1">
                {t('accounts.creditLimitInfo')}
              </p>
            )}
          </div>
          {(formData.tipo && (formData.tipo.toLowerCase().includes('cartão') || formData.tipo.toLowerCase().includes('cartao'))) && (
            <>
              <div>
                <label className="label">{t('accounts.closingDayLabel')}</label>
                <input
                  type="number"
                  min="1"
                  max="31"
                  value={formData.diaFechamento}
                  onChange={(e) => setFormData({ ...formData, diaFechamento: e.target.value })}
                  className="input"
                  placeholder="Ex: 15"
                />
              </div>
              <div>
                <label className="label">{t('accounts.paymentDayLabel')}</label>
                <input
                  type="number"
                  min="1"
                  max="31"
                  value={formData.diaPagamento}
                  onChange={(e) => setFormData({ ...formData, diaPagamento: e.target.value })}
                  className="input"
                  placeholder="Ex: 20"
                />
              </div>
            </>
          )}
          <div className="flex gap-2 justify-end">
            <button
              type="button"
              onClick={handleCloseModal}
              className="btn-secondary"
            >
              {t('common.cancel')}
            </button>
            <button type="submit" className="btn-primary" disabled={submitting}>
              {submitting ? (
                <>
                  <Spinner size={16} className="text-white mr-2" />
                  {editingAccount ? t('accounts.updating') : t('accounts.creating')}
                </>
              ) : (
                editingAccount ? t('accounts.update') : t('accounts.create')
              )}
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
}

