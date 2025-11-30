import React, { useState } from 'react';
import { Wallet, LogOut, Moon, Sun, User, Menu } from 'lucide-react';
import { useAuth } from '../../contexts/AuthContext';
import { useTheme } from '../../contexts/ThemeContext';
import { useNavigate } from 'react-router-dom';
import ChangePasswordModal from '../common/ChangePasswordModal';

export default function Header({ onMenuClick }) {
  const { user, logout } = useAuth();
  const { theme, toggleTheme } = useTheme();
  const navigate = useNavigate();
  const [isPasswordModalOpen, setPasswordModalOpen] = useState(false);

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  return (
    <header className="bg-white dark:bg-gray-800 border-b border-gray-200 dark:border-gray-700 sticky top-0 z-40 shadow-sm">
      <div className="px-4 lg:px-6 py-4 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <button
            onClick={onMenuClick}
            className="lg:hidden p-2 -ml-2 text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg"
            aria-label="Menu"
          >
            <Menu className="w-6 h-6" />
          </button>
          <div className="w-10 h-10 bg-primary-600 rounded-xl flex items-center justify-center shadow-md shrink-0">
            <Wallet className="w-6 h-6 text-white" />
          </div>
          <h1 className="text-xl font-bold text-gray-900 dark:text-white hidden sm:block">
            Controle-se
          </h1>
        </div>

        <div className="flex items-center gap-2 lg:gap-4">
          <button
            onClick={toggleTheme}
            className="p-2 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
            aria-label="Alternar tema"
          >
            {theme === 'dark' ? (
              <Sun className="w-5 h-5 text-yellow-500" />
            ) : (
              <Moon className="w-5 h-5 text-gray-700" />
            )}
          </button>

          <button
            type="button"
            onClick={() => setPasswordModalOpen(true)}
            className="flex items-center gap-3 px-3 py-2 bg-gray-50 dark:bg-gray-700 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary-500"
            title="Alterar senha"
          >
            <div className="w-8 h-8 bg-primary-600 rounded-full flex items-center justify-center shrink-0">
              <User className="w-4 h-4 text-white" />
            </div>
            <span className="text-sm font-medium text-gray-900 dark:text-white hidden sm:block">
              {user?.name || 'Usuário'}
            </span>
          </button>

          <button
            onClick={handleLogout}
            className="btn-secondary p-2"
            title="Sair"
          >
            <LogOut className="w-4 h-4" />
          </button>
        </div>
      </div>

      <ChangePasswordModal
        isOpen={isPasswordModalOpen}
        onClose={() => setPasswordModalOpen(false)}
      />
    </header>
  );
}

