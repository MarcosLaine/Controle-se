import React, { useState, useEffect, useRef } from 'react';
import { ArrowUp, ArrowDown, Scale, Landmark, TrendingUp, Info, CreditCard } from 'lucide-react';
import { useAuth } from '../../contexts/AuthContext';
import { useData } from '../../contexts/DataContext';
import { useLanguage } from '../../contexts/LanguageContext';
import api from '../../services/api';
import axios from 'axios';
import { formatCurrency, formatDate } from '../../utils/formatters';
import SummaryCard from '../common/SummaryCard';
import Modal from '../common/Modal';
import SkeletonSection from '../common/SkeletonSection';
import { Doughnut, Bar } from 'react-chartjs-2';
import {
  Chart as ChartJS,
  ArcElement,
  Tooltip,
  Legend,
  CategoryScale,
  LinearScale,
  BarElement,
  Title,
} from 'chart.js';

ChartJS.register(
  ArcElement,
  Tooltip,
  Legend,
  CategoryScale,
  LinearScale,
  BarElement,
  Title
);

export default function Overview() {
  const { user } = useAuth();
  const { fetchData, getCachedData } = useData();
  const { t } = useLanguage();
  const [loading, setLoading] = useState(true);
  const [overviewData, setOverviewData] = useState(null);
  const [recentTransactions, setRecentTransactions] = useState([]);
  const [showSaldoInfo, setShowSaldoInfo] = useState(false);
  const [showPatrimonioInfo, setShowPatrimonioInfo] = useState(false);
  
  // Ref para armazenar o AbortController da requisição atual
  const abortControllerRef = useRef(null);

  useEffect(() => {
    if (!user) return;
    
    const loadData = async () => {
      const cacheKey = `overview-${user.id}`;
      const transactionsKey = `recent-transactions-${user.id}`;
      
      // Verifica cache primeiro
      const cachedOverview = getCachedData(cacheKey);
      const cachedTrans = getCachedData(transactionsKey);
      
      if (cachedOverview && cachedTrans) {
        setOverviewData(cachedOverview);
        setRecentTransactions(cachedTrans);
        setLoading(false);
        return;
      }

      // Cancela a requisição anterior se ainda estiver em andamento
      if (abortControllerRef.current) {
        abortControllerRef.current.abort();
      }

      // Cria um novo AbortController para esta requisição
      const abortController = new AbortController();
      abortControllerRef.current = abortController;

      setLoading(true);
      try {
        const [overviewRes, transactionsRes] = await Promise.all([
          fetchData(
            cacheKey,
            async () => {
              const res = await api.get(`/dashboard/overview?userId=${user.id}`, {
                signal: abortController.signal
              });
              return res.success ? res.data : null;
            }
          ),
          fetchData(
            transactionsKey,
            async () => {
              const res = await api.get(`/transactions/recent?userId=${user.id}`, {
                signal: abortController.signal
              });
              return res.success ? (res.data || []) : [];
            }
          ),
        ]);

        // Verifica se a requisição foi cancelada antes de processar a resposta
        if (abortController.signal.aborted) {
          return;
        }

        if (overviewRes) {
          setOverviewData(overviewRes);
        } else {
          // Se não há dados, define um objeto vazio para evitar tela em branco
          setOverviewData({
            totalIncome: 0,
            totalExpense: 0,
            balance: 0,
            netWorth: 0,
            categoryBreakdown: []
          });
        }
        if (transactionsRes) {
          // Backend já filtra parcelas e limita a 3, mas garantimos aqui também
          const nonInstallmentTransactions = transactionsRes
            .filter(t => !t.idGrupoParcela && !t.installmentGroupId && !t.installment_group_id)
            .slice(0, 3);
          setRecentTransactions(nonInstallmentTransactions);
        } else {
          setRecentTransactions([]);
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
        
        console.error('Erro ao carregar dados:', error);
        // Define valores padrão em caso de erro para evitar tela em branco
        setOverviewData({
          totalIncome: 0,
          totalExpense: 0,
          balance: 0,
          netWorth: 0,
          categoryBreakdown: []
        });
        setRecentTransactions([]);
        // Garante que o loading seja atualizado mesmo em caso de erro
        setLoading(false);
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
    };

    loadData();
    
    // Cleanup: cancela requisições quando o componente é desmontado
    return () => {
      if (abortControllerRef.current) {
        abortControllerRef.current.abort();
        abortControllerRef.current = null;
      }
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user]);

  const categoryChartData = overviewData?.categoryBreakdown
    ? {
        labels: overviewData.categoryBreakdown.map((cat) => cat.name),
        datasets: [
          {
            data: overviewData.categoryBreakdown.map((cat) => cat.value),
            backgroundColor: [
              '#ef4444',
              '#f59e0b',
              '#3b82f6',
              '#8b5cf6',
              '#ec4899',
              '#14b8a6',
              '#f97316',
              '#6366f1',
            ],
            borderWidth: 2,
            borderColor: '#fff',
          },
        ],
      }
    : null;

  // Mostra skeleton enquanto está carregando ou quando não há dados ainda
  if (loading || !overviewData) {
    return <SkeletonSection type="overview" />;
  }

  return (
    <div className="space-y-6 animate-fade-in">
      {/* Header */}
      <div>
        <h2 className="text-3xl font-bold text-gray-900 dark:text-white">
          {t('overview.title')}
        </h2>
        <p className="text-gray-600 dark:text-gray-400 mt-1">
          {t('overview.subtitle')}
        </p>
      </div>

      {/* Summary Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <SummaryCard
          title={t('overview.income')}
          amount={formatCurrency(overviewData?.totalIncome || 0)}
          icon={ArrowUp}
          type="income"
        />
        <SummaryCard
          title={t('overview.expenses')}
          amount={formatCurrency(overviewData?.totalExpense || 0)}
          icon={ArrowDown}
          type="expense"
        />
        <SummaryCard
          title={
            <span className="flex items-center gap-2">
              {t('overview.balance')}
              <Info
                className="w-4 h-4 cursor-pointer opacity-70 hover:opacity-100"
                onClick={(e) => {
                  e.stopPropagation();
                  setShowSaldoInfo(true);
                }}
              />
            </span>
          }
          amount={formatCurrency(overviewData?.balance || 0)}
          icon={Scale}
          type="balance"
        />
        <SummaryCard
          title={
            <span className="flex items-center gap-2">
              {t('overview.patrimony')}
              <Info
                className="w-4 h-4 cursor-pointer opacity-70 hover:opacity-100"
                onClick={(e) => {
                  e.stopPropagation();
                  setShowPatrimonioInfo(true);
                }}
              />
            </span>
          }
          amount={formatCurrency(overviewData?.netWorth || 0)}
          icon={Landmark}
          type="default"
        />
      </div>

      {/* Card da Próxima Fatura */}
      {(overviewData?.valorFaturaAPagar !== undefined && overviewData?.valorFaturaAPagar !== null) || overviewData?.cartoesInfo ? (
        <div className="card border-l-4 border-orange-500 bg-orange-50 dark:bg-orange-900/10">
          <div className="flex items-center justify-between">
            <div className="flex-1">
              <p className="text-sm text-gray-600 dark:text-gray-400 mb-1">
                {t('overview.nextInvoice')}
              </p>
              <p className="text-2xl font-bold text-orange-600 dark:text-orange-400">
                {formatCurrency(overviewData?.valorFaturaAPagar || 0)}
              </p>
              {overviewData?.cartoesInfo && (
                <div className="space-y-1 text-sm mt-2">
                  <div className="flex items-center justify-between">
                    <span className="text-gray-600 dark:text-gray-400">{t('overview.nextClosing')}</span>
                    <span className="font-medium text-gray-900 dark:text-white">
                      {formatDate(overviewData.cartoesInfo.proximoFechamento)}
                    </span>
                  </div>
                  <div className="flex items-center justify-between">
                    <span className="text-gray-600 dark:text-gray-400">{t('overview.nextPayment')}</span>
                    <span className={`font-medium ${
                      overviewData.cartoesInfo.diasAtePagamento <= 7 
                        ? 'text-red-600 dark:text-red-400' 
                        : overviewData.cartoesInfo.diasAtePagamento <= 15
                        ? 'text-yellow-600 dark:text-yellow-400'
                        : 'text-gray-900 dark:text-white'
                    }`}>
                      {formatDate(overviewData.cartoesInfo.proximoPagamento)}
                    </span>
                  </div>
                </div>
              )}
            </div>
            <div className="w-12 h-12 bg-orange-100 dark:bg-orange-900/30 rounded-full flex items-center justify-center ml-4">
              <CreditCard className="w-6 h-6 text-orange-600 dark:text-orange-400" />
            </div>
          </div>
        </div>
      ) : null}

      {/* Charts and Recent Transactions */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Category Chart */}
        <div className="card">
          <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
            {t('overview.byCategory')}
          </h3>
          {categoryChartData ? (
            <div className="h-64">
              <Doughnut
                data={categoryChartData}
                options={{
                  responsive: true,
                  maintainAspectRatio: false,
                  plugins: {
                    legend: {
                      position: 'bottom',
                      labels: {
                        padding: 15,
                        usePointStyle: true,
                        color: '#6b7280',
                      },
                    },
                    tooltip: {
                      callbacks: {
                        label: (context) => {
                          const label = context.label || '';
                          const value = formatCurrency(context.parsed);
                          return `${label}: ${value}`;
                        },
                      },
                    },
                  },
                }}
              />
            </div>
          ) : (
            <p className="text-center text-gray-500 py-8">
              {t('common.noData')}
            </p>
          )}
        </div>

        {/* Recent Transactions */}
        <div className="card">
          <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
            {t('overview.recentTransactions')}
          </h3>
          <div className="space-y-3 max-h-64 overflow-y-auto">
            {recentTransactions.length > 0 ? (
              recentTransactions.map((transaction) => (
                <div
                  key={transaction.id}
                  className="flex items-center justify-between p-3 bg-gray-50 dark:bg-gray-700 rounded-lg"
                >
                  <div className="flex items-center gap-3">
                    <div
                      className={`w-10 h-10 rounded-lg flex items-center justify-center ${
                        transaction.type === 'income'
                          ? 'bg-green-100 dark:bg-green-900/30 text-green-600'
                          : 'bg-red-100 dark:bg-red-900/30 text-red-600'
                      }`}
                    >
                      {transaction.type === 'income' ? (
                        <ArrowUp className="w-5 h-5" />
                      ) : (
                        <ArrowDown className="w-5 h-5" />
                      )}
                    </div>
                    <div>
                      <p className="font-medium text-gray-900 dark:text-white">
                        {transaction.description || t('transactions.noDescription') || 'Sem descrição'}
                      </p>
                      <p className="text-sm text-gray-500 dark:text-gray-400">
                        {formatDate(transaction.date)}
                      </p>
                    </div>
                  </div>
                  <div
                    className={`font-semibold ${
                      transaction.type === 'income'
                        ? 'text-green-600 dark:text-green-400'
                        : 'text-red-600 dark:text-red-400'
                    }`}
                  >
                    {transaction.type === 'income' ? '+' : '-'}
                    {formatCurrency(transaction.value || 0)}
                  </div>
                </div>
              ))
            ) : (
              <p className="text-center text-gray-500 py-8">
                {t('overview.noRecentTransactions') || t('common.noData')}
              </p>
            )}
          </div>
        </div>
      </div>

      {/* Info Modals */}
      <Modal
        isOpen={showSaldoInfo}
        onClose={() => setShowSaldoInfo(false)}
        title={`${t('common.info')}: ${t('overview.balance')}`}
      >
        <div className="space-y-2">
          <p>{t('overview.balanceInfoDetail')}</p>
        </div>
      </Modal>

      <Modal
        isOpen={showPatrimonioInfo}
        onClose={() => setShowPatrimonioInfo(false)}
        title={`${t('common.info')}: ${t('overview.patrimony')}`}
      >
        <div className="space-y-2">
          <p>{t('overview.patrimonyInfoDetail')}</p>
        </div>
      </Modal>
    </div>
  );
}

