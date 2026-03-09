import React, { useState } from 'react';
import { useNavigate, useSearchParams, Link } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import { useLanguage } from '../contexts/LanguageContext';
import { Lock, Mail } from 'lucide-react';

export default function ResetPasswordPage() {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token') || '';
  const { resetPassword } = useAuth();
  const { t } = useLanguage();
  const navigate = useNavigate();

  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!token.trim()) {
      return;
    }
    if (newPassword.length < 8) {
      return;
    }
    if (newPassword !== confirmPassword) {
      return;
    }
    setLoading(true);
    const result = await resetPassword(token.trim(), newPassword);
    setLoading(false);
    if (result.success) {
      navigate('/login', { replace: true });
    }
  };

  if (!token.trim()) {
    return (
      <div className="min-h-screen flex items-center justify-center p-4 bg-gray-50 dark:bg-gray-900">
        <div className="card max-w-md w-full text-center">
          <Lock className="w-12 h-12 mx-auto text-gray-400 dark:text-gray-500 mb-4" />
          <h1 className="text-xl font-semibold text-gray-900 dark:text-white mb-2">
            {t('auth.resetPassword')}
          </h1>
          <p className="text-gray-600 dark:text-gray-400 mb-6">
            {t('auth.resetPasswordNoToken')}
          </p>
          <Link
            to="/login"
            className="inline-flex items-center gap-2 text-primary-600 dark:text-primary-400 hover:underline font-medium"
          >
            {t('auth.backToLogin')}
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen flex items-center justify-center p-4 bg-gray-50 dark:bg-gray-900">
      <div className="card max-w-md w-full">
        <h1 className="text-xl font-semibold text-gray-900 dark:text-white mb-2 flex items-center gap-2">
          <Lock className="w-5 h-5" />
          {t('auth.resetPassword')}
        </h1>
        <p className="text-sm text-gray-600 dark:text-gray-400 mb-6">
          {t('auth.resetPasswordInstructions')}
        </p>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="label">
              <Lock className="inline w-4 h-4 mr-2" />
              {t('auth.newPassword')}
            </label>
            <input
              type="password"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              className="input"
              placeholder={t('auth.newPasswordPlaceholder')}
              required
              minLength={8}
            />
          </div>
          <div>
            <label className="label">{t('auth.confirmPassword')}</label>
            <input
              type="password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              className="input"
              placeholder={t('auth.confirmPasswordPlaceholder')}
              required
              minLength={8}
            />
            {confirmPassword && newPassword !== confirmPassword && (
              <p className="text-sm text-red-600 dark:text-red-400 mt-1">
                {t('auth.passwordsDontMatch')}
              </p>
            )}
          </div>
          <div className="flex flex-col sm:flex-row gap-2 pt-2">
            <Link
              to="/login"
              className="btn-secondary flex-1 justify-center text-center"
            >
              {t('common.cancel')}
            </Link>
            <button
              type="submit"
              disabled={loading || newPassword.length < 8 || newPassword !== confirmPassword}
              className="btn-primary flex-1 justify-center disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {loading ? t('common.loading') : t('auth.resetPasswordSubmit')}
            </button>
          </div>
        </form>
        <p className="mt-4 text-center">
          <Link
            to="/login"
            className="text-sm text-primary-600 dark:text-primary-400 hover:underline flex items-center justify-center gap-1"
          >
            <Mail className="w-4 h-4" />
            {t('auth.backToLogin')}
          </Link>
        </p>
      </div>
    </div>
  );
}
