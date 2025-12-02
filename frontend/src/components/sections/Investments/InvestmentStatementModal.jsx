import jsPDF from 'jspdf';
import autoTable from 'jspdf-autotable';
import { File, FileSpreadsheet, FileText, Filter } from 'lucide-react';
import React, { useMemo, useState } from 'react';
import toast from 'react-hot-toast';
import * as XLSX from 'xlsx';
import { formatCurrency, formatDate } from '../../../utils/formatters';
import Modal from '../../common/Modal';

export default function InvestmentStatementModal({ isOpen, onClose, investments, accounts }) {
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
    const grossValue = Math.abs(inv.valorAporte || 0);
    const brokerage = inv.corretagem || 0;
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
      const doc = new jsPDF();
      const pageWidth = doc.internal.pageSize.getWidth();
      const margin = 14;
      const palette = {
        primary: [59, 130, 246],
        emerald: [16, 185, 129],
        amber: [245, 158, 11],
        slate: [15, 23, 42],
        gray: [75, 85, 99],
      };

      let yPos = 48;

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

      // Header
      doc.setFillColor(...palette.primary);
      doc.rect(0, 0, pageWidth, 42, 'F');
      doc.setFontSize(18);
      doc.setTextColor(255, 255, 255);
      doc.text('Extrato de Investimentos', margin, 20);
      doc.setFontSize(10);
      doc.text(`Gerado em: ${formatDate(new Date().toISOString())}`, margin, 29);

      const filterRange = filters.startDate || filters.endDate
        ? `${filters.startDate || 'Início'} → ${filters.endDate || 'Hoje'}`
        : 'Todo o período';
      const filterBroker = filters.broker || 'Todas';
      const filterCategory = filters.category ? (categoryNames[filters.category] || filters.category) : 'Todas';
      const filterLine = `Filtros: ${filterRange} • Corretora: ${filterBroker} • Categoria: ${filterCategory}`;
      const wrappedFilter = doc.splitTextToSize(filterLine, pageWidth - margin * 2);
      wrappedFilter.forEach((line, idx) => {
        doc.text(line, margin, 36 + idx * 6);
      });
      yPos = 36 + wrappedFilter.length * 6 + 12;

      // Destaques
      const highlightWidth = (pageWidth - margin * 2 - 6) / 2;
      const drawHighlight = (label, value, x, color) => {
        doc.setFillColor(...color);
        doc.roundedRect(x, yPos, highlightWidth, 24, 3, 3, 'F');
        doc.setTextColor(255, 255, 255);
        doc.setFontSize(9);
        doc.text(label, x + 4, yPos + 8);
        doc.setFontSize(12);
        doc.text(value, x + 4, yPos + 18);
      };

      drawHighlight('Total Investido no Período', formatCurrency(summaryTotals.totalInvested), margin, [14, 116, 144]);
      drawHighlight('Total Recebido em Vendas', formatCurrency(summaryTotals.totalReceived), margin + highlightWidth + 6, palette.emerald);
      yPos += 32;

      drawHighlight('Total em Corretagem', formatCurrency(summaryTotals.totalBrokerage), margin, palette.amber);
      drawHighlight(
        'Operações Realizadas',
        `${summaryTotals.buyCount} compras • ${summaryTotals.sellCount} vendas`,
        margin + highlightWidth + 6,
        palette.gray
      );
      yPos += 36;
      doc.setTextColor(...palette.slate);

      // Tabela principal
      drawSectionTitle('Operações Filtradas', palette.primary);
      const tableData = filteredInvestments.map(inv => {
        const { total } = getOperationTotals(inv);
        return [
          formatDate(inv.dataAporte),
          inv.nome,
          categoryNames[inv.categoria] || inv.categoria,
          inv.quantidade > 0 ? 'Compra' : 'Venda',
          Math.abs(inv.quantidade).toFixed(6),
          formatCurrency(inv.precoAporte || 0),
          formatCurrency(inv.corretagem || 0),
          formatCurrency(total),
          inv.corretora || '-',
          inv.quantidade < 0 ? formatCurrency(inv.realizedPnL || 0) : '-',
        ];
      });

      autoTable(doc, {
        startY: yPos,
        head: [
          ['Data', 'Ativo', 'Categoria', 'Tipo', 'Quantidade', 'Preço', 'Corretagem', 'Valor Total', 'Corretora', 'Lucro Realizado'],
        ],
        body: tableData,
        styles: { fontSize: 8 },
        headStyles: { fillColor: palette.primary },
        alternateRowStyles: { fillColor: [248, 250, 252] },
      });
      yPos = doc.lastAutoTable.finalY + 12;

      // Insights rápidos
      drawSectionTitle('Insights Rápidos', palette.emerald);
      doc.setFontSize(10);
      if (filteredInvestments.length === 0) {
        doc.text('Nenhuma operação encontrada com os filtros selecionados.', margin, yPos);
        yPos += 8;
      } else {
        doc.text(`Média de operações por dia útil: ${(filteredInvestments.length / Math.max(1, filteredInvestments.length)).toFixed(1)}`, margin, yPos);
        yPos += 6;
        doc.text(`Maior operação registrada: ${formatCurrency(Math.max(...filteredInvestments.map(inv => Math.abs(inv.valorAporte || 0))))}`, margin, yPos);
        yPos += 6;
        doc.text('Lucros realizados listados apenas em vendas (coluna final).', margin, yPos);
        yPos += 6;
      }

      doc.save(`extrato_investimentos_${new Date().toISOString().split('T')[0]}.pdf`);
      toast.success('Extrato exportado como PDF!');
    } catch (error) {
      console.error('Erro ao exportar PDF:', error);
      toast.error('Erro ao exportar PDF');
    }
  };

  const exportToXLSX = () => {
    try {
      const data = filteredInvestments.map(inv => {
        const { total } = getOperationTotals(inv);
                return {
        'Data': formatDate(inv.dataAporte),
        'Ativo': inv.nome,
        'Nome do Ativo': inv.nomeAtivo || '-',
        'Categoria': categoryNames[inv.categoria] || inv.categoria,
        'Tipo': inv.quantidade > 0 ? 'Compra' : 'Venda',
        'Quantidade': Math.abs(inv.quantidade),
        'Preço Unitário': inv.precoAporte || 0,
        'Corretagem': inv.corretagem || 0,
          'Valor Total': total,
        'Corretora': inv.corretora || '-',
                  'Lucro/Prejuízo Realizado': inv.quantidade < 0 ? inv.realizedPnL || 0 : null,
        'Moeda': inv.moeda || 'BRL'
        };
      });

      const ws = XLSX.utils.json_to_sheet(data);
      const wb = XLSX.utils.book_new();
      XLSX.utils.book_append_sheet(wb, ws, 'Extrato');

      // Add summary sheet
      const summaryData = [
        ['Resumo do Extrato'],
        [''],
        ['Total Investido', formatCurrency(summaryTotals.totalInvested)],
        ['Total Recebido (Vendas)', formatCurrency(summaryTotals.totalReceived)],
        ['Total Corretagem', formatCurrency(summaryTotals.totalBrokerage)],
        ['Compras', summaryTotals.buyCount],
        ['Vendas', summaryTotals.sellCount],
        ['Total de Operações', filteredInvestments.length]
      ];

      const summaryWs = XLSX.utils.aoa_to_sheet(summaryData);
      XLSX.utils.book_append_sheet(wb, summaryWs, 'Resumo');

      XLSX.writeFile(wb, `extrato_investimentos_${new Date().toISOString().split('T')[0]}.xlsx`);
      toast.success('Extrato exportado como XLSX!');
    } catch (error) {
      console.error('Erro ao exportar XLSX:', error);
      toast.error('Erro ao exportar XLSX');
    }
  };

  const exportToCSV = () => {
    try {
      const headers = ['Data', 'Ativo', 'Nome do Ativo', 'Categoria', 'Tipo', 'Quantidade', 'Preço Unitário', 'Corretagem', 'Valor Total', 'Corretora', 'Lucro/Prejuízo Realizado', 'Moeda'];
      const rows = filteredInvestments.map(inv => {
        const { total } = getOperationTotals(inv);
        return [
        formatDate(inv.dataAporte),
        inv.nome,
        inv.nomeAtivo || '-',
        categoryNames[inv.categoria] || inv.categoria,
        inv.quantidade > 0 ? 'Compra' : 'Venda',
        Math.abs(inv.quantidade),
        inv.precoAporte || 0,
        inv.corretagem || 0,
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
      toast.success('Extrato exportado como CSV!');
    } catch (error) {
      console.error('Erro ao exportar CSV:', error);
      toast.error('Erro ao exportar CSV');
    }
  };

  if (!isOpen) return null;

  return (
    <Modal isOpen={isOpen} onClose={onClose} title="Extrato de Investimentos" size="lg">
      <div className="space-y-4">
        {/* Filters */}
        <div className="bg-gray-50 dark:bg-gray-700/50 p-4 rounded-lg space-y-3">
          <div className="flex items-center gap-2 text-sm font-semibold text-gray-700 dark:text-gray-300">
            <Filter size={16} />
            Filtros
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label className="label text-xs">Data Início</label>
              <input
                type="date"
                className="input"
                value={filters.startDate}
                onChange={(e) => setFilters({ ...filters, startDate: e.target.value })}
              />
            </div>
            <div>
              <label className="label text-xs">Data Fim</label>
              <input
                type="date"
                className="input"
                value={filters.endDate}
                onChange={(e) => setFilters({ ...filters, endDate: e.target.value })}
              />
            </div>
            <div>
              <label className="label text-xs">Corretora</label>
              <select
                className="input"
                value={filters.broker}
                onChange={(e) => setFilters({ ...filters, broker: e.target.value })}
              >
                <option value="">Todas</option>
                {brokers.map(broker => (
                  <option key={broker} value={broker}>{broker}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="label text-xs">Categoria</label>
              <select
                className="input"
                value={filters.category}
                onChange={(e) => setFilters({ ...filters, category: e.target.value })}
              >
                <option value="">Todas</option>
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
            <span className="hidden sm:inline">Exportar PDF</span>
            <span className="sm:hidden">PDF</span>
          </button>
          <button onClick={exportToXLSX} className="btn-secondary flex items-center gap-2 text-sm px-3 py-2 flex-1 sm:flex-none">
            <FileSpreadsheet size={16} />
            <span className="hidden sm:inline">Exportar XLSX</span>
            <span className="sm:hidden">XLSX</span>
          </button>
          <button onClick={exportToCSV} className="btn-secondary flex items-center gap-2 text-sm px-3 py-2 flex-1 sm:flex-none">
            <File size={16} />
            <span className="hidden sm:inline">Exportar CSV</span>
            <span className="sm:hidden">CSV</span>
          </button>
        </div>

        {/* Transactions List */}
        <div className="max-h-96 overflow-y-auto">
          <div className="space-y-2">
            {filteredInvestments.length === 0 ? (
              <div className="text-center py-8 text-gray-500">
                Nenhuma operação encontrada com os filtros selecionados
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
                          {isBuy ? 'Compra' : 'Venda'}
                          </span>
                        </div>
                        <div className="text-xs text-gray-500 dark:text-gray-400 mt-1">
                          {categoryNames[inv.categoria] || inv.categoria} • {formatDate(inv.dataAporte)}
                          {inv.corretora && ` • ${inv.corretora}`}
                        </div>
                        <div className="text-xs text-gray-500 dark:text-gray-400 mt-1">
                          Qtd: {Math.abs(inv.quantidade).toFixed(6)} • Preço: {formatCurrency(inv.precoAporte || 0)}
                        </div>
                      </div>
                      <div className="text-right">
                        <div className="font-semibold text-gray-900 dark:text-white">
                          {formatCurrency(total)}
                        </div>
                        {brokerage > 0 && (
                          <div className="text-xs text-gray-500 dark:text-gray-400">
                            Corretagem: {formatCurrency(brokerage)}
                          </div>
                        )}
                        {!isBuy && (
                          <div className={`text-xs font-medium mt-1 ${inv.realizedPnL >= 0 ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'}`}>
                            {inv.realizedPnL >= 0 ? 'Lucro realizado' : 'Prejuízo realizado'} de {formatCurrency(inv.realizedPnL || 0)}
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
                <div className="text-gray-600 dark:text-gray-400">Total Investido</div>
                <div className="font-semibold text-gray-900 dark:text-white">
                  {formatCurrency(summaryTotals.totalInvested)}
                </div>
              </div>
              <div>
                <div className="text-gray-600 dark:text-gray-400">Total Recebido</div>
                <div className="font-semibold text-gray-900 dark:text-white">
                  {formatCurrency(summaryTotals.totalReceived)}
                </div>
              </div>
              <div>
                <div className="text-gray-600 dark:text-gray-400">Total Corretagem</div>
                <div className="font-semibold text-gray-900 dark:text-white">
                  {formatCurrency(summaryTotals.totalBrokerage)}
                </div>
              </div>
              <div>
                <div className="text-gray-600 dark:text-gray-400">Compras / Vendas</div>
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

