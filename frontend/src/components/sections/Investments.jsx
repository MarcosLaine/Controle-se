import axios from 'axios';
import {
  ArcElement,
  CategoryScale,
  Chart as ChartJS,
  Filler,
  Legend,
  LinearScale,
  LineElement,
  PointElement,
  Title,
  Tooltip
} from 'chart.js';
import {
  eachDayOfInterval,
  format,
  parseISO, startOfDay,
  startOfYear,
  subDays,
  subMonths,
  subWeeks,
  subYears
} from 'date-fns';
import { ptBR } from 'date-fns/locale';
import jsPDF from 'jspdf';
import autoTable from 'jspdf-autotable';
import { ChevronDown, ChevronUp, Download, File, FileSpreadsheet, FileText, Info, PieChart, Plus, TrendingUp, Wallet } from 'lucide-react';
import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Doughnut, Line } from 'react-chartjs-2';
import toast from 'react-hot-toast';
import Spinner from '../common/Spinner';
import * as XLSX from 'xlsx';
import { useAuth } from '../../contexts/AuthContext';
import { useData } from '../../contexts/DataContext';
import { useLanguage } from '../../contexts/LanguageContext';
import api from '../../services/api';
import { formatCurrency, formatDate, parseFloatBrazilian } from '../../utils/formatters';
import Modal from '../common/Modal';
import SkeletonSection from '../common/SkeletonSection';
import SummaryCard from '../common/SummaryCard';
import InvestmentModal from './InvestmentModal';
import AssetDetailsModal from './Investments/AssetDetailsModal';
import InvestmentStatementModal from './Investments/InvestmentStatementModal';

ChartJS.register(
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend,
  ArcElement,
  Filler
);

const getTradeDate = (trade) => {
  const raw = trade.dataAporte || trade.data || trade.date;
  return raw ? startOfDay(parseISO(raw)) : startOfDay(new Date());
};

const getTradePrice = (trade) => {
  if (trade.precoAtual && trade.precoAtual > 0) return trade.precoAtual;
  if (trade.valorAtual && trade.quantidade) {
    const derived = trade.valorAtual / Math.abs(trade.quantidade);
    if (derived > 0) return derived;
  }
  if (trade.precoAporte && trade.precoAporte > 0) return trade.precoAporte;
  return null;
};

const computeHoldingStats = (transactions = []) => {
  if (!transactions.length) {
    return {
      realizedProfit: 0,
      remainingQty: 0,
      remainingCost: 0,
      investedCapital: 0,
      realizedCostBasis: 0,
    };
  }

  const sorted = [...transactions].sort((a, b) => {
    const dateA = new Date(a.dataAporte || a.data || 0).getTime();
    const dateB = new Date(b.dataAporte || b.data || 0).getTime();
    return (isNaN(dateA) ? 0 : dateA) - (isNaN(dateB) ? 0 : dateB);
  });

  const buyLayers = [];
  let realizedProfit = 0;
  let realizedCostBasis = 0;

  sorted.forEach((trade) => {
    const qty = trade.quantidade || 0;
    if (qty === 0) {
      return;
    }

    if (qty > 0) {
      // Para compras, usa valor convertido para BRL se disponível, senão usa original
      // Isso garante que todos os valores sejam comparáveis na mesma moeda
      // Nota: valorAporte já inclui a corretagem, então não precisa somar novamente
      const valorAporte = trade.valorAporteBRL || trade.valorAporte || 0;
      const unitCost = qty !== 0 ? Math.abs(valorAporte) / Math.abs(qty) : 0;
      buyLayers.push({ qty, unitCost });
    } else {
      let remaining = Math.abs(qty);
      let costBasis = 0;
      let matchedQty = 0;

      while (remaining > 0 && buyLayers.length > 0) {
        const layer = buyLayers[0];
        const qtyToUse = Math.min(remaining, layer.qty);
        costBasis += qtyToUse * layer.unitCost;
        matchedQty += qtyToUse;
        layer.qty -= qtyToUse;
        remaining -= qtyToUse;
        if (layer.qty === 0) {
          buyLayers.shift();
        }
      }

      if (matchedQty > 0) {
        // Para vendas, também usa valores convertidos para BRL
        const valorAporte = trade.valorAporteBRL || trade.valorAporte || 0;
        const corretagem = trade.corretagemBRL || trade.corretagem || 0;
        const grossRevenue = Math.abs(valorAporte);
        const revenuePortion = grossRevenue * (matchedQty / Math.abs(qty));
        const netRevenuePortion = revenuePortion - corretagem;
        realizedProfit += netRevenuePortion - costBasis;
        realizedCostBasis += costBasis;
      }
    }
  });

  const remainingQty = buyLayers.reduce((sum, layer) => sum + layer.qty, 0);
  const remainingCost = buyLayers.reduce((sum, layer) => sum + (layer.qty * layer.unitCost), 0);
  const investedCapital = remainingCost + realizedCostBasis;

  return {
    realizedProfit,
    remainingQty,
    remainingCost,
    investedCapital,
    realizedCostBasis,
  };
};

// Função auxiliar para gerar intervalos de 2 horas
const eachTwoHoursOfInterval = (interval) => {
  const { start, end } = interval;
  const dates = [];
  let current = new Date(start);
  const endTime = new Date(end);
  
  while (current <= endTime) {
    dates.push(new Date(current));
    current = new Date(current.getTime() + 2 * 60 * 60 * 1000); // Adiciona 2 horas
  }
  
  return dates;
};

const buildEvolutionSeries = (transactions, startDate, endDate, t) => {
  if (!transactions.length) return null;

  const sorted = [...transactions].sort((a, b) => getTradeDate(a) - getTradeDate(b));
  const msDiff = Math.abs(endDate.getTime() - startDate.getTime());
  const isTwoHourSteps = msDiff <= 24 * 60 * 60 * 1000;
  const interval = isTwoHourSteps
    ? eachTwoHoursOfInterval({ start: startDate, end: endDate })
    : eachDayOfInterval({ start: startDate, end: endDate });
  const assetState = new Map();
  const labels = [];
  const investedPoints = [];
  const currentPoints = [];
  let txIndex = 0;

  // Cria um mapa de preços atuais por ativo para o último ponto (hoje)
  const today = startOfDay(new Date());
  const currentPricesMap = new Map();
  transactions.forEach(trade => {
    const key = `${trade.categoria || 'OUTROS'}_${trade.nome}`;
    // Usa o preço atual mais recente disponível
    if (trade.precoAtual && trade.precoAtual > 0) {
      const existing = currentPricesMap.get(key);
      if (!existing || !existing.precoAtual || existing.precoAtual <= 0) {
        currentPricesMap.set(key, trade);
      }
    }
  });

  interval.forEach((date, dateIndex) => {
    const isLastPoint = dateIndex === interval.length - 1;
    const isToday = startOfDay(date).getTime() === today.getTime();
    
    while (
      txIndex < sorted.length &&
      getTradeDate(sorted[txIndex]) <= date
    ) {
      const trade = sorted[txIndex];
      const key = `${trade.categoria || 'OUTROS'}_${trade.nome}`;
      if (!assetState.has(key)) {
        assetState.set(key, {
          layers: [],
          currentPrice: getTradePrice(trade),
        });
      }
      const state = assetState.get(key);
      const qty = trade.quantidade || 0;
      const amount = trade.valorAporte || 0;
      const brokerage = trade.corretagem || 0;
      const unitCost = qty !== 0 ? Math.abs(amount) / Math.abs(qty) : 0;
      const price = getTradePrice(trade);
      if (price) {
        state.currentPrice = price;
      }

      if (qty > 0) {
        state.layers.push({ qty, unitCost });
      } else if (qty < 0) {
        let remaining = Math.abs(qty);
        while (remaining > 0 && state.layers.length > 0) {
          const layer = state.layers[0];
          const qtyToUse = Math.min(remaining, layer.qty);
          layer.qty -= qtyToUse;
          remaining -= qtyToUse;
          if (layer.qty === 0) {
            state.layers.shift();
          }
        }
      }

      if (state.layers.length === 0) {
        assetState.delete(key);
      }

      txIndex++;
    }

    // Para o último ponto (hoje), atualiza preços atuais de todos os ativos
    if (isLastPoint || isToday) {
      assetState.forEach((state, key) => {
        const currentTrade = currentPricesMap.get(key);
        if (currentTrade && currentTrade.precoAtual && currentTrade.precoAtual > 0) {
          state.currentPrice = currentTrade.precoAtual;
        }
      });
    }

    let totalInvested = 0;
    let totalCurrent = 0;
    assetState.forEach((state) => {
      const quantity = state.layers.reduce((sum, layer) => sum + layer.qty, 0);
      if (quantity <= 0) return;
      const costBasis = state.layers.reduce((sum, layer) => sum + layer.qty * layer.unitCost, 0);
      totalInvested += costBasis;
      // Para o último ponto, prioriza sempre o preço atual se disponível
      const price = (isLastPoint || isToday) && state.currentPrice 
        ? state.currentPrice 
        : (state.currentPrice || (costBasis > 0 ? costBasis / quantity : 0));
      totalCurrent += quantity * price;
    });

    labels.push(format(date, isTwoHourSteps ? 'HH:mm' : 'dd/MM', { locale: ptBR }));
    investedPoints.push(totalInvested);
    currentPoints.push(totalCurrent);
  });

  return {
    labels,
    datasets: [
      {
        label: t('investments.patrimonialValue'),
        data: currentPoints,
        borderColor: '#3b82f6',
        backgroundColor: 'rgba(59, 130, 246, 0.1)',
        fill: true,
        tension: 0.4,
        pointRadius: 0,
        pointHoverRadius: 4,
      },
      {
        label: t('investments.investedValue'),
        data: investedPoints,
        borderColor: '#9ca3af',
        borderDash: [5, 5],
        fill: false,
        tension: 0.4,
        pointRadius: 0,
        pointHoverRadius: 4,
      },
    ],
    resolution: isTwoHourSteps ? '2h' : '1d',
  };
};

// Mapeamento de cores consistente para todas as categorias
const getCategoryColors = () => ({
  'ACAO': { border: '#3b82f6', background: 'rgba(59, 130, 246, 0.1)', solid: '#3b82f6' },
  'STOCK': { border: '#8b5cf6', background: 'rgba(139, 92, 246, 0.1)', solid: '#8b5cf6' },
  'CRYPTO': { border: '#f59e0b', background: 'rgba(245, 158, 11, 0.1)', solid: '#f59e0b' },
  'FII': { border: '#10b981', background: 'rgba(16, 185, 129, 0.1)', solid: '#10b981' },
  'RENDA_FIXA': { border: '#06b6d4', background: 'rgba(6, 182, 212, 0.1)', solid: '#06b6d4' },
  'OUTROS': { border: '#6b7280', background: 'rgba(107, 114, 128, 0.1)', solid: '#6b7280' }
});

const buildChartDataFromSeries = (series, t) => {
  if (!series || !series.labels || !series.current || !series.current.length) {
    return null;
  }

  const categoryNames = {
    'ACAO': t('investments.categories.ACAO'),
    'STOCK': t('investments.categories.STOCK'),
    'CRYPTO': t('investments.categories.CRYPTO'),
    'FII': t('investments.categories.FII'),
    'RENDA_FIXA': t('investments.categories.RENDA_FIXA'),
    'OUTROS': t('investments.categories.OUTROS')
  };

  // Cores para cada categoria
  const categoryColors = getCategoryColors();

  const datasets = [
    {
      label: t('investments.patrimonialValue'),
      data: series.current,
      borderColor: '#3b82f6',
      backgroundColor: 'rgba(59, 130, 246, 0.1)',
      fill: true,
      tension: 0.4,
      pointRadius: 0,
      pointHoverRadius: 4,
    },
    {
      label: t('investments.investedValue'),
      data: series.invested || [],
      borderColor: '#9ca3af',
      borderDash: [5, 5],
      fill: false,
      tension: 0.4,
      pointRadius: 0,
      pointHoverRadius: 4,
    },
  ];

  // Adiciona linhas por categoria se disponível
  if (series.categories && typeof series.categories === 'object') {
    Object.keys(series.categories).forEach((category) => {
      const catData = series.categories[category];
      const colors = categoryColors[category] || categoryColors['OUTROS'];
      const categoryLabel = categoryNames[category] || category;
      
      // Verifica se há dados e se o tamanho corresponde ao número de labels
      if (catData && catData.current && catData.current.length > 0) {
        // Garante que o array de dados tem o mesmo tamanho que os labels
        const dataArray = catData.current;
        if (dataArray.length === series.labels.length) {
          datasets.push({
            label: categoryLabel,
            data: dataArray,
            borderColor: colors.border,
            backgroundColor: colors.background,
            fill: false,
            tension: 0.4,
            pointRadius: 0,
            pointHoverRadius: 4,
            // Não oculta a linha mesmo se houver valores zero
            spanGaps: false,
          });
        }
      }
    });
  }

  return {
    labels: series.labels,
    datasets,
  };
};

export default function Investments() {
  const { user } = useAuth();
  const { fetchAccounts } = useData();
  const { t } = useLanguage();
  const [investments, setInvestments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [summary, setSummary] = useState({ totalInvested: 0, totalCurrent: 0, totalReturn: 0, totalReturnPercent: 0 });
  const [showModal, setShowModal] = useState(false);
  const [expandedCategories, setExpandedCategories] = useState({});
  const [selectedAssetGroup, setSelectedAssetGroup] = useState(null);
  const [showDetailsModal, setShowDetailsModal] = useState(false);
  const [showStatementModal, setShowStatementModal] = useState(false);
  const [accounts, setAccounts] = useState([]);
  const [chartPeriod, setChartPeriod] = useState('1M');
  const [evolutionSeries, setEvolutionSeries] = useState(null);
  const [evolutionLoading, setEvolutionLoading] = useState(false);
  const [evolutionError, setEvolutionError] = useState(null);
  const [showReturnInfo, setShowReturnInfo] = useState(false);
  const [showExportMenu, setShowExportMenu] = useState(false);
  const [assetsLimitPerCategory, setAssetsLimitPerCategory] = useState({}); // Quantos ativos mostrar por categoria
  const [transactionsLimitPerAsset, setTransactionsLimitPerAsset] = useState({}); // Quantas transações mostrar por ativo
  
  // Ref para armazenar o AbortController da requisição atual
  const abortControllerRef = useRef(null);
  // Ref para armazenar os investimentos mais recentes para acesso no callback
  const investmentsRef = useRef(investments);

  const fetchEvolutionData = useCallback(async (periodOverride = chartPeriod) => {
    // Usa investmentsRef para ter acesso aos investimentos mais recentes
    const currentInvestments = investmentsRef.current;
    if (!user || !currentInvestments.length) {
      setEvolutionSeries(null);
      return;
    }

    // Cancela a requisição anterior se ainda estiver em andamento
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
    }

    // Cria um novo AbortController para esta requisição
    const abortController = new AbortController();
    abortControllerRef.current = abortController;

    setEvolutionLoading(true);
    setEvolutionError(null);

    try {
      const params = new URLSearchParams({
        userId: user.id,
        period: periodOverride,
      });

      const response = await api.get(`/investments/evolution?${params.toString()}`, {
        signal: abortController.signal
      });
      
      // Verifica se a requisição foi cancelada antes de processar a resposta
      if (abortController.signal.aborted) {
        return;
      }
      
      if (response.success) {
        setEvolutionSeries(response.data || null);
      } else {
        setEvolutionSeries(null);
        setEvolutionError(response.message || t('investments.cannotUpdateChart'));
      }
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
      
      setEvolutionSeries(null);
      setEvolutionError(error.message || t('investments.cannotUpdateChart'));
    } finally {
      // Só atualiza o loading se a requisição não foi cancelada
      if (!abortController.signal.aborted) {
        setEvolutionLoading(false);
      }
      // Limpa a referência se esta ainda é a requisição atual
      if (abortControllerRef.current === abortController) {
        abortControllerRef.current = null;
      }
    }
  }, [chartPeriod, user]);

  useEffect(() => {
    if (user) {
      // Carrega investimentos e contas em paralelo para melhor performance
      Promise.all([loadInvestments(), loadAccounts()]).catch(error => {
        console.error('Erro ao carregar dados iniciais:', error);
      });
    }
  }, [user]);

  useEffect(() => {
    if (!user) return;
    
    // Listener para recarregar investimentos quando uma conta for excluída
    const handleAccountDeleted = () => {
      loadInvestments();
    };
    
    window.addEventListener('accountDeleted', handleAccountDeleted);
    
    return () => {
      window.removeEventListener('accountDeleted', handleAccountDeleted);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [user]);

  useEffect(() => {
    if (!user) return;
    // Atualiza a ref sempre que investments mudar
    investmentsRef.current = investments;
    
    if (!investments.length) {
      setEvolutionSeries(null);
      return;
    }
    fetchEvolutionData(chartPeriod);
    
    // Cleanup: cancela a requisição quando o componente desmonta ou quando as dependências mudam
    return () => {
      if (abortControllerRef.current) {
        abortControllerRef.current.abort();
        abortControllerRef.current = null;
      }
    };
  }, [user, investments.length, chartPeriod, fetchEvolutionData]);

  const loadInvestments = async () => {
    setLoading(true);
    try {
      // Carrega todos os investimentos (sem paginação)
      const response = await api.get(`/investments?userId=${user.id}`);
      if (response && response.success) {
        const newInvestments = response.data || [];
        setInvestments(newInvestments);
        // Atualiza a ref imediatamente para que fetchEvolutionData tenha acesso aos dados mais recentes
        investmentsRef.current = newInvestments;
        
        // Atualiza o gráfico imediatamente após carregar os investimentos
        // Isso garante que novos investimentos apareçam no gráfico sem esperar o reset do cache
        if (newInvestments.length > 0 && user) {
          fetchEvolutionData(chartPeriod);
        } else {
          setEvolutionSeries(null);
        }
      }
    } catch (error) {
      console.error('Erro ao carregar investimentos:', error);
      toast.error(t('investments.errorLoading'));
    } finally {
      setLoading(false);
    }
  };

  const loadAccounts = async () => {
    try {
      // Usa cache do DataContext
      const accountsData = await fetchAccounts(user.id).catch(err => {
        console.error('Erro ao carregar contas:', err);
        return [];
      });
      setAccounts(accountsData || []);
    } catch (error) {
      console.error('Erro ao carregar contas:', error);
    }
  };

  const [deletingIds, setDeletingIds] = useState(new Set());

  const handleDelete = async (id) => {
    if (!confirm(t('investments.deleteConfirm'))) return;
    
    setDeletingIds(prev => new Set(prev).add(id));
    try {
      const response = await api.delete(`/investments?id=${id}`);
      if (response.success) {
        toast.success(t('investments.deletedSuccess'));
        loadInvestments();
      }
    } catch (error) {
      toast.error(t('investments.errorDeleting'));
    } finally {
      setDeletingIds(prev => {
        const next = new Set(prev);
        next.delete(id);
        return next;
      });
    }
  };

  const toggleCategory = (category) => {
    setExpandedCategories(prev => ({
      ...prev,
      [category]: !prev[category]
    }));
  };

  const openAssetDetails = (assetGroup) => {
    setSelectedAssetGroup(assetGroup);
    setShowDetailsModal(true);
  };

  const handleEditInvestment = (investment) => {
    // Ao editar um aporte individual, fecha o modal de detalhes do ativo e abre o modal de edição
    setSelectedAssetGroup(null); 
    setShowDetailsModal(false); 
    // Passa o aporte para o modal de edição
    // Precisamos garantir que o InvestmentModal saiba lidar com isso
    // O setSelectedInvestment no código anterior era usado para o modal de edição
    // Aqui vamos reutilizar o state ou passar direto para o modal se possível, 
    // mas como o modal de edição é controlado por props, precisamos de um state 'investmentToEdit'
    // Vou criar um state separado para edição se necessário, ou reutilizar um genérico
    setInvestmentToEdit(investment);
    setShowModal(true); 
  };

  const [investmentToEdit, setInvestmentToEdit] = useState(null);

  // Chart Data Generation
  const evolutionChartData = useMemo(() => {
    if (evolutionSeries?.labels?.length && evolutionSeries?.current?.length) {
      return buildChartDataFromSeries(evolutionSeries, t);
    }

    if (!investments.length) return null;

    const today = startOfDay(new Date());
    let startDate;

    switch (chartPeriod) {
      case '1D': startDate = subDays(today, 1); break;
      case '1W': startDate = subWeeks(today, 1); break;
      case '1M': startDate = subMonths(today, 1); break;
      case '6M': startDate = subMonths(today, 6); break;
      case 'YTD': startDate = startOfYear(today); break;
      case '1Y': startDate = subYears(today, 1); break;
      case '5Y': startDate = subYears(today, 5); break;
      case 'ALL': {
        const earliest = investments.reduce((min, inv) => {
          const d = getTradeDate(inv);
          return d < min ? d : min;
        }, today);
        startDate = earliest;
        break;
      }
      default: startDate = subMonths(today, 1);
    }

    if (startDate > today) startDate = today;

    return buildEvolutionSeries(investments, startDate, today, t);
  }, [evolutionSeries, investments, chartPeriod, t]);

  const usingServerSeries = useMemo(() => (
    Boolean(evolutionSeries?.labels?.length && evolutionSeries?.current?.length)
  ), [evolutionSeries]);

  const chartPeriods = [
    { id: '1D', label: '1D' },
    { id: '1W', label: '1S' },
    { id: '1M', label: '1M' },
    { id: '6M', label: '6M' },
    { id: 'YTD', label: 'YTD' },
    { id: '1Y', label: '1A' },
    { id: '5Y', label: '5A' },
    { id: 'ALL', label: 'Tudo' },
  ];

  // Group investments by category AND asset name
const {
    groupedByCategory: groupedInvestments,
    totalRealizedProfit,
  } = useMemo(() => {
    const grouped = investments.reduce((acc, inv) => {
    const category = inv.categoria || 'OUTROS';
    if (!acc[category]) acc[category] = {};
    
    const assetName = inv.nome;
    if (!acc[category][assetName]) {
        acc[category][assetName] = {
            nome: assetName,
            nomeAtivo: inv.nomeAtivo,
            categoria: category,
            tipoInvestimento: inv.tipoInvestimento,
            quantidadeTotal: 0,
            valorAporteTotal: 0, // Basis cost of current holdings
            valorAtualTotal: 0,
            retornoTotal: 0,
            aportes: []
        };
    }
    
    const group = acc[category][assetName];
    
    // Atualiza nomeAtivo sempre que o investimento atual tiver um nomeAtivo válido
    // Prioriza nomes mais completos (mais longos) mas sempre atualiza se o grupo não tiver
    if (inv.nomeAtivo && inv.nomeAtivo.trim()) {
        if (!group.nomeAtivo || !group.nomeAtivo.trim()) {
            // Se o grupo não tem nomeAtivo, usa o do investimento atual
            group.nomeAtivo = inv.nomeAtivo;
        } else if (inv.nomeAtivo.length >= group.nomeAtivo.length) {
            // Se o investimento atual tem um nome igual ou mais completo, atualiza
            // Isso garante que sempre temos o nome mais completo disponível
            group.nomeAtivo = inv.nomeAtivo;
        }
    }
    
    // Add to transactions list
    group.aportes.push(inv);
    
    return acc;
  }, {});

    let totalRealizedProfit = 0;
    const activeGroups = {};

    Object.keys(grouped).forEach(cat => {
      Object.entries(grouped[cat]).forEach(([assetName, group]) => {
          const {
            realizedProfit,
            remainingQty,
            remainingCost,
            investedCapital,
            realizedCostBasis,
          } = computeHoldingStats(group.aportes);

          group.quantidadeTotal = remainingQty;
          group.valorAporteTotal = remainingCost;
          group.precoMedio = remainingQty > 0
            ? remainingCost / remainingQty
            : 0;

          let currentPrice = 0;
          const validAporte = group.aportes.find(a => a.precoAtual > 0);
          if (validAporte) {
              currentPrice = validAporte.precoAtual;
          }

          // Garante que o nomeAtivo seja preservado mesmo após o processamento
          // Busca o nomeAtivo mais completo entre todos os aportes
          if (!group.nomeAtivo || !group.nomeAtivo.trim()) {
              const aporteComNome = group.aportes.find(a => a.nomeAtivo && a.nomeAtivo.trim());
              if (aporteComNome) {
                  group.nomeAtivo = aporteComNome.nomeAtivo;
              }
          } else {
              // Se já tem nomeAtivo, verifica se há algum mais completo
              const aporteComNomeMaisCompleto = group.aportes.find(a => 
                  a.nomeAtivo && a.nomeAtivo.trim() && a.nomeAtivo.length > group.nomeAtivo.length
              );
              if (aporteComNomeMaisCompleto) {
                  group.nomeAtivo = aporteComNomeMaisCompleto.nomeAtivo;
              }
          }

          group.valorAtualTotal = group.quantidadeTotal * currentPrice;

          const unrealizedReturn = group.valorAtualTotal - group.valorAporteTotal;
          group.realizedProfit = realizedProfit;
          group.retornoTotal = realizedProfit + unrealizedReturn;

          const roiBase = investedCapital > 0 ? investedCapital : group.valorAporteTotal;
          group.retornoPercent = roiBase > 0
              ? (group.retornoTotal / roiBase) * 100
              : 0;

          totalRealizedProfit += realizedProfit;

          if (group.quantidadeTotal > 0) {
            if (!activeGroups[cat]) activeGroups[cat] = {};
            activeGroups[cat][assetName] = group;
          }
      });
    });

    return {
      groupedByCategory: activeGroups,
      totalRealizedProfit,
    };
  }, [investments]);

  const categoryNames = {
    'ACAO': t('investments.categories.ACAO'),
    'STOCK': t('investments.categories.STOCK'),
    'CRYPTO': t('investments.categories.CRYPTO'),
    'FII': t('investments.categories.FII'),
    'RENDA_FIXA': t('investments.categories.RENDA_FIXA'),
    'OUTROS': t('investments.categories.OUTROS')
  };

  // Calculate allocation for chart (only include investments with quantity > 0)
  const allocationData = [];
  Object.keys(groupedInvestments).forEach(cat => {
      let catTotal = 0;
      Object.values(groupedInvestments[cat]).forEach(group => {
          // Only include groups with positive quantity
          if (group.quantidadeTotal > 0) {
              catTotal += group.valorAtualTotal;
          }
      });
      if (catTotal > 0) {
          allocationData.push({
              category: categoryNames[cat] || cat,
              categoryKey: cat, // Mantém a chave original para mapear cores
              value: catTotal
          });
      }
  });
  allocationData.sort((a, b) => b.value - a.value);

  // Calculate summary from grouped investments (including realized profits/losses from sold investments)
  const calculatedSummary = useMemo(() => {
    let totalInvested = 0;
    let totalCurrent = 0;
    
    Object.keys(groupedInvestments).forEach(cat => {
      Object.values(groupedInvestments[cat]).forEach(group => {
        totalInvested += group.valorAporteTotal;
        totalCurrent += group.valorAtualTotal;
      });
    });
    
    const unrealizedReturn = totalCurrent - totalInvested;
    const totalReturn = totalRealizedProfit + unrealizedReturn;
    const totalReturnPercent = totalInvested > 0
      ? (totalReturn / totalInvested) * 100
      : 0;
    
    return { 
      totalInvested, 
      totalCurrent, 
      totalReturn, 
      totalReturnPercent,
      realizedProfitLoss: totalRealizedProfit 
    };
  }, [groupedInvestments, totalRealizedProfit]);

  // Sempre usar o resumo calculado para refletir lucros realizados mesmo sem posições ativas
  const displaySummary = calculatedSummary;

  const exportInvestmentReport = async (format = 'xlsx') => {
    console.log('exportInvestmentReport chamado com format:', format);
    console.log('investments:', investments);
    console.log('calculatedSummary:', calculatedSummary);
    console.log('groupedInvestments:', groupedInvestments);
    
    try {
      if (format === 'pdf') {
        console.log('Iniciando exportação PDF...');
        // Detectar tema atual
        const isDark = document.documentElement.classList.contains('dark') || 
                       localStorage.getItem('theme') === 'dark';
        
        const doc = new jsPDF();
        const pageWidth = doc.internal.pageSize.getWidth();
        const margin = 14;
        let yPos = margin;

        // Definir cor de fundo da página baseado no tema
        if (isDark) {
          doc.setFillColor(17, 24, 39); // gray-900
          doc.rect(0, 0, pageWidth, doc.internal.pageSize.getHeight(), 'F');
        }

        // Paleta de cores baseada no tema
        const palette = isDark ? {
          // Modo Escuro
          primary: [34, 197, 94],              // green-500
          primaryLight: [22, 101, 52],        // green-900/30
          green: [34, 197, 94],               // green-500
          greenLight: [22, 101, 52],          // green-900/30
          blue: [96, 165, 250],               // blue-400
          blueLight: [30, 58, 138],          // blue-900/30
          purple: [196, 181, 253],           // purple-300
          purpleLight: [88, 28, 135],        // purple-900/30
          amber: [251, 191, 36],             // amber-400
          amberLight: [120, 53, 15],         // amber-900/30
          emerald: [52, 211, 153],           // emerald-400
          emeraldLight: [6, 78, 59],         // emerald-900/30
          gray: [156, 163, 175],            // gray-400
          grayLight: [55, 65, 81],          // gray-700 (cabeçalhos mais visíveis)
          grayBorder: [75, 85, 101],       // gray-600 (bordas mais visíveis)
          textPrimary: [243, 244, 246],     // gray-100
          textSecondary: [209, 213, 219],   // gray-300 (mais visível)
          white: [31, 41, 55],               // gray-800
          whiteAlt: [39, 49, 63],            // gray-750 (linhas alternadas)
          pageBg: [17, 24, 39],             // gray-900
        } : {
          // Modo Claro
          primary: [22, 163, 74],            // primary-600
          primaryLight: [240, 253, 244],     // primary-50
          green: [34, 197, 94],              // green-500
          greenLight: [240, 253, 244],       // green-50
          blue: [59, 130, 246],              // blue-500
          blueLight: [239, 246, 255],        // blue-50
          purple: [168, 85, 247],            // purple-500
          purpleLight: [250, 245, 255],      // purple-50
          amber: [245, 158, 11],             // amber-500
          amberLight: [255, 251, 235],       // amber-50
          emerald: [16, 185, 129],           // emerald-500
          emeraldLight: [236, 253, 245],     // emerald-50
          gray: [107, 114, 128],             // gray-500
          grayLight: [249, 250, 251],        // gray-50
          grayBorder: [229, 231, 235],       // gray-200
          textPrimary: [17, 24, 39],         // gray-900
          textSecondary: [107, 114, 128],    // gray-500
          white: [255, 255, 255],
          pageBg: [255, 255, 255],
        };

        const ensureSpace = (space = 20) => {
          if (yPos + space > doc.internal.pageSize.getHeight() - 20) {
            doc.addPage();
            yPos = margin;
          }
        };

        // Função para desenhar card estilo SummaryCard
        const drawSummaryCard = (label, value, subtitle, x, y, width, bgColor, borderColor, textColor) => {
          // Fundo do card
          doc.setFillColor(...bgColor);
          doc.roundedRect(x, y, width, 32, 4, 4, 'F');
          
          // Borda sutil
          doc.setDrawColor(...borderColor);
          doc.setLineWidth(0.5);
          doc.roundedRect(x, y, width, 32, 4, 4, 'S');
          
          // Label
          doc.setFontSize(7);
          doc.setFont(undefined, 'normal');
          doc.setTextColor(...textColor);
          doc.text(label, x + 6, y + 8);
          
          // Valor
          doc.setFontSize(12);
          doc.setFont(undefined, 'bold');
          doc.setTextColor(...palette.textPrimary);
          doc.text(value, x + 6, y + 18);
          
          // Subtitle
          if (subtitle) {
            doc.setFontSize(6);
            doc.setFont(undefined, 'normal');
            doc.setTextColor(...palette.textSecondary);
            const subtitleLines = doc.splitTextToSize(subtitle, width - 12);
            subtitleLines.forEach((line, idx) => {
              doc.text(line, x + 6, y + 26 + idx * 4);
            });
          }
        };

        // Função para desenhar título de seção
        const drawSectionTitle = (title) => {
          ensureSpace(16);
          doc.setFontSize(14);
          doc.setFont(undefined, 'bold');
          doc.setTextColor(...palette.textPrimary);
          doc.text(title, margin, yPos);
          yPos += 10;
        };

        // Header minimalista estilo app
        doc.setFontSize(24);
        doc.setFont(undefined, 'bold');
        doc.setTextColor(...palette.textPrimary);
        doc.text(t('investments.reportTitle'), margin, yPos);
        yPos += 8;
        
        doc.setFontSize(9);
        doc.setFont(undefined, 'normal');
        doc.setTextColor(...palette.textSecondary);
        doc.text(`${t('investments.generatedAt')} ${formatDate(new Date().toISOString())}`, margin, yPos);
        yPos += 16;

        // Summary cards estilo SummaryCard
        const cardWidth = (pageWidth - margin * 2 - 12) / 3;
        const cardSpacing = 4;
        
        drawSummaryCard(
          t('investments.totalInvested'),
          formatCurrency(displaySummary.totalInvested),
          t('investments.capitalStillInvested'),
          margin,
          yPos,
          cardWidth,
          palette.white,
          palette.grayBorder,
          palette.textSecondary
        );
        
        drawSummaryCard(
          t('investments.currentValue'),
          formatCurrency(displaySummary.totalCurrent),
          t('investments.livePositions'),
          margin + cardWidth + cardSpacing,
          yPos,
          cardWidth,
          palette.blueLight,
          isDark ? [96, 165, 250] : [59, 130, 246],
          palette.textSecondary
        );
        
        drawSummaryCard(
          t('investments.totalReturn'),
          formatCurrency(displaySummary.totalReturn),
          `${displaySummary.totalReturnPercent.toFixed(2)}% (${t('investments.includesRealized')})`,
          margin + (cardWidth + cardSpacing) * 2,
          yPos,
          cardWidth,
          palette.emeraldLight,
          isDark ? [52, 211, 153] : [16, 185, 129],
          palette.textSecondary
        );
        
        yPos += 40;

        // Seções principais
        drawSectionTitle(t('investments.activePositions'));
        const positions = [];
        Object.keys(groupedInvestments).forEach(cat => {
          Object.values(groupedInvestments[cat]).forEach(group => {
            if (group.quantidadeTotal > 0) {
              positions.push([
                categoryNames[cat] || cat,
                group.nome,
                group.quantidadeTotal.toFixed(6),
                formatCurrency(group.precoMedio),
                formatCurrency(group.valorAtualTotal),
                formatCurrency(group.valorAporteTotal),
                `${group.retornoPercent.toFixed(2)}%`
              ]);
            }
          });
        });
        if (positions.length > 0) {
          autoTable(doc, {
            startY: yPos,
            head: [[t('investments.category'), t('investments.asset'), t('investments.qty'), t('investments.averagePrice'), t('investments.currentValue'), t('investments.totalInvested'), t('investments.returnPercent')]],
            body: positions.sort((a, b) => {
              // Remove caracteres não numéricos exceto vírgula e ponto, depois normaliza
              const valB = b[4].replace(/[^\d,-]/g, '');
              const valA = a[4].replace(/[^\d,-]/g, '');
              return parseFloatBrazilian(valB) - parseFloatBrazilian(valA);
            }).slice(0, 18),
            styles: { 
              fontSize: 9,
              cellPadding: 3,
              fillColor: isDark ? palette.white : palette.white,
              textColor: palette.textPrimary,
              lineColor: palette.grayBorder,
              lineWidth: isDark ? 0.5 : 0.3,
            },
            headStyles: { 
              fillColor: palette.grayLight,
              textColor: palette.textPrimary,
              fontStyle: 'bold',
              lineColor: palette.grayBorder,
              lineWidth: isDark ? 0.7 : 0.5,
            },
            alternateRowStyles: { fillColor: isDark ? palette.whiteAlt : [249, 250, 251] },
            columnStyles: {
              0: { textColor: palette.textSecondary },
              1: { textColor: palette.textPrimary },
              2: { halign: 'right', textColor: palette.textSecondary },
              3: { halign: 'right', textColor: palette.textSecondary },
              4: { halign: 'right', fontStyle: 'bold', textColor: [59, 130, 246] },
              5: { halign: 'right', textColor: palette.textSecondary },
              6: { halign: 'right', textColor: [34, 197, 94] },
            },
          });
          yPos = doc.lastAutoTable.finalY + 12;
        } else {
          doc.setFontSize(10);
          doc.setTextColor(...palette.textSecondary);
          doc.text('Nenhuma posição ativa no momento.', margin, yPos);
          yPos += 10;
        }

        drawSectionTitle('Distribuição por Categoria');
        const allocationRows = allocationData.map(item => {
          const percent = displaySummary.totalCurrent > 0
            ? (item.value / displaySummary.totalCurrent) * 100
            : 0;
          return [
            item.category,
            formatCurrency(item.value),
            `${percent.toFixed(2)}%`,
          ];
        });
        if (allocationRows.length > 0) {
          autoTable(doc, {
            startY: yPos,
            head: [['Categoria', 'Valor', 'Participação']],
            body: allocationRows.slice(0, 10),
            styles: { 
              fontSize: 9,
              cellPadding: 3,
              fillColor: isDark ? palette.white : palette.white,
              textColor: palette.textPrimary,
              lineColor: palette.grayBorder,
              lineWidth: isDark ? 0.5 : 0.3,
            },
            headStyles: { 
              fillColor: palette.grayLight,
              textColor: palette.textPrimary,
              fontStyle: 'bold',
              lineColor: palette.grayBorder,
              lineWidth: isDark ? 0.7 : 0.5,
            },
            alternateRowStyles: { fillColor: isDark ? palette.whiteAlt : [249, 250, 251] },
            columnStyles: {
              0: { textColor: palette.textPrimary },
              1: { halign: 'right', textColor: palette.textSecondary },
              2: { halign: 'right', textColor: palette.textSecondary },
            },
          });
          yPos = doc.lastAutoTable.finalY + 12;
        } else {
          doc.setFontSize(10);
          doc.setTextColor(...palette.textSecondary);
          doc.text(t('investments.noDistributionToDisplay'), margin, yPos);
          yPos += 10;
        }

        drawSectionTitle('Operações Recentes');
        const recentOperations = investments
          .slice()
          .sort((a, b) => new Date(b.dataAporte) - new Date(a.dataAporte))
          .slice(0, 20)
          .map(inv => [
            formatDate(inv.dataAporte),
            inv.quantidade > 0 ? 'Compra' : 'Venda',
            inv.nome,
            Math.abs(inv.quantidade).toFixed(4),
            formatCurrency(inv.precoAporte || 0),
            formatCurrency((inv.valorAporte || 0) + (inv.corretagem || 0)),
          ]);
        if (recentOperations.length > 0) {
          autoTable(doc, {
            startY: yPos,
            head: [['Data', 'Tipo', 'Ativo', 'Qtd.', 'Preço', 'Valor Total']],
            body: recentOperations,
            styles: { 
              fontSize: 9,
              cellPadding: 3,
              fillColor: isDark ? palette.white : palette.white,
              textColor: palette.textPrimary,
              lineColor: palette.grayBorder,
              lineWidth: isDark ? 0.5 : 0.3,
            },
            headStyles: { 
              fillColor: palette.grayLight,
              textColor: palette.textPrimary,
              fontStyle: 'bold',
              lineColor: palette.grayBorder,
              lineWidth: isDark ? 0.7 : 0.5,
            },
            alternateRowStyles: { fillColor: isDark ? palette.whiteAlt : [249, 250, 251] },
            columnStyles: {
              0: { textColor: palette.textSecondary },
              1: { textColor: palette.textPrimary },
              2: { textColor: palette.textPrimary },
              3: { textColor: palette.textSecondary },
              4: { halign: 'right', textColor: palette.textSecondary },
              5: { halign: 'right', textColor: palette.textSecondary },
              6: { halign: 'right', fontStyle: 'bold', textColor: palette.textPrimary },
            },
          });
          yPos = doc.lastAutoTable.finalY + 12;
        } else {
          doc.setFontSize(10);
          doc.setTextColor(...palette.textSecondary);
          doc.text('Nenhuma operação registrada recentemente.', margin, yPos);
          yPos += 10;
        }

        drawSectionTitle(t('investments.additionalDetails'));
        doc.setFontSize(9);
        doc.setFont(undefined, 'normal');
        doc.setTextColor(...palette.textSecondary);
        doc.text(`Lucro/Prejuízo Realizado acumulado: ${formatCurrency(calculatedSummary.realizedProfitLoss || 0)}`, margin, yPos);
        yPos += 6;
        doc.text(`Total de operações registradas: ${investments.length}`, margin, yPos);
        yPos += 6;
        doc.text('Este relatório foi gerado automaticamente pelo Controle-se.', margin, yPos);
        yPos += 8;

        // Rodapé
        const totalPages = doc.internal.pages.length - 1;
        for (let i = 1; i <= totalPages; i++) {
          doc.setPage(i);
          doc.setFontSize(8);
          doc.setTextColor(...palette.gray);
          doc.text(
            t('investments.reportPage', { current: i, total: totalPages }),
            pageWidth / 2,
            doc.internal.pageSize.getHeight() - 10,
            { align: 'center' }
          );
        }

        console.log('PDF gerado com sucesso, salvando...');
        doc.save(`relatorio_investimentos_${new Date().toISOString().split('T')[0]}.pdf`);
        toast.success(t('investments.exportSuccessPDF'));
      } else if (format === 'xlsx') {
        console.log('Iniciando exportação XLSX...');
        // Export as XLSX
        const wb = XLSX.utils.book_new();
        
        // Summary sheet
        const summaryData = [
          ['Relatório de Investimentos'],
          [`Gerado em: ${formatDate(new Date().toISOString())}`],
          [''],
          ['Resumo'],
          ['Total Investido', displaySummary.totalInvested],
          ['Valor Atual', displaySummary.totalCurrent],
          ['Retorno Total', displaySummary.totalReturn],
          ['Retorno %', `${displaySummary.totalReturnPercent.toFixed(2)}%`],
          [''],
          ['Lucro/Prejuízo Realizado', calculatedSummary.realizedProfitLoss || 0]
        ];
        const summaryWs = XLSX.utils.aoa_to_sheet(summaryData);
        XLSX.utils.book_append_sheet(wb, summaryWs, 'Resumo');

        // Investments by category
        const investmentsData = [];
        Object.keys(groupedInvestments).forEach(cat => {
          Object.values(groupedInvestments[cat]).forEach(group => {
            if (group.quantidadeTotal > 0) {
              investmentsData.push({
                'Categoria': categoryNames[cat] || cat,
                'Ativo': group.nome,
                'Nome do Ativo': group.nomeAtivo || '-',
                'Quantidade': group.quantidadeTotal,
                'Preço Médio': group.precoMedio,
                [t('investments.currentValue')]: group.valorAtualTotal,
                [t('investments.totalInvested')]: group.valorAporteTotal,
                [t('investments.return')]: group.retornoTotal,
                [t('investments.returnPercent')]: `${group.retornoPercent.toFixed(2)}%`
              });
            }
          });
        });
        const investmentsWs = XLSX.utils.json_to_sheet(investmentsData);
        XLSX.utils.book_append_sheet(wb, investmentsWs, 'Investimentos');

        // All transactions
        const transactionsData = investments.map(inv => ({
          'Data': formatDate(inv.dataAporte),
          'Ativo': inv.nome,
          'Categoria': categoryNames[inv.categoria] || inv.categoria,
          'Tipo': inv.quantidade > 0 ? 'Compra' : 'Venda',
          'Quantidade': Math.abs(inv.quantidade),
          'Preço': inv.precoAporte || 0,
          'Corretagem': inv.corretagem || 0,
          'Valor Total': (inv.valorAporte || 0) + (inv.corretagem || 0),
          'Corretora': inv.corretora || '-'
        }));
        const transactionsWs = XLSX.utils.json_to_sheet(transactionsData);
        XLSX.utils.book_append_sheet(wb, transactionsWs, 'Transações');

        console.log('XLSX gerado com sucesso, salvando...');
        XLSX.writeFile(wb, `relatorio_investimentos_${new Date().toISOString().split('T')[0]}.xlsx`);
        toast.success(t('investments.exportSuccessXLSX'));
      } else if (format === 'csv') {
        console.log('Iniciando exportação CSV...');
        // Export as CSV
        const data = [];
        Object.keys(groupedInvestments).forEach(cat => {
          Object.values(groupedInvestments[cat]).forEach(group => {
            if (group.quantidadeTotal > 0) {
              data.push({
                [t('investments.category')]: categoryNames[cat] || cat,
                [t('investments.asset')]: group.nome,
                [t('investments.quantity')]: group.quantidadeTotal,
                [t('investments.averagePrice')]: group.precoMedio,
                [t('investments.currentValue')]: group.valorAtualTotal,
                [t('investments.return')]: group.retornoTotal,
                [t('investments.returnPercent')]: `${group.retornoPercent.toFixed(2)}%`
              });
            }
          });
        });

        const csvContent = [
          Object.keys(data[0] || {}).join(','),
          ...data.map(row => Object.values(row).map(cell => `"${cell}"`).join(','))
        ].join('\n');

        const blob = new Blob(['\ufeff' + csvContent], { type: 'text/csv;charset=utf-8;' });
        const link = document.createElement('a');
        link.href = URL.createObjectURL(blob);
        link.download = `relatorio_investimentos_${new Date().toISOString().split('T')[0]}.csv`;
        link.click();
        console.log('CSV gerado com sucesso');
        toast.success(t('investments.exportSuccessCSV'));
      }
    } catch (error) {
      console.error('Erro ao exportar relatório:', error);
      console.error('Stack trace:', error.stack);
      console.error('Detalhes do erro:', {
        message: error.message,
        name: error.name,
        format: format,
        investmentsLength: investments?.length,
        calculatedSummary: calculatedSummary,
        groupedInvestments: groupedInvestments
      });
      toast.error(`${t('investments.exportError')}: ${error.message || t('investments.exportErrorUnknown')}`);
    }
  };

  if (loading) {
    return <SkeletonSection type="investments" />;
  }

  return (
    <div className="space-y-6 animate-fade-in">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-2xl sm:text-3xl font-bold text-gray-900 dark:text-white">{t('investments.title')}</h2>
          <p className="text-sm sm:text-base text-gray-600 dark:text-gray-400 mt-1">{t('investments.subtitle')}</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <button 
            onClick={() => setShowStatementModal(true)} 
            className="btn-secondary flex items-center gap-2 text-sm sm:text-base px-3 sm:px-4 py-2"
          >
            <FileText className="w-4 h-4" />
            <span className="hidden sm:inline">{t('investments.statement')}</span>
          </button>
          <div className="relative group">
            <button 
              className="btn-secondary flex items-center gap-2 text-sm sm:text-base px-3 sm:px-4 py-2"
              onClick={(e) => {
                e.stopPropagation();
                console.log('Botão exportar clicado');
                setShowExportMenu(!showExportMenu);
              }}
              onBlur={() => {
                // Fecha o menu quando perde o foco (após um pequeno delay para permitir cliques nos itens)
                setTimeout(() => setShowExportMenu(false), 200);
              }}
            >
              <Download className="w-4 h-4" />
              <span className="hidden sm:inline">{t('investments.exportReport')}</span>
              <span className="sm:hidden">{t('investments.export')}</span>
            </button>
            <div 
              className={`absolute right-0 mt-2 w-48 bg-white dark:bg-gray-800 rounded-lg shadow-lg border border-gray-200 dark:border-gray-700 transition-all z-50 ${
                showExportMenu ? 'opacity-100 visible' : 'opacity-0 invisible group-hover:opacity-100 group-hover:visible'
              }`}
              onMouseEnter={() => setShowExportMenu(true)}
              onMouseLeave={() => setShowExportMenu(false)}
            >
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  console.log('Botão PDF clicado');
                  setShowExportMenu(false);
                  exportInvestmentReport('pdf');
                }}
                className="w-full text-left px-4 py-2 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-t-lg flex items-center gap-2"
              >
                <FileText size={16} />
                {t('investments.exportPDF')}
              </button>
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  console.log('Botão XLSX clicado');
                  setShowExportMenu(false);
                  exportInvestmentReport('xlsx');
                }}
                className="w-full text-left px-4 py-2 hover:bg-gray-100 dark:hover:bg-gray-700 flex items-center gap-2"
              >
                <FileSpreadsheet size={16} />
                {t('investments.exportExcel')}
              </button>
              <button
                onClick={(e) => {
                  e.stopPropagation();
                  console.log('Botão CSV clicado');
                  setShowExportMenu(false);
                  exportInvestmentReport('csv');
                }}
                className="w-full text-left px-4 py-2 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-b-lg flex items-center gap-2"
              >
                <File size={16} />
                {t('investments.exportCSV')}
              </button>
            </div>
          </div>
          <button 
            onClick={() => { setInvestmentToEdit(null); setShowModal(true); }} 
            className="btn-primary flex items-center gap-2 text-sm sm:text-base px-3 sm:px-4 py-2"
          >
            <Plus className="w-4 h-4" />
            <span className="hidden sm:inline">{t('investments.addInvestment')}</span>
            <span className="sm:hidden">{t('common.new')}</span>
          </button>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <SummaryCard
          title={t('investments.totalInvested')}
          amount={formatCurrency(displaySummary.totalInvested)}
          icon={Wallet}
          type="default"
        />
        <SummaryCard
          title={t('investments.currentValue')}
          amount={formatCurrency(displaySummary.totalCurrent)}
          icon={TrendingUp}
          type="balance"
        />
        <SummaryCard
          title={t('investments.totalReturn')}
          amount={formatCurrency(displaySummary.totalReturn)}
          icon={PieChart}
          type={displaySummary.totalReturn >= 0 ? 'income' : 'expense'}
          subtitle={`${displaySummary.totalReturnPercent.toFixed(2)}%`}
          action={
            <button
              type="button"
              onClick={(e) => {
                e.stopPropagation();
                setShowReturnInfo(true);
              }}
              className="flex items-center gap-1 text-xs text-gray-500 hover:text-gray-700 dark:hover:text-gray-200 transition-colors"
              aria-label={t('investments.returnInfo')}
            >
              <Info className="w-4 h-4" />
              <span>{t('common.understand')}</span>
            </button>
          }
        />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Evolution Chart */}
        <div className="lg:col-span-2 card">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between mb-6 gap-4">
            <h3 className="text-base sm:text-lg font-semibold text-gray-900 dark:text-white">{t('investments.evolutionTitle')}</h3>
            <div className="flex flex-wrap gap-2">
              {chartPeriods.map(period => (
                <button
                  key={period.id}
                  onClick={() => setChartPeriod(period.id)}
                  className={`px-3 py-1 text-xs font-medium rounded-full transition-colors ${
                    chartPeriod === period.id
                      ? 'bg-primary-100 text-primary-700 dark:bg-primary-900/30 dark:text-primary-400'
                      : 'bg-gray-100 text-gray-600 hover:bg-gray-200 dark:bg-gray-700 dark:text-gray-400 dark:hover:bg-gray-600'
                  }`}
                >
                  {period.label}
                </button>
              ))}
            </div>
          </div>
          <div className="h-64 w-full">
            {evolutionLoading ? (
              <div className="h-full flex items-center justify-center text-gray-500 dark:text-gray-400 text-sm">
                {t('investments.loadingEvolution')}
              </div>
            ) : evolutionChartData ? (
              <>
                <Line
                  data={evolutionChartData}
                  options={{
                    responsive: true,
                    maintainAspectRatio: false,
                    interaction: {
                      mode: 'index',
                      intersect: false,
                    },
                    plugins: {
                      legend: {
                        position: 'top',
                        align: 'end',
                        labels: { boxWidth: 10, usePointStyle: true }
                      },
                      tooltip: {
                        callbacks: {
                          label: function(context) {
                            let label = context.dataset.label || '';
                            if (label) {
                              label += ': ';
                            }
                            if (context.parsed.y !== null) {
                              label += new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(context.parsed.y);
                            }
                            return label;
                          }
                        }
                      }
                    },
                    scales: {
                      x: {
                        grid: { display: false },
                        ticks: { maxTicksLimit: 8 }
                      },
                      y: {
                        grid: { color: 'rgba(0, 0, 0, 0.05)' },
                        ticks: {
                          callback: (value) => new Intl.NumberFormat('pt-BR', {
                            notation: 'compact',
                            compactDisplay: 'short',
                            style: 'currency',
                            currency: 'BRL'
                          }).format(value)
                        }
                      }
                    }
                  }}
                />
                {evolutionError && (
                  <p className="mt-2 text-xs text-amber-500 dark:text-amber-400">
                  {/* <!--  Não conseguimos buscar todas as cotações históricas agora. Exibindo o cálculo local como fallback. --> */}
                  </p>
                )}
                {!usingServerSeries && !evolutionError && (
                  <p className="mt-2 text-xs text-gray-500 dark:text-gray-400">
                    {t('investments.calculatedFromContributions')}
                  </p>
                )}
              </>
            ) : (
              <div className="h-full flex items-center justify-center text-gray-500 dark:text-gray-400 text-sm">
                {t('investments.registerToViewChart')}
              </div>
            )}
          </div>
        </div>

        {/* Assets Allocation Chart */}
        <div className="lg:col-span-1 card">
          <h3 className="text-base sm:text-lg font-semibold text-gray-900 dark:text-white mb-4">{t('investments.assetAllocation')}</h3>
          {allocationData.length > 0 ? (
            <div className="h-64">
              <Doughnut
                data={{
                  labels: allocationData.map(d => d.category),
                  datasets: [{
                    data: allocationData.map(d => d.value),
                    backgroundColor: allocationData.map(d => {
                      const categoryColors = getCategoryColors();
                      const colors = categoryColors[d.categoryKey] || categoryColors['OUTROS'];
                      return colors.solid;
                    }),
                    borderWidth: 0
                  }]
                }}
                options={{
                  responsive: true,
                  maintainAspectRatio: false,
                  plugins: {
                    legend: { position: 'bottom' }
                  }
                }}
              />
            </div>
          ) : (
            <p className="text-center text-gray-500 py-8">{t('common.noData')}</p>
          )}
        </div>
      </div>

      {/* Investments List */}
      <div className="space-y-6">
        {Object.keys(groupedInvestments).length === 0 ? (
           <div className="card text-center py-12">
             <p className="text-gray-600 dark:text-gray-400">{t('investments.noInvestments')}</p>
           </div>
        ) : (
          Object.entries(groupedInvestments).map(([category, assetsMap]) => {
            // Filter assets with quantity > 0
            const assets = Object.values(assetsMap).filter(asset => asset.quantidadeTotal !== 0);
            if (assets.length === 0) return null;
            
            const isExpanded = expandedCategories[category];
            const limit = assetsLimitPerCategory[category] || 12;
            const displayAssets = isExpanded ? assets.slice(0, limit) : assets.slice(0, 5);
            const hasMoreAssets = assets.length > limit;
            const totalValue = assets.reduce((sum, a) => sum + a.valorAtualTotal, 0);
            const totalInvested = assets.reduce((sum, a) => sum + a.valorAporteTotal, 0);
            const totalReturn = totalValue - totalInvested;
            const totalReturnPercent = totalInvested > 0 ? (totalReturn / totalInvested) * 100 : 0;
            const isCategoryPositive = totalReturn >= 0;

            return (
              <div key={category} className="card">
                <div 
                  className="flex flex-col sm:flex-row sm:justify-between sm:items-center p-4 -m-6 mb-0 cursor-pointer hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors gap-2"
                  onClick={() => toggleCategory(category)}
                >
                  <div className="flex items-center gap-2 flex-1">
                      {isExpanded ? <ChevronUp size={20} className="text-gray-400 flex-shrink-0" /> : <ChevronDown size={20} className="text-gray-400 flex-shrink-0" />}
                      <h3 className="text-base sm:text-lg font-bold text-gray-900 dark:text-white truncate">
                          {categoryNames[category] || category}
                      </h3>
                      <span className="text-xs bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-300 px-2 py-0.5 rounded-full flex-shrink-0">
                          {assets.length}
                      </span>
                  </div>
                  <div className="text-left sm:text-right flex-shrink-0">
                    <div className="font-bold text-sm sm:text-base text-gray-900 dark:text-white">
                        {formatCurrency(totalValue)}
                    </div>
                    <div className={`text-xs font-medium ${isCategoryPositive ? 'text-green-600' : 'text-red-600'}`}>
                        {isCategoryPositive ? '+' : ''}{formatCurrency(totalReturn)} ({totalReturnPercent.toFixed(2)}%)
                    </div>
                  </div>
                </div>

                {isExpanded && (
                  <div className="space-y-3 mt-6">
                    {displayAssets.map(asset => {
                        const isPositive = asset.retornoTotal >= 0;
                        return (
                            <div 
                                key={asset.nome} 
                                className="flex flex-col sm:flex-row sm:items-center justify-between p-3 rounded-lg bg-gray-50 dark:bg-gray-700/50 hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors gap-3 cursor-pointer"
                                onClick={(e) => {
                                  e.stopPropagation();
                                  openAssetDetails(asset);
                                }}
                            >
                                <div className="flex-1 min-w-0">
                                    <div className="flex flex-col sm:flex-row sm:justify-between sm:items-center gap-1 sm:gap-4">
                                        <span className="font-semibold text-sm sm:text-base text-gray-900 dark:text-white truncate">
                                            {asset.nome}{asset.nomeAtivo && asset.nomeAtivo.trim() && asset.nomeAtivo !== asset.nome ? ` - ${asset.nomeAtivo}` : ''}
                                        </span>
                                        {asset.quantidadeTotal > 0 && (
                                            <span className="text-xs text-gray-500 dark:text-gray-400 flex-shrink-0">
                                                {asset.quantidadeTotal} {asset.categoria === 'RENDA_FIXA' ? t('investments.units') : t('investments.shares')}
                                            </span>
                                        )}
                                    </div>
                                    <div className="text-xs text-gray-500 dark:text-gray-400 mt-1">
                                        {t('investments.averagePrice')}: {formatCurrency(asset.precoMedio)}
                                    </div>
                                </div>
                                
                                <div className="flex justify-between sm:justify-end items-center gap-4 flex-shrink-0">
                                    <div className="text-left sm:text-right">
                                        <div className="font-bold text-sm sm:text-base text-gray-900 dark:text-white">
                                            {formatCurrency(asset.valorAtualTotal)}
                                        </div>
                                        <div className={`text-xs font-medium ${isPositive ? 'text-green-600' : 'text-red-600'}`}>
                                            {isPositive ? '+' : ''}{formatCurrency(asset.retornoTotal)} ({asset.retornoPercent.toFixed(2)}%)
                                        </div>
                                    </div>
                                </div>
                            </div>
                        );
                    })}
                    {hasMoreAssets && (
                      <div className="flex justify-center pt-2">
                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            setAssetsLimitPerCategory(prev => ({
                              ...prev,
                              [category]: (prev[category] || 12) + 12
                            }));
                          }}
                          className="btn-secondary text-sm"
                        >
                          {t('investments.loadMoreAssets')}
                        </button>
                      </div>
                    )}
                  </div>
                )}
              </div>
            );
          })
        )}
      </div>

      <InvestmentModal 
        isOpen={showModal} 
        onClose={() => { setShowModal(false); setInvestmentToEdit(null); }} 
        onSuccess={loadInvestments} 
        investmentToEdit={investmentToEdit}
      />

      <AssetDetailsModal
        isOpen={showDetailsModal}
        onClose={() => setShowDetailsModal(false)}
        assetGroup={selectedAssetGroup}
        onEdit={handleEditInvestment}
        onDelete={handleDelete}
        deletingIds={deletingIds}
      />

      <InvestmentStatementModal
        isOpen={showStatementModal}
        onClose={() => setShowStatementModal(false)}
        investments={investments}
        accounts={accounts}
      />

      <Modal
        isOpen={showReturnInfo}
        onClose={() => setShowReturnInfo(false)}
        title={t('investments.howWeCalculateReturn')}
      >
        <div className="space-y-3 text-sm text-gray-600 dark:text-gray-300">
          <p>
            {t('investments.returnExplanation1')}
          </p>
          <p>
            {t('investments.returnExplanation2')}
          </p>
          <p>
            {t('investments.returnExplanation3')}
          </p>
        </div>
      </Modal>
    </div>
  );
}

