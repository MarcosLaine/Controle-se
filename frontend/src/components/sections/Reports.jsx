import React, { useState, useEffect, useRef, useCallback } from 'react';
import { FileText, Download, ArrowUp, ArrowDown, Scale, TrendingUp } from 'lucide-react';
import { useAuth } from '../../contexts/AuthContext';
import { useLanguage } from '../../contexts/LanguageContext';
import api from '../../services/api';
import axios from 'axios';
import { formatCurrency, formatDate } from '../../utils/formatters';
import SummaryCard from '../common/SummaryCard';
import toast from 'react-hot-toast';
import SkeletonSection from '../common/SkeletonSection';
import { Doughnut, Bar } from 'react-chartjs-2';
import jsPDF from 'jspdf';
import autoTable from 'jspdf-autotable';
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

ChartJS.register(ArcElement, Tooltip, Legend, CategoryScale, LinearScale, BarElement, Title);

export default function Reports() {
  const { user } = useAuth();
  const { t } = useLanguage();
  const [loading, setLoading] = useState(true);
  const [period, setPeriod] = useState('month');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [reportData, setReportData] = useState(null);
  const [exportFormat, setExportFormat] = useState('csv');
  
  // Ref para armazenar o AbortController da requisição atual
  const abortControllerRef = useRef(null);

  const loadReports = useCallback(async () => {
    if (!user) return;
    if (period === 'custom' && (!startDate || !endDate)) return;
    
    // Cancela a requisição anterior se ainda estiver em andamento
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
    }

    // Cria um novo AbortController para esta requisição
    const abortController = new AbortController();
    abortControllerRef.current = abortController;
    
    setLoading(true);
    try {
      let url = `/reports?userId=${user.id}&period=${period}`;
      if (period === 'custom' && startDate && endDate) {
        url += `&startDate=${startDate}&endDate=${endDate}`;
      }
      const response = await api.get(url, {
        signal: abortController.signal
      });
      
      // Verifica se a requisição foi cancelada antes de processar a resposta
      if (abortController.signal.aborted) {
        return;
      }
      
      if (response.success) {
        setReportData(response.data);
      } else {
        toast.error(t('reports.errorLoading'));
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
      
      console.error('Erro ao carregar relatórios:', error);
      toast.error('Erro ao carregar relatórios');
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
  }, [user, period, startDate, endDate]);

  useEffect(() => {
    if (user && (period !== 'custom' || (startDate && endDate))) {
      loadReports();
    }
    
    // Cleanup: cancela requisições quando o componente é desmontado ou quando os parâmetros mudam
    return () => {
      if (abortControllerRef.current) {
        abortControllerRef.current.abort();
        abortControllerRef.current = null;
      }
    };
  }, [loadReports]);

  const exportToPDF = () => {
    if (!reportData) {
      toast.error(t('reports.noData'));
      return;
    }

    try {
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
        red: [248, 113, 113],              // red-400 (mais claro)
        redLight: [127, 29, 29],            // red-900/30
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
        red: [239, 68, 68],                // red-500
        redLight: [254, 242, 242],         // red-50
        blue: [59, 130, 246],              // blue-500
        blueLight: [239, 246, 255],       // blue-50
        purple: [168, 85, 247],           // purple-500
        purpleLight: [250, 245, 255],    // purple-50
        amber: [245, 158, 11],             // amber-500
        amberLight: [255, 251, 235],      // amber-50
        emerald: [16, 185, 129],          // emerald-500
        emeraldLight: [236, 253, 245],    // emerald-50
        gray: [107, 114, 128],            // gray-500
        grayLight: [249, 250, 251],       // gray-50
        grayBorder: [229, 231, 235],      // gray-200
        textPrimary: [17, 24, 39],        // gray-900
        textSecondary: [107, 114, 128],   // gray-500
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
          doc.text(subtitle, x + 6, y + 26);
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
      doc.text(t('reports.reportTitle'), margin, yPos);
      yPos += 8;
      
      doc.setFontSize(9);
      doc.setFont(undefined, 'normal');
      doc.setTextColor(...palette.textSecondary);
      
      const periodText = period === 'custom' && startDate && endDate
        ? `${formatDate(startDate)} a ${formatDate(endDate)}`
        : period === 'month'
        ? t('reports.thisMonth')
        : period === 'year'
        ? t('reports.thisYear')
        : t('reports.selectedPeriod');
      
      doc.text(`${t('reports.generatedAt')} ${formatDate(new Date().toISOString())} • ${t('reports.period')}: ${periodText}`, margin, yPos);
      yPos += 16;

      // Summary cards estilo SummaryCard
      const cardWidth = (pageWidth - margin * 2 - 12) / 3;
      const cardSpacing = 4;
      
      drawSummaryCard(
        t('reports.totalIncomes'),
        formatCurrency(reportData.totalIncomes || 0),
        `${reportData.incomeCount || 0} ${t('reports.transactions')}`,
        margin,
        yPos,
        cardWidth,
        palette.greenLight,
        isDark ? [34, 197, 94] : [34, 197, 94],
        palette.textSecondary
      );
      
      drawSummaryCard(
        t('reports.totalExpenses'),
        formatCurrency(reportData.totalExpenses || 0),
        `${reportData.expenseCount || 0} ${t('reports.transactions')}`,
        margin + cardWidth + cardSpacing,
        yPos,
        cardWidth,
        palette.redLight,
        isDark ? [248, 113, 113] : [239, 68, 68],
        palette.textSecondary
      );
      
      drawSummaryCard(
        t('reports.balance'),
        formatCurrency(reportData.balance || 0),
        `${t('reports.income')} - ${t('reports.expenses')}`,
        margin + (cardWidth + cardSpacing) * 2,
        yPos,
        cardWidth,
        palette.blueLight,
        isDark ? [96, 165, 250] : [59, 130, 246],
        palette.textSecondary
      );
      
      yPos += 40;

      // Gastos por Categoria
      if (categoryData.length > 0) {
        ensureSpace(30);
        drawSectionTitle(t('reports.expensesByCategory'));
        const categoryRows = categoryData
          .sort((a, b) => b.total - a.total)
          .slice(0, 15)
          .map(item => [
            item.name,
            formatCurrency(item.total),
            reportData.totalExpenses > 0
              ? `${((item.total / reportData.totalExpenses) * 100).toFixed(2)}%`
              : '0%'
          ]);
        
        autoTable(doc, {
          startY: yPos,
          head: [[t('reports.category'), t('reports.value'), t('reports.participation')]],
          body: categoryRows,
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
      }

      // Gastos por Conta
      if (reportData.accountAnalysis && Object.keys(reportData.accountAnalysis).length > 0) {
        ensureSpace(30);
        drawSectionTitle(t('reports.expensesByAccount'));
        const accountRows = Object.entries(reportData.accountAnalysis)
          .sort((a, b) => b[1] - a[1])
          .slice(0, 10)
          .map(([account, total]) => [
            account,
            formatCurrency(total),
            reportData.totalExpenses > 0
              ? `${((total / reportData.totalExpenses) * 100).toFixed(2)}%`
              : '0%'
          ]);
        
        autoTable(doc, {
          startY: yPos,
          head: [['Conta', 'Valor', 'Participação']],
          body: accountRows,
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
      }

      // Evolução Mensal
      if (monthlyData.length > 0) {
        ensureSpace(30);
        drawSectionTitle('Evolução Mensal');
        const monthNames = [
          'Jan', 'Fev', 'Mar', 'Abr', 'Mai', 'Jun',
          'Jul', 'Ago', 'Set', 'Out', 'Nov', 'Dez'
        ];
        const monthlyRows = monthlyData
          .slice(0, 12)
          .map(m => [
            `${monthNames[m.month - 1]}/${m.year}`,
            formatCurrency(m.incomes || 0),
            formatCurrency(m.expenses || 0),
            formatCurrency((m.incomes || 0) - (m.expenses || 0))
          ]);
        
        autoTable(doc, {
          startY: yPos,
          head: [[t('reports.month'), t('reports.income'), t('reports.expenses'), t('reports.balance')]],
          body: monthlyRows,
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
            1: { halign: 'right', textColor: [34, 197, 94] },
            2: { halign: 'right', textColor: [239, 68, 68] },
            3: { halign: 'right', fontStyle: 'bold', textColor: [59, 130, 246] },
          },
        });
        yPos = doc.lastAutoTable.finalY + 12;
      }

      // Top Gastos
      if (reportData.topExpenses && reportData.topExpenses.length > 0) {
        ensureSpace(30);
        drawSectionTitle(t('reports.topExpenses'));
        const topExpensesRows = reportData.topExpenses
          .slice(0, 15)
          .map(expense => [
            formatDate(expense.date),
            expense.description,
            expense.category || '-',
            formatCurrency(expense.value || 0)
          ]);
        
        autoTable(doc, {
          startY: yPos,
          head: [[t('transactions.date'), t('transactions.description'), t('reports.category'), t('reports.value')]],
          body: topExpensesRows,
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
            2: { textColor: palette.textSecondary },
            3: { halign: 'right', fontStyle: 'bold', textColor: [239, 68, 68] },
          },
        });
        yPos = doc.lastAutoTable.finalY + 12;
      }

      // Detalhes Adicionais
      ensureSpace(20);
        drawSectionTitle(t('reports.additionalDetails'));
      doc.setFontSize(9);
      doc.setFont(undefined, 'normal');
      doc.setTextColor(...palette.textSecondary);
      doc.text(`${t('reports.totalTransactions')}: ${(reportData.incomeCount || 0) + (reportData.expenseCount || 0)}`, margin, yPos);
      yPos += 6;
      if (reportData.startDate && reportData.endDate) {
        doc.text(`${t('reports.period')}: ${formatDate(reportData.startDate)} ${t('common.to')} ${formatDate(reportData.endDate)}`, margin, yPos);
        yPos += 6;
      }
      doc.text(t('reports.autoGenerated'), margin, yPos);
      yPos += 8;

      // Rodapé
      const totalPages = doc.internal.pages.length - 1;
      for (let i = 1; i <= totalPages; i++) {
        doc.setPage(i);
        doc.setFontSize(8);
        doc.setTextColor(...palette.gray);
        doc.text(
          t('reports.reportPage', { current: i, total: totalPages }),
          pageWidth / 2,
          doc.internal.pageSize.getHeight() - 10,
          { align: 'center' }
        );
      }

      const fileName = period === 'custom' && startDate && endDate
        ? `relatorio_${startDate}_${endDate}.pdf`
        : `relatorio_${period}_${new Date().toISOString().split('T')[0]}.pdf`;
      
      doc.save(fileName);
      toast.success(t('reports.exportSuccessPDF'));
    } catch (error) {
      console.error('Erro ao exportar PDF:', error);
      toast.error(t('reports.errorExportingPDF'));
    }
  };

  const handleExport = async () => {
    if (!user) return;
    
    if (exportFormat === 'pdf') {
      exportToPDF();
      return;
    }

    try {
      const data = {
        userId: user.id,
        format: exportFormat,
        period: period === 'custom' ? 'custom' : period,
      };
      if (period === 'custom' && startDate && endDate) {
        data.startDate = startDate;
        data.endDate = endDate;
      }

      const API_BASE_URL = window.location.hostname === 'localhost' 
        ? 'http://localhost:8080/api' 
        : `${window.location.origin}/api`;

      const response = await fetch(`${API_BASE_URL}/reports`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(data),
      });

      if (response.ok) {
        const blob = await response.blob();
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `relatorio_${startDate || 'inicio'}_${endDate || 'fim'}.${exportFormat}`;
        document.body.appendChild(a);
        a.click();
        window.URL.revokeObjectURL(url);
        document.body.removeChild(a);
        toast.success(t('reports.exportSuccess'));
      } else {
        toast.error(t('reports.errorExportingReport'));
      }
    } catch (error) {
      console.error('Erro ao exportar:', error);
      toast.error('Erro ao exportar relatório');
    }
  };

  if (loading) {
    return <SkeletonSection type="reports" />;
  }

  // Converte categoryAnalysis de Map para array
  const categoryData = reportData?.categoryAnalysis
    ? Object.entries(reportData.categoryAnalysis).map(([name, total]) => ({ name, total }))
    : [];

  // Converte monthlyAnalysis para gráfico
  const monthlyData = reportData?.monthlyAnalysis || [];

  return (
    <div className="space-y-6 animate-fade-in">
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4">
        <div>
          <h2 className="text-3xl font-bold text-gray-900 dark:text-white">{t('reports.title')}</h2>
          <p className="text-gray-600 dark:text-gray-400 mt-1">{t('reports.subtitle')}</p>
        </div>
        <div className="flex flex-col sm:flex-row gap-2">
          <select
            value={period}
            onChange={(e) => setPeriod(e.target.value)}
            className="input w-full sm:w-auto"
          >
            <option value="month">{t('reports.thisMonth')}</option>
            <option value="year">{t('reports.thisYear')}</option>
            <option value="custom">{t('reports.customPeriod')}</option>
          </select>
          {period === 'custom' && (
            <>
              <input
                type="date"
                value={startDate}
                onChange={(e) => setStartDate(e.target.value)}
                className="input w-full sm:w-auto"
                placeholder="Data Início"
              />
              <input
                type="date"
                value={endDate}
                onChange={(e) => setEndDate(e.target.value)}
                className="input w-full sm:w-auto"
                placeholder="Data Fim"
              />
            </>
          )}
          <div className="flex gap-2">
            <select
              value={exportFormat}
              onChange={(e) => setExportFormat(e.target.value)}
              className="input w-full sm:w-auto"
            >
              <option value="csv">CSV</option>
              <option value="xlsx">XLSX</option>
              <option value="pdf">PDF</option>
            </select>
            <button onClick={handleExport} className="btn-primary flex-1 sm:flex-none justify-center">
              <Download className="w-4 h-4" />
              {t('reports.export')}
            </button>
          </div>
        </div>
      </div>

      {reportData ? (
        <>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <SummaryCard
              title={t('reports.totalIncomes')}
              amount={formatCurrency(reportData.totalIncomes || 0)}
              icon={ArrowUp}
              type="income"
              subtitle={`${reportData.incomeCount || 0} transações`}
            />
            <SummaryCard
              title={t('reports.totalExpenses')}
              amount={formatCurrency(reportData.totalExpenses || 0)}
              icon={ArrowDown}
              type="expense"
              subtitle={`${reportData.expenseCount || 0} transações`}
            />
            <SummaryCard
              title={t('reports.balancePeriod')}
              amount={formatCurrency(reportData.balance || 0)}
              icon={Scale}
              type="balance"
              subtitle={reportData.startDate && reportData.endDate 
                ? `${formatDate(reportData.startDate)} a ${formatDate(reportData.endDate)}`
                : t('reports.selectedPeriod')}
            />
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            {/* Gastos por Categoria */}
            {categoryData.length > 0 && (
              <div className="card">
                <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
                  {t('reports.expensesByCategory')}
                </h3>
                <div className="h-64">
                  <Doughnut
                    data={{
                      labels: categoryData.map((c) => c.name),
                      datasets: [
                        {
                          data: categoryData.map((c) => c.total),
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
                        },
                      ],
                    }}
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
              </div>
            )}

            {/* Evolução Mensal */}
            {monthlyData.length > 0 && (
              <div className="card">
                <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
                  Evolução Mensal
                </h3>
                <div className="h-64">
                  <Bar
                    data={{
                      labels: monthlyData.map((m) => {
                        const monthNames = [
                          'Jan', 'Fev', 'Mar', 'Abr', 'Mai', 'Jun',
                          'Jul', 'Ago', 'Set', 'Out', 'Nov', 'Dez'
                        ];
                        return `${monthNames[m.month - 1]}/${m.year}`;
                      }),
                      datasets: [
                        {
                          label: 'Receitas',
                          data: monthlyData.map((m) => m.incomes || 0),
                          backgroundColor: '#22c55e',
                        },
                        {
                          label: 'Gastos',
                          data: monthlyData.map((m) => m.expenses || 0),
                          backgroundColor: '#ef4444',
                        },
                      ],
                    }}
                    options={{
                      responsive: true,
                      maintainAspectRatio: false,
                      plugins: {
                        legend: {
                          position: 'bottom',
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
              </div>
            )}
          </div>

          {/* Gastos por Conta */}
          {reportData.accountAnalysis && Object.keys(reportData.accountAnalysis).length > 0 && (
            <div className="card">
              <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
                Gastos por Conta
              </h3>
              <div className="h-64">
                <Doughnut
                  data={{
                    labels: Object.keys(reportData.accountAnalysis),
                    datasets: [
                      {
                        data: Object.values(reportData.accountAnalysis),
                        backgroundColor: [
                          '#3b82f6',
                          '#8b5cf6',
                          '#ec4899',
                          '#14b8a6',
                          '#f97316',
                        ],
                      },
                    ],
                  }}
                  options={{
                    responsive: true,
                    maintainAspectRatio: false,
                    plugins: {
                      legend: {
                        position: 'bottom',
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
            </div>
          )}

          {/* Top Gastos */}
          {reportData.topExpenses && reportData.topExpenses.length > 0 && (
            <div className="card">
              <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
                Maiores Gastos
              </h3>
              <div className="space-y-3">
                {reportData.topExpenses.map((expense, idx) => (
                  <div
                    key={idx}
                    className="flex items-center justify-between p-3 bg-gray-50 dark:bg-gray-700 rounded-lg"
                  >
                    <div>
                      <p className="font-semibold text-gray-900 dark:text-white">
                        {expense.description}
                      </p>
                      <p className="text-sm text-gray-500 dark:text-gray-400">
                        {formatDate(expense.date)} - {expense.category}
                      </p>
                    </div>
                    <p className="text-lg font-bold text-red-600 dark:text-red-400">
                      {formatCurrency(expense.value || 0)}
                    </p>
                  </div>
                ))}
              </div>
            </div>
          )}
        </>
      ) : (
        <div className="card text-center py-12">
          <FileText className="w-16 h-16 text-gray-400 mx-auto mb-4" />
          <p className="text-gray-600 dark:text-gray-400">{t('reports.noData')}</p>
        </div>
      )}
    </div>
  );
}

