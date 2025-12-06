import React, { useState, useEffect, useRef, useCallback } from 'react';
import { Plus, Minus, Trash2, ArrowUp, ArrowDown, Filter, CreditCard, Search, Upload } from 'lucide-react';
import { useAuth } from '../../contexts/AuthContext';
import { useData } from '../../contexts/DataContext';
import { useLanguage } from '../../contexts/LanguageContext';
import api from '../../services/api';
import axios from 'axios';
import { formatCurrency, formatDate } from '../../utils/formatters';
import Modal from '../common/Modal';
import toast from 'react-hot-toast';
import SkeletonSection from '../common/SkeletonSection';
import Spinner from '../common/Spinner';
import ImportTransactionsModal from './ImportTransactionsModal';

export default function Transactions() {
  const { user } = useAuth();
  const { invalidateCache } = useData();
  const { t } = useLanguage();
  const [transactions, setTransactions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showExpenseModal, setShowExpenseModal] = useState(false);
  const [showIncomeModal, setShowIncomeModal] = useState(false);
  const [categories, setCategories] = useState([]);
  const [accounts, setAccounts] = useState([]);
  const [tags, setTags] = useState([]);
  const [filters, setFilters] = useState({ category: '', tag: '', dateStart: '', dateEnd: '', type: '', search: '' });
  const [showPayInstallmentModal, setShowPayInstallmentModal] = useState(false);
  const [selectedInstallment, setSelectedInstallment] = useState(null);
  const [hasMore, setHasMore] = useState(false);
  const [offset, setOffset] = useState(0);
  const [loadingMore, setLoadingMore] = useState(false);
  const [showImportModal, setShowImportModal] = useState(false);
  
  // Ref para armazenar o AbortController da requisição atual
  const abortControllerRef = useRef(null);

  // Função para normalizar string (remover acentos e converter para minúsculas)
  const normalizeString = (str) => {
    if (!str) return '';
    return str
      .toLowerCase()
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '');
  };

  const loadData = useCallback(async (resetOffset = true) => {
    if (!user) return;
    
    // Cancela a requisição anterior se ainda estiver em andamento
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
    }

    // Cria um novo AbortController para esta requisição
    const abortController = new AbortController();
    abortControllerRef.current = abortController;

    let currentOffset = 0;
    if (resetOffset) {
      setLoading(true);
      setOffset(0);
      currentOffset = 0;
    } else {
      setLoadingMore(true);
      // Usa o offset atual do estado através de uma função
      setOffset(prevOffset => {
        currentOffset = prevOffset;
        return prevOffset;
      });
    }
    
    try {
      
      // Constrói URL com parâmetros de filtro
      let transactionsUrl = `/transactions?userId=${user.id}&limit=12&offset=${currentOffset}`;
      if (filters.category) {
        transactionsUrl += `&categoryId=${filters.category}`;
      }
      if (filters.dateStart) {
        transactionsUrl += `&dateStart=${filters.dateStart}`;
      }
      if (filters.dateEnd) {
        transactionsUrl += `&dateEnd=${filters.dateEnd}`;
      }
      // Se o filtro for "parceladas" ou "unicas", envia para o backend
      // Se for "expense" ou "income", filtra no frontend
      if (filters.type && (filters.type === 'parceladas' || filters.type === 'unicas')) {
        transactionsUrl += `&type=${filters.type}`;
      }

      const [transRes, catRes, accRes, tagRes] = await Promise.all([
        api.get(transactionsUrl, { signal: abortController.signal }),
        api.get(`/categories?userId=${user.id}`, { signal: abortController.signal }),
        api.get(`/accounts?userId=${user.id}`, { signal: abortController.signal }),
        api.get(`/tags?userId=${user.id}`, { signal: abortController.signal }),
      ]);

      // Verifica se a requisição foi cancelada antes de processar a resposta
      if (abortController.signal.aborted) {
        return;
      }

      if (transRes.success) {
        let filtered = transRes.data || [];
        // Filtro de tag ainda é feito no cliente, pois o backend não suporta filtro por tag diretamente
        if (filters.tag) {
          filtered = filtered.filter((t) =>
            t.tags?.some((tag) => tag.idTag === parseInt(filters.tag))
          );
        }
        // Filtro por tipo de transação (gasto/receita) feito no cliente
        if (filters.type === 'expense') {
          // Mostra apenas gastos (incluindo parcelados e únicos)
          filtered = filtered.filter((t) => t.type === 'expense');
        } else if (filters.type === 'income') {
          // Mostra apenas receitas (incluindo parceladas e únicas)
          filtered = filtered.filter((t) => t.type === 'income');
        }
        // Filtro de busca por palavra na descrição (case-insensitive e sem acentos)
        if (filters.search) {
          const searchNormalized = normalizeString(filters.search);
          filtered = filtered.filter((t) => {
            const descriptionNormalized = normalizeString(t.description || '');
            return descriptionNormalized.includes(searchNormalized);
          });
        }
        // O filtro por tipo (parceladas/únicas) já é feito no backend quando aplicável
        if (resetOffset) {
          setTransactions(filtered);
        } else {
          setTransactions(prev => [...prev, ...filtered]);
        }
        setHasMore(transRes.hasMore || false);
        if (resetOffset) {
          setOffset(12);
        } else {
          setOffset(prev => prev + 12);
        }
      }
      if (catRes.success) setCategories(catRes.data || []);
      if (accRes.success) setAccounts(accRes.data || []);
      if (tagRes.success) setTags(tagRes.data || []);
    } catch (error) {
      // Ignora erros de cancelamento - não atualiza o estado se foi cancelado
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
      
      toast.error(t('transactions.errorLoading'));
    } finally {
      // Só atualiza o loading se a requisição não foi cancelada
      if (!abortController.signal.aborted) {
        setLoading(false);
        setLoadingMore(false);
      }
      // Limpa a referência se esta ainda é a requisição atual
      if (abortControllerRef.current === abortController) {
        abortControllerRef.current = null;
      }
    }
  }, [user, filters]);
  
  // Função separada para carregar mais
  const loadMore = useCallback(async () => {
    if (!user || loadingMore) return;
    
    setLoadingMore(true);
    try {
      const currentOffset = offset;
      let transactionsUrl = `/transactions?userId=${user.id}&limit=12&offset=${currentOffset}`;
      if (filters.category) {
        transactionsUrl += `&categoryId=${filters.category}`;
      }
      if (filters.dateStart) {
        transactionsUrl += `&dateStart=${filters.dateStart}`;
      }
      if (filters.dateEnd) {
        transactionsUrl += `&dateEnd=${filters.dateEnd}`;
      }
      if (filters.type && (filters.type === 'parceladas' || filters.type === 'unicas')) {
        transactionsUrl += `&type=${filters.type}`;
      }

      const transRes = await api.get(transactionsUrl);

      if (transRes.success) {
        let filtered = transRes.data || [];
        if (filters.tag) {
          filtered = filtered.filter((t) =>
            t.tags?.some((tag) => tag.idTag === parseInt(filters.tag))
          );
        }
        if (filters.type === 'expense') {
          filtered = filtered.filter((t) => t.type === 'expense');
        } else if (filters.type === 'income') {
          filtered = filtered.filter((t) => t.type === 'income');
        }
        if (filters.search) {
          const searchNormalized = normalizeString(filters.search);
          filtered = filtered.filter((t) => {
            const descriptionNormalized = normalizeString(t.description || '');
            return descriptionNormalized.includes(searchNormalized);
          });
        }
        setTransactions(prev => [...prev, ...filtered]);
        setHasMore(transRes.hasMore || false);
        setOffset(prev => prev + 12);
      }
    } catch (error) {
      toast.error(t('transactions.errorLoadingMore'));
    } finally {
      setLoadingMore(false);
    }
  }, [user, filters, offset, loadingMore]);

  useEffect(() => {
    if (user) {
      // Reseta offset quando os filtros mudarem
      setOffset(0);
      setHasMore(false);
      loadData(true);
    }
    
    // Cleanup: cancela requisições quando o componente é desmontado ou quando os filtros mudam
    return () => {
      if (abortControllerRef.current) {
        abortControllerRef.current.abort();
        abortControllerRef.current = null;
      }
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user, filters.category, filters.dateStart, filters.dateEnd, filters.type]);

  // Verifica se uma parcela está paga
  const isParcelaPaga = (transaction) => {
    if (!transaction.numeroParcela || !transaction.totalParcelas) {
      return false;
    }
    
    // Se o backend indicou que foi paga
    if (transaction.parcelaPaga === true || transaction.parcelaPaga === 'true') {
      return true;
    }
    
    // Se ativo for false, verifica se foi paga ou excluída
    if (transaction.ativo === false || transaction.ativo === 'false') {
      // Se o backend não indicou, usa lógica de fallback: se data passou, foi paga
      try {
        const parcelaDate = new Date(transaction.date);
        const hoje = new Date();
        hoje.setHours(0, 0, 0, 0);
        parcelaDate.setHours(0, 0, 0, 0);
        
        // Se a data passou, considera como paga (fechamento de fatura)
        if (parcelaDate < hoje) {
          return true;
        }
      } catch (e) {
        console.error('Erro ao verificar data da parcela:', e);
      }
    }
    
    // Se está ativa e a data passou, foi paga por fechamento de fatura
    if (transaction.ativo === true || transaction.ativo === 'true') {
      try {
        const parcelaDate = new Date(transaction.date);
        const hoje = new Date();
        hoje.setHours(0, 0, 0, 0);
        parcelaDate.setHours(0, 0, 0, 0);
        
        if (parcelaDate < hoje) {
          return true;
        }
      } catch (e) {
        console.error('Erro ao verificar data da parcela:', e);
      }
    }
    
    return false;
  };
  
  // Verifica se uma parcela foi excluída (não paga)
  const isParcelaExcluida = (transaction) => {
    if (!transaction.numeroParcela || !transaction.totalParcelas) {
      return false;
    }
    
    // Se está inativa e não foi paga, foi excluída
    if ((transaction.ativo === false || transaction.ativo === 'false') && 
        !isParcelaPaga(transaction)) {
      return true;
    }
    
    return false;
  };

  const [deletingIds, setDeletingIds] = useState(new Set());

  const handleDelete = async (id, type) => {
    if (!confirm(t('common.deleteTransactionConfirm'))) return;

    setDeletingIds(prev => new Set(prev).add(id));
    try {
      const endpoint = type === 'income' ? '/incomes' : '/expenses';
      const response = await api.delete(`${endpoint}?id=${id}`);
      if (response.success) {
        toast.success(t('transactions.deletedSuccess'));
        
        // Invalidate cache for overview and recent transactions
        invalidateCache(`overview-${user.id}-month`);
        invalidateCache(`overview-${user.id}-year`);
        invalidateCache(`overview-${user.id}-all`);
        invalidateCache(`recent-transactions-${user.id}`);
        
        loadData();
      }
    } catch (error) {
      toast.error(t('transactions.errorDeleting'));
    } finally {
      setDeletingIds(prev => {
        const next = new Set(prev);
        next.delete(id);
        return next;
      });
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
            {t('transactions.title')}
          </h2>
          <p className="text-gray-600 dark:text-gray-400 mt-1">
            {t('transactions.subtitle')}
          </p>
        </div>
        <div className="flex gap-2 w-full sm:w-auto">
          <button onClick={() => setShowImportModal(true)} className="flex-1 sm:flex-none btn-secondary justify-center">
            <Upload className="w-4 h-4" />
            {t('import.title') || 'Importar'}
          </button>
          <button onClick={() => setShowExpenseModal(true)} className="flex-1 sm:flex-none btn-secondary justify-center">
            <Minus className="w-4 h-4" />
            {t('transactions.newExpense')}
          </button>
          <button onClick={() => setShowIncomeModal(true)} className="flex-1 sm:flex-none btn-primary justify-center">
            <Plus className="w-4 h-4" />
            {t('transactions.newIncome')}
          </button>
        </div>
      </div>

      {/* Filters */}
      <div className="card">
        <div className="flex items-center gap-2 mb-4">
          <Filter className="w-5 h-5 text-gray-500" />
          <h3 className="font-semibold text-gray-900 dark:text-white">{t('transactions.filters')}</h3>
        </div>
        <div className="mb-4">
          <label className="label text-sm">{t('transactions.searchByDescription')}</label>
          <div className="relative">
            <Search className="absolute left-2 top-1/2 transform -translate-y-1/2 w-4 h-4 text-gray-400" />
            <input
              type="text"
              value={filters.search}
              onChange={(e) => setFilters({ ...filters, search: e.target.value })}
              className="input pl-8 text-sm py-2"
              // placeholder="Ex: Farm, farma, Farmácia..."
            />
          </div>
        </div>
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-5 lg:grid-cols-6 gap-3">
          <div>
            <label className="label text-xs mb-1">{t('transactions.category')}</label>
            <select
              value={filters.category}
              onChange={(e) => setFilters({ ...filters, category: e.target.value })}
              className="input text-sm py-2"
            >
              <option value="">{t('transactions.allCategories')}</option>
              {categories.map((cat) => (
                <option key={cat.idCategoria} value={cat.idCategoria}>
                  {cat.nome}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className="label text-xs mb-1">{t('transactions.tags')}</label>
            <select
              value={filters.tag}
              onChange={(e) => setFilters({ ...filters, tag: e.target.value })}
              className="input text-sm py-2"
            >
              <option value="">{t('transactions.allTags')}</option>
              {tags.map((tag) => (
                <option key={tag.idTag} value={tag.idTag}>
                  {tag.nome}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className="label text-xs mb-1">{t('transactions.startDate')}</label>
            <input
              type="date"
              value={filters.dateStart}
              onChange={(e) => setFilters({ ...filters, dateStart: e.target.value })}
              className="input text-sm py-2"
            />
          </div>
          <div>
            <label className="label text-xs mb-1">{t('transactions.endDate')}</label>
            <input
              type="date"
              value={filters.dateEnd}
              onChange={(e) => setFilters({ ...filters, dateEnd: e.target.value })}
              className="input text-sm py-2"
            />
          </div>
          <div>
            <label className="label text-xs mb-1">{t('transactions.type')}</label>
            <select
              value={filters.type}
              onChange={(e) => setFilters({ ...filters, type: e.target.value })}
              className="input text-sm py-2"
            >
              <option value="">{t('transactions.allTransactions')}</option>
              <option value="expense">{t('transactions.expenses')}</option>
              <option value="income">{t('transactions.incomes')}</option>
              <option value="parceladas">{t('transactions.installmentPurchases')}</option>
              <option value="unicas">{t('transactions.uniqueTransactions')}</option>
            </select>
          </div>
          <div>
            <label className="label text-xs mb-1 invisible">{'\u200B'}</label>
            <button
              onClick={() => setFilters({ category: '', tag: '', dateStart: '', dateEnd: '', type: '', search: '' })}
              className="btn-secondary text-sm py-2 w-full"
            >
              {t('transactions.clearFilters')}
            </button>
          </div>
        </div>
      </div>

      {/* Transactions List */}
      {transactions.length === 0 ? (
        <div className="card text-center py-12">
          <p className="text-gray-600 dark:text-gray-400">
            {t('transactions.noTransactions')}
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
                  <div className="flex items-center gap-2">
                    <p className={`font-semibold truncate ${
                      isParcelaPaga(transaction)
                        ? 'line-through opacity-60 text-gray-600 dark:text-gray-400'
                        : isParcelaExcluida(transaction)
                        ? 'line-through opacity-40 text-gray-400 dark:text-gray-600'
                        : 'text-gray-900 dark:text-white'
                    }`}>
                      {transaction.description}
                    </p>
                    {transaction.numeroParcela && transaction.totalParcelas && (
                      <span className={`px-2 py-0.5 text-xs font-medium rounded-full shrink-0 ${
                        isParcelaPaga(transaction)
                          ? 'bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-400 line-through opacity-60'
                          : isParcelaExcluida(transaction)
                          ? 'bg-gray-200 dark:bg-gray-800 text-gray-500 dark:text-gray-600 line-through opacity-40'
                          : 'bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300'
                      }`}>
                        {transaction.numeroParcela}/{transaction.totalParcelas}
                        {isParcelaPaga(transaction) && ' ✓'}
                        {isParcelaExcluida(transaction) && ' ✕'}
                      </span>
                    )}
                  </div>
                  <p className={`text-sm truncate ${
                    isParcelaPaga(transaction)
                      ? 'text-gray-400 dark:text-gray-500 line-through opacity-60'
                      : isParcelaExcluida(transaction)
                      ? 'text-gray-400 dark:text-gray-600 line-through opacity-40'
                      : 'text-gray-500 dark:text-gray-400'
                  }`}>
                    {formatDate(transaction.date)}
                    {transaction.category && transaction.category !== 'Sem Categoria' && (
                      <> - {transaction.category}</>
                    )}
                  </p>
                  {transaction.dataEntradaFatura && transaction.type === 'expense' && (() => {
                    try {
                      const purchaseDate = new Date(transaction.date);
                      const invoiceDate = new Date(transaction.dataEntradaFatura);
                      const purchaseMonth = purchaseDate.getMonth();
                      const purchaseYear = purchaseDate.getFullYear();
                      const invoiceMonth = invoiceDate.getMonth();
                      const invoiceYear = invoiceDate.getFullYear();
                      
                      // Se a data de entrada na fatura é diferente da data da compra, calcula o mês da fatura
                      if (invoiceDate.getTime() !== purchaseDate.getTime()) {
                        let invoiceMonthToShow = invoiceMonth;
                        let invoiceYearToShow = invoiceYear;
                        
                        // Se tiver informações da conta, calcula o mês da fatura baseado na data de pagamento
                        if (transaction.diaFechamento && transaction.diaPagamento) {
                          const diaFechamento = transaction.diaFechamento;
                          const diaPagamento = transaction.diaPagamento;
                          
                          // Calcula qual é o próximo pagamento a partir da data de entrada na fatura
                          const invoiceDateObj = new Date(invoiceDate);
                          invoiceDateObj.setHours(0, 0, 0, 0);
                          
                          // Primeiro, encontra o fechamento da fatura que a data de entrada pertence
                          // Tenta criar a data de fechamento no mesmo mês da data de entrada
                          let fechamentoEsteMes = new Date(invoiceDateObj.getFullYear(), invoiceDateObj.getMonth(), diaFechamento);
                          fechamentoEsteMes.setHours(0, 0, 0, 0);
                          
                          let fechamentoFatura;
                          if (invoiceDateObj > fechamentoEsteMes) {
                            // A data de entrada é depois do fechamento deste mês, então o fechamento é no próximo mês
                            fechamentoFatura = new Date(invoiceDateObj.getFullYear(), invoiceDateObj.getMonth() + 1, diaFechamento);
                          } else {
                            // A data de entrada é antes ou igual ao fechamento deste mês, então o fechamento é deste mês
                            // (compras efetivadas no dia do fechamento entram na fatura que fecha naquele dia, igual às compras normais)
                            fechamentoFatura = fechamentoEsteMes;
                          }
                          
                          // Agora encontra o pagamento correspondente a esse fechamento
                          // O pagamento é no mesmo mês do fechamento, ou no próximo mês se o fechamento for depois do dia de pagamento
                          let pagamentoFatura = new Date(fechamentoFatura.getFullYear(), fechamentoFatura.getMonth(), diaPagamento);
                          pagamentoFatura.setHours(0, 0, 0, 0);
                          
                          // Se o fechamento é depois ou igual ao pagamento do mesmo mês, o pagamento é no próximo mês
                          if (fechamentoFatura >= pagamentoFatura) {
                            pagamentoFatura = new Date(fechamentoFatura.getFullYear(), fechamentoFatura.getMonth() + 1, diaPagamento);
                          }
                          
                          // A fatura é identificada pela data de pagamento
                          invoiceMonthToShow = pagamentoFatura.getMonth();
                          invoiceYearToShow = pagamentoFatura.getFullYear();
                        }
                        
                        const monthNames = [
                          t('common.january') || 'Janeiro',
                          t('common.february') || 'Fevereiro',
                          t('common.march') || 'Março',
                          t('common.april') || 'Abril',
                          t('common.may') || 'Maio',
                          t('common.june') || 'Junho',
                          t('common.july') || 'Julho',
                          t('common.august') || 'Agosto',
                          t('common.september') || 'Setembro',
                          t('common.october') || 'Outubro',
                          t('common.november') || 'Novembro',
                          t('common.december') || 'Dezembro'
                        ];
                        const monthName = monthNames[invoiceMonthToShow] || `${invoiceMonthToShow + 1}/${invoiceYearToShow}`;
                        return (
                          <p className="text-xs text-blue-600 dark:text-blue-400 mt-1">
                            {t('transactions.enteredInvoiceMonth', { month: `${monthName} ${invoiceYearToShow}` })}
                          </p>
                        );
                      }
                    } catch (e) {
                      // Ignora erros de parsing de data
                      console.error('Erro ao calcular mês da fatura:', e);
                    }
                    return null;
                  })()}
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
                      <p className="text-xs text-gray-500 dark:text-gray-400 font-medium">{t('transactions.notes')}</p>
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
                    isParcelaPaga(transaction)
                      ? 'line-through opacity-60 text-green-600 dark:text-green-500'
                      : isParcelaExcluida(transaction)
                      ? 'line-through opacity-40 text-gray-400 dark:text-gray-600'
                      : transaction.type === 'income'
                      ? 'text-green-600 dark:text-green-400'
                      : 'text-red-600 dark:text-red-400'
                  }`}
                >
                  {transaction.type === 'income' ? '+' : '-'}
                  {formatCurrency(transaction.value || 0)}
                </span>
                <div className="flex items-center gap-2">
                  {transaction.type === 'expense' && transaction.numeroParcela && transaction.totalParcelas && !isParcelaPaga(transaction) && !isParcelaExcluida(transaction) && (
                    <button
                      onClick={(e) => {
                        e.preventDefault();
                        e.stopPropagation();
                        // console.log('Clicou no botão de pagar parcela:', transaction);
                        setSelectedInstallment(transaction);
                        setShowPayInstallmentModal(true);
                      }}
                      className="btn-secondary shrink-0 flex items-center gap-1 px-2 py-1.5 relative z-10"
                      title={t('transactions.registerAdvancePayment')}
                      type="button"
                      style={{ pointerEvents: 'auto' }}
                    >
                      <CreditCard className="w-4 h-4" />
                      <span className="text-xs hidden sm:inline">{t('transactions.register')}</span>
                    </button>
                  )}
                  <button
                    onClick={(e) => {
                      e.preventDefault();
                      e.stopPropagation();
                      handleDelete(transaction.id, transaction.type);
                    }}
                    className="btn-danger shrink-0 relative z-10"
                    disabled={deletingIds.has(transaction.id)}
                    type="button"
                    style={{ pointerEvents: 'auto' }}
                  >
                    {deletingIds.has(transaction.id) ? (
                      <Spinner size={16} className="text-white" />
                    ) : (
                      <Trash2 className="w-4 h-4" />
                    )}
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
      
      {/* Load More Button */}
      {hasMore && transactions.length > 0 && (
        <div className="flex justify-center mt-6">
          <button
            onClick={loadMore}
            disabled={loadingMore}
            className="btn-secondary flex items-center gap-2"
          >
            {loadingMore ? (
              <>
                <Spinner size={16} className="text-gray-600 dark:text-gray-400" />
                {t('common.loading')}
              </>
            ) : (
              <>
                Carregar mais
              </>
            )}
          </button>
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

      {/* Pay Installment Modal */}
      {showPayInstallmentModal && selectedInstallment && (
        <PayInstallmentModal
          isOpen={showPayInstallmentModal}
          onClose={() => {
            setShowPayInstallmentModal(false);
            setSelectedInstallment(null);
          }}
          installment={selectedInstallment}
          accounts={accounts}
          onSuccess={() => {
            loadData();
            invalidateCache(`overview-${user.id}-month`);
            invalidateCache(`overview-${user.id}-year`);
            invalidateCache(`overview-${user.id}-all`);
            invalidateCache(`recent-transactions-${user.id}`);
          }}
          user={user}
        />
      )}

      {/* Import Transactions Modal */}
      <ImportTransactionsModal
        isOpen={showImportModal}
        onClose={() => setShowImportModal(false)}
        onSuccess={() => {
          loadData();
          invalidateCache(`overview-${user.id}-month`);
          invalidateCache(`overview-${user.id}-year`);
          invalidateCache(`overview-${user.id}-all`);
          invalidateCache(`recent-transactions-${user.id}`);
        }}
      />
    </div>
  );
}

export function TransactionModal({ isOpen, onClose, type, categories, accounts, tags, onSuccess, user, initialData, isEditMode }) {
  const { t } = useLanguage();
  const [formData, setFormData] = useState({
    description: '',
    value: '',
    date: new Date().toISOString().split('T')[0],
    accountId: '',
    categoryIds: [],
    tagIds: [],
    observacoes: '',
    pagamentoFatura: false,
    isParcelado: false,
    numeroParcelas: '',
    intervaloDias: 30,
    contaOrigemId: '', // Conta de onde o dinheiro sai para pagar a fatura
    compraRetida: false,
    dataEntradaFatura: '',
  });
  const [invoiceInfo, setInvoiceInfo] = useState(null);
  const [loading, setLoading] = useState(false);
  const [showRetainedPurchaseModal, setShowRetainedPurchaseModal] = useState(false);

  // Carrega dados iniciais se for modo de edição
  useEffect(() => {
    if (isOpen && initialData) {
      setFormData({
        description: initialData.description || '',
        value: initialData.value || '',
        date: initialData.date || new Date().toISOString().split('T')[0],
        accountId: initialData.accountId || '',
        categoryIds: initialData.categoryIds || [],
        tagIds: initialData.tagIds || [],
        observacoes: initialData.observacoes || '',
        pagamentoFatura: initialData.pagamentoFatura || false,
        isParcelado: initialData.isParcelado || false,
        numeroParcelas: initialData.numeroParcelas || '',
        intervaloDias: initialData.intervaloDias || 30,
        contaOrigemId: initialData.contaOrigemId || '',
        compraRetida: initialData.compraRetida || false,
        dataEntradaFatura: initialData.dataEntradaFatura || '',
        frequency: initialData.frequency || 'UNICA',
      });

      // Carrega informações da fatura se necessário
      if (type === 'income' && initialData.accountId) {
        loadInvoiceInfo(initialData.accountId);
      }
    } else if (isOpen && !initialData) {
      // Reset form quando não é edição
      setFormData({
        description: '',
        value: '',
        date: new Date().toISOString().split('T')[0],
        accountId: '',
        categoryIds: [],
        tagIds: [],
        observacoes: '',
        pagamentoFatura: false,
        isParcelado: false,
        numeroParcelas: '',
        intervaloDias: 30,
        contaOrigemId: '',
        compraRetida: false,
        dataEntradaFatura: '',
        frequency: 'UNICA',
      });
      setInvoiceInfo(null);
    }
  }, [isOpen, initialData, type]);

  const loadInvoiceInfo = async (accountId) => {
    if (!user || !accountId) return;
    try {
      const response = await api.get(`/accounts/${accountId}/invoice-info?userId=${user.id}`);
      if (response.success && response.data) {
        setInvoiceInfo({
          valorFatura: response.data.valorFatura || 0,
          valorJaPago: response.data.valorJaPago || 0,
          valorDisponivel: response.data.valorDisponivelPagamento || 0
        });
      }
    } catch (error) {
      console.error('Erro ao carregar informações da fatura:', error);
      setInvoiceInfo(null);
    }
  };

  // Verifica se há contas do tipo cartão de crédito
  const hasCreditCardAccounts = accounts.some(acc => {
    const tipo = acc.tipo?.toLowerCase() || '';
    return tipo.includes('cartao') || tipo.includes('cartão') || 
           tipo.includes('credito') || tipo.includes('crédito') ||
           tipo === 'cartao_credito';
  });

  // Verifica se uma conta é cartão de crédito
  const isCreditCardAccount = (account) => {
    if (!account || !account.tipo) return false;
    const tipo = account.tipo.toLowerCase();
    return tipo.includes('cartao') || tipo.includes('cartão') || 
           tipo.includes('credito') || tipo.includes('crédito') ||
           tipo === 'cartao_credito';
  };

  const formatAccountType = (tipo) => {
    if (!tipo) return '';
    const tipoLower = tipo.toLowerCase();
    if (tipoLower.includes('cartão') || tipoLower.includes('cartao') || tipoLower.includes('cartao_credito') || tipoLower.includes('credito') || tipoLower.includes('crédito')) {
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
    // Retorna o tipo original formatado (primeira letra maiúscula, resto minúscula)
    return tipo.split('_').map(word => 
      word.charAt(0).toUpperCase() + word.slice(1).toLowerCase()
    ).join(' ');
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    
    // Validação: compra parcelada deve ter pelo menos 2 parcelas
    if (type === 'expense' && formData.isParcelado) {
      if (formData.numeroParcelas < 2) {
        toast.error(t('transactions.minInstallmentsError'));
        return;
      }
    }
    
    // Validação no frontend: se for receita em cartão de crédito, verifica valor
    if (type === 'income' && formData.pagamentoFatura && invoiceInfo) {
      const valorInformado = parseFloat(formData.value);
      const valorDisponivel = invoiceInfo.valorDisponivel || 0;
      
      if (valorInformado > valorDisponivel + 0.01) {
        let mensagem = '';
        if (invoiceInfo.valorFatura <= 0) {
          mensagem = t('transactions.noPendingInvoice', { invoiceValue: formatCurrency(invoiceInfo.valorFatura) });
        } else if (invoiceInfo.valorJaPago >= invoiceInfo.valorFatura) {
          mensagem = t('transactions.invoiceFullyPaid', { 
            invoiceAmount: formatCurrency(invoiceInfo.valorFatura), 
            paidAmount: formatCurrency(invoiceInfo.valorJaPago) 
          });
        } else {
          mensagem = t('transactions.valueExceedsAvailableDetailed', { 
            informedValue: formatCurrency(valorInformado), 
            availableValue: formatCurrency(valorDisponivel) 
          });
        }
        toast.error(mensagem);
        return;
      }
    }
    
    // Se for modo de edição (importação), apenas retorna os dados sem salvar
    if (isEditMode) {
      onSuccess(formData);
      onClose();
      return;
    }
    
    setLoading(true);
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
        // Se for pagamento de fatura e tiver conta origem, envia
        if (formData.pagamentoFatura && formData.contaOrigemId) {
          data.contaOrigemId = parseInt(formData.contaOrigemId);
        }
      }

      // Se for gasto parcelado, adiciona informações de parcelas
      if (type === 'expense' && formData.isParcelado) {
        const numParcelas = parseInt(formData.numeroParcelas);
        if (isNaN(numParcelas) || numParcelas < 2) {
          toast.error(t('transactions.minInstallmentsError'));
          return;
        }
        data.numeroParcelas = numParcelas;
        data.intervaloDias = parseInt(formData.intervaloDias);
        // Para parcelas, o valor é o valor total
        // O backend vai dividir automaticamente
      }

      // Se for gasto com compra retida, adiciona data de entrada na fatura
      if (type === 'expense' && formData.compraRetida && formData.dataEntradaFatura) {
        data.dataEntradaFatura = formData.dataEntradaFatura;
      }

      const endpoint = type === 'expense' ? '/expenses' : '/incomes';
      const response = await api.post(endpoint, data);
      if (response.success) {
        toast.success(t(`transactions.${type === 'expense' ? 'expenseAdded' : 'incomeAdded'}`) + '!');
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
          isParcelado: false,
          numeroParcelas: '',
          intervaloDias: 30,
          contaOrigemId: '',
          compraRetida: false,
          dataEntradaFatura: '',
        });
      }
    } catch (error) {
      toast.error(error.message || t('transactions.errorSaving'));
    } finally {
      setLoading(false);
    }
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={isEditMode 
        ? (type === 'expense' ? t('transactions.editExpense') || 'Editar Gasto' : t('transactions.editIncome') || 'Editar Receita')
        : (type === 'expense' ? t('transactions.newExpense') : t('transactions.newIncome'))
      }
    >
      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label className="label">{t('transactions.description')}</label>
          <input
            type="text"
            value={formData.description}
            onChange={(e) => setFormData({ ...formData, description: e.target.value })}
            className="input"
            required
          />
        </div>
        <div>
          <label className="label">
            {formData.isParcelado && type === 'expense' ? t('transactions.totalValue') : t('transactions.value')}
          </label>
          <input
            type="number"
            step="0.01"
            value={formData.value}
            onChange={(e) => setFormData({ ...formData, value: e.target.value })}
            className="input"
            required
          />
          {formData.isParcelado && type === 'expense' && formData.value && formData.numeroParcelas > 1 && (
            <p className="text-sm text-gray-600 dark:text-gray-400 mt-1">
              {t('transactions.valuePerInstallment', { value: formatCurrency(parseFloat(formData.value || 0) / formData.numeroParcelas) })}
            </p>
          )}
        </div>
        <div>
          <label className="label">
            {formData.isParcelado && type === 'expense' ? t('transactions.firstInstallmentDate') : t('transactions.date')}
          </label>
          <input
            type="date"
            value={formData.date}
            onChange={(e) => setFormData({ ...formData, date: e.target.value })}
            className="input"
            required
          />
        </div>
        {type === 'expense' && hasCreditCardAccounts && (
          <div>
            <label className="flex items-center gap-2 cursor-pointer">
              <input
                type="checkbox"
                checked={formData.isParcelado}
                onChange={(e) => {
                  const checked = e.target.checked;
                  // Se marcar compra parcelada e a conta selecionada não for cartão de crédito, limpa a conta
                  if (checked && formData.accountId) {
                    const selectedAccount = accounts.find(acc => acc.idConta.toString() === formData.accountId);
                    if (selectedAccount && !isCreditCardAccount(selectedAccount)) {
                      setFormData({ ...formData, isParcelado: checked, accountId: '' });
                    } else {
                      setFormData({ ...formData, isParcelado: checked });
                    }
                  } else {
                    setFormData({ ...formData, isParcelado: checked });
                  }
                }}
                className="w-4 h-4 text-primary-600 rounded focus:ring-primary-500"
              />
              <span className="label">{t('transactions.installmentPurchase')}</span>
            </label>
            {formData.isParcelado && (
              <div className="mt-3 space-y-3 pl-6 border-l-2 border-primary-200 dark:border-primary-800">
                <div>
                  <label className="label">{t('transactions.numberOfInstallments')}</label>
                  <input
                    type="number"
                    min="0"
                    max="120"
                    value={formData.numeroParcelas}
                    onChange={(e) => {
                      const value = e.target.value;
                      // Permite campo vazio ou qualquer número
                      if (value === '' || value === null || value === undefined) {
                        setFormData({ ...formData, numeroParcelas: '' });
                        return;
                      }
                      const num = parseInt(value);
                      if (isNaN(num)) {
                        setFormData({ ...formData, numeroParcelas: '' });
                        return;
                      }
                      // Limita entre 0 e 120, mas permite qualquer valor digitado
                      const clampedNum = Math.max(0, Math.min(120, num));
                      setFormData({ ...formData, numeroParcelas: clampedNum });
                    }}
                    className="input"
                    required
                  />
                  {formData.numeroParcelas !== '' && formData.numeroParcelas < 2 && (
                    <p className="text-xs text-red-600 dark:text-red-400 mt-1">
                      {t('transactions.minInstallmentsError')}
                    </p>
                  )}
                </div>
                <div>
                  <label className="label">{t('transactions.installmentInterval')}</label>
                  <select
                    value={formData.intervaloDias}
                    onChange={(e) => setFormData({ ...formData, intervaloDias: parseInt(e.target.value) })}
                    className="input"
                  >
                    <option value="7">{t('transactions.weekly')}</option>
                    <option value="15">{t('transactions.biweekly')}</option>
                    <option value="30">{t('transactions.monthly')}</option>
                    <option value="60">{t('transactions.bimonthly')}</option>
                    <option value="90">{t('transactions.quarterly')}</option>
                  </select>
                </div>
                {formData.value && formData.numeroParcelas !== '' && parseInt(formData.numeroParcelas) >= 2 && formData.date && (
                  <div className="bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-lg p-3">
                    <p className="text-sm font-medium text-blue-800 dark:text-blue-200 mb-2">
                      {t('transactions.installmentPreview')}
                    </p>
                    <div className="space-y-1 text-xs text-blue-700 dark:text-blue-300 max-h-32 overflow-y-auto">
                      {Array.from({ length: Math.min(parseInt(formData.numeroParcelas), 10) }, (_, i) => {
                        const parcelaNum = i + 1;
                        const numParcelas = parseInt(formData.numeroParcelas);
                        const valorParcela = parseFloat(formData.value || 0) / numParcelas;
                        return (
                          <div key={parcelaNum} className="flex justify-between">
                            <span>{t('transactions.installment', { num: parcelaNum, total: numParcelas })}</span>
                            <span className="font-semibold">
                              {formatCurrency(valorParcela)}
                            </span>
                          </div>
                        );
                      })}
                      {parseInt(formData.numeroParcelas) > 10 && (
                        <p className="text-blue-600 dark:text-blue-400 italic">
                          {t('transactions.andMore', { count: parseInt(formData.numeroParcelas) - 10 })}
                        </p>
                      )}
                    </div>
                  </div>
                )}
              </div>
            )}
          </div>
        )}
        {type === 'expense' && (
          <div>
            <label className="label">{t('transactions.categories')}</label>
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
          <label className="label">{t('transactions.account')}</label>
          <select
            value={formData.accountId}
            onChange={async (e) => {
              const selectedAccount = accounts.find(acc => acc.idConta.toString() === e.target.value);
              const isCreditCard = selectedAccount?.tipo?.toLowerCase().includes('cartao') || 
                                   selectedAccount?.tipo?.toLowerCase().includes('cartão') ||
                                   selectedAccount?.tipo?.toLowerCase().includes('credito') ||
                                   selectedAccount?.tipo?.toLowerCase().includes('crédito');
              // Se for receita e cartão de crédito, marca checkbox por padrão e carrega informações da fatura
              if (type === 'income' && isCreditCard && user) {
                setFormData({ 
                  ...formData, 
                  accountId: e.target.value,
                  pagamentoFatura: true, // Marca por padrão quando for cartão de crédito
                  contaOrigemId: ''
                });
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
              } else {
                setFormData({ 
                  ...formData, 
                  accountId: e.target.value,
                  pagamentoFatura: false,
                  contaOrigemId: ''
                });
                setInvoiceInfo(null);
              }
            }}
            className="input"
            required
          >
            <option value="">{t('accounts.selectAccount') || t('transactions.account')}</option>
            {accounts
              .filter((acc) => {
                // Se for gasto parcelado, mostra apenas contas de cartão de crédito
                if (type === 'expense' && formData.isParcelado) {
                  return isCreditCardAccount(acc);
                }
                // Caso contrário, filtra apenas investimentos
                return acc.tipo?.toLowerCase() !== 'investimento';
              })
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
                          console.error(t('transactions.errorLoadingInvoice'), error);
                        }
                      }
                    }}
                    className="w-4 h-4 text-primary-600 rounded focus:ring-primary-500"
                    required
                  />
                  <span className="label">{t('transactions.invoicePaymentCheckbox')}</span>
                </label>
                {!formData.pagamentoFatura && (
                  <p className="text-sm text-red-600 dark:text-red-400 mt-1">
                    {t('transactions.invoicePaymentRequired')}
                  </p>
                )}
                {formData.pagamentoFatura && invoiceInfo && (
                  <>
                    <div className="bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-lg p-3 mt-2">
                      <p className="text-sm text-blue-800 dark:text-blue-200 font-medium mb-2">
                        {t('transactions.invoiceInfo')}
                      </p>
                      <div className="text-xs text-blue-700 dark:text-blue-300 space-y-1">
                        <div className="flex justify-between">
                          <span>{t('transactions.invoiceValue')}</span>
                          <span className="font-semibold">{formatCurrency(invoiceInfo.valorFatura || 0)}</span>
                        </div>
                        <div className="flex justify-between">
                          <span>{t('transactions.alreadyPaid')}</span>
                          <span className="font-semibold">{formatCurrency(invoiceInfo.valorJaPago || 0)}</span>
                        </div>
                        <div className="flex justify-between pt-1 border-t border-blue-200 dark:border-blue-700">
                          <span className="font-medium">{t('transactions.availableForPayment')}</span>
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
                          {t('transactions.valueExceedsAvailable')}
                        </p>
                      )}
                    </div>
                    <div className="mt-3">
                      <label className="label">{t('transactions.sourceAccountOptional')}</label>
                      <p className="text-xs text-gray-500 dark:text-gray-400 mb-2">
                        {t('transactions.sourceAccountHelp')}
                      </p>
                      <select
                        value={formData.contaOrigemId}
                        onChange={(e) => setFormData({ ...formData, contaOrigemId: e.target.value })}
                        className="input"
                      >
                        <option value="">{t('transactions.none')}</option>
                        {accounts
                          .filter((acc) => {
                            const tipo = acc.tipo?.toLowerCase() || '';
                            return (
                              acc.idConta.toString() !== formData.accountId && // Não pode ser a mesma conta do cartão
                              (tipo.includes('corrente') || tipo.includes('poupança') || tipo.includes('poupanca') || tipo.includes('dinheiro'))
                            );
                          })
                          .map((acc) => (
                            <option key={acc.idConta} value={acc.idConta}>
                              {acc.nome} {acc.tipo ? `(${formatAccountType(acc.tipo)})` : ''}
                            </option>
                          ))}
                      </select>
                    </div>
                  </>
                )}
              </div>
            );
          }
          return null;
        })()}
        {type === 'expense' && (() => {
          const selectedAccount = accounts.find(acc => acc.idConta.toString() === formData.accountId);
          const isCreditCard = selectedAccount && isCreditCardAccount(selectedAccount);
          
          if (isCreditCard) {
            return (
              <div className="space-y-2">
                <div className="flex items-center justify-between gap-3">
                  <label className="flex items-center gap-2 cursor-pointer">
                    <input
                      type="checkbox"
                      checked={formData.compraRetida}
                      onChange={(e) => {
                        const checked = e.target.checked;
                        setFormData({ 
                          ...formData, 
                          compraRetida: checked,
                          dataEntradaFatura: checked ? formData.dataEntradaFatura : ''
                        });
                      }}
                      className="w-4 h-4 text-primary-600 rounded focus:ring-primary-500 shrink-0"
                    />
                    <span className="label text-sm">{t('transactions.retainedPurchase')}</span>
                  </label>
                  <button
                    type="button"
                    onClick={(e) => {
                      e.preventDefault();
                      setShowRetainedPurchaseModal(true);
                    }}
                    className="text-primary-600 dark:text-primary-400 hover:text-primary-700 dark:hover:text-primary-300 text-sm font-medium underline hover:no-underline transition-all shrink-0"
                    title={t('transactions.retainedPurchaseModalTitle')}
                  >
                    {t('common.help') || 'Ajuda'}
                  </button>
                </div>
                {formData.compraRetida && (
                  <div className="pl-6 space-y-2">
                    <label className="label">{t('transactions.invoiceEntryDate')}</label>
                    <input
                      type="date"
                      value={formData.dataEntradaFatura}
                      onChange={(e) => setFormData({ ...formData, dataEntradaFatura: e.target.value })}
                      className="input"
                      required={formData.compraRetida}
                      min={formData.date}
                    />
                    <p className="text-xs text-gray-600 dark:text-gray-400">
                      {t('transactions.retainedPurchaseHelp')}
                    </p>
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
          <label className="label">{t('transactions.notes')} ({t('common.optional')})</label>
          <textarea
            value={formData.observacoes}
            onChange={(e) => setFormData({ ...formData, observacoes: e.target.value })}
            className="input"
            rows="3"
            placeholder={t('transactions.notesPlaceholder') || 'Digite observações separadas por vírgula ou quebra de linha...'}
          />
        </div>
        <div className="flex gap-2 justify-end">
          <button type="button" onClick={onClose} className="btn-secondary">
            {t('common.cancel')}
          </button>
          <button type="submit" className="btn-primary" disabled={loading}>
            {loading ? (
              <>
                <Spinner size={16} className="text-white mr-2" />
                {t('common.saving')}
              </>
            ) : (
              t('common.save')
            )}
          </button>
        </div>
      </form>
      
      {/* Modal explicativo sobre compras retidas */}
      <Modal
        isOpen={showRetainedPurchaseModal}
        onClose={() => setShowRetainedPurchaseModal(false)}
        title={t('transactions.retainedPurchaseModalTitle')}
      >
        <div className="space-y-4">
          <p className="text-gray-700 dark:text-gray-300">
            {t('transactions.retainedPurchaseModalContent')}
          </p>
          
          <div className="bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-lg p-4">
            <p className="font-semibold text-blue-900 dark:text-blue-100 mb-2">
              {t('transactions.retainedPurchaseExample')}
            </p>
            <p className="text-sm text-blue-800 dark:text-blue-200">
              {t('transactions.retainedPurchaseExampleText')}
            </p>
          </div>
          
          <div className="bg-green-50 dark:bg-green-900/20 border border-green-200 dark:border-green-800 rounded-lg p-4">
            <p className="font-semibold text-green-900 dark:text-green-100 mb-2">
              {t('transactions.retainedPurchaseHowToUse')}
            </p>
            <p className="text-sm text-green-800 dark:text-green-200">
              {t('transactions.retainedPurchaseHowToUseText')}
            </p>
          </div>
          
          <div className="flex justify-end">
            <button
              type="button"
              onClick={() => setShowRetainedPurchaseModal(false)}
              className="btn-primary"
            >
              {t('common.understood') || t('common.ok')}
            </button>
          </div>
        </div>
      </Modal>
    </Modal>
  );
}

function PayInstallmentModal({ isOpen, onClose, installment, accounts, onSuccess, user }) {
  const { t } = useLanguage();
  const [contaOrigemId, setContaOrigemId] = useState('');
  const [loading, setLoading] = useState(false);

  const formatAccountType = (tipo) => {
    if (!tipo) return '';
    const tipoLower = tipo.toLowerCase();
    if (tipoLower.includes('cartão') || tipoLower.includes('cartao') || tipoLower.includes('cartao_credito') || tipoLower.includes('credito') || tipoLower.includes('crédito')) {
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
    return tipo.split('_').map(word => 
      word.charAt(0).toUpperCase() + word.slice(1).toLowerCase()
    ).join(' ');
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    
    setLoading(true);
    try {
      const requestData = {
        expenseId: installment.id,
        userId: user.id
      };
      
      // Só envia contaOrigemId se foi selecionada
      if (contaOrigemId) {
        requestData.contaOrigemId = parseInt(contaOrigemId);
      }
      
      const response = await api.post('/expenses/pay-installment', requestData);

      if (response.success) {
        toast.success(t('transactions.paymentRegisteredSuccess'));
        onSuccess();
        onClose();
        setContaOrigemId('');
      } else {
        toast.error(response.message || t('transactions.errorRegisteringPayment'));
      }
    } catch (error) {
      toast.error(error.message || t('transactions.errorRegisteringPayment'));
    } finally {
      setLoading(false);
    }
  };

  if (!isOpen || !installment) return null;

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={`${t('transactions.registerAdvancePayment')} - ${t('transactions.installmentInfo', { current: installment.numeroParcela, total: installment.totalParcelas })}`}
    >
      <form onSubmit={handleSubmit} className="space-y-4">
        <div className="bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-lg p-4">
          <p className="text-sm font-medium text-blue-800 dark:text-blue-200 mb-2">
            {t('transactions.installmentInfoTitle') || t('transactions.installmentInfo', { current: installment.numeroParcela, total: installment.totalParcelas })}:
          </p>
          <div className="text-xs text-blue-700 dark:text-blue-300 space-y-1">
            <div className="flex justify-between">
              <span>{t('transactions.description')}:</span>
              <span className="font-semibold">{installment.description}</span>
            </div>
            <div className="flex justify-between">
              <span>{t('transactions.value')}:</span>
              <span className="font-semibold">{formatCurrency(installment.value || 0)}</span>
            </div>
            <div className="flex justify-between">
              <span>{t('transactions.date')} {t('common.original') || 'original'}:</span>
              <span className="font-semibold">{formatDate(installment.date)}</span>
            </div>
            <div className="flex justify-between pt-1 border-t border-blue-200 dark:border-blue-700">
              <span className="font-medium">{t('transactions.installmentLabel')}:</span>
              <span className="font-bold">
                {installment.numeroParcela}/{installment.totalParcelas}
              </span>
            </div>
          </div>
        </div>

        <div>
          <label className="label">{t('transactions.sourceAccountOptional')}</label>
          <p className="text-xs text-gray-500 dark:text-gray-400 mb-2">
            {t('transactions.sourceAccountHelp')}
          </p>
          <select
            value={contaOrigemId}
            onChange={(e) => setContaOrigemId(e.target.value)}
            className="input"
          >
            <option value="">{t('accounts.selectAccount') || t('transactions.account')}</option>
            {accounts
              .filter((acc) => {
                const tipo = acc.tipo?.toLowerCase() || '';
                return (
                  (tipo.includes('corrente') || tipo.includes('poupança') || tipo.includes('poupanca') || tipo.includes('dinheiro')) &&
                  acc.tipo?.toLowerCase() !== 'investimento'
                );
              })
              .map((acc) => (
                <option key={acc.idConta} value={acc.idConta}>
                  {acc.nome} {acc.tipo ? `(${formatAccountType(acc.tipo)})` : ''}
                </option>
              ))}
          </select>
        </div>

        <div className="bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-lg p-3">
          <p className="text-xs text-blue-800 dark:text-blue-200 mb-2 flex items-start gap-2">
            <strong>ℹ️ {t('common.info')}:</strong>
            <span>{t('transactions.noRealBankTransaction')}</span>
          </p>
        </div>

        <div className="flex gap-2 justify-end">
          <button type="button" onClick={onClose} className="btn-secondary" disabled={loading}>
            {t('common.cancel')}
          </button>
          <button type="submit" className="btn-primary" disabled={loading}>
            {loading ? t('transactions.registering') : t('transactions.registerPayment')}
          </button>
        </div>
      </form>
    </Modal>
  );
}


