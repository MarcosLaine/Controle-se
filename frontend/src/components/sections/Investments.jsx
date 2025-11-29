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
import { ChevronDown, ChevronUp, PieChart, Plus, TrendingUp, Wallet, FileText, Download, FileSpreadsheet, File } from 'lucide-react';
import React, { useEffect, useMemo, useState } from 'react';
import { Doughnut, Line } from 'react-chartjs-2';
import toast from 'react-hot-toast';
import { useAuth } from '../../contexts/AuthContext';
import api from '../../services/api';
import { formatCurrency, formatDate } from '../../utils/formatters';
import SummaryCard from '../common/SummaryCard';
import InvestmentModal from './InvestmentModal';
import AssetDetailsModal from './Investments/AssetDetailsModal';
import InvestmentStatementModal from './Investments/InvestmentStatementModal';
import jsPDF from 'jspdf';
import 'jspdf-autotable';
import * as XLSX from 'xlsx';
import html2canvas from 'html2canvas';

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
  const [showStatementModal, setShowStatementModal] = useState(false);
  const [accounts, setAccounts] = useState([]);
  const [chartPeriod, setChartPeriod] = useState('1M');

  useEffect(() => {
    if (user) {
      loadInvestments();
      loadAccounts();
    }
  }, [user]);

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
  const groupedInvestments = useMemo(() => {
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

  // Calculate averages and totals for groups
  Object.keys(grouped).forEach(cat => {
    Object.values(grouped[cat]).forEach(group => {
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

  // Filter out investments with zero total quantity (completely sold)
  Object.keys(grouped).forEach(cat => {
    Object.keys(grouped[cat]).forEach(assetName => {
      if (grouped[cat][assetName].quantidadeTotal === 0) {
        delete grouped[cat][assetName];
      }
    });
    // Remove empty categories
    if (Object.keys(grouped[cat]).length === 0) {
      delete grouped[cat];
    }
  });

  return grouped;
  }, [investments]);

  const categoryNames = {
    'ACAO': 'Ações (B3)',
    'STOCK': 'Stocks (NYSE/NASDAQ)',
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
    let realizedProfitLoss = 0; // Lucros/prejuízos realizados de investimentos completamente vendidos
    
    // Calculate for active investments (quantity > 0)
    Object.keys(groupedInvestments).forEach(cat => {
      Object.values(groupedInvestments[cat]).forEach(group => {
        if (group.quantidadeTotal !== 0) {
          totalInvested += group.valorAporteTotal;
          totalCurrent += group.valorAtualTotal;
        }
      });
    });
    
    // Calculate realized profit/loss from completely sold investments using FIFO method
    const assetTransactions = {};
    investments.forEach(inv => {
      const key = `${inv.categoria || 'OUTROS'}_${inv.nome}`;
      if (!assetTransactions[key]) {
        assetTransactions[key] = { buys: [], sells: [], totalQty: 0 };
      }
      
      if (inv.quantidade > 0) {
        assetTransactions[key].buys.push({
          qty: inv.quantidade,
          cost: inv.valorAporte || 0,
          date: inv.dataAporte
        });
        assetTransactions[key].totalQty += inv.quantidade;
      } else if (inv.quantidade < 0) {
        assetTransactions[key].sells.push({
          qty: Math.abs(inv.quantidade),
          revenue: (inv.valorAporte || 0) - (inv.corretagem || 0),
          date: inv.dataAporte
        });
        assetTransactions[key].totalQty += inv.quantidade;
      }
    });
    
    // Calculate realized P&L using FIFO method for completely sold assets
    Object.values(assetTransactions).forEach(asset => {
      if (asset.totalQty === 0 && asset.sells.length > 0) {
        // Asset completely sold, calculate realized P&L
        let remainingBuys = [...asset.buys].sort((a, b) => new Date(a.date) - new Date(b.date));
        
        asset.sells.forEach(sell => {
          let remainingSellQty = sell.qty;
          let sellCostBasis = 0;
          
          while (remainingSellQty > 0 && remainingBuys.length > 0) {
            const buy = remainingBuys[0];
            const qtyToUse = Math.min(remainingSellQty, buy.qty);
            const buyPricePerUnit = buy.qty > 0 ? buy.cost / buy.qty : 0;
            sellCostBasis += buyPricePerUnit * qtyToUse;
            
            buy.qty -= qtyToUse;
            remainingSellQty -= qtyToUse;
            
            if (buy.qty === 0) {
              remainingBuys.shift();
            }
          }
          
          const realizedPL = sell.revenue - sellCostBasis;
          realizedProfitLoss += realizedPL;
        });
      }
    });
    
    const totalReturn = totalCurrent - totalInvested + realizedProfitLoss;
    const totalReturnPercent = totalInvested > 0 ? (totalReturn / totalInvested) * 100 : 0;
    
    return { 
      totalInvested, 
      totalCurrent, 
      totalReturn, 
      totalReturnPercent,
      realizedProfitLoss 
    };
  }, [groupedInvestments, investments]);

  // Use calculated summary if available, otherwise use state
  const displaySummary = Object.keys(groupedInvestments).length > 0 ? calculatedSummary : summary;

  const exportInvestmentReport = async (format = 'xlsx') => {
    try {
      if (format === 'pdf') {
        // Export as PDF with charts
        const doc = new jsPDF();
        
        // Title
        doc.setFontSize(18);
        doc.text('Relatório de Investimentos', 14, 20);
        doc.setFontSize(10);
        doc.text(`Gerado em: ${new Date().toLocaleDateString('pt-BR')}`, 14, 30);
        
        // Summary
        let yPos = 40;
        doc.setFontSize(12);
        doc.text('Resumo', 14, yPos);
        doc.setFontSize(10);
        yPos += 10;
        doc.text(`Total Investido: ${formatCurrency(displaySummary.totalInvested)}`, 14, yPos);
        yPos += 7;
        doc.text(`Valor Atual: ${formatCurrency(displaySummary.totalCurrent)}`, 14, yPos);
        yPos += 7;
        doc.text(`Retorno Total: ${formatCurrency(displaySummary.totalReturn)}`, 14, yPos);
        yPos += 7;
        doc.text(`Retorno %: ${displaySummary.totalReturnPercent.toFixed(2)}%`, 14, yPos);
        yPos += 15;

        // Charts - we'll capture them as images
        const chartElements = document.querySelectorAll('canvas');
        if (chartElements.length > 0) {
          for (let i = 0; i < Math.min(chartElements.length, 2); i++) {
            const canvas = chartElements[i];
            try {
              const imgData = await html2canvas(canvas.parentElement, { 
                backgroundColor: '#ffffff',
                scale: 2 
              }).then(canvas => canvas.toDataURL('image/png'));
              
              if (yPos > 250) {
                doc.addPage();
                yPos = 20;
              }
              
              const imgWidth = 180;
              const imgHeight = (canvas.height / canvas.width) * imgWidth;
              doc.addImage(imgData, 'PNG', 14, yPos, imgWidth, imgHeight);
              yPos += imgHeight + 10;
            } catch (err) {
              console.error('Erro ao capturar gráfico:', err);
            }
          }
        }

        // Investments by category
        if (yPos > 250) {
          doc.addPage();
          yPos = 20;
        }
        doc.setFontSize(12);
        doc.text('Investimentos por Categoria', 14, yPos);
        yPos += 10;

        const categoryData = [];
        Object.keys(groupedInvestments).forEach(cat => {
          Object.values(groupedInvestments[cat]).forEach(group => {
            if (group.quantidadeTotal > 0) {
              categoryData.push([
                categoryNames[cat] || cat,
                group.nome,
                group.quantidadeTotal.toFixed(6),
                formatCurrency(group.precoMedio),
                formatCurrency(group.valorAtualTotal),
                formatCurrency(group.retornoTotal),
                `${group.retornoPercent.toFixed(2)}%`
              ]);
            }
          });
        });

        doc.autoTable({
          startY: yPos,
          head: [['Categoria', 'Ativo', 'Quantidade', 'Preço Médio', 'Valor Atual', 'Retorno', 'Retorno %']],
          body: categoryData,
          styles: { fontSize: 8 },
          headStyles: { fillColor: [59, 130, 246] }
        });

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
    </div>
  );
}

