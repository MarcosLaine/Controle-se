import React, { useState } from 'react';
import { X, Edit2, Trash2, Calendar, DollarSign, TrendingUp } from 'lucide-react';
import { useLanguage } from '../../../contexts/LanguageContext';
import Modal from '../../common/Modal';
import { formatCurrency, formatDate } from '../../../utils/formatters';
import Spinner from '../../common/Spinner';

export default function AssetDetailsModal({ isOpen, onClose, assetGroup, onEdit, onDelete, deletingIds = new Set() }) {
  const { t } = useLanguage();
  const [transactionsLimit, setTransactionsLimit] = useState(12);
  
  // Reseta o limite quando o modal for fechado
  React.useEffect(() => {
    if (!isOpen) {
      setTransactionsLimit(12);
    }
  }, [isOpen]);
  
  if (!assetGroup) return null;

  const isPositive = (assetGroup.retornoTotal || 0) >= 0;
  const isFixedIncome = assetGroup.categoria === 'RENDA_FIXA';
  
  // Limita as transações exibidas
  const displayTransactions = assetGroup.aportes ? assetGroup.aportes.slice(0, transactionsLimit) : [];
  const hasMoreTransactions = assetGroup.aportes ? assetGroup.aportes.length > transactionsLimit : false;

  return (
    <Modal isOpen={isOpen} onClose={onClose} title={`${assetGroup.nome} ${assetGroup.nomeAtivo && assetGroup.nomeAtivo !== assetGroup.nome ? ` - ${assetGroup.nomeAtivo}` : ''}`} size="lg">
      <div className="space-y-6">
        {/* Summary Header */}
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
          <div className="p-3 bg-gray-50 dark:bg-gray-700/50 rounded-lg">
            <p className="text-xs text-gray-500 dark:text-gray-400 uppercase">{t('investments.averagePriceLabel')}</p>
            <p className="text-lg font-bold text-gray-900 dark:text-white">
              {formatCurrency(assetGroup.precoMedio)}
            </p>
          </div>
          <div className="p-3 bg-gray-50 dark:bg-gray-700/50 rounded-lg">
            <p className="text-xs text-gray-500 dark:text-gray-400 uppercase">{t('investments.currentValueLabel')}</p>
            <p className="text-lg font-bold text-gray-900 dark:text-white">
              {formatCurrency(assetGroup.valorAtualTotal)}
            </p>
          </div>
          <div className="p-3 bg-gray-50 dark:bg-gray-700/50 rounded-lg">
            <p className="text-xs text-gray-500 dark:text-gray-400 uppercase">{t('investments.investedValueLabel')}</p>
            <p className="text-lg font-bold text-gray-900 dark:text-white">
              {formatCurrency(assetGroup.valorAporteTotal)}
            </p>
          </div>
          <div className="p-3 bg-gray-50 dark:bg-gray-700/50 rounded-lg">
            <p className="text-xs text-gray-500 dark:text-gray-400 uppercase">{t('investments.returnLabel')}</p>
            <p className={`text-lg font-bold ${isPositive ? 'text-green-600' : 'text-red-600'}`}>
              {isPositive ? '+' : ''}{formatCurrency(assetGroup.retornoTotal)}
            </p>
          </div>
        </div>

        {/* List of Contributions (Aportes) */}
        <div>
          <h4 className="text-sm font-semibold text-gray-900 dark:text-white mb-3 border-b border-gray-100 dark:border-gray-700 pb-2">
            {t('investments.contributionsMade')}
          </h4>
          <div className="space-y-3 max-h-96 overflow-y-auto pr-2">
            {displayTransactions.map((aporte) => {
              const isSell = aporte.quantidade < 0;
              // For Sells: Return is (Sell Price - Avg Price) * Qty ? Or just show transaction details.
              // Let's simplify: Show transaction value.
              
              // Original calc for Buys:
              let aporteReturn = (aporte.valorAtual || 0) - (aporte.valorAporte || 0);
              
              // For Sells, "valorAporte" from backend is negative (qty * price).
              // "valorAtual" is also negative (qty * currentPrice).
              // This logic might be confusing for sells.
              // Let's just show: "Venda" | Qty | Price | Total Received.
              
              const aporteReturnPercent = aporte.valorAporte !== 0 
                ? (aporteReturn / aporte.valorAporte) * 100 
                : 0;
              const isAportePositive = aporteReturn >= 0;

              return (
                <div key={aporte.idInvestimento} className={`flex flex-col sm:flex-row sm:items-center justify-between p-3 rounded-lg bg-white dark:bg-gray-800 border ${isSell ? 'border-red-100 dark:border-red-900/30' : 'border-gray-100 dark:border-gray-700'} hover:shadow-sm transition-shadow gap-3`}>
                  <div className="flex-1 grid grid-cols-2 sm:grid-cols-4 gap-2 text-sm">
                    <div className="col-span-2 sm:col-span-1">
                        {isSell ? (
                            <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-300">
                                {t('investments.sellLabel')}
                            </span>
                        ) : (
                            <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300">
                                {t('investments.buyLabel')}
                            </span>
                        )}
                    </div>
                    <div>
                      <p className="text-xs text-gray-500">{t('investments.dateLabel')}</p>
                      <p className="font-medium">{formatDate(aporte.dataAporte)}</p>
                    </div>
                    <div>
                      <p className="text-xs text-gray-500">{t('investments.quantityLabel')}</p>
                      <p className="font-medium">
                        {Math.abs(aporte.quantidade)} {isFixedIncome ? t('investments.units') : t('investments.shares')}
                      </p>
                    </div>
                    <div>
                      <p className="text-xs text-gray-500">{t('investments.unitPrice')}</p>
                      <p className="font-medium">{formatCurrency(aporte.precoAporte)}</p>
                    </div>
                  </div>

                  <div className="flex items-center justify-between sm:justify-end gap-4 border-t sm:border-t-0 border-gray-100 dark:border-gray-700 pt-2 sm:pt-0 mt-2 sm:mt-0">
                    <div className="text-right">
                      <p className="text-xs text-gray-500">{isSell ? t('investments.valueReceived') : t('investments.investedValueLabel')}</p>
                      <p className={`font-bold ${isSell ? 'text-green-600' : 'text-gray-900 dark:text-white'}`}>
                        {formatCurrency(Math.abs(aporte.valorAporte))}
                      </p>
                    </div>
                    <div className="flex gap-1">
                      <button
                        onClick={() => {
                            onEdit(aporte);
                            onClose();
                        }}
                        className="p-1.5 text-gray-400 hover:text-primary-600 hover:bg-primary-50 dark:hover:bg-primary-900/20 rounded transition-colors"
                        title={t('common.edit')}
                      >
                        <Edit2 size={14} />
                      </button>
                      <button
                        onClick={() => {
                            onDelete(aporte.idInvestimento);
                            onClose();
                        }}
                        className="p-1.5 text-gray-400 hover:text-red-600 hover:bg-red-50 dark:hover:bg-red-900/20 rounded transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                        title={t('common.delete')}
                        disabled={deletingIds.has(aporte.idInvestimento)}
                      >
                        {deletingIds.has(aporte.idInvestimento) ? (
                          <Spinner size={14} className="text-red-600 dark:text-red-400" />
                        ) : (
                          <Trash2 size={14} />
                        )}
                      </button>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
          {hasMoreTransactions && (
            <div className="flex justify-center pt-4 mt-4 border-t border-gray-100 dark:border-gray-700">
              <button
                onClick={() => setTransactionsLimit(prev => prev + 12)}
                className="btn-secondary text-sm"
              >
                {t('common.loadMore')}
              </button>
            </div>
          )}
        </div>
      </div>
    </Modal>
  );
}

