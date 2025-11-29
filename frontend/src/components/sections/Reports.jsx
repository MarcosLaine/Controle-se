import React, { useState, useEffect } from 'react';
import { FileText, Download, ArrowUp, ArrowDown, Scale, TrendingUp } from 'lucide-react';
import { useAuth } from '../../contexts/AuthContext';
import api from '../../services/api';
import { formatCurrency, formatDate } from '../../utils/formatters';
import SummaryCard from '../common/SummaryCard';
import toast from 'react-hot-toast';
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

ChartJS.register(ArcElement, Tooltip, Legend, CategoryScale, LinearScale, BarElement, Title);

export default function Reports() {
  const { user } = useAuth();
  const [loading, setLoading] = useState(true);
  const [period, setPeriod] = useState('month');
  const [startDate, setStartDate] = useState('');
  const [endDate, setEndDate] = useState('');
  const [reportData, setReportData] = useState(null);
  const [exportFormat, setExportFormat] = useState('csv');

  useEffect(() => {
    if (user && (period !== 'custom' || (startDate && endDate))) {
      loadReports();
    }
  }, [user, period, startDate, endDate]);

  const loadReports = async () => {
    if (!user) return;
    setLoading(true);
    try {
      let url = `/reports?userId=${user.id}&period=${period}`;
      if (period === 'custom' && startDate && endDate) {
        url += `&startDate=${startDate}&endDate=${endDate}`;
      }
      const response = await api.get(url);
      if (response.success) {
        setReportData(response.data);
      } else {
        toast.error('Erro ao carregar relatórios');
      }
    } catch (error) {
      console.error('Erro ao carregar relatórios:', error);
      toast.error('Erro ao carregar relatórios');
    } finally {
      setLoading(false);
    }
  };

  const handleExport = async () => {
    if (!user) return;
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
        toast.success('Relatório exportado com sucesso!');
      } else {
        toast.error('Erro ao exportar relatório');
      }
    } catch (error) {
      console.error('Erro ao exportar:', error);
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

  // Converte categoryAnalysis de Map para array
  const categoryData = reportData?.categoryAnalysis
    ? Object.entries(reportData.categoryAnalysis).map(([name, total]) => ({ name, total }))
    : [];

  // Converte monthlyAnalysis para gráfico
  const monthlyData = reportData?.monthlyAnalysis || [];

  return (
    <div className="space-y-6 animate-fade-in">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-3xl font-bold text-gray-900 dark:text-white">Relatórios</h2>
          <p className="text-gray-600 dark:text-gray-400 mt-1">Análise detalhada das suas finanças</p>
        </div>
        <div className="flex gap-2">
          <select
            value={period}
            onChange={(e) => setPeriod(e.target.value)}
            className="input w-auto"
          >
            <option value="month">Este Mês</option>
            <option value="year">Este Ano</option>
            <option value="custom">Período Personalizado</option>
          </select>
          {period === 'custom' && (
            <>
              <input
                type="date"
                value={startDate}
                onChange={(e) => setStartDate(e.target.value)}
                className="input w-auto"
                placeholder="Data Início"
              />
              <input
                type="date"
                value={endDate}
                onChange={(e) => setEndDate(e.target.value)}
                className="input w-auto"
                placeholder="Data Fim"
              />
            </>
          )}
          <select
            value={exportFormat}
            onChange={(e) => setExportFormat(e.target.value)}
            className="input w-auto"
          >
            <option value="csv">CSV</option>
            <option value="xlsx">XLSX</option>
          </select>
          <button onClick={handleExport} className="btn-primary">
            <Download className="w-4 h-4" />
            Exportar
          </button>
        </div>
      </div>

      {reportData ? (
        <>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <SummaryCard
              title="Total de Receitas"
              amount={formatCurrency(reportData.totalIncomes || 0)}
              icon={ArrowUp}
              type="income"
              subtitle={`${reportData.incomeCount || 0} transações`}
            />
            <SummaryCard
              title="Total de Gastos"
              amount={formatCurrency(reportData.totalExpenses || 0)}
              icon={ArrowDown}
              type="expense"
              subtitle={`${reportData.expenseCount || 0} transações`}
            />
            <SummaryCard
              title="Saldo do Período"
              amount={formatCurrency(reportData.balance || 0)}
              icon={Scale}
              type="balance"
              subtitle={reportData.startDate && reportData.endDate 
                ? `${formatDate(reportData.startDate)} a ${formatDate(reportData.endDate)}`
                : 'Período selecionado'}
            />
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            {/* Gastos por Categoria */}
            {categoryData.length > 0 && (
              <div className="card">
                <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
                  Gastos por Categoria
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
          <p className="text-gray-600 dark:text-gray-400">Nenhum dado disponível para o período selecionado</p>
        </div>
      )}
    </div>
  );
}

