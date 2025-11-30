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
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Doughnut, Line } from 'react-chartjs-2';
import toast from 'react-hot-toast';
import * as XLSX from 'xlsx';
import { useAuth } from '../../contexts/AuthContext';
import api from '../../services/api';
import { formatCurrency, formatDate } from '../../utils/formatters';
import Modal from '../common/Modal';
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
      const unitCost = qty !== 0 ? (trade.valorAporte || 0) / qty : 0;
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
        const grossRevenue = Math.abs(trade.valorAporte || 0);
        const revenuePortion = grossRevenue * (matchedQty / Math.abs(qty));
        const netRevenuePortion = revenuePortion - (trade.corretagem || 0);
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

const buildEvolutionSeries = (transactions, startDate, endDate) => {
  if (!transactions.length) return null;

  const sorted = [...transactions].sort((a, b) => getTradeDate(a) - getTradeDate(b));
  const interval = eachDayOfInterval({ start: startDate, end: endDate });
  const assetState = new Map();
  const labels = [];
  const investedPoints = [];
  const currentPoints = [];
  let txIndex = 0;

  interval.forEach((date) => {
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

    let totalInvested = 0;
    let totalCurrent = 0;
    assetState.forEach((state) => {
      const quantity = state.layers.reduce((sum, layer) => sum + layer.qty, 0);
      if (quantity <= 0) return;
      const costBasis = state.layers.reduce((sum, layer) => sum + layer.qty * layer.unitCost, 0);
      totalInvested += costBasis;
      const price = state.currentPrice || (costBasis > 0 ? costBasis / quantity : 0);
      totalCurrent += quantity * price;
    });

    labels.push(format(date, 'dd/MM', { locale: ptBR }));
    investedPoints.push(totalInvested);
    currentPoints.push(totalCurrent);
  });

  return {
    labels,
    datasets: [
      {
        label: 'Valor Patrimonial',
        data: currentPoints,
        borderColor: '#3b82f6',
        backgroundColor: 'rgba(59, 130, 246, 0.1)',
        fill: true,
        tension: 0.4,
        pointRadius: 0,
        pointHoverRadius: 4,
      },
      {
        label: 'Valor Investido',
        data: investedPoints,
        borderColor: '#9ca3af',
        borderDash: [5, 5],
        fill: false,
        tension: 0.4,
        pointRadius: 0,
        pointHoverRadius: 4,
      },
    ],
  };
};

const buildChartDataFromSeries = (series) => {
  if (!series || !series.labels || !series.current || !series.current.length) {
    return null;
  }

  return {
    labels: series.labels,
    datasets: [
      {
        label: 'Valor Patrimonial',
        data: series.current,
        borderColor: '#3b82f6',
        backgroundColor: 'rgba(59, 130, 246, 0.1)',
        fill: true,
        tension: 0.4,
        pointRadius: 0,
        pointHoverRadius: 4,
      },
      {
        label: 'Valor Investido',
        data: series.invested || [],
        borderColor: '#9ca3af',
        borderDash: [5, 5],
        fill: false,
        tension: 0.4,
        pointRadius: 0,
        pointHoverRadius: 4,
      },
    ],
  };
};

export default function Investments() {
  const { user } = useAuth();
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

  const fetchEvolutionData = useCallback(async (periodOverride = chartPeriod) => {
    if (!user || !investments.length) {
      setEvolutionSeries(null);
      return;
    }

    setEvolutionLoading(true);
    setEvolutionError(null);

    try {
      const params = new URLSearchParams({
        userId: user.id,
        period: periodOverride,
      });

      const response = await api.get(`/investments/evolution?${params.toString()}`);
      if (response.success) {
        setEvolutionSeries(response.data || null);
      } else {
        setEvolutionSeries(null);
        setEvolutionError(response.message || 'Não foi possível atualizar o gráfico.');
      }
    } catch (error) {
      setEvolutionSeries(null);
      setEvolutionError(error.message || 'Não foi possível atualizar o gráfico.');
    } finally {
      setEvolutionLoading(false);
    }
  }, [chartPeriod, investments.length, user]);

  useEffect(() => {
    if (user) {
      loadInvestments();
      loadAccounts();
    }
  }, [user]);

  useEffect(() => {
    if (!user) return;
    if (!investments.length) {
      setEvolutionSeries(null);
      return;
    }
    fetchEvolutionData(chartPeriod);
  }, [user, investments.length, chartPeriod, fetchEvolutionData]);

  const loadInvestments = async () => {
    setLoading(true);
    try {
      const response = await api.get(`/investments?userId=${user.id}`);
      if (response.success) {
        setInvestments(response.data || []);
      }
    } catch (error) {
      toast.error('Erro ao carregar investimentos');
    } finally {
      setLoading(false);
    }
  };

  const loadAccounts = async () => {
    try {
      const response = await api.get(`/accounts?userId=${user.id}`);
      if (response.success) {
        setAccounts(response.data || []);
      }
    } catch (error) {
      console.error('Erro ao carregar contas:', error);
    }
  };

  const handleDelete = async (id) => {
    if (!confirm('Tem certeza que deseja excluir este investimento?')) return;
    
    try {
      const response = await api.delete(`/investments?id=${id}`);
      if (response.success) {
        toast.success('Investimento excluído com sucesso');
        loadInvestments();
      }
    } catch (error) {
      toast.error('Erro ao excluir investimento');
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
      return buildChartDataFromSeries(evolutionSeries);
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

    return buildEvolutionSeries(investments, startDate, today);
  }, [evolutionSeries, investments, chartPeriod]);

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
            aportes: [],
            // Aux variables for PM calculation
            totalBuyQty: 0,
            totalBuyCost: 0
        };
    }
    
    const group = acc[category][assetName];
    const quantity = inv.quantidade || 0;
    const isBuy = quantity > 0;
    const isSell = quantity < 0;
    const absQuantity = Math.abs(quantity);
    
    // Update Portfolio Quantity
    group.quantidadeTotal += quantity;
    
    // Update PM Logic
    if (isBuy) {
        group.totalBuyQty += absQuantity;
        group.totalBuyCost += (inv.valorAporte || 0);
    }
    // Sales do not affect PM (weighted average price of buys), they only reduce quantity held.
    
    // Calculate Current PM (Preço Médio)
    // Avoid division by zero
    const currentPM = group.totalBuyQty > 0 ? group.totalBuyCost / group.totalBuyQty : 0;
    group.precoMedio = currentPM;

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
    'ACAO': 'Ações (B3)',
    'STOCK': 'Stocks (Ações Internacionais)',
    'CRYPTO': 'Criptomoedas',
    'FII': 'Fundos Imobiliários',
    'RENDA_FIXA': 'Renda Fixa',
    'OUTROS': 'Outros'
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
    try {
      if (format === 'pdf') {
        const doc = new jsPDF();
        const pageWidth = doc.internal.pageSize.getWidth();
        const margin = 14;
        const palette = {
          primary: [59, 130, 246],
          emerald: [16, 185, 129],
          amber: [245, 158, 11],
          purple: [99, 102, 241],
          slate: [15, 23, 42],
          gray: [55, 65, 81]
        };

        const ensureSpace = (space = 20) => {
          if (yPos + space > doc.internal.pageSize.getHeight() - 20) {
            doc.addPage();
            yPos = 20;
          }
        };

        const drawSectionTitle = (title, color = palette.primary) => {
          ensureSpace(16);
          doc.setFillColor(...color);
          doc.setTextColor(255, 255, 255);
          doc.roundedRect(margin, yPos, pageWidth - margin * 2, 10, 2, 2, 'F');
          doc.setFontSize(11);
          doc.text(title, margin + 4, yPos + 7);
          yPos += 16;
          doc.setTextColor(...palette.slate);
        };

        const drawSummaryCard = (label, value, subtitle, x, y, width, color) => {
          doc.setFillColor(...color);
          doc.roundedRect(x, y, width, 28, 3, 3, 'F');
          doc.setTextColor(255, 255, 255);
          doc.setFontSize(9);
          doc.text(label.toUpperCase(), x + 4, y + 8);
          doc.setFontSize(12);
          doc.text(value, x + 4, y + 17);
          if (subtitle) {
            doc.setFontSize(9);
            doc.text(subtitle, x + 4, y + 24);
          }
        };

        // Hero header
        doc.setFillColor(...palette.primary);
        doc.rect(0, 0, pageWidth, 38, 'F');
        doc.setFontSize(20);
        doc.setTextColor(255, 255, 255);
        doc.text('Relatório de Investimentos', margin, 20);
        doc.setFontSize(10);
        doc.text(`Gerado em: ${new Date().toLocaleDateString('pt-BR')}`, margin, 30);

        // Summary cards
        let yPos = 48;
        const cardWidth = (pageWidth - margin * 2 - 8) / 3;
        drawSummaryCard(
          'Total Investido',
          formatCurrency(displaySummary.totalInvested),
          'Capital ainda aplicado',
          margin,
          yPos,
          cardWidth,
          [6, 78, 59]
        );
        drawSummaryCard(
          'Valor Atual',
          formatCurrency(displaySummary.totalCurrent),
          'Posições vivas no dia',
          margin + cardWidth + 4,
          yPos,
          cardWidth,
          palette.primary
        );
        drawSummaryCard(
          'Retorno Total',
          formatCurrency(displaySummary.totalReturn),
          `${displaySummary.totalReturnPercent.toFixed(2)}% (inclui valores realizados)`,
          margin + (cardWidth + 4) * 2,
          yPos,
          cardWidth,
          palette.emerald
        );
        yPos += 42;

        // Nota
        ensureSpace(18);
        doc.setFontSize(9);
        doc.setTextColor(120, 113, 108);
        doc.text(
          'O retorno mostra a soma dos ganhos/perdas já realizados e o que ainda está em carteira – por isso pode diferir de Valor Atual - Investido.',
          margin,
          yPos,
          { maxWidth: pageWidth - margin * 2 }
        );
        doc.setTextColor(...palette.slate);
        yPos += 14;

        // Seções principais
        drawSectionTitle('Posições Ativas', palette.purple);
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
            head: [['Categoria', 'Ativo', 'Qtd.', 'Preço Médio', 'Valor Atual', 'Valor Investido', 'Retorno %']],
            body: positions.sort((a, b) => parseFloat(b[4].replace(/[^\d,-]/g, '').replace(',', '.')) - parseFloat(a[4].replace(/[^\d,-]/g, '').replace(',', '.'))).slice(0, 18),
            styles: { fontSize: 8 },
            headStyles: { fillColor: palette.purple },
          });
          yPos = doc.lastAutoTable.finalY + 12;
        } else {
          doc.setFontSize(10);
          doc.text('Nenhuma posição ativa no momento.', margin, yPos);
          yPos += 10;
        }

        drawSectionTitle('Distribuição por Categoria', palette.amber);
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
            styles: { fontSize: 8 },
            headStyles: { fillColor: palette.amber },
          });
          yPos = doc.lastAutoTable.finalY + 12;
        } else {
          doc.setFontSize(10);
          doc.text('Não há distribuição para exibir.', margin, yPos);
          yPos += 10;
        }

        drawSectionTitle('Operações Recentes', palette.primary);
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
            styles: { fontSize: 8 },
            headStyles: { fillColor: palette.primary },
          });
          yPos = doc.lastAutoTable.finalY + 12;
        } else {
          doc.setFontSize(10);
          doc.text('Nenhuma operação registrada recentemente.', margin, yPos);
          yPos += 10;
        }

        drawSectionTitle('Detalhes Adicionais', palette.gray);
        doc.setFontSize(10);
        doc.text(`Lucro/Prejuízo Realizado acumulado: ${formatCurrency(calculatedSummary.realizedProfitLoss || 0)}`, margin, yPos);
        yPos += 6;
        doc.text(`Total de operações registradas: ${investments.length}`, margin, yPos);
        yPos += 6;
        doc.text('Este relatório foi gerado automaticamente pelo Controle-se.', margin, yPos);

        doc.save(`relatorio_investimentos_${new Date().toISOString().split('T')[0]}.pdf`);
        toast.success('Relatório exportado como PDF!');
      } else if (format === 'xlsx') {
        // Export as XLSX
        const wb = XLSX.utils.book_new();
        
        // Summary sheet
        const summaryData = [
          ['Relatório de Investimentos'],
          [`Gerado em: ${new Date().toLocaleDateString('pt-BR')}`],
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
                'Valor Atual': group.valorAtualTotal,
                'Valor Investido': group.valorAporteTotal,
                'Retorno': group.retornoTotal,
                'Retorno %': `${group.retornoPercent.toFixed(2)}%`
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

        XLSX.writeFile(wb, `relatorio_investimentos_${new Date().toISOString().split('T')[0]}.xlsx`);
        toast.success('Relatório exportado como XLSX!');
      } else if (format === 'csv') {
        // Export as CSV
        const data = [];
        Object.keys(groupedInvestments).forEach(cat => {
          Object.values(groupedInvestments[cat]).forEach(group => {
            if (group.quantidadeTotal > 0) {
              data.push({
                'Categoria': categoryNames[cat] || cat,
                'Ativo': group.nome,
                'Quantidade': group.quantidadeTotal,
                'Preço Médio': group.precoMedio,
                'Valor Atual': group.valorAtualTotal,
                'Retorno': group.retornoTotal,
                'Retorno %': `${group.retornoPercent.toFixed(2)}%`
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
        toast.success('Relatório exportado como CSV!');
      }
    } catch (error) {
      console.error('Erro ao exportar relatório:', error);
      toast.error('Erro ao exportar relatório');
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
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-2xl sm:text-3xl font-bold text-gray-900 dark:text-white">Investimentos</h2>
          <p className="text-sm sm:text-base text-gray-600 dark:text-gray-400 mt-1">Acompanhe seus investimentos</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <button 
            onClick={() => setShowStatementModal(true)} 
            className="btn-secondary flex items-center gap-2 text-sm sm:text-base px-3 sm:px-4 py-2"
          >
            <FileText className="w-4 h-4" />
            <span className="hidden sm:inline">Extrato</span>
          </button>
          <div className="relative group">
            <button 
              className="btn-secondary flex items-center gap-2 text-sm sm:text-base px-3 sm:px-4 py-2"
            >
              <Download className="w-4 h-4" />
              <span className="hidden sm:inline">Exportar Relatório</span>
              <span className="sm:hidden">Exportar</span>
            </button>
            <div className="absolute right-0 mt-2 w-48 bg-white dark:bg-gray-800 rounded-lg shadow-lg border border-gray-200 dark:border-gray-700 opacity-0 invisible group-hover:opacity-100 group-hover:visible transition-all z-10">
              <button
                onClick={() => exportInvestmentReport('pdf')}
                className="w-full text-left px-4 py-2 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-t-lg flex items-center gap-2"
              >
                <FileText size={16} />
                PDF
              </button>
              <button
                onClick={() => exportInvestmentReport('xlsx')}
                className="w-full text-left px-4 py-2 hover:bg-gray-100 dark:hover:bg-gray-700 flex items-center gap-2"
              >
                <FileSpreadsheet size={16} />
                XLSX
              </button>
              <button
                onClick={() => exportInvestmentReport('csv')}
                className="w-full text-left px-4 py-2 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-b-lg flex items-center gap-2"
              >
                <File size={16} />
                CSV
              </button>
            </div>
          </div>
          <button 
            onClick={() => { setInvestmentToEdit(null); setShowModal(true); }} 
            className="btn-primary flex items-center gap-2 text-sm sm:text-base px-3 sm:px-4 py-2"
          >
            <Plus className="w-4 h-4" />
            <span className="hidden sm:inline">Novo Investimento</span>
            <span className="sm:hidden">Novo</span>
          </button>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <SummaryCard
          title="Total Investido"
          amount={formatCurrency(displaySummary.totalInvested)}
          icon={Wallet}
          type="default"
        />
        <SummaryCard
          title="Valor Atual"
          amount={formatCurrency(displaySummary.totalCurrent)}
          icon={TrendingUp}
          type="balance"
        />
        <SummaryCard
          title="Retorno Total"
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
              aria-label="Informações sobre o retorno total"
            >
              <Info className="w-4 h-4" />
              <span>Entenda</span>
            </button>
          }
        />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Evolution Chart */}
        <div className="lg:col-span-2 card">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between mb-6 gap-4">
            <h3 className="text-base sm:text-lg font-semibold text-gray-900 dark:text-white">Evolução Patrimonial Dos Seus Investimentos</h3>
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
                Atualizando cotações históricas...
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
                    Não conseguimos buscar todas as cotações históricas agora. Exibindo o cálculo local como fallback.
                  </p>
                )}
                {!usingServerSeries && !evolutionError && (
                  <p className="mt-2 text-xs text-gray-500 dark:text-gray-400">
                    Dados calculados apenas com base nos aportes registrados.
                  </p>
                )}
              </>
            ) : (
              <div className="h-full flex items-center justify-center text-gray-500 dark:text-gray-400 text-sm">
                Cadastre seus investimentos para visualizar o gráfico.
              </div>
            )}
          </div>
        </div>

        {/* Assets Allocation Chart */}
        <div className="lg:col-span-1 card">
          <h3 className="text-base sm:text-lg font-semibold text-gray-900 dark:text-white mb-4">Alocação de Ativos</h3>
          {allocationData.length > 0 ? (
            <div className="h-64">
              <Doughnut
                data={{
                  labels: allocationData.map(d => d.category),
                  datasets: [{
                    data: allocationData.map(d => d.value),
                    backgroundColor: [
                      '#3b82f6', '#ef4444', '#fbbf24', '#10b981', '#8b5cf6', '#6b7280'
                    ],
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
            <p className="text-center text-gray-500 py-8">Sem dados para exibir</p>
          )}
        </div>
      </div>

      {/* Investments List */}
      <div className="space-y-6">
        {Object.keys(groupedInvestments).length === 0 ? (
           <div className="card text-center py-12">
             <p className="text-gray-600 dark:text-gray-400">Nenhum investimento cadastrado</p>
           </div>
        ) : (
          Object.entries(groupedInvestments).map(([category, assetsMap]) => {
            // Filter assets with quantity > 0
            const assets = Object.values(assetsMap).filter(asset => asset.quantidadeTotal !== 0);
            if (assets.length === 0) return null;
            
            const isExpanded = expandedCategories[category];
            const displayAssets = isExpanded ? assets : assets.slice(0, 5);
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
                    {assets.map(asset => {
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
                                            {asset.nome} {asset.nomeAtivo && asset.nomeAtivo !== asset.nome && ` - ${asset.nomeAtivo}`}
                                        </span>
                                        {asset.quantidadeTotal > 0 && (
                                            <span className="text-xs text-gray-500 dark:text-gray-400 flex-shrink-0">
                                                {asset.quantidadeTotal} {asset.categoria === 'RENDA_FIXA' ? 'un' : 'cotas'}
                                            </span>
                                        )}
                                    </div>
                                    <div className="text-xs text-gray-500 dark:text-gray-400 mt-1">
                                        Preço Médio: {formatCurrency(asset.precoMedio)}
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
        title="Como calculamos o retorno?"
      >
        <div className="space-y-3 text-sm text-gray-600 dark:text-gray-300">
          <p>
            O <strong>Retorno Total</strong> considera tanto o que ainda está aplicado quanto tudo o que já foi vendido.
          </p>
          <p>
            Por isso, ele soma os ganhos e perdas realizados às variações dos investimentos atuais. Assim, ele pode ser diferente de um cálculo simples de
            <em> valor atual - total investido</em>.
          </p>
          <p>
            Em resumo: se você já resgatou parte dos investimentos, o retorno continua exibindo esse histórico para que você saiba exatamente quanto acumulou.
          </p>
        </div>
      </Modal>
    </div>
  );
}

