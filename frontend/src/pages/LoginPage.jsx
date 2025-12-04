import React, { useState, useRef } from 'react';
import { useAuth } from '../contexts/AuthContext';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Wallet, Mail, Lock, User, Moon, Sun, Calculator, TrendingUp, Globe } from 'lucide-react';
import { Link } from 'react-router-dom';
import { useTheme } from '../contexts/ThemeContext';
import { useLanguage } from '../contexts/LanguageContext';
import ReCaptcha from '../components/common/ReCaptcha';

// Site key do reCAPTCHA
// Configure via variável de ambiente VITE_RECAPTCHA_SITE_KEY ou substitua abaixo
// Para obter chaves: https://www.google.com/recaptcha/admin/create
// Chave de teste (sempre passa): 6LeIxAcTAAAAAJcZVRqyHh71UMIEGNQ_MXjiZKhI
const RECAPTCHA_SITE_KEY = import.meta.env.VITE_RECAPTCHA_SITE_KEY || '6LeIxAcTAAAAAJcZVRqyHh71UMIEGNQ_MXjiZKhI';

export default function LoginPage() {
  const [activeTab, setActiveTab] = useState('login');
  const [loading, setLoading] = useState(false);
  const { login, register } = useAuth();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { theme, toggleTheme } = useTheme();
  const { language, changeLanguage, t } = useLanguage();
  const redirectTo = searchParams.get('redirect') || '/dashboard';
  const [isLanguageMenuOpen, setIsLanguageMenuOpen] = useState(false);

  // Login form state
  const [loginEmail, setLoginEmail] = useState('');
  const [loginPassword, setLoginPassword] = useState('');
  const [requiresCaptcha, setRequiresCaptcha] = useState(false);
  const [captchaToken, setCaptchaToken] = useState(null);
  const captchaRef = useRef(null);

  // Register form state
  const [registerName, setRegisterName] = useState('');
  const [registerEmail, setRegisterEmail] = useState('');
  const [registerPassword, setRegisterPassword] = useState('');

  const handleLogin = async (e) => {
    e.preventDefault();
    setLoading(true);
    
    // Se CAPTCHA é necessário mas não foi fornecido, não envia
    if (requiresCaptcha && !captchaToken) {
      setLoading(false);
      return;
    }
    
    const result = await login(loginEmail, loginPassword, captchaToken);
    setLoading(false);
    
    if (result.success) {
      // Limpa estado do CAPTCHA em caso de sucesso
      setRequiresCaptcha(false);
      setCaptchaToken(null);
      navigate(redirectTo);
    } else if (result.requiresCaptcha) {
      // Se o servidor requer CAPTCHA, mostra o componente
      console.log('CAPTCHA requerido - ativando componente');
      setRequiresCaptcha(true);
      setCaptchaToken(null);
      // Reseta o CAPTCHA se já estava renderizado
      setTimeout(() => {
        if (captchaRef.current) {
          captchaRef.current.reset();
        }
      }, 100);
    }
  };

  const handleCaptchaVerify = (token) => {
    setCaptchaToken(token);
  };

  const handleCaptchaExpire = () => {
    setCaptchaToken(null);
  };

  const handleRegister = async (e) => {
    e.preventDefault();
    setLoading(true);
    const result = await register(registerName, registerEmail, registerPassword);
    setLoading(false);
    if (result.success) {
      // Navega para o redirect ou dashboard após cadastro bem-sucedido
      navigate(redirectTo);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-primary-50 via-white to-primary-50 dark:from-gray-900 dark:via-gray-800 dark:to-gray-900 p-4">
      <div className="absolute top-4 right-4 flex items-center gap-2">
        <div className="relative">
          <button
            onClick={() => setIsLanguageMenuOpen(!isLanguageMenuOpen)}
            className="p-2 rounded-lg bg-white dark:bg-gray-800 shadow-md hover:shadow-lg transition-all"
            aria-label={t('language.select')}
            title={t('language.select')}
          >
            <Globe className="w-5 h-5 text-gray-700 dark:text-gray-300" />
          </button>
          {isLanguageMenuOpen && (
            <>
              <div 
                className="fixed inset-0 z-10" 
                onClick={() => setIsLanguageMenuOpen(false)}
              />
              <div className="absolute right-0 mt-2 w-48 bg-white dark:bg-gray-800 rounded-lg shadow-lg border border-gray-200 dark:border-gray-700 z-20">
                <button
                  onClick={() => {
                    changeLanguage('pt-BR');
                    setIsLanguageMenuOpen(false);
                  }}
                  className={`w-full text-left px-4 py-2 text-sm hover:bg-gray-100 dark:hover:bg-gray-700 first:rounded-t-lg last:rounded-b-lg ${
                    language === 'pt-BR' ? 'bg-primary-50 dark:bg-primary-900/20 text-primary-600 dark:text-primary-400' : 'text-gray-700 dark:text-gray-300'
                  }`}
                >
                  🇧🇷 {t('language.portuguese')}
                </button>
                <button
                  onClick={() => {
                    changeLanguage('en-US');
                    setIsLanguageMenuOpen(false);
                  }}
                  className={`w-full text-left px-4 py-2 text-sm hover:bg-gray-100 dark:hover:bg-gray-700 first:rounded-t-lg last:rounded-b-lg ${
                    language === 'en-US' ? 'bg-primary-50 dark:bg-primary-900/20 text-primary-600 dark:text-primary-400' : 'text-gray-700 dark:text-gray-300'
                  }`}
                >
                  🇺🇸 {t('language.english')}
                </button>
              </div>
            </>
          )}
        </div>
        <button
          onClick={toggleTheme}
          className="p-2 rounded-lg bg-white dark:bg-gray-800 shadow-md hover:shadow-lg transition-all"
          aria-label={t('common.toggleTheme')}
          title={t('common.toggleTheme')}
        >
          {theme === 'dark' ? (
            <Sun className="w-5 h-5 text-yellow-500" />
          ) : (
            <Moon className="w-5 h-5 text-gray-700" />
          )}
        </button>
      </div>

      <div className="w-full max-w-md">
        {/* Logo */}
        <div className="text-center mb-8">
          <div className="inline-flex items-center justify-center w-16 h-16 bg-primary-600 rounded-2xl mb-4 shadow-lg">
            <Wallet className="w-8 h-8 text-white" />
          </div>
          <h1 className="text-3xl font-bold text-gray-900 dark:text-white mb-2">
            {t('app.name')}
          </h1>
          <p className="text-gray-600 dark:text-gray-400">
            {t('app.subtitle')}
          </p>
        </div>

        {/* Auth Card */}
        <div className="card">
          {/* Tabs */}
          <div className="flex gap-2 mb-6">
            <button
              onClick={() => setActiveTab('login')}
              className={`flex-1 py-2 px-4 rounded-lg font-medium transition-all ${
                activeTab === 'login'
                  ? 'bg-primary-600 text-white shadow-md'
                  : 'bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-300 hover:bg-gray-200 dark:hover:bg-gray-600'
              }`}
            >
              {t('auth.login')}
            </button>
            <button
              onClick={() => setActiveTab('register')}
              className={`flex-1 py-2 px-4 rounded-lg font-medium transition-all ${
                activeTab === 'register'
                  ? 'bg-primary-600 text-white shadow-md'
                  : 'bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-300 hover:bg-gray-200 dark:hover:bg-gray-600'
              }`}
            >
              {t('auth.register')}
            </button>
          </div>

          {/* Login Form */}
          {activeTab === 'login' && (
            <form onSubmit={handleLogin} className="space-y-4">
              <div>
                <label className="label">
                  <Mail className="inline w-4 h-4 mr-2" />
                  {t('auth.email')}
                </label>
                <input
                  type="email"
                  value={loginEmail}
                  onChange={(e) => setLoginEmail(e.target.value)}
                  className="input"
                  placeholder={t('auth.emailPlaceholder')}
                  required
                />
              </div>
              <div>
                <label className="label">
                  <Lock className="inline w-4 h-4 mr-2" />
                  {t('auth.password')}
                </label>
                <input
                  type="password"
                  value={loginPassword}
                  onChange={(e) => setLoginPassword(e.target.value)}
                  className="input"
                  placeholder={t('auth.passwordPlaceholder')}
                  required
                />
              </div>
              
              {/* CAPTCHA - aparece apenas quando necessário */}
              {requiresCaptcha && (
                <div className="py-2">
                  <div className="text-sm text-gray-600 dark:text-gray-400 mb-2 text-center">
                    {t('auth.captchaRequired')}
                  </div>
                  <ReCaptcha
                    key={`captcha-${requiresCaptcha}`} // Force re-render quando requiresCaptcha muda
                    ref={captchaRef}
                    siteKey={RECAPTCHA_SITE_KEY}
                    onVerify={handleCaptchaVerify}
                    onExpire={handleCaptchaExpire}
                    theme={theme}
                  />
                </div>
              )}
              
              <button
                type="submit"
                disabled={loading || (requiresCaptcha && !captchaToken)}
                className="btn-primary w-full justify-center disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {loading ? (
                  <>
                    <div className="animate-spin rounded-full h-4 w-4 border-2 border-white border-t-transparent"></div>
                    {t('auth.loggingIn')}
                  </>
                ) : (
                  t('auth.login')
                )}
              </button>
            </form>
          )}

          {/* Register Form */}
          {activeTab === 'register' && (
            <form onSubmit={handleRegister} className="space-y-4">
              <div>
                <label className="label">
                  <User className="inline w-4 h-4 mr-2" />
                  {t('auth.name')}
                </label>
                <input
                  type="text"
                  value={registerName}
                  onChange={(e) => setRegisterName(e.target.value)}
                  className="input"
                  placeholder={t('auth.namePlaceholder')}
                  required
                />
              </div>
              <div>
                <label className="label">
                  <Mail className="inline w-4 h-4 mr-2" />
                  {t('auth.email')}
                </label>
                <input
                  type="email"
                  value={registerEmail}
                  onChange={(e) => setRegisterEmail(e.target.value)}
                  className="input"
                  placeholder={t('auth.emailPlaceholder')}
                  required
                />
              </div>
              <div>
                <label className="label">
                  <Lock className="inline w-4 h-4 mr-2" />
                  {t('auth.password')}
                </label>
                <input
                  type="password"
                  value={registerPassword}
                  onChange={(e) => setRegisterPassword(e.target.value)}
                  className="input"
                  placeholder={t('auth.passwordPlaceholder')}
                  required
                />
              </div>
              <button
                type="submit"
                disabled={loading}
                className="btn-primary w-full justify-center"
              >
                {loading ? (
                  <>
                    <div className="animate-spin rounded-full h-4 w-4 border-2 border-white border-t-transparent"></div>
                    {t('auth.registering')}
                  </>
                ) : (
                  t('auth.register')
                )}
              </button>
            </form>
          )}

          {/* Divisor com link para ferramentas */}
          <div className="mt-6 pt-6 border-t border-gray-200 dark:border-gray-700">
            <div className="text-center">
              <p className="text-xs text-gray-500 dark:text-gray-400 mb-3">
                {t('auth.freeTools')}
              </p>
              <Link
                to="/calculadora-juros-compostos"
                className="inline-flex items-center gap-2 text-sm text-primary-600 dark:text-primary-400 hover:text-primary-700 dark:hover:text-primary-300 font-medium transition-colors"
              >
                <Calculator className="w-4 h-4" />
                {t('auth.compoundInterestCalculator')}
                <TrendingUp className="w-3 h-3" />
              </Link>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

