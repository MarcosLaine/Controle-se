import React, { useState, useMemo, useRef, useEffect } from 'react';
import { Calculator, FileText, Save, Download, TrendingUp, DollarSign, Percent, Calendar, History, Trash2, Loader2 } from 'lucide-react';
import { useAuth } from '../../contexts/AuthContext';
import { useLanguage } from '../../contexts/LanguageContext';
import api from '../../services/api';
import { formatCurrency, formatDate } from '../../utils/formatters';
import toast from 'react-hot-toast';
import Modal from '../common/Modal';
import Spinner from '../common/Spinner';
import { Line } from 'react-chartjs-2';
import {
  Chart as ChartJS,
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend,
  Filler,
} from 'chart.js';
import jsPDF from 'jspdf';
import autoTable from 'jspdf-autotable';
import html2canvas from 'html2canvas';

ChartJS.register(
  CategoryScale,
  LinearScale,
  PointElement,
  LineElement,
  Title,
  Tooltip,
  Legend,
  Filler
);

export default function Tools() {
  const { t } = useLanguage();
  const [activeTab, setActiveTab] = useState('juros-compostos');
  const chartRef = useRef(null);

  return (
    <div className="space-y-6 animate-fade-in">
      <div>
        <h2 className="text-3xl font-bold text-gray-900 dark:text-white">
          {t('tools.title')}
        </h2>
        <p className="text-gray-600 dark:text-gray-400 mt-1">
          {t('tools.subtitle')}
        </p>
      </div>

      {/* Tabs */}
      <div className="border-b border-gray-200 dark:border-gray-700">
        <nav className="flex space-x-8">
          <button
            onClick={() => setActiveTab('juros-compostos')}
            className={`py-4 px-1 border-b-2 font-medium text-sm transition-colors ${
              activeTab === 'juros-compostos'
                ? 'border-primary-600 text-primary-600'
                : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300 dark:text-gray-400 dark:hover:text-gray-300'
            }`}
          >
            <div className="flex items-center gap-2">
              <TrendingUp className="w-4 h-4" />
              {t('tools.compoundInterestCalculator')}
            </div>
          </button>
          <button
            onClick={() => setActiveTab('ir')}
            className={`py-4 px-1 border-b-2 font-medium text-sm transition-colors ${
              activeTab === 'ir'
                ? 'border-primary-600 text-primary-600'
                : 'border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300 dark:text-gray-400 dark:hover:text-gray-300'
            }`}
          >
            <div className="flex items-center gap-2">
              <Calculator className="w-4 h-4" />
              {t('tools.irCalculator')}
            </div>
          </button>
        </nav>
      </div>

      {/* Tab Content */}
      <div className="mt-6">
        {activeTab === 'juros-compostos' && <JurosCompostosCalculator chartRef={chartRef} />}
        {activeTab === 'ir' && <IRCalculatorPlaceholder />}
      </div>
    </div>
  );
}

function IRCalculatorPlaceholder() {
  return (
    <div className="card text-center py-16">
      <Calculator className="w-16 h-16 mx-auto text-gray-400 mb-4" />
      <h3 className="text-xl font-semibold text-gray-900 dark:text-white mb-2">
        {t('tools.irCalculator')}
      </h3>
      <p className="text-gray-600 dark:text-gray-400">
        {t('tools.irCalculatorPlaceholder')}
      </p>
    </div>
  );
}

function JurosCompostosCalculator({ chartRef }) {
  const { user } = useAuth();
  const { t } = useLanguage();
  const [formData, setFormData] = useState({
    aporteInicial: '',
    aporteMensal: '',
    frequenciaAporte: 'mensal', // mensal, quinzenal, anual
    taxaJuros: '',
    tipoTaxa: 'anual', // mensal, anual
    prazo: '',
    tipoPrazo: 'anos', // meses, anos
  });

  const [results, setResults] = useState(null);
  const [loading, setLoading] = useState(false);
  const [showHistoryModal, setShowHistoryModal] = useState(false);
  const [history, setHistory] = useState([]);
  const [loadingHistory, setLoadingHistory] = useState(false);
  const [deletingHistoryIds, setDeletingHistoryIds] = useState(new Set());

  // Cálculo dos juros compostos
  const calculateResults = () => {
    const aporteInicial = parseFloat(formData.aporteInicial) || 0;
    const aporteMensal = parseFloat(formData.aporteMensal) || 0;
    const taxaJuros = parseFloat(formData.taxaJuros) || 0;
    const prazo = parseInt(formData.prazo) || 0;

    if (prazo <= 0 || taxaJuros < 0 || aporteInicial < 0 || aporteMensal < 0) {
      return null;
    }

    // Converter taxa para mensal se necessário
    let taxaMensal = taxaJuros / 100;
    if (formData.tipoTaxa === 'anual') {
      taxaMensal = Math.pow(1 + taxaJuros / 100, 1 / 12) - 1;
    }

    // Converter prazo para meses se necessário
    let meses = prazo;
    if (formData.tipoPrazo === 'anos') {
      meses = prazo * 12;
    }

    const monthlyData = [];
    let saldo = aporteInicial;
    let totalInvestido = aporteInicial;
    let totalJuros = 0;

    // Taxa para quinzenal (meio mês)
    const taxaQuinzenal = Math.pow(1 + taxaMensal, 1 / 2) - 1;

    for (let mes = 0; mes <= meses; mes++) {
      if (mes > 0) {
        if (formData.frequenciaAporte === 'quinzenal') {
          // Aportes quinzenais: 2 por mês
          // Primeira quinzena: aplica juros e adiciona aporte
          const jurosQuinzena1 = saldo * taxaQuinzenal;
          saldo += jurosQuinzena1;
          totalJuros += jurosQuinzena1;
          saldo += aporteMensal;
          totalInvestido += aporteMensal;

          // Segunda quinzena: aplica juros e adiciona aporte
          const jurosQuinzena2 = saldo * taxaQuinzenal;
          saldo += jurosQuinzena2;
          totalJuros += jurosQuinzena2;
          saldo += aporteMensal;
          totalInvestido += aporteMensal;
        } else if (formData.frequenciaAporte === 'mensal') {
          // Aporte mensal: aplica juros e depois adiciona aporte
          const jurosDoMes = saldo * taxaMensal;
          saldo += jurosDoMes;
          totalJuros += jurosDoMes;
          saldo += aporteMensal;
          totalInvestido += aporteMensal;
        } else if (formData.frequenciaAporte === 'anual') {
          // Aporte anual: aplica juros todos os meses, aporte apenas no primeiro mês de cada ano
          const jurosDoMes = saldo * taxaMensal;
          saldo += jurosDoMes;
          totalJuros += jurosDoMes;
          
          // Aporte anual: no início de cada ano (mês 1, 13, 25, etc)
          if ((mes - 1) % 12 === 0) {
            saldo += aporteMensal;
            totalInvestido += aporteMensal;
          }
        }
      }

      monthlyData.push({
        mes,
        saldo: saldo,
        investido: totalInvestido,
        juros: totalJuros,
      });
    }

    return {
      monthlyData,
      totalInvestido,
      saldoFinal: saldo,
      totalJuros,
    };
  };

  const resultsData = useMemo(() => {
    if (!formData.prazo || !formData.taxaJuros) {
      return null;
    }
    return calculateResults();
  }, [formData]);

  // Atualiza results quando resultsData muda
  React.useEffect(() => {
    setResults(resultsData);
  }, [resultsData]);

  // Dados do gráfico
  const chartData = useMemo(() => {
    if (!results || !results.monthlyData || results.monthlyData.length === 0) {
      return null;
    }

    const labels = results.monthlyData.map((_, index) => {
      const meses = index;
      if (meses === 0) return 'Início';
      if (meses < 12) return `${meses}M`;
      const anos = Math.floor(meses / 12);
      const mesesRestantes = meses % 12;
      if (mesesRestantes === 0) return `${anos}A`;
      return `${anos}A ${mesesRestantes}M`;
    });

    return {
      labels,
      datasets: [
        {
          label: t('tools.totalBalance'),
          data: results.monthlyData.map((d) => d.saldo),
          borderColor: '#3b82f6',
          backgroundColor: 'rgba(59, 130, 246, 0.1)',
          fill: true,
          tension: 0.4,
          pointRadius: 0,
          pointHoverRadius: 4,
        },
        {
          label: t('tools.investedValue'),
          data: results.monthlyData.map((d) => d.investido),
          borderColor: '#9ca3af',
          borderDash: [5, 5],
          fill: false,
          tension: 0.4,
          pointRadius: 0,
          pointHoverRadius: 4,
        },
      ],
    };
  }, [results]);

  const handleCalculate = () => {
    const calculated = calculateResults();
    if (!calculated) {
      toast.error(t('tools.fillAllFields'));
      return;
    }
    setResults(calculated);
    toast.success(t('tools.calculationSuccess'));
  };

  const handleSaveToHistory = async () => {
    if (!user || !results) {
      toast.error(t('tools.mustBeLoggedIn'));
      return;
    }

    try {
      setLoading(true);
      const payload = {
        userId: user.id,
        aporteInicial: parseFloat(formData.aporteInicial) || 0,
        aporteMensal: parseFloat(formData.aporteMensal) || 0,
        frequenciaAporte: formData.frequenciaAporte,
        taxaJuros: parseFloat(formData.taxaJuros) || 0,
        tipoTaxa: formData.tipoTaxa,
        prazo: parseInt(formData.prazo) || 0,
        tipoPrazo: formData.tipoPrazo,
        totalInvestido: results.totalInvestido,
        saldoFinal: results.saldoFinal,
        totalJuros: results.totalJuros,
        monthlyData: results.monthlyData,
      };

      const response = await api.post('/tools/compound-interest', payload);
      if (response.success) {
        toast.success(t('tools.calculationSaved'));
        // Recarrega o histórico se o modal estiver aberto
        if (showHistoryModal) {
          loadHistory();
        }
      } else {
        toast.error(t('tools.errorSaving'));
      }
    } catch (error) {
      console.error('Erro ao salvar:', error);
      toast.error(t('tools.errorSavingHistory'));
    } finally {
      setLoading(false);
    }
  };

  const loadHistory = async () => {
    if (!user) return;
    
    try {
      setLoadingHistory(true);
      const response = await api.get(`/tools/compound-interest?userId=${user.id}`);
      if (response.success) {
        setHistory(response.data || []);
      } else {
        toast.error(t('tools.errorLoadingHistory'));
        setHistory([]);
      }
    } catch (error) {
      console.error('Erro ao carregar histórico:', error);
      toast.error('Erro ao carregar histórico');
      setHistory([]);
    } finally {
      setLoadingHistory(false);
    }
  };

  const handleDeleteFromHistory = async (idCalculo) => {
    if (!confirm(t('common.deleteCalculationConfirm'))) {
      return;
    }

    // Adiciona o ID ao set para mostrar loading
    setDeletingHistoryIds(prev => {
      const next = new Set(prev);
      next.add(idCalculo);
      return next;
    });

    try {
      const response = await api.delete(`/tools/compound-interest?id=${idCalculo}`);
      if (response.success) {
        toast.success(t('tools.calculationDeleted'));
        loadHistory();
      } else {
        toast.error(t('tools.errorDeletingCalculation'));
      }
    } catch (error) {
      console.error('Erro ao excluir:', error);
      toast.error(t('tools.errorDeletingCalculationHistory'));
    } finally {
      setDeletingHistoryIds(prev => {
        const next = new Set(prev);
        next.delete(idCalculo);
        return next;
      });
    }
  };

  const handleLoadFromHistory = (calculo) => {
    setFormData({
      aporteInicial: calculo.aporteInicial?.toString() || '',
      aporteMensal: calculo.aporteMensal?.toString() || '',
      frequenciaAporte: calculo.frequenciaAporte || 'mensal',
      taxaJuros: calculo.taxaJuros?.toString() || '',
      tipoTaxa: calculo.tipoTaxa || 'anual',
      prazo: calculo.prazo?.toString() || '',
      tipoPrazo: calculo.tipoPrazo || 'anos',
    });
    
    // Se houver monthlyData, recalcula os resultados
    if (calculo.monthlyData && calculo.monthlyData.length > 0) {
      setResults({
        totalInvestido: calculo.totalInvestido,
        saldoFinal: calculo.saldoFinal,
        totalJuros: calculo.totalJuros,
        monthlyData: calculo.monthlyData,
      });
    }
    
    setShowHistoryModal(false);
    toast.success(t('tools.calculationLoaded'));
  };

  useEffect(() => {
    if (showHistoryModal && user) {
      loadHistory();
    }
  }, [showHistoryModal, user]);

  const handleExportPDF = async () => {
    if (!results) {
      toast.error(t('tools.performCalculationFirst'));
      return;
    }

    try {
      // Detectar tema atual
      const isDark = document.documentElement.classList.contains('dark') || 
                     localStorage.getItem('theme') === 'dark';
      
      const doc = new jsPDF();
      const margin = 14;
      const pageWidth = doc.internal.pageSize.getWidth();
      let yPos = margin;

      // Definir cor de fundo da página baseado no tema
      if (isDark) {
        doc.setFillColor(17, 24, 39); // gray-900
        doc.rect(0, 0, pageWidth, doc.internal.pageSize.getHeight(), 'F');
      }

      // Paleta de cores baseada no tema
      const palette = isDark ? {
        // Modo Escuro
        primary: [34, 197, 94],              // green-500 (mais claro no escuro)
        primaryLight: [22, 101, 52],        // green-900/30
        green: [34, 197, 94],               // green-500
        greenLight: [22, 101, 52],          // green-900/30
        blue: [96, 165, 250],               // blue-400 (mais claro)
        blueLight: [30, 58, 138],           // blue-900/30
        purple: [196, 181, 253],            // purple-300 (mais claro)
        purpleLight: [88, 28, 135],         // purple-900/30
        gray: [156, 163, 175],              // gray-400
        grayLight: [55, 65, 81],            // gray-700 (cabeçalhos mais visíveis)
        grayBorder: [75, 85, 101],         // gray-600 (bordas mais visíveis)
        darkGray: [17, 24, 39],             // gray-900
        textPrimary: [243, 244, 246],       // gray-100
        textSecondary: [209, 213, 219],     // gray-300 (mais visível)
        white: [31, 41, 55],                 // gray-800 (fundo de cards)
        whiteAlt: [39, 49, 63],              // gray-750 (linhas alternadas)
        pageBg: [17, 24, 39],               // gray-900
      } : {
        // Modo Claro
        primary: [22, 163, 74],              // primary-600 (verde)
        primaryLight: [240, 253, 244],       // primary-50
        green: [34, 197, 94],                // green-500
        greenLight: [240, 253, 244],         // green-50
        blue: [59, 130, 246],                 // blue-500
        blueLight: [239, 246, 255],          // blue-50
        purple: [168, 85, 247],              // purple-500
        purpleLight: [250, 245, 255],        // purple-50
        gray: [107, 114, 128],                // gray-500
        grayLight: [249, 250, 251],          // gray-50
        grayBorder: [229, 231, 235],         // gray-200
        darkGray: [31, 41, 55],              // gray-800
        textPrimary: [17, 24, 39],           // gray-900
        textSecondary: [107, 114, 128],      // gray-500
        white: [255, 255, 255],
        pageBg: [255, 255, 255],
      };

      // Função para adicionar nova página com fundo escuro se necessário
      const addNewPage = () => {
        doc.addPage();
        if (isDark) {
          doc.setFillColor(17, 24, 39); // gray-900
          doc.rect(0, 0, pageWidth, doc.internal.pageSize.getHeight(), 'F');
        }
        yPos = margin;
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
          doc.text(subtitle, x + 6, y + 26);
        }
      };

      // Header minimalista estilo app
      doc.setFontSize(24);
      doc.setFont(undefined, 'bold');
      doc.setTextColor(...palette.textPrimary);
      doc.text('Calculadora de Juros Compostos', margin, yPos);
      yPos += 8;
      
      doc.setFontSize(9);
      doc.setFont(undefined, 'normal');
      doc.setTextColor(...palette.textSecondary);
      doc.text(`Gerado em ${new Date().toLocaleDateString('pt-BR', { 
        day: '2-digit', 
        month: 'long', 
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit'
      })}`, margin, yPos);
      yPos += 16;

      // Cards de Resumo (4 cards lado a lado)
      const cardWidth = (pageWidth - margin * 2 - 12) / 4;
      const cardSpacing = 4;
      
      drawSummaryCard(
        t('tools.totalInvested'),
        formatCurrency(results.totalInvestido),
        t('tools.contributionsMade'),
        margin,
        yPos,
        cardWidth,
        palette.white,
        palette.grayBorder,
        palette.textSecondary
      );
      
      drawSummaryCard(
        t('tools.finalBalance'),
        formatCurrency(results.saldoFinal),
        t('tools.accumulatedValue'),
        margin + cardWidth + cardSpacing,
        yPos,
        cardWidth,
        palette.blueLight,
        isDark ? [96, 165, 250] : [59, 130, 246],
        palette.textSecondary
      );
      
      drawSummaryCard(
        t('tools.totalInterest'),
        formatCurrency(results.totalJuros),
        t('tools.earnings'),
        margin + (cardWidth + cardSpacing) * 2,
        yPos,
        cardWidth,
        palette.greenLight,
        isDark ? [34, 197, 94] : [34, 197, 94],
        palette.textSecondary
      );
      
      const rentabilidade = ((results.totalJuros / results.totalInvestido) * 100).toFixed(2);
      drawSummaryCard(
        t('tools.profitability'),
        `${rentabilidade}%`,
        t('tools.onInvested'),
        margin + (cardWidth + cardSpacing) * 3,
        yPos,
        cardWidth,
        palette.purpleLight,
        isDark ? [196, 181, 253] : [168, 85, 247],
        palette.textSecondary
      );
      
      yPos += 40;

      // Seção de Parâmetros estilo card
      const paramCardHeight = Math.ceil(5 / 2) * 10 + 12;
      doc.setFillColor(...palette.white);
      doc.setDrawColor(...palette.grayBorder);
      doc.setLineWidth(0.5);
      doc.roundedRect(margin, yPos, pageWidth - margin * 2, paramCardHeight, 4, 4, 'FD');
      
      yPos += 8;
      doc.setFontSize(14);
      doc.setFont(undefined, 'bold');
      doc.setTextColor(...palette.textPrimary);
      doc.text(t('tools.calculationParameters'), margin + 4, yPos);
      yPos += 10;
      
      doc.setFontSize(9);
      doc.setFont(undefined, 'normal');
      
      const params = [
        { label: t('tools.initialContribution'), value: formatCurrency(parseFloat(formData.aporteInicial) || 0) },
        { label: t('tools.monthlyContribution'), value: formatCurrency(parseFloat(formData.aporteMensal) || 0) },
        { label: t('tools.contributionFrequency'), value: formData.frequenciaAporte === 'mensal' ? t('tools.monthly') : formData.frequenciaAporte === 'quinzenal' ? t('tools.biweekly') : t('tools.annual') },
        { label: t('tools.interestRate'), value: `${formData.taxaJuros}% ${formData.tipoTaxa === 'mensal' ? t('tools.monthly') : t('tools.annual')}` },
        { label: t('tools.period'), value: `${formData.prazo} ${formData.tipoPrazo === 'meses' ? t('tools.months') : t('tools.years')}` },
      ];

      // Desenha parâmetros em duas colunas
      const paramColWidth = (pageWidth - margin * 2 - 20) / 2;
      params.forEach((param, index) => {
        const col = index % 2;
        const row = Math.floor(index / 2);
        const x = margin + 8 + col * (paramColWidth + 8);
        const y = yPos + row * 10;
        
        doc.setFont(undefined, 'normal');
        doc.setFontSize(8);
        doc.setTextColor(...palette.textSecondary);
        doc.text(param.label + ':', x, y);
        doc.setFont(undefined, 'bold');
        doc.setTextColor(...palette.textPrimary);
        doc.text(param.value, x + 45, y);
      });
      
      yPos += Math.ceil(params.length / 2) * 10 + 12;

      // Capturar gráfico
      if (chartRef.current) {
        try {
          // Adiciona nova página se necessário
          if (yPos > 200) {
            addNewPage();
          }
          
          // Título da seção do gráfico
          doc.setFontSize(14);
          doc.setFont(undefined, 'bold');
          doc.setTextColor(...palette.textPrimary);
          doc.text('Evolução do Investimento', margin, yPos);
          yPos += 10;
          
          const canvas = await html2canvas(chartRef.current, {
            backgroundColor: isDark ? '#111827' : '#ffffff',
            scale: 2,
          });
          const imgData = canvas.toDataURL('image/png');
          const imgWidth = pageWidth - margin * 2;
          const imgHeight = (canvas.height * imgWidth) / canvas.width;

          // Verifica se precisa de nova página
          if (yPos + imgHeight > 250) {
            addNewPage();
            doc.setFontSize(14);
            doc.setFont(undefined, 'bold');
            doc.setTextColor(...palette.textPrimary);
            doc.text('Evolução do Investimento', margin, yPos);
            yPos += 10;
          }
          
          // Card ao redor do gráfico
          const graphCardHeight = imgHeight + 16;
          doc.setFillColor(...palette.white);
          doc.setDrawColor(...palette.grayBorder);
          doc.setLineWidth(0.5);
          doc.roundedRect(margin, yPos - 4, pageWidth - margin * 2, graphCardHeight, 4, 4, 'FD');

          doc.addImage(imgData, 'PNG', margin + 8, yPos, imgWidth - 16, imgHeight);
          yPos += imgHeight + 16;
        } catch (error) {
          console.error('Erro ao capturar gráfico:', error);
        }
      }

      // Tabela mensal (primeiros 12 meses e últimos 12 meses)
      if (results.monthlyData.length > 0) {
        // Verifica se precisa de nova página
        if (yPos > 200) {
          addNewPage();
        }
        
        // Título da seção
        doc.setFontSize(14);
        doc.setFont(undefined, 'bold');
        doc.setTextColor(...palette.textPrimary);
        doc.text(`${t('tools.monthlyEvolution')} - ${t('tools.first12Months')}`, margin, yPos);
        yPos += 10;

        const first12Months = results.monthlyData.slice(0, 13);
        const tableData = first12Months.map((d, idx) => [
          idx === 0 ? 'Início' : `${idx} mês${idx > 1 ? 'es' : ''}`,
          formatCurrency(d.investido),
          formatCurrency(d.saldo),
          formatCurrency(d.juros),
        ]);

        autoTable(doc, {
          startY: yPos,
          head: [[t('tools.periodColumn'), t('tools.investedValue'), t('tools.totalBalance'), t('tools.accumulatedInterest')]],
          body: tableData,
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
            2: { halign: 'right', fontStyle: 'bold', textColor: [34, 197, 94] },
            3: { halign: 'right', textColor: [59, 130, 246] },
          },
        });
        yPos = doc.lastAutoTable.finalY + 12;

        // Últimos 12 meses se houver mais de 24 meses
        if (results.monthlyData.length > 24) {
          if (yPos > 200) {
            addNewPage();
          }
          
          doc.setFontSize(14);
          doc.setFont(undefined, 'bold');
          doc.setTextColor(...palette.textPrimary);
          doc.text(`${t('tools.monthlyEvolution')} - ${t('tools.last12Months')}`, margin, yPos);
          yPos += 10;

          const last12Months = results.monthlyData.slice(-13);
          const lastTableData = last12Months.map((d, idx) => {
            const monthIndex = results.monthlyData.length - last12Months.length + idx;
            let periodLabel = 'Início';
            if (monthIndex > 0) {
              if (monthIndex < 12) {
                periodLabel = `${monthIndex} mês${monthIndex > 1 ? 'es' : ''}`;
              } else {
                const anos = Math.floor(monthIndex / 12);
                const mesesRestantes = monthIndex % 12;
                if (mesesRestantes === 0) {
                  periodLabel = `${anos} ano${anos > 1 ? 's' : ''}`;
                } else {
                  periodLabel = `${anos} ano${anos > 1 ? 's' : ''} e ${mesesRestantes} mês${mesesRestantes > 1 ? 'es' : ''}`;
                }
              }
            }
            return [
              periodLabel,
              formatCurrency(d.investido),
              formatCurrency(d.saldo),
              formatCurrency(d.juros),
            ];
          });

          autoTable(doc, {
            startY: yPos,
            head: [[t('tools.periodColumn'), t('tools.investedValue'), t('tools.totalBalance'), t('tools.accumulatedInterest')]],
            body: lastTableData,
            styles: { 
              fontSize: 9,
              cellPadding: 3,
              lineColor: palette.grayBorder,
              lineWidth: 0.3,
            },
            headStyles: { 
              fillColor: palette.grayLight,
              textColor: palette.textPrimary,
              fontStyle: 'bold',
              lineColor: palette.grayBorder,
              lineWidth: 0.5,
            },
            alternateRowStyles: { fillColor: palette.white },
            columnStyles: {
              0: { textColor: palette.textPrimary },
              1: { halign: 'right', textColor: palette.textSecondary },
              2: { halign: 'right', fontStyle: 'bold', textColor: [34, 197, 94] },
              3: { halign: 'right', textColor: [59, 130, 246] },
            },
          });
        }
      }

      // Rodapé - garantir que todas as páginas tenham fundo escuro se necessário
      const totalPages = doc.internal.pages.length - 1;
      for (let i = 1; i <= totalPages; i++) {
        doc.setPage(i);
        // Aplicar fundo escuro se necessário (caso não tenha sido aplicado antes)
        if (isDark) {
          doc.setFillColor(17, 24, 39); // gray-900
          doc.rect(0, 0, pageWidth, doc.internal.pageSize.getHeight(), 'F');
        }
        doc.setFontSize(8);
        doc.setTextColor(...palette.gray);
        doc.text(
          `Página ${i} de ${totalPages} - Controle-se - Calculadora de Juros Compostos`,
          pageWidth / 2,
          doc.internal.pageSize.getHeight() - 10,
          { align: 'center' }
        );
      }

      const fileName = `juros_compostos_${new Date().toISOString().split('T')[0]}.pdf`;
      doc.save(fileName);
      toast.success(t('tools.exportSuccessPDF'));
    } catch (error) {
      console.error('Erro ao exportar PDF:', error);
      toast.error(t('tools.errorExportingPDF'));
    }
  };

  return (
    <div className="space-y-6">
      {/* Formulário */}
      <div className="card">
        <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
          Parâmetros do Cálculo
        </h3>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <label className="label">
              <DollarSign className="w-4 h-4 inline mr-1" />
              Aporte Inicial (R$)
            </label>
            <input
              type="number"
              step="0.01"
              min="0"
              value={formData.aporteInicial}
              onChange={(e) => setFormData({ ...formData, aporteInicial: e.target.value })}
              className="input"
              placeholder="0.00"
            />
          </div>

          <div>
            <label className="label">
              <DollarSign className="w-4 h-4 inline mr-1" />
              Aporte Mensal (R$)
            </label>
            <input
              type="number"
              step="0.01"
              min="0"
              value={formData.aporteMensal}
              onChange={(e) => setFormData({ ...formData, aporteMensal: e.target.value })}
              className="input"
              placeholder="0.00"
            />
          </div>

          <div>
            <label className="label">Frequência de Aporte</label>
            <select
              value={formData.frequenciaAporte}
              onChange={(e) => setFormData({ ...formData, frequenciaAporte: e.target.value })}
              className="input"
            >
              <option value="mensal">Mensal</option>
              <option value="quinzenal">Quinzenal</option>
              <option value="anual">Anual</option>
            </select>
          </div>

          <div>
            <label className="label">
              <Percent className="w-4 h-4 inline mr-1" />
              Taxa de Juros (%)
            </label>
            <div className="flex gap-2">
              <input
                type="number"
                step="0.01"
                min="0"
                value={formData.taxaJuros}
                onChange={(e) => setFormData({ ...formData, taxaJuros: e.target.value })}
                className="input flex-1"
                placeholder="0.00"
              />
              <select
                value={formData.tipoTaxa}
                onChange={(e) => setFormData({ ...formData, tipoTaxa: e.target.value })}
                className="input w-32"
              >
                <option value="anual">Anual</option>
                <option value="mensal">Mensal</option>
              </select>
            </div>
          </div>

          <div>
            <label className="label">
              <Calendar className="w-4 h-4 inline mr-1" />
              Prazo
            </label>
            <div className="flex gap-2">
              <input
                type="number"
                min="1"
                value={formData.prazo}
                onChange={(e) => setFormData({ ...formData, prazo: e.target.value })}
                className="input flex-1"
                placeholder="0"
              />
              <select
                value={formData.tipoPrazo}
                onChange={(e) => setFormData({ ...formData, tipoPrazo: e.target.value })}
                className="input w-32"
              >
                <option value="anos">Anos</option>
                <option value="meses">Meses</option>
              </select>
            </div>
          </div>
        </div>

        <div className="flex items-center justify-between mt-4">
          <button onClick={handleCalculate} className="btn-primary w-full md:w-auto">
            <Calculator className="w-4 h-4" />
            Calcular
          </button>
          {user && (
            <button
              onClick={() => setShowHistoryModal(true)}
              className="btn-secondary flex items-center gap-2 px-4 py-2"
              title="Ver histórico de cálculos salvos"
            >
              <History className="w-4 h-4" />
              <span>Histórico</span>
            </button>
          )}
        </div>
      </div>

      {/* Resultados */}
      {results ? (
        <>
          {/* Resumo */}
          <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
            <div className="card">
              <div className="flex items-center gap-2 mb-2">
                <DollarSign className="w-5 h-5 text-gray-500" />
                <span className="text-sm text-gray-600 dark:text-gray-400">Total Investido</span>
              </div>
              <p className="text-2xl font-bold text-gray-900 dark:text-white">
                {formatCurrency(results.totalInvestido)}
              </p>
            </div>

            <div className="card">
              <div className="flex items-center gap-2 mb-2">
                <TrendingUp className="w-5 h-5 text-green-500" />
                <span className="text-sm text-gray-600 dark:text-gray-400">Saldo Final</span>
              </div>
              <p className="text-2xl font-bold text-green-600 dark:text-green-400">
                {formatCurrency(results.saldoFinal)}
              </p>
            </div>

            <div className="card">
              <div className="flex items-center gap-2 mb-2">
                <Percent className="w-5 h-5 text-blue-500" />
                <span className="text-sm text-gray-600 dark:text-gray-400">Total de Juros</span>
              </div>
              <p className="text-2xl font-bold text-blue-600 dark:text-blue-400">
                {formatCurrency(results.totalJuros)}
              </p>
            </div>

            <div className="card">
              <div className="flex items-center gap-2 mb-2">
                <TrendingUp className="w-5 h-5 text-purple-500" />
                <span className="text-sm text-gray-600 dark:text-gray-400">Rentabilidade</span>
              </div>
              <p className="text-2xl font-bold text-purple-600 dark:text-purple-400">
                {((results.totalJuros / results.totalInvestido) * 100).toFixed(2)}%
              </p>
            </div>
          </div>

          {/* Gráfico */}
          <div className="card">
              <div className="flex items-center justify-between mb-4">
              <h3 className="text-lg font-semibold text-gray-900 dark:text-white">
                Evolução do Investimento
              </h3>
              <div className="flex gap-2">
                {user && (
                  <button
                    onClick={handleSaveToHistory}
                    disabled={loading}
                    className="btn-secondary"
                  >
                    {loading ? (
                      <>
                        <Spinner size={16} className="mr-2" />
                        Salvando...
                      </>
                    ) : (
                      <>
                        <Save className="w-4 h-4" />
                        Salvar no Histórico
                      </>
                    )}
                  </button>
                )}
                <button onClick={handleExportPDF} className="btn-secondary">
                  <Download className="w-4 h-4" />
                  Exportar PDF
                </button>
              </div>
            </div>
            {chartData ? (
              <div ref={chartRef} className="h-96">
                <Line
                  data={chartData}
                  options={{
                    responsive: true,
                    maintainAspectRatio: false,
                    plugins: {
                      legend: {
                        position: 'top',
                      },
                      tooltip: {
                        callbacks: {
                          label: (context) => {
                            return `${context.dataset.label}: ${formatCurrency(context.parsed.y)}`;
                          },
                        },
                      },
                    },
                    scales: {
                      y: {
                        beginAtZero: true,
                        ticks: {
                          callback: (value) => formatCurrency(value),
                        },
                      },
                    },
                  }}
                />
              </div>
            ) : (
              <p className="text-center text-gray-500 py-8">
                Realize um cálculo para ver o gráfico
              </p>
            )}
          </div>

          {/* Tabela Mensal */}
          <div className="card">
            <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
              Evolução Mês a Mês
            </h3>
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead>
                  <tr className="border-b border-gray-200 dark:border-gray-700">
                    <th className="text-left py-3 px-4 text-sm font-semibold text-gray-900 dark:text-white">
                      {t('tools.month')}
                    </th>
                    <th className="text-right py-3 px-4 text-sm font-semibold text-gray-900 dark:text-white">
                      {t('tools.investedValue')}
                    </th>
                    <th className="text-right py-3 px-4 text-sm font-semibold text-gray-900 dark:text-white">
                      {t('tools.totalBalance')}
                    </th>
                    <th className="text-right py-3 px-4 text-sm font-semibold text-gray-900 dark:text-white">
                      {t('tools.accumulatedInterest')}
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {results.monthlyData.map((data, index) => {
                    const meses = index;
                    let mesLabel = 'Início';
                    if (meses > 0) {
                      if (meses < 12) {
                        mesLabel = `${meses} mês${meses > 1 ? 'es' : ''}`;
                      } else {
                        const anos = Math.floor(meses / 12);
                        const mesesRestantes = meses % 12;
                        if (mesesRestantes === 0) {
                          mesLabel = `${anos} ano${anos > 1 ? 's' : ''}`;
                        } else {
                          mesLabel = `${anos} ano${anos > 1 ? 's' : ''} e ${mesesRestantes} mês${mesesRestantes > 1 ? 'es' : ''}`;
                        }
                      }
                    }
                    return (
                      <tr
                        key={index}
                        className="border-b border-gray-100 dark:border-gray-800 hover:bg-gray-50 dark:hover:bg-gray-800/50"
                      >
                        <td className="py-3 px-4 text-sm text-gray-900 dark:text-white">
                          {mesLabel}
                        </td>
                        <td className="py-3 px-4 text-sm text-right text-gray-700 dark:text-gray-300">
                          {formatCurrency(data.investido)}
                        </td>
                        <td className="py-3 px-4 text-sm text-right font-semibold text-green-600 dark:text-green-400">
                          {formatCurrency(data.saldo)}
                        </td>
                        <td className="py-3 px-4 text-sm text-right text-blue-600 dark:text-blue-400">
                          {formatCurrency(data.juros)}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>
        </>
      ) : (
        /* Placeholder quando não há simulação */
        <div className="card">
          <div className="text-center py-12">
            <Calculator className="w-16 h-16 mx-auto text-gray-400 mb-4" />
            <h3 className="text-xl font-semibold text-gray-900 dark:text-white mb-2">
              Nenhuma simulação realizada
            </h3>
            <p className="text-gray-600 dark:text-gray-400">
              Preencha os campos acima e clique em "Calcular" para ver os resultados da simulação
            </p>
          </div>
        </div>
      )}

      {/* Modal de Histórico */}
      <Modal
        isOpen={showHistoryModal}
        onClose={() => setShowHistoryModal(false)}
        title="Histórico de Cálculos"
      >
        <div className="space-y-4">
          {loadingHistory ? (
            <div className="flex items-center justify-center py-8">
              <Loader2 className="w-6 h-6 animate-spin text-primary-600" />
              <span className="ml-2 text-gray-600 dark:text-gray-400">Carregando histórico...</span>
            </div>
          ) : history.length === 0 ? (
            <div className="text-center py-8">
              <History className="w-12 h-12 mx-auto text-gray-400 mb-4" />
              <p className="text-gray-600 dark:text-gray-400">
                Nenhum cálculo salvo no histórico ainda.
              </p>
            </div>
          ) : (
            <div className="space-y-3 max-h-96 overflow-y-auto">
              {history.map((calculo) => (
                <div
                  key={calculo.idCalculo}
                  className="border border-gray-200 dark:border-gray-700 rounded-lg p-4 hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors"
                >
                  <div className="flex items-start justify-between">
                    <div className="flex-1">
                      <div className="flex items-center gap-2 mb-2">
                        <div className="text-sm font-semibold text-gray-900 dark:text-white">
                          {formatCurrency(calculo.aporteInicial || 0)} inicial
                          {calculo.aporteMensal > 0 && (
                            <> + {formatCurrency(calculo.aporteMensal)} {calculo.frequenciaAporte === 'mensal' ? 'mensal' : calculo.frequenciaAporte === 'quinzenal' ? 'quinzenal' : 'anual'}</>
                          )}
                        </div>
                      </div>
                      <div className="text-xs text-gray-600 dark:text-gray-400 space-y-1">
                        <div>
                          Taxa: {calculo.taxaJuros}% {calculo.tipoTaxa === 'mensal' ? 'Mensal' : 'Anual'}
                        </div>
                        <div>
                          Prazo: {calculo.prazo} {calculo.tipoPrazo === 'meses' ? 'meses' : 'anos'}
                        </div>
                        <div className="mt-2 pt-2 border-t border-gray-200 dark:border-gray-700">
                          <div className="flex items-center gap-4">
                            <span className="text-green-600 dark:text-green-400 font-semibold">
                              Saldo Final: {formatCurrency(calculo.saldoFinal || 0)}
                            </span>
                            <span className="text-blue-600 dark:text-blue-400">
                              Juros: {formatCurrency(calculo.totalJuros || 0)}
                            </span>
                          </div>
                        </div>
                        {calculo.dataCalculo && (
                          <div className="text-xs text-gray-500 dark:text-gray-500 mt-1">
                            {formatDate(calculo.dataCalculo)}
                          </div>
                        )}
                      </div>
                    </div>
                    <div className="flex gap-2 ml-4">
                      <button
                        onClick={() => handleLoadFromHistory(calculo)}
                        className="btn-primary text-sm px-3 py-1.5"
                        title="Carregar este cálculo"
                      >
                        Carregar
                      </button>
                      <button
                        onClick={() => handleDeleteFromHistory(calculo.idCalculo)}
                        className="btn-secondary text-sm px-3 py-1.5 text-red-600 hover:text-red-700 dark:text-red-400 dark:hover:text-red-300 disabled:opacity-50 disabled:cursor-not-allowed"
                        title="Excluir do histórico"
                        disabled={deletingHistoryIds.has(calculo.idCalculo)}
                      >
                        {deletingHistoryIds.has(calculo.idCalculo) ? (
                          <Spinner size={14} className="text-red-600 dark:text-red-400" />
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
        </div>
      </Modal>
    </div>
  );
}

