import React from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { Toaster } from 'react-hot-toast';
import { AuthProvider, useAuth } from './contexts/AuthContext';
import { DataProvider } from './contexts/DataContext';
import LoginPage from './pages/LoginPage';
import Dashboard from './pages/Dashboard';
import CompoundInterestCalculatorPage from './pages/CompoundInterestCalculatorPage';
import SkeletonScreen from './components/common/SkeletonScreen';
import ThemeProvider from './contexts/ThemeContext';
import LanguageProvider from './contexts/LanguageContext';
import InactivityMonitor from './components/InactivityMonitor';

function ProtectedRoute({ children }) {
  const { user, loading } = useAuth();

  if (loading) {
    return <SkeletonScreen />;
  }

  if (!user) {
    return <Navigate to="/login" replace />;
  }

  return children;
}

function AppRoutes() {
  const { user, loading } = useAuth();

  if (loading) {
    return <SkeletonScreen />;
  }

  return (
    <Routes>
      <Route 
        path="/login" 
        element={user ? <Navigate to="/dashboard" replace /> : <LoginPage />} 
      />
      <Route
        path="/dashboard"
        element={
          <ProtectedRoute>
            <Dashboard />
          </ProtectedRoute>
        }
      />
      <Route
        path="/calculadora-juros-compostos"
        element={<CompoundInterestCalculatorPage />}
      />
      <Route path="/" element={<Navigate to="/dashboard" replace />} />
    </Routes>
  );
}

function App() {
  return (
    <LanguageProvider>
      <ThemeProvider>
        <AuthProvider>
          <DataProvider>
            <Router>
              <div className="min-h-screen bg-gray-50 dark:bg-gray-900">
                <InactivityMonitor />
                <AppRoutes />
                <Toaster
                position="top-center"
                toastOptions={{
                  duration: 3000,
                  className: 'toast-blur',
                  style: {
                    background: 'rgba(255, 255, 255, 0.85)',
                    backdropFilter: 'blur(12px)',
                    WebkitBackdropFilter: 'blur(12px)',
                    color: '#1f2937',
                    border: '1px solid rgba(255, 255, 255, 0.2)',
                    boxShadow: '0 8px 32px 0 rgba(0, 0, 0, 0.1)',
                  },
                  success: {
                    className: 'toast-blur toast-success',
                    style: {
                      background: 'rgba(34, 197, 94, 0.15)',
                      backdropFilter: 'blur(12px)',
                      WebkitBackdropFilter: 'blur(12px)',
                      color: '#1f2937',
                      border: '1px solid rgba(34, 197, 94, 0.3)',
                      boxShadow: '0 8px 32px 0 rgba(34, 197, 94, 0.2)',
                    },
                    iconTheme: {
                      primary: '#22c55e',
                      secondary: '#fff',
                    },
                  },
                  error: {
                    className: 'toast-blur toast-error',
                    style: {
                      background: 'rgba(239, 68, 68, 0.15)',
                      backdropFilter: 'blur(12px)',
                      WebkitBackdropFilter: 'blur(12px)',
                      color: '#1f2937',
                      border: '1px solid rgba(239, 68, 68, 0.3)',
                      boxShadow: '0 8px 32px 0 rgba(239, 68, 68, 0.2)',
                    },
                    iconTheme: {
                      primary: '#ef4444',
                      secondary: '#fff',
                    },
                  },
                }}
              />
            </div>
          </Router>
        </DataProvider>
      </AuthProvider>
    </ThemeProvider>
    </LanguageProvider>
  );
}

export default App;

