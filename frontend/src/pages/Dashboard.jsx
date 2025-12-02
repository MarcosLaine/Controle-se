import React, { lazy, Suspense, useCallback, useState } from 'react';
import SkeletonSection from '../components/common/SkeletonSection';
import Header from '../components/layout/Header';
import Sidebar from '../components/layout/Sidebar';

const lazyWithPreload = (loader) => {
  const Component = lazy(loader);
  Component.preload = loader;
  return Component;
};

const sectionComponents = {
  overview: lazyWithPreload(() => import('../components/sections/Overview')),
  categories: lazyWithPreload(() => import('../components/sections/CategoriesAndTags')),
  accounts: lazyWithPreload(() => import('../components/sections/Accounts')),
  transactions: lazyWithPreload(() => import('../components/sections/Transactions')),
  reports: lazyWithPreload(() => import('../components/sections/Reports')),
  investments: lazyWithPreload(() => import('../components/sections/Investments')),
  tools: lazyWithPreload(() => import('../components/sections/Tools')),
};

const SectionFallback = ({ sectionType }) => (
  <SkeletonSection type={sectionType} />
);

export default function Dashboard() {
  const [activeSection, setActiveSection] = useState(() => {
    return localStorage.getItem('controle-se-active-section') || 'overview';
  });
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);

  const prefetchSection = useCallback((sectionId) => {
    sectionComponents[sectionId]?.preload?.();
  }, []);

  const handleSectionChange = useCallback((section) => {
    if (section === activeSection) {
      setIsMobileMenuOpen(false);
      return;
    }

    prefetchSection(section);
    setActiveSection(section);
    localStorage.setItem('controle-se-active-section', section);
    setIsMobileMenuOpen(false);
  }, [activeSection, prefetchSection]);

  const ActiveSection = sectionComponents[activeSection] || sectionComponents.overview;

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-900">
      <Header onMenuClick={() => setIsMobileMenuOpen(!isMobileMenuOpen)} />
      <div className="flex">
        <Sidebar 
          activeSection={activeSection} 
          onSectionChange={handleSectionChange}
          onSectionHover={prefetchSection}
          isOpen={isMobileMenuOpen}
          onClose={() => setIsMobileMenuOpen(false)}
        />
        <main className="flex-1 p-4 lg:p-6 overflow-y-auto h-[calc(100vh-73px)] w-full">
          <div className="max-w-7xl mx-auto">
            <Suspense fallback={<SectionFallback sectionType={activeSection} />}>
              <ActiveSection />
            </Suspense>
          </div>
        </main>
      </div>
    </div>
  );
}

