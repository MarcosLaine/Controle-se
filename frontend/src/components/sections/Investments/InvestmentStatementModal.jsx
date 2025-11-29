import React, { useState, useMemo } from 'react';
import { X, Download, Filter, FileText, FileSpreadsheet, File } from 'lucide-react';
import Modal from '../../common/Modal';
import { formatCurrency, formatDate } from '../../../utils/formatters';
import jsPDF from 'jspdf';
import 'jspdf-autotable';
import * as XLSX from 'xlsx';
import toast from 'react-hot-toast';

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
    return investments.filter(inv => {
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
    }).sort((a, b) => new Date(b.dataAporte) - new Date(a.dataAporte));
  }, [investments, filters]);

  const categoryNames = {
    'ACAO': 'Ações (B3)',
    'STOCK': 'Stocks (NYSE/NASDAQ)',
    'CRYPTO': 'Criptomoedas',
    'FII': 'Fundos Imobiliários',
    'RENDA_FIXA': 'Renda Fixa',
    'OUTROS': 'Outros'
  };

  const exportToPDF = () => {
    try {
      const doc = new jsPDF();
      
      // Title
      doc.setFontSize(18);
      doc.text('Extrato de Investimentos', 14, 20);
      
      // Filters info
      doc.setFontSize(10);
      let yPos = 30;
      if (filters.startDate || filters.endDate) {
        doc.text(`Período: ${filters.startDate || 'Início'} a ${filters.endDate || 'Fim'}`, 14, yPos);
        yPos += 5;
      }
      if (filters.broker) {
        doc.text(`Corretora: ${filters.broker}`, 14, yPos);
        yPos += 5;
      }
      if (filters.category) {
        doc.text(`Categoria: ${categoryNames[filters.category] || filters.category}`, 14, yPos);
        yPos += 5;
      }
      yPos += 5;

      // Table data
      const tableData = filteredInvestments.map(inv => [
        formatDate(inv.dataAporte),
        inv.nome,
        categoryNames[inv.categoria] || inv.categoria,
        inv.quantidade > 0 ? 'Compra' : 'Venda',
        Math.abs(inv.quantidade).toFixed(6),
        formatCurrency(inv.precoAporte || 0),
        formatCurrency(inv.corretagem || 0),
        formatCurrency((inv.valorAporte || 0) + (inv.corretagem || 0)),
        inv.corretora || '-'
      ]);

      doc.autoTable({
        startY: yPos,
        head: [['Data', 'Ativo', 'Categoria', 'Tipo', 'Quantidade', 'Preço', 'Corretagem', 'Valor Total', 'Corretora']],
        body: tableData,
        styles: { fontSize: 8 },
        headStyles: { fillColor: [59, 130, 246] },
        alternateRowStyles: { fillColor: [245, 247, 250] }
      });

      // Summary
      const finalY = doc.lastAutoTable.finalY + 10;
      doc.setFontSize(12);
      doc.text('Resumo', 14, finalY);
      
      const totalInvested = filteredInvestments.reduce((sum, inv) => sum + (inv.valorAporte || 0), 0);
      const totalBrokerage = filteredInvestments.reduce((sum, inv) => sum + (inv.corretagem || 0), 0);
      const buyCount = filteredInvestments.filter(inv => inv.quantidade > 0).length;
      const sellCount = filteredInvestments.filter(inv => inv.quantidade < 0).length;

      doc.setFontSize(10);
      doc.text(`Total Investido: ${formatCurrency(totalInvested)}`, 14, finalY + 10);
      doc.text(`Total Corretagem: ${formatCurrency(totalBrokerage)}`, 14, finalY + 15);
      doc.text(`Compras: ${buyCount} | Vendas: ${sellCount}`, 14, finalY + 20);
      doc.text(`Total de Operações: ${filteredInvestments.length}`, 14, finalY + 25);

      doc.save(`extrato_investimentos_${new Date().toISOString().split('T')[0]}.pdf`);
      toast.success('Extrato exportado como PDF!');
    } catch (error) {
      console.error('Erro ao exportar PDF:', error);
      toast.error('Erro ao exportar PDF');
    }
  };

  const exportToXLSX = () => {
    try {
      const data = filteredInvestments.map(inv => ({
        'Data': formatDate(inv.dataAporte),
        'Ativo': inv.nome,
        'Nome do Ativo': inv.nomeAtivo || '-',
        'Categoria': categoryNames[inv.categoria] || inv.categoria,
        'Tipo': inv.quantidade > 0 ? 'Compra' : 'Venda',
        'Quantidade': Math.abs(inv.quantidade),
        'Preço Unitário': inv.precoAporte || 0,
        'Corretagem': inv.corretagem || 0,
        'Valor Total': (inv.valorAporte || 0) + (inv.corretagem || 0),
        'Corretora': inv.corretora || '-',
        'Moeda': inv.moeda || 'BRL'
      }));

      const ws = XLSX.utils.json_to_sheet(data);
      const wb = XLSX.utils.book_new();
      XLSX.utils.book_append_sheet(wb, ws, 'Extrato');

      // Add summary sheet
      const totalInvested = filteredInvestments.reduce((sum, inv) => sum + (inv.valorAporte || 0), 0);
      const totalBrokerage = filteredInvestments.reduce((sum, inv) => sum + (inv.corretagem || 0), 0);
      const buyCount = filteredInvestments.filter(inv => inv.quantidade > 0).length;
      const sellCount = filteredInvestments.filter(inv => inv.quantidade < 0).length;

      const summaryData = [
        ['Resumo do Extrato'],
        [''],
        ['Total Investido', formatCurrency(totalInvested)],
        ['Total Corretagem', formatCurrency(totalBrokerage)],
        ['Compras', buyCount],
        ['Vendas', sellCount],
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
      const headers = ['Data', 'Ativo', 'Nome do Ativo', 'Categoria', 'Tipo', 'Quantidade', 'Preço Unitário', 'Corretagem', 'Valor Total', 'Corretora', 'Moeda'];
      const rows = filteredInvestments.map(inv => [
        formatDate(inv.dataAporte),
        inv.nome,
        inv.nomeAtivo || '-',
        categoryNames[inv.categoria] || inv.categoria,
        inv.quantidade > 0 ? 'Compra' : 'Venda',
        Math.abs(inv.quantidade),
        inv.precoAporte || 0,
        inv.corretagem || 0,
        (inv.valorAporte || 0) + (inv.corretagem || 0),
        inv.corretora || '-',
        inv.moeda || 'BRL'
      ]);

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
              filteredInvestments.map((inv, idx) => (
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
                          inv.quantidade > 0 
                            ? 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400'
                            : 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400'
                        }`}>
                          {inv.quantidade > 0 ? 'Compra' : 'Venda'}
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
                        {formatCurrency((inv.valorAporte || 0) + (inv.corretagem || 0))}
                      </div>
                      {inv.corretagem > 0 && (
                        <div className="text-xs text-gray-500 dark:text-gray-400">
                          Corretagem: {formatCurrency(inv.corretagem)}
                        </div>
                      )}
                    </div>
                  </div>
                </div>
              ))
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
                  {formatCurrency(filteredInvestments.reduce((sum, inv) => sum + (inv.valorAporte || 0), 0))}
                </div>
              </div>
              <div>
                <div className="text-gray-600 dark:text-gray-400">Total Corretagem</div>
                <div className="font-semibold text-gray-900 dark:text-white">
                  {formatCurrency(filteredInvestments.reduce((sum, inv) => sum + (inv.corretagem || 0), 0))}
                </div>
              </div>
              <div>
                <div className="text-gray-600 dark:text-gray-400">Compras</div>
                <div className="font-semibold text-gray-900 dark:text-white">
                  {filteredInvestments.filter(inv => inv.quantidade > 0).length}
                </div>
              </div>
              <div>
                <div className="text-gray-600 dark:text-gray-400">Vendas</div>
                <div className="font-semibold text-gray-900 dark:text-white">
                  {filteredInvestments.filter(inv => inv.quantidade < 0).length}
                </div>
              </div>
            </div>
          </div>
        )}
      </div>
    </Modal>
  );
}

