import React from 'react';

export default function SummaryCard({ 
  title, 
  amount, 
  icon: Icon, 
  type = 'default',
  subtitle,
  onClick,
  className = '',
  action,
}) {
  const typeClasses = {
    income: 'bg-green-50 dark:bg-green-900/20 border-green-200 dark:border-green-800',
    expense: 'bg-red-50 dark:bg-red-900/20 border-red-200 dark:border-red-800',
    balance: 'bg-blue-50 dark:bg-blue-900/20 border-blue-200 dark:border-blue-800',
    default: 'bg-white dark:bg-gray-800 border-gray-200 dark:border-gray-700',
  };

  const iconClasses = {
    income: 'bg-green-500 text-white',
    expense: 'bg-red-500 text-white',
    balance: 'bg-blue-500 text-white',
    default: 'bg-primary-500 text-white',
  };

  return (
    <div
      className={`card border-2 ${typeClasses[type]} ${onClick ? 'cursor-pointer hover:scale-105' : ''} ${className}`}
      onClick={onClick}
    >
      <div className="flex items-start justify-between">
        <div className="flex-1">
          <div className="flex items-center gap-2 mb-2">
            <h3 className="text-sm font-medium text-gray-600 dark:text-gray-400">
              {title}
            </h3>
          </div>
          <p className="text-2xl font-bold text-gray-900 dark:text-white mb-1">
            {amount}
          </p>
          {(subtitle || action) && (
            <div className="flex items-center gap-2 text-xs text-gray-500 dark:text-gray-400">
              {subtitle && <span>{subtitle}</span>}
              {action}
            </div>
          )}
        </div>
        {Icon && (
          <div className={`w-12 h-12 rounded-xl flex items-center justify-center ${iconClasses[type]} shadow-md`}>
            <Icon className="w-6 h-6" />
          </div>
        )}
      </div>
    </div>
  );
}

