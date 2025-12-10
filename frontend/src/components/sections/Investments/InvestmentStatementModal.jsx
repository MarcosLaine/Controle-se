import jsPDF from 'jspdf';
import autoTable from 'jspdf-autotable';
import { File, FileSpreadsheet, FileText, Filter } from 'lucide-react';
import React, { useMemo, useState } from 'react';
import toast from 'react-hot-toast';
import { useLanguage } from '../../../contexts/LanguageContext';
import * as XLSX from 'xlsx';
import { formatCurrency, formatDate } from '../../../utils/formatters';
import Modal from '../../common/Modal';

export default function InvestmentStatementModal({ isOpen, onClose, investments, accounts }) {
  const { t } = useLanguage();
  const [filters, setFilters] = useState({
    startDate: '',
    endDate: '',
    broker: '',
    category: ''
  });

  // Get unique brokers and categories from investments
  const brokers = useMemo(() => {
    const uniqueBrokers = new Set();
    investments.forEach(inv => {
      if (inv.corretora) uniqueBrokers.add(inv.corretora);
    });
    return Array.from(uniqueBrokers).sort();
  }, [investments]);

  const categories = useMemo(() => {
    const uniqueCategories = new Set();
    investments.forEach(inv => {
      if (inv.categoria) uniqueCategories.add(inv.categoria);
    });
    return Array.from(uniqueCategories).sort();
  }, [investments]);

  // Filter investments
  const filteredInvestments = useMemo(() => {
    const filtered = investments.filter(inv => {
      if (filters.startDate && new Date(inv.dataAporte) < new Date(filters.startDate)) {
        return false;
      }
      if (filters.endDate && new Date(inv.dataAporte) > new Date(filters.endDate)) {
        return false;
      }
      if (filters.broker && inv.corretora !== filters.broker) {
        return false;
      }
      if (filters.category && inv.categoria !== filters.category) {
        return false;
      }
      return true;
    });
    
    const fifoState = {};
    const withRealized = filtered
      .slice()
      .sort((a, b) => new Date(a.dataAporte) - new Date(b.dataAporte))
      .map(inv => {
        const key = `${inv.categoria || 'OUTROS'}_${inv.nome}`;
        if (!fifoState[key]) {
          fifoState[key] = [];
        }

        const qty = inv.quantidade || 0;
        const amount = inv.valorAporte || 0;
        const brokerage = inv.corretagem || 0;

        if (qty > 0) {
          const unitCost = qty !== 0 ? amount / qty : 0;
          fifoState[key].push({ qty, unitCost });
          return { ...inv, realizedPnL: 0 };
        }

        let remainingQty = Math.abs(qty);
        let totalCostBasis = 0;
        let totalMatchedQty = 0;

        while (remainingQty > 0 && fifoState[key].length > 0) {
          const layer = fifoState[key][0];
          const qtyToUse = Math.min(remainingQty, layer.qty);
          totalCostBasis += qtyToUse * layer.unitCost;
          totalMatchedQty += qtyToUse;
          remainingQty -= qtyToUse;
          layer.qty -= qtyToUse;
          if (layer.qty === 0) {
            fifoState[key].shift();
          }
        }

        const grossRevenue = Math.abs(amount);
        const revenuePortion = totalMatchedQty > 0
          ? grossRevenue * (totalMatchedQty / Math.abs(qty))
          : 0;
        const realizedPnL = revenuePortion - totalCostBasis - brokerage;

        return {
          ...inv,
          realizedPnL,
        };
      });

    return withRealized.sort((a, b) => new Date(b.dataAporte) - new Date(a.dataAporte));
  }, [investments, filters]);

  const categoryNames = {
    'ACAO': 'Ações (B3)',
    'STOCK': 'Stocks (Ações Internacionais)',
    'CRYPTO': 'Criptomoedas',
    'FII': 'Fundos Imobiliários',
    'RENDA_FIXA': 'Renda Fixa',
    'OUTROS': 'Outros'
  };

  const getOperationTotals = (inv) => {
    // Usa valores convertidos para BRL se disponíveis, senão usa originais
    const grossValue = Math.abs(inv.valorAporteBRL || inv.valorAporte || 0);
    const brokerage = inv.corretagemBRL || inv.corretagem || 0;
    const isBuy = inv.quantidade > 0;
    const total = isBuy
      ? grossValue + brokerage
      : Math.max(grossValue - brokerage, 0);
    return { total, grossValue, brokerage, isBuy };
  };

  const summaryTotals = useMemo(() => {
    return filteredInvestments.reduce(
      (acc, inv) => {
        const { total, brokerage, isBuy } = getOperationTotals(inv);
        if (isBuy) {
          acc.totalInvested += total;
        } else {
          acc.totalReceived += total;
        }
        acc.totalBrokerage += brokerage;
        if (isBuy) acc.buyCount += 1;
        else acc.sellCount += 1;
        return acc;
      },
      {
        totalInvested: 0,
        totalReceived: 0,
        totalBrokerage: 0,
        buyCount: 0,
        sellCount: 0,
      }
    );
  }, [filteredInvestments]);

  const exportToPDF = () => {
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
      doc.text(t('investments.statementTitle'), margin, yPos);
      yPos += 8;
      
      doc.setFontSize(9);
      doc.setFont(undefined, 'normal');
      doc.setTextColor(...palette.textSecondary);
      doc.text(`Gerado em ${formatDate(new Date().toISOString())}`, margin, yPos);
      yPos += 6;

      const filterRange = filters.startDate || filters.endDate
        ? `${filters.startDate || t('investments.start')} → ${filters.endDate || t('investments.today')}`
        : t('investments.allPeriod');
      const filterBroker = filters.broker || t('investments.all');
      const filterCategory = filters.category ? (categoryNames[filters.category] || filters.category) : t('investments.all');
      doc.text(`Filtros: ${filterRange} • Corretora: ${filterBroker} • Categoria: ${filterCategory}`, margin, yPos);
      yPos += 16;

      // Cards de resumo estilo SummaryCard
      const cardWidth = (pageWidth - margin * 2 - 12) / 2;
      const cardSpacing = 4;
      
      drawSummaryCard(
        t('investments.totalInvestedPeriod'),
        formatCurrency(summaryTotals.totalInvested),
        t('investments.contributionsMade'),
        margin,
        yPos,
        cardWidth,
        palette.blueLight,
        isDark ? [96, 165, 250] : [59, 130, 246],
        palette.textSecondary
      );
      
      drawSummaryCard(
        t('investments.totalReceivedSales'),
        formatCurrency(summaryTotals.totalReceived),
        t('investments.valueReceived'),
        margin + cardWidth + cardSpacing,
        yPos,
        cardWidth,
        palette.emeraldLight,
        isDark ? [52, 211, 153] : [16, 185, 129],
        palette.textSecondary
      );
      
      yPos += 40;
      
      drawSummaryCard(
        t('investments.totalBrokerage'),
        formatCurrency(summaryTotals.totalBrokerage),
        t('investments.feesPaid'),
        margin,
        yPos,
        cardWidth,
        palette.amberLight,
        isDark ? [251, 191, 36] : [245, 158, 11],
        palette.textSecondary
      );
      
      drawSummaryCard(
        t('investments.operationsPerformed'),
        `${summaryTotals.buyCount} ${t('investments.buys')} • ${summaryTotals.sellCount} ${t('investments.sells')}`,
        t('investments.totalTransactions'),
        margin + cardWidth + cardSpacing,
        yPos,
        cardWidth,
        palette.white,
        palette.grayBorder,
        palette.textSecondary
      );
      
      yPos += 40;

      // Tabela principal
      drawSectionTitle(t('investments.filteredOperations'));
      const tableData = filteredInvestments.map(inv => {
        const { total } = getOperationTotals(inv);
        return [
          formatDate(inv.dataAporte),
          inv.nome,
          categoryNames[inv.categoria] || inv.categoria,
          inv.quantidade > 0 ? t('investments.buyLabel') : t('investments.sellLabel'),
          Math.abs(inv.quantidade).toFixed(6),
          formatCurrency(inv.precoAporteBRL || inv.precoAporte || 0),
          formatCurrency(inv.corretagemBRL || inv.corretagem || 0),
          formatCurrency(total),
          inv.corretora || '-',
          inv.quantidade < 0 ? formatCurrency(inv.realizedPnL || 0) : '-',
        ];
      });

      autoTable(doc, {
        startY: yPos,
        head: [
          [t('investments.dateLabel'), t('investments.assetLabel'), t('investments.categoryLabel'), t('investments.typeLabel'), t('investments.quantityLabel'), t('investments.price'), t('investments.brokerageLabel'), t('investments.totalValueLabel'), t('investments.brokerageLabel'), t('investments.realizedProfit')],
        ],
        body: tableData,
        styles: { 
          fontSize: 8,
          cellPadding: 2,
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
          3: { textColor: palette.textPrimary },
          4: { halign: 'right', textColor: palette.textSecondary },
          5: { halign: 'right', textColor: palette.textSecondary },
          6: { halign: 'right', textColor: palette.textSecondary },
          7: { halign: 'right', fontStyle: 'bold', textColor: palette.textPrimary },
          8: { textColor: palette.textSecondary },
          9: { halign: 'right', fontStyle: 'bold', textColor: [34, 197, 94] },
        },
      });
      yPos = doc.lastAutoTable.finalY + 12;

      // Insights rápidos
      drawSectionTitle(t('investments.quickInsights'));
      doc.setFontSize(9);
      doc.setFont(undefined, 'normal');
      doc.setTextColor(...palette.textSecondary);
      if (filteredInvestments.length === 0) {
        doc.text(t('investments.noOperationsFound'), margin, yPos);
        yPos += 8;
      } else {
        doc.text(`${t('investments.avgOperationsPerDay')} ${(filteredInvestments.length / Math.max(1, filteredInvestments.length)).toFixed(1)}`, margin, yPos);
        yPos += 6;
        doc.text(`${t('investments.largestOperation')} ${formatCurrency(Math.max(...filteredInvestments.map(inv => Math.abs(inv.valorAporteBRL || inv.valorAporte || 0))))}`, margin, yPos);
        yPos += 6;
        doc.text(t('investments.realizedProfitNote'), margin, yPos);
        yPos += 6;
      }
      yPos += 8;

      // Rodapé
      const totalPages = doc.internal.pages.length - 1;
      for (let i = 1; i <= totalPages; i++) {
        doc.setPage(i);
        doc.setFontSize(8);
        doc.setTextColor(...palette.gray);
        doc.text(
          `Página ${i} de ${totalPages} - Controle-se - Extrato de Investimentos`,
          pageWidth / 2,
          doc.internal.pageSize.getHeight() - 10,
          { align: 'center' }
        );
      }

      doc.save(`extrato_investimentos_${new Date().toISOString().split('T')[0]}.pdf`);
      toast.success(t('investments.statementExportSuccessPDF'));
    } catch (error) {
      console.error(t('investments.statementExportErrorPDF'), error);
      toast.error(t('investments.statementExportErrorPDF'));
    }
  };

  const exportToXLSX = () => {
    try {
      const data = filteredInvestments.map(inv => {
        const { total } = getOperationTotals(inv);
                return {
        [t('investments.dateLabel')]: formatDate(inv.dataAporte),
        [t('investments.assetLabel')]: inv.nome,
        [t('investments.assetNameLabel')]: inv.nomeAtivo || '-',
        [t('investments.categoryLabel')]: categoryNames[inv.categoria] || inv.categoria,
        [t('investments.typeLabel')]: inv.quantidade > 0 ? t('investments.buyLabel') : t('investments.sellLabel'),
        [t('investments.quantityLabel')]: Math.abs(inv.quantidade),
        [t('investments.unitPrice')]: inv.precoAporteBRL || inv.precoAporte || 0,
        [t('investments.brokerageLabel')]: inv.corretagemBRL || inv.corretagem || 0,
        [t('investments.totalValueLabel')]: total,
        [t('investments.brokerageLabel')]: inv.corretora || '-',
        [t('investments.realizedPnL')]: inv.quantidade < 0 ? inv.realizedPnL || 0 : null,
        [t('investments.currencyLabel')]: inv.moeda || 'BRL'
        };
      });

      const ws = XLSX.utils.json_to_sheet(data);
      const wb = XLSX.utils.book_new();
      XLSX.utils.book_append_sheet(wb, ws, t('investments.statementSheet'));

      // Add summary sheet
      const summaryData = [
        [t('investments.statementSummary')],
        [''],
        [t('investments.totalInvestedLabel'), formatCurrency(summaryTotals.totalInvested)],
        [t('investments.totalReceived'), formatCurrency(summaryTotals.totalReceived)],
        [t('investments.totalBrokerageLabel'), formatCurrency(summaryTotals.totalBrokerage)],
        [t('investments.buys'), summaryTotals.buyCount],
        [t('investments.sells'), summaryTotals.sellCount],
        [t('investments.totalOperations'), filteredInvestments.length]
      ];

      const summaryWs = XLSX.utils.aoa_to_sheet(summaryData);
      XLSX.utils.book_append_sheet(wb, summaryWs, t('investments.summarySheet'));

      XLSX.writeFile(wb, `extrato_investimentos_${new Date().toISOString().split('T')[0]}.xlsx`);
      toast.success(t('investments.statementExportSuccessXLSX'));
    } catch (error) {
      console.error(t('investments.statementExportErrorXLSX'), error);
      toast.error(t('investments.statementExportErrorXLSX'));
    }
  };

  const exportToCSV = () => {
    try {
      const headers = [t('investments.dateLabel'), t('investments.assetLabel'), t('investments.assetNameLabel'), t('investments.categoryLabel'), t('investments.typeLabel'), t('investments.quantityLabel'), t('investments.unitPrice'), t('investments.brokerageLabel'), t('investments.totalValueLabel'), t('investments.brokerageLabel'), t('investments.realizedPnL'), t('investments.currencyLabel')];
      const rows = filteredInvestments.map(inv => {
        const { total } = getOperationTotals(inv);
        return [
        formatDate(inv.dataAporte),
        inv.nome,
        inv.nomeAtivo || '-',
        categoryNames[inv.categoria] || inv.categoria,
        inv.quantidade > 0 ? 'Compra' : 'Venda',
        Math.abs(inv.quantidade),
        inv.precoAporteBRL || inv.precoAporte || 0,
        inv.corretagemBRL || inv.corretagem || 0,
        total,
        inv.corretora || '-',
        inv.quantidade < 0 ? inv.realizedPnL || 0 : '',
        inv.moeda || 'BRL'
      ];
      });

      const csvContent = [
        headers.join(','),
        ...rows.map(row => row.map(cell => `"${cell}"`).join(','))
      ].join('\n');

      const blob = new Blob(['\ufeff' + csvContent], { type: 'text/csv;charset=utf-8;' });
      const link = document.createElement('a');
      link.href = URL.createObjectURL(blob);
      link.download = `extrato_investimentos_${new Date().toISOString().split('T')[0]}.csv`;
      link.click();
      toast.success(t('investments.statementExportSuccessCSV'));
    } catch (error) {
      console.error(t('investments.statementExportErrorCSV'), error);
      toast.error(t('investments.statementExportErrorCSV'));
    }
  };

  if (!isOpen) return null;

  return (
    <Modal isOpen={isOpen} onClose={onClose} title={t('investments.statementTitle')} size="lg">
      <div className="space-y-4">
        {/* Filters */}
        <div className="bg-gray-50 dark:bg-gray-700/50 p-4 rounded-lg space-y-3">
          <div className="flex items-center gap-2 text-sm font-semibold text-gray-700 dark:text-gray-300">
            <Filter size={16} />
            {t('investments.filters')}
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label className="label text-xs">{t('investments.start')}</label>
              <input
                type="date"
                className="input"
                value={filters.startDate}
                onChange={(e) => setFilters({ ...filters, startDate: e.target.value })}
              />
            </div>
            <div>
              <label className="label text-xs">{t('reports.endDate')}</label>
              <input
                type="date"
                className="input"
                value={filters.endDate}
                onChange={(e) => setFilters({ ...filters, endDate: e.target.value })}
              />
            </div>
            <div>
              <label className="label text-xs">{t('investments.brokerageLabel')}</label>
              <select
                className="input"
                value={filters.broker}
                onChange={(e) => setFilters({ ...filters, broker: e.target.value })}
              >
                <option value="">{t('investments.all')}</option>
                {brokers.map(broker => (
                  <option key={broker} value={broker}>{broker}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="label text-xs">{t('investments.categoryLabel')}</label>
              <select
                className="input"
                value={filters.category}
                onChange={(e) => setFilters({ ...filters, category: e.target.value })}
              >
                <option value="">{t('investments.all')}</option>
                {categories.map(cat => (
                  <option key={cat} value={cat}>{categoryNames[cat] || cat}</option>
                ))}
              </select>
            </div>
          </div>
        </div>

        {/* Export Buttons */}
        <div className="flex flex-wrap gap-2">
          <button onClick={exportToPDF} className="btn-secondary flex items-center gap-2 text-sm px-3 py-2 flex-1 sm:flex-none">
            <FileText size={16} />
            <span className="hidden sm:inline">{t('investments.exportPDF')}</span>
            <span className="sm:hidden">PDF</span>
          </button>
          <button onClick={exportToXLSX} className="btn-secondary flex items-center gap-2 text-sm px-3 py-2 flex-1 sm:flex-none">
            <FileSpreadsheet size={16} />
            <span className="hidden sm:inline">{t('investments.exportExcel')}</span>
            <span className="sm:hidden">XLSX</span>
          </button>
          <button onClick={exportToCSV} className="btn-secondary flex items-center gap-2 text-sm px-3 py-2 flex-1 sm:flex-none">
            <File size={16} />
            <span className="hidden sm:inline">{t('investments.exportCSV')}</span>
            <span className="sm:hidden">CSV</span>
          </button>
        </div>

        {/* Transactions List */}
        <div className="max-h-96 overflow-y-auto">
          <div className="space-y-2">
            {filteredInvestments.length === 0 ? (
              <div className="text-center py-8 text-gray-500">
                {t('investments.noOperationsFound')}
              </div>
            ) : (
              filteredInvestments.map((inv, idx) => {
                const { total, brokerage, isBuy } = getOperationTotals(inv);
                return (
                  <div
                    key={idx}
                    className="p-3 bg-gray-50 dark:bg-gray-700/50 rounded-lg border border-gray-200 dark:border-gray-600"
                  >
                    <div className="flex justify-between items-start">
                      <div className="flex-1">
                        <div className="flex items-center gap-2">
                          <span className="font-semibold text-gray-900 dark:text-white">
                            {inv.nome}
                          </span>
                          <span className={`text-xs px-2 py-0.5 rounded ${
                            isBuy
                              ? 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400'
                              : 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400'
                          }`}>
                          {isBuy ? t('investments.buyLabel') : t('investments.sellLabel')}
                          </span>
                        </div>
                        <div className="text-xs text-gray-500 dark:text-gray-400 mt-1">
                          {categoryNames[inv.categoria] || inv.categoria} • {formatDate(inv.dataAporte)}
                          {inv.corretora && ` • ${inv.corretora}`}
                        </div>
                        <div className="text-xs text-gray-500 dark:text-gray-400 mt-1">
                          {t('investments.qty')} {Math.abs(inv.quantidade).toFixed(6)} • {t('investments.price')} {formatCurrency(inv.precoAporteBRL || inv.precoAporte || 0)}
                        </div>
                      </div>
                      <div className="text-right">
                        <div className="font-semibold text-gray-900 dark:text-white">
                          {formatCurrency(total)}
                        </div>
                        {brokerage > 0 && (
                          <div className="text-xs text-gray-500 dark:text-gray-400">
                            {t('investments.brokerage')}: {formatCurrency(brokerage)}
                          </div>
                        )}
                        {!isBuy && (
                          <div className={`text-xs font-medium mt-1 ${inv.realizedPnL >= 0 ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'}`}>
                            {inv.realizedPnL >= 0 ? t('investments.realizedProfit') : t('investments.realizedLoss')} {t('investments.of')} {formatCurrency(inv.realizedPnL || 0)}
                          </div>
                        )}
                      </div>
                    </div>
                  </div>
                );
              })
            )}
          </div>
        </div>

        {/* Summary */}
        {filteredInvestments.length > 0 && (
          <div className="bg-primary-50 dark:bg-primary-900/20 p-4 rounded-lg">
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 sm:gap-4 text-xs sm:text-sm">
              <div>
                <div className="text-gray-600 dark:text-gray-400">{t('investments.totalInvestedLabel')}</div>
                <div className="font-semibold text-gray-900 dark:text-white">
                  {formatCurrency(summaryTotals.totalInvested)}
                </div>
              </div>
              <div>
                <div className="text-gray-600 dark:text-gray-400">{t('investments.totalReceived')}</div>
                <div className="font-semibold text-gray-900 dark:text-white">
                  {formatCurrency(summaryTotals.totalReceived)}
                </div>
              </div>
              <div>
                <div className="text-gray-600 dark:text-gray-400">{t('investments.totalBrokerageLabel')}</div>
                <div className="font-semibold text-gray-900 dark:text-white">
                  {formatCurrency(summaryTotals.totalBrokerage)}
                </div>
              </div>
              <div>
                <div className="text-gray-600 dark:text-gray-400">{t('investments.buys')} / {t('investments.sells')}</div>
                <div className="font-semibold text-gray-900 dark:text-white">
                  {summaryTotals.buyCount} / {summaryTotals.sellCount}
                </div>
              </div>
            </div>
          </div>
        )}
      </div>
    </Modal>
  );
}

