import React, { useState } from 'react';
import Header from '../components/layout/Header';
import Sidebar from '../components/layout/Sidebar';
import Overview from '../components/sections/Overview';
import Categories from '../components/sections/Categories';
import Accounts from '../components/sections/Accounts';
import Transactions from '../components/sections/Transactions';
import Budgets from '../components/sections/Budgets';
import Tags from '../components/sections/Tags';
import Reports from '../components/sections/Reports';
import Investments from '../components/sections/Investments';

export default function Dashboard() {
  const [activeSection, setActiveSection] = useState(() => {
    return localStorage.getItem('controle-se-active-section') || 'overview';
  });
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);

  const renderSection = () => {
    switch (activeSection) {
      case 'overview':
        return <Overview />;
      case 'categories':
        return <Categories />;
      case 'accounts':
        return <Accounts />;
      case 'transactions':
        return <Transactions />;
      case 'budgets':
        return <Budgets />;
      case 'tags':
        return <Tags />;
      case 'reports':
        return <Reports />;
      case 'investments':
        return <Investments />;
      default:
        return <Overview />;
    }
  };

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-900">
      <Header onMenuClick={() => setIsMobileMenuOpen(!isMobileMenuOpen)} />
      <div className="flex">
        <Sidebar 
          activeSection={activeSection} 
          onSectionChange={(section) => {
            setActiveSection(section);
            localStorage.setItem('controle-se-active-section', section);
            setIsMobileMenuOpen(false);
          }}
          isOpen={isMobileMenuOpen}
          onClose={() => setIsMobileMenuOpen(false)}
        />
        <main className="flex-1 p-4 lg:p-6 overflow-y-auto h-[calc(100vh-73px)] w-full">
          <div className="max-w-7xl mx-auto">
            {renderSection()}
          </div>
        </main>
      </div>
    </div>
  );
}

