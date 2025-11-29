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
  differenceInDays,
  eachDayOfInterval,
  format,
  isAfter,
  parseISO, startOfDay,
  startOfYear,
  subDays,
  subMonths,
  subWeeks,
  subYears
} from 'date-fns';
import { ptBR } from 'date-fns/locale';
import { ChevronDown, ChevronUp, PieChart, Plus, TrendingUp, Wallet } from 'lucide-react';
import React, { useEffect, useMemo, useState } from 'react';
import { Doughnut, Line } from 'react-chartjs-2';
import toast from 'react-hot-toast';
import { useAuth } from '../../contexts/AuthContext';
import api from '../../services/api';
import { formatCurrency } from '../../utils/formatters';
import SummaryCard from '../common/SummaryCard';
import InvestmentModal from './InvestmentModal';
import AssetDetailsModal from './Investments/AssetDetailsModal';

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

export default function Investments() {
  const { user } = useAuth();
  const [investments, setInvestments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [summary, setSummary] = useState({ totalInvested: 0, totalCurrent: 0, totalReturn: 0, totalReturnPercent: 0 });
  const [showModal, setShowModal] = useState(false);
  const [expandedCategories, setExpandedCategories] = useState({});
  const [selectedAssetGroup, setSelectedAssetGroup] = useState(null);
  const [showDetailsModal, setShowDetailsModal] = useState(false);
  const [chartPeriod, setChartPeriod] = useState('1M');

  useEffect(() => {
    if (user) loadInvestments();
  }, [user]);

  const loadInvestments = async () => {
    setLoading(true);
    try {
      const response = await api.get(`/investments?userId=${user.id}`);
      if (response.success) {
        setInvestments(response.data || []);
        if (response.summary) {
            setSummary(response.summary);
        } else {
            calculateSummary(response.data || []);
        }
      }
    } catch (error) {
      toast.error('Erro ao carregar investimentos');
    } finally {
      setLoading(false);
    }
  };

  const calculateSummary = (investments) => {
    const totalInvested = investments.reduce((sum, inv) => sum + (inv.valorAporte || 0), 0);
    const totalCurrent = investments.reduce((sum, inv) => sum + (inv.valorAtual || 0), 0);
    const totalReturn = totalCurrent - totalInvested;
    const totalReturnPercent = totalInvested > 0 ? (totalReturn / totalInvested) * 100 : 0;

    setSummary({ totalInvested, totalCurrent, totalReturn, totalReturnPercent });
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
      case 'ALL': 
        const dates = investments.map(i => parseISO(i.dataAporte));
        startDate = dates.reduce((min, d) => d < min ? d : min, today);
        break;
      default: startDate = subMonths(today, 1);
    }

    const interval = eachDayOfInterval({ start: startDate, end: today });
    
    const dataPoints = interval.map(date => {
      let invested = 0;
      let current = 0;

      investments.forEach(inv => {
        const aporteDate = startOfDay(parseISO(inv.dataAporte));
        
        if (isAfter(aporteDate, date)) return;

        // Valor Investido: soma simples se a data do aporte for anterior ou igual à data atual do loop
        invested += (inv.valorAporte || 0);

        // Valor Atual: Interpolação linear
        const valorAporte = inv.valorAporte || 0;
        const valorAtualFinal = inv.valorAtual || 0;
        
        const totalDays = differenceInDays(today, aporteDate);
        const daysPassed = differenceInDays(date, aporteDate);
        
        if (totalDays <= 0) {
          current += valorAtualFinal;
        } else {
          const growth = valorAtualFinal - valorAporte;
          const currentGrowth = (growth * daysPassed) / totalDays;
          current += (valorAporte + currentGrowth);
        }
      });

      return {
        date: format(date, 'dd/MM', { locale: ptBR }),
        fullDate: format(date, 'dd/MM/yyyy', { locale: ptBR }),
        invested,
        current
      };
    });

    return {
      labels: dataPoints.map(d => d.date),
      datasets: [
        {
          label: 'Valor Patrimonial',
          data: dataPoints.map(d => d.current),
          borderColor: '#3b82f6',
          backgroundColor: 'rgba(59, 130, 246, 0.1)',
          fill: true,
          tension: 0.4,
          pointRadius: 0,
          pointHoverRadius: 4
        },
        {
          label: 'Valor Investido',
          data: dataPoints.map(d => d.invested),
          borderColor: '#9ca3af',
          borderDash: [5, 5],
          fill: false,
          tension: 0.4,
          pointRadius: 0,
          pointHoverRadius: 4
        }
      ]
    };
  }, [investments, chartPeriod]);

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
  const groupedInvestments = investments.reduce((acc, inv) => {
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

  // Calculate averages and totals for groups
  Object.keys(groupedInvestments).forEach(cat => {
    Object.values(groupedInvestments[cat]).forEach(group => {
        // Recalculate totals based on final quantity and current price
        // We need the current price.
        // Since we don't have a direct "current price" field in the group, we infer it from the latest update or recalculate.
        // Actually, `inv.valorAtual` from backend is `qty * currentPrice`.
        // So `currentPrice` = `inv.valorAtual / inv.quantidade` (if qty != 0).
        
        let currentPrice = 0;
        // Find a valid current price from any active holding or valid quote
        const validAporte = group.aportes.find(a => a.precoAtual > 0);
        if (validAporte) {
            currentPrice = validAporte.precoAtual;
        }

        // Total Current Value = Current Quantity * Current Price
        group.valorAtualTotal = group.quantidadeTotal * currentPrice;

        // Total Invested (Cost Basis) = Current Quantity * Average Price
        // This represents the cost of the shares we CURRENTLY own.
        group.valorAporteTotal = group.quantidadeTotal * group.precoMedio;

        // Total Return = Current Value - Cost Basis
        group.retornoTotal = group.valorAtualTotal - group.valorAporteTotal;

        group.retornoPercent = group.valorAporteTotal > 0 
            ? (group.retornoTotal / group.valorAporteTotal) * 100 
            : 0;
    });
  });

  const categoryNames = {
    'ACAO': 'Ações (B3)',
    'STOCK': 'Stocks (NYSE/NASDAQ)',
    'CRYPTO': 'Criptomoedas',
    'FII': 'Fundos Imobiliários',
    'RENDA_FIXA': 'Renda Fixa',
    'OUTROS': 'Outros'
  };

  // Calculate allocation for chart
  const allocationData = [];
  Object.keys(groupedInvestments).forEach(cat => {
      let catTotal = 0;
      Object.values(groupedInvestments[cat]).forEach(group => {
          catTotal += group.valorAtualTotal;
      });
      allocationData.push({
          category: categoryNames[cat] || cat,
          value: catTotal
      });
  });
  allocationData.sort((a, b) => b.value - a.value);

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
          <h2 className="text-3xl font-bold text-gray-900 dark:text-white">Investimentos</h2>
          <p className="text-gray-600 dark:text-gray-400 mt-1">Acompanhe seus investimentos</p>
        </div>
        <button onClick={() => { setInvestmentToEdit(null); setShowModal(true); }} className="btn-primary">
          <Plus className="w-4 h-4" />
          Novo Investimento
        </button>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <SummaryCard
          title="Total Investido"
          amount={formatCurrency(summary.totalInvested)}
          icon={Wallet}
          type="default"
        />
        <SummaryCard
          title="Valor Atual"
          amount={formatCurrency(summary.totalCurrent)}
          icon={TrendingUp}
          type="balance"
        />
        <SummaryCard
          title="Retorno Total"
          amount={formatCurrency(summary.totalReturn)}
          icon={PieChart}
          type={summary.totalReturn >= 0 ? 'income' : 'expense'}
          subtitle={`${summary.totalReturnPercent.toFixed(2)}%`}
        />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Evolution Chart */}
        <div className="lg:col-span-2 card">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between mb-6 gap-4">
            <h3 className="text-lg font-semibold text-gray-900 dark:text-white">Evolução Patrimonial Dos Seus Investimentos</h3>
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
            {evolutionChartData ? (
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
            ) : (
              <div className="h-full flex items-center justify-center text-gray-500">
                Carregando gráfico...
              </div>
            )}
          </div>
        </div>

        {/* Assets Allocation Chart */}
        <div className="lg:col-span-1 card">
          <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">Alocação de Ativos</h3>
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
            const assets = Object.values(assetsMap);
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
                  className="flex justify-between items-center p-4 -m-6 mb-0 cursor-pointer hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors"
                  onClick={() => toggleCategory(category)}
                >
                  <div className="flex items-center gap-2">
                      {isExpanded ? <ChevronUp size={20} className="text-gray-400" /> : <ChevronDown size={20} className="text-gray-400" />}
                      <h3 className="text-lg font-bold text-gray-900 dark:text-white">
                          {categoryNames[category] || category}
                      </h3>
                      <span className="text-xs bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-300 px-2 py-0.5 rounded-full">
                          {assets.length}
                      </span>
                  </div>
                  <div className="text-right">
                    <div className="font-bold text-gray-900 dark:text-white">
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
                                <div className="flex-1">
                                    <div className="flex justify-between sm:justify-start sm:gap-4 items-center">
                                        <span className="font-semibold text-gray-900 dark:text-white">
                                            {asset.nome} {asset.nomeAtivo && asset.nomeAtivo !== asset.nome && ` - ${asset.nomeAtivo}`}
                                        </span>
                                        {asset.quantidadeTotal > 0 && (
                                            <span className="text-xs text-gray-500 dark:text-gray-400">
                                                {asset.quantidadeTotal} {asset.categoria === 'RENDA_FIXA' ? 'un' : 'cotas'}
                                            </span>
                                        )}
                                    </div>
                                    <div className="text-xs text-gray-500 dark:text-gray-400 mt-1">
                                        Preço Médio: {formatCurrency(asset.precoMedio)}
                                    </div>
                                </div>
                                
                                <div className="flex justify-between sm:justify-end items-center gap-4">
                                    <div className="text-right">
                                        <div className="font-bold text-gray-900 dark:text-white">
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
    </div>
  );
}

