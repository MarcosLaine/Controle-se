import React from 'react';
import { X, Edit2, Trash2, Calendar, DollarSign, TrendingUp, Percent } from 'lucide-react';
import { useLanguage } from '../../../contexts/LanguageContext';
import Modal from '../../common/Modal';
import { formatCurrency, formatDate } from '../../../utils/formatters';
import Spinner from '../../common/Spinner';

export default function InvestmentDetailsModal({ isOpen, onClose, investment, onEdit, onDelete, deletingIds = new Set() }) {
  const { t } = useLanguage();
  if (!investment) return null;

  const isPositive = (investment.retorno || 0) >= 0;
  const isFixedIncome = investment.categoria === 'RENDA_FIXA';

  return (
    <Modal isOpen={isOpen} onClose={onClose} title={t('investments.investmentDetails')}>
      <div className="space-y-6">
        {/* Header */}
        <div className="flex justify-between items-start">
          <div>
            <h3 className="text-xl font-bold text-gray-900 dark:text-white">
              {investment.nome}
            </h3>
            <p className="text-sm text-gray-500 dark:text-gray-400">
              {investment.categoria} {isFixedIncome && investment.tipoInvestimento ? `• ${investment.tipoInvestimento}` : ''}
            </p>
          </div>
          <div className={`px-3 py-1 rounded-full text-sm font-medium ${isPositive ? 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400' : 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-400'}`}>
            {isPositive ? '+' : ''}{investment.retornoPercent?.toFixed(2)}%
          </div>
        </div>

        {/* Main Stats */}
        <div className="grid grid-cols-2 gap-4">
          <div className="p-4 bg-gray-50 dark:bg-gray-700/50 rounded-lg">
            <div className="flex items-center gap-2 text-gray-500 dark:text-gray-400 mb-1">
              <DollarSign size={16} />
              <span className="text-xs font-medium uppercase">{t('investments.currentValueLabel')}</span>
            </div>
            <p className="text-lg font-bold text-gray-900 dark:text-white">
              {formatCurrency(investment.valorAtual || 0)}
            </p>
          </div>
          <div className="p-4 bg-gray-50 dark:bg-gray-700/50 rounded-lg">
            <div className="flex items-center gap-2 text-gray-500 dark:text-gray-400 mb-1">
              <TrendingUp size={16} />
              <span className="text-xs font-medium uppercase">{t('investments.returnLabel')}</span>
            </div>
            <p className={`text-lg font-bold ${isPositive ? 'text-green-600' : 'text-red-600'}`}>
              {isPositive ? '+' : ''}{formatCurrency(investment.retorno || 0)}
            </p>
          </div>
        </div>

        {/* Details List */}
        <div className="space-y-3 text-sm">
          <div className="flex justify-between py-2 border-b border-gray-100 dark:border-gray-700">
            <span className="text-gray-600 dark:text-gray-400">{t('investments.investedValueLabel')}</span>
            <span className="font-medium text-gray-900 dark:text-white">{formatCurrency(investment.valorAporte || 0)}</span>
          </div>
          
          {investment.quantidade > 0 && (
            <div className="flex justify-between py-2 border-b border-gray-100 dark:border-gray-700">
              <span className="text-gray-600 dark:text-gray-400">{t('investments.quantityLabel')}</span>
              <span className="font-medium text-gray-900 dark:text-white">
                {investment.quantidade} {isFixedIncome ? 'un' : 'cotas'}
              </span>
            </div>
          )}

          {investment.precoAporte > 0 && (
            <div className="flex justify-between py-2 border-b border-gray-100 dark:border-gray-700">
              <span className="text-gray-600 dark:text-gray-400">{t('investments.averagePriceLabel')}</span>
              <span className="font-medium text-gray-900 dark:text-white">{formatCurrency(investment.precoAporte)}</span>
            </div>
          )}

          <div className="flex justify-between py-2 border-b border-gray-100 dark:border-gray-700">
            <span className="text-gray-600 dark:text-gray-400">{t('investments.contributionDateLabel')}</span>
            <span className="font-medium text-gray-900 dark:text-white flex items-center gap-1">
              <Calendar size={14} />
              {formatDate(investment.dataAporte)}
            </span>
          </div>

          {investment.dataVencimento && (
            <div className="flex justify-between py-2 border-b border-gray-100 dark:border-gray-700">
              <span className="text-gray-600 dark:text-gray-400">{t('investments.maturityDateLabel')}</span>
              <span className="font-medium text-gray-900 dark:text-white flex items-center gap-1">
                <Calendar size={14} />
                {formatDate(investment.dataVencimento)}
              </span>
            </div>
          )}

          {/* Fixed Income Specifics */}
          {isFixedIncome && (
            <>
              <div className="flex justify-between py-2 border-b border-gray-100 dark:border-gray-700">
                <span className="text-gray-600 dark:text-gray-400">{t('investments.profitabilityLabel')}</span>
                <span className="font-medium text-gray-900 dark:text-white">
                  {investment.tipoRentabilidade === 'PRE_FIXADO' ? t('investments.preFixedLabel') : 
                   investment.tipoRentabilidade === 'POS_FIXADO' ? t('investments.postFixedLabel') : t('investments.hybrid')}
                </span>
              </div>
              
              {(investment.indice || investment.taxaFixa) && (
                <div className="flex justify-between py-2 border-b border-gray-100 dark:border-gray-700">
                  <span className="text-gray-600 dark:text-gray-400">{t('investments.rates')}</span>
                  <span className="font-medium text-gray-900 dark:text-white">
                    {investment.indice && `${investment.percentualIndice}% do ${investment.indice}`}
                    {investment.indice && investment.taxaFixa ? ' + ' : ''}
                    {investment.taxaFixa && `${investment.taxaFixa}% a.a.`}
                  </span>
                </div>
              )}
            </>
          )}
          
          {investment.corretora && (
             <div className="flex justify-between py-2 border-b border-gray-100 dark:border-gray-700">
                <span className="text-gray-600 dark:text-gray-400">{t('investments.brokerageLabel')}</span>
                <span className="font-medium text-gray-900 dark:text-white">{investment.corretora}</span>
             </div>
          )}
        </div>

        {/* Actions */}
        <div className="flex gap-3 mt-6 pt-4 border-t border-gray-100 dark:border-gray-700">
          <button
            onClick={() => {
                onEdit(investment);
                onClose();
            }}
            className="flex-1 btn-secondary flex justify-center items-center gap-2"
          >
            <Edit2 size={16} />
            {t('common.edit')}
          </button>
          <button
            onClick={() => {
                onDelete(investment.idInvestimento);
                onClose();
            }}
            className="flex-1 btn-danger flex justify-center items-center gap-2"
            disabled={deletingIds.has(investment.idInvestimento)}
          >
            {deletingIds.has(investment.idInvestimento) ? (
              <Spinner size={16} className="text-white" />
            ) : (
              <>
                <Trash2 size={16} />
                {t('common.delete')}
              </>
            )}
          </button>
        </div>
      </div>
    </Modal>
  );
}

