import React from 'react';
import { 
  LayoutDashboard, 
  Tag, 
  Building2, 
  ArrowLeftRight, 
  TrendingUp, 
  FileText, 
  PieChart,
  BarChart3,
  X
} from 'lucide-react';

const menuItems = [
  { id: 'overview', label: 'Visão Geral', icon: LayoutDashboard },
  { id: 'categories', label: 'Categorias', icon: Tag },
  { id: 'accounts', label: 'Contas', icon: Building2 },
  { id: 'transactions', label: 'Transações', icon: ArrowLeftRight },
  { id: 'budgets', label: 'Orçamentos', icon: TrendingUp },
  { id: 'tags', label: 'Tags', icon: Tag },
  { id: 'reports', label: 'Relatórios', icon: FileText },
  { id: 'investments', label: 'Investimentos', icon: PieChart },
];

export default function Sidebar({ activeSection, onSectionChange, onSectionHover, isOpen, onClose }) {
  const handleClick = (sectionId) => {
    onSectionChange(sectionId);
  };

  return (
    <>
      {/* Overlay para mobile */}
      {isOpen && (
        <div 
          className="fixed inset-0 bg-black/50 z-40 lg:hidden"
          onClick={onClose}
        />
      )}
      
      <aside className={`
        fixed lg:static inset-y-0 left-0 z-50
        w-64 bg-white dark:bg-gray-800 
        border-r border-gray-200 dark:border-gray-700 
        h-full lg:h-[calc(100vh-73px)] 
        overflow-y-auto transition-transform duration-300 ease-in-out
        ${isOpen ? 'translate-x-0' : '-translate-x-full lg:translate-x-0'}
      `}>
        <div className="flex items-center justify-between p-4 lg:hidden border-b border-gray-200 dark:border-gray-700">
          <span className="font-bold text-lg text-gray-900 dark:text-white">Menu</span>
          <button 
            onClick={onClose}
            className="p-2 text-gray-500 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <nav className="p-4 space-y-1">
          {menuItems.map((item) => {
            const Icon = item.icon;
            const isActive = activeSection === item.id;
            
            return (
              <button
                key={item.id}
                onClick={() => handleClick(item.id)}
                onMouseEnter={() => onSectionHover?.(item.id)}
                className={`w-full flex items-center gap-3 px-4 py-3 rounded-lg transition-all duration-200 ${
                  isActive
                    ? 'bg-primary-600 text-white shadow-md'
                    : 'text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700'
                }`}
              >
                <Icon className="w-5 h-5" />
                <span className="font-medium">{item.label}</span>
              </button>
            );
          })}
        </nav>
      </aside>
    </>
  );
}

