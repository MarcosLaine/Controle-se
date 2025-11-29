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

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-900">
      <Header />
      <div className="flex">
        <Sidebar activeSection={activeSection} onSectionChange={setActiveSection} />
        <main className="flex-1 p-6 overflow-y-auto h-[calc(100vh-73px)]">
          <div className="max-w-7xl mx-auto">
            {/* Mantém todos os componentes montados, apenas oculta os inativos */}
            <div style={{ display: activeSection === 'overview' ? 'block' : 'none' }}>
              <Overview />
            </div>
            <div style={{ display: activeSection === 'categories' ? 'block' : 'none' }}>
              <Categories />
            </div>
            <div style={{ display: activeSection === 'accounts' ? 'block' : 'none' }}>
              <Accounts />
            </div>
            <div style={{ display: activeSection === 'transactions' ? 'block' : 'none' }}>
              <Transactions />
            </div>
            <div style={{ display: activeSection === 'budgets' ? 'block' : 'none' }}>
              <Budgets />
            </div>
            <div style={{ display: activeSection === 'tags' ? 'block' : 'none' }}>
              <Tags />
            </div>
            <div style={{ display: activeSection === 'reports' ? 'block' : 'none' }}>
              <Reports />
            </div>
            <div style={{ display: activeSection === 'investments' ? 'block' : 'none' }}>
              <Investments />
            </div>
          </div>
        </main>
      </div>
    </div>
  );
}

