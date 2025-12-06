import React, { useState } from 'react';
import { Upload, Download, FileText, X } from 'lucide-react';
import { useLanguage } from '../../contexts/LanguageContext';
import { useAuth } from '../../contexts/AuthContext';
import api from '../../services/api';
import Modal from '../common/Modal';
import toast from 'react-hot-toast';
import Spinner from '../common/Spinner';
import TransactionImportPreview from './TransactionImportPreview';

export default function ImportTransactionsModal({ isOpen, onClose, onSuccess }) {
  const { t } = useLanguage();
  const { user } = useAuth();
  const [csvContent, setCsvContent] = useState('');
  const [selectedFile, setSelectedFile] = useState(null);
  const [loading, setLoading] = useState(false);
  const [previewData, setPreviewData] = useState(null);
  const [step, setStep] = useState('upload'); // 'upload' ou 'preview'

  const handleDownloadTemplate = async () => {
    try {
      // Usa fetch direto para evitar problemas com autenticação no template
      const API_BASE_URL = window.location.hostname === 'localhost' 
        ? 'http://localhost:8080/api' 
        : `${window.location.origin}/api`;
      
      const user = JSON.parse(localStorage.getItem('controle-se-user') || 'null');
      const headers = {
        'Content-Type': 'application/json',
      };
      if (user && user.token) {
        headers['Authorization'] = `Bearer ${user.token}`;
      }
      
      const response = await fetch(`${API_BASE_URL}/transactions/import/template`, {
        method: 'GET',
        headers: headers,
      });
      
      if (!response.ok) {
        throw new Error('Erro ao baixar template');
      }
      
      const blob = await response.blob();
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', 'transactions_import_template.csv');
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
      
      toast.success(t('import.downloadSuccess') || 'Template baixado com sucesso!');
    } catch (error) {
      toast.error(error.message || t('import.downloadError') || 'Erro ao baixar template');
    }
  };

  const handleFileSelect = (e) => {
    const file = e.target.files[0];
    if (!file) {
      setSelectedFile(null);
      setCsvContent('');
      return;
    }

    if (!file.name.endsWith('.csv')) {
      toast.error(t('import.invalidFile') || 'Por favor, selecione um arquivo CSV');
      setSelectedFile(null);
      setCsvContent('');
      return;
    }

    setSelectedFile(file);
    const reader = new FileReader();
    reader.onload = (event) => {
      const content = event.target.result;
      setCsvContent(content);
      // Toast será mostrado apenas se o conteúdo for válido
      if (content && content.trim().length > 0) {
        const message = t('import.fileLoaded') || 'Arquivo carregado com sucesso!';
        toast.success(message.replace('{{name}}', file.name));
      }
    };
    reader.onerror = () => {
      toast.error(t('import.readError') || 'Erro ao ler arquivo');
      setSelectedFile(null);
      setCsvContent('');
    };
    reader.readAsText(file, 'UTF-8');
  };

  const handleProcessCSV = async () => {
    if (!csvContent.trim()) {
      toast.error(t('import.emptyFile') || 'Por favor, selecione um arquivo CSV');
      return;
    }

    setLoading(true);
    try {
      const response = await api.post('/transactions/import', {
        csvContent: csvContent,
      });

      if (response.success) {
        setPreviewData({
          transactions: response.transactions || [],
          errors: response.errors || [],
        });
        setStep('preview');
      } else {
        toast.error(response.message || t('import.processError') || 'Erro ao processar CSV');
        if (response.errors && response.errors.length > 0) {
          setPreviewData({
            transactions: response.transactions || [],
            errors: response.errors || [],
          });
          setStep('preview');
        }
      }
    } catch (error) {
      toast.error(error.message || t('import.processError') || 'Erro ao processar CSV');
    } finally {
      setLoading(false);
    }
  };

  const handleConfirm = async (transactions) => {
    setLoading(true);
    try {
      const response = await api.post('/transactions/import/confirm', {
        transactions: transactions,
      });

      if (response.success) {
        toast.success(
          response.message || 
          t('import.success', { count: response.successCount || transactions.length }) ||
          `Importação concluída: ${response.successCount || transactions.length} transação(ões) importada(s)`
        );
        if (onSuccess) {
          onSuccess();
        }
        handleClose();
      } else {
        toast.error(response.message || t('import.confirmError') || 'Erro ao confirmar importação');
        if (response.errors && response.errors.length > 0) {
          toast.error(response.errors.join(', '));
        }
      }
    } catch (error) {
      toast.error(error.message || t('import.confirmError') || 'Erro ao confirmar importação');
    } finally {
      setLoading(false);
    }
  };

  const handleClose = () => {
    setCsvContent('');
    setSelectedFile(null);
    setPreviewData(null);
    setStep('upload');
    // Limpa o input file
    const fileInput = document.getElementById('csv-file-input');
    if (fileInput) {
      fileInput.value = '';
    }
    onClose();
  };

  if (!isOpen) return null;

  if (step === 'preview' && previewData) {
    return (
      <TransactionImportPreview
        isOpen={isOpen}
        onClose={handleClose}
        transactions={previewData.transactions}
        errors={previewData.errors}
        onConfirm={handleConfirm}
        onBack={() => setStep('upload')}
        loading={loading}
      />
    );
  }

  return (
    <Modal
      isOpen={isOpen}
      onClose={handleClose}
      title={t('import.title') || 'Importar Transações'}
      size="lg"
    >
      <div className="space-y-6">
        {/* Download Template */}
        <div className="bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-lg p-4">
          <div className="flex items-start gap-3">
            <FileText className="w-5 h-5 text-blue-600 dark:text-blue-400 mt-0.5" />
            <div className="flex-1">
              <h4 className="font-semibold text-blue-900 dark:text-blue-100 mb-1">
                {t('import.templateTitle') || 'Baixar Modelo CSV'}
              </h4>
              <p className="text-sm text-blue-800 dark:text-blue-200 mb-3">
                {t('import.templateDescription') || 'Baixe o modelo CSV com exemplos de todos os tipos de transações para preencher corretamente.'}
              </p>
              <button
                onClick={handleDownloadTemplate}
                className="btn-secondary flex items-center gap-2"
              >
                <Download className="w-4 h-4" />
                {t('import.downloadTemplate') || 'Baixar Modelo CSV'}
              </button>
            </div>
          </div>
        </div>

        {/* Upload File */}
        <div>
          <label className="label mb-2">
            {t('import.selectFile') || 'Selecionar Arquivo CSV'}
          </label>
          <div className={`border-2 border-dashed rounded-lg p-6 text-center transition-colors ${
            selectedFile 
              ? 'border-green-500 dark:border-green-400 bg-green-50 dark:bg-green-900/20' 
              : 'border-gray-300 dark:border-gray-600 hover:border-primary-500 dark:hover:border-primary-400'
          }`}>
            <input
              type="file"
              accept=".csv"
              onChange={handleFileSelect}
              className="hidden"
              id="csv-file-input"
            />
            <label
              htmlFor="csv-file-input"
              className="cursor-pointer flex flex-col items-center gap-2"
            >
              <Upload className={`w-8 h-8 ${selectedFile ? 'text-green-600 dark:text-green-400' : 'text-gray-400'}`} />
              {selectedFile ? (
                <div className="flex flex-col items-center gap-1">
                  <span className="text-sm font-semibold text-green-700 dark:text-green-300">
                    {t('import.fileSelected') || 'Arquivo selecionado'}
                  </span>
                  <span className="text-xs text-green-600 dark:text-green-400 font-medium">
                    {selectedFile.name}
                  </span>
                  <span className="text-xs text-green-600 dark:text-green-400">
                    {(selectedFile.size / 1024).toFixed(2)} KB
                  </span>
                </div>
              ) : (
                <span className="text-sm text-gray-600 dark:text-gray-400">
                  {t('import.clickToSelect') || 'Clique para selecionar um arquivo CSV'}
                </span>
              )}
            </label>
          </div>
          {selectedFile && csvContent && (
            <p className="text-xs text-green-600 dark:text-green-400 mt-2 font-medium">
              ✓ {t('import.fileReady') || 'Arquivo pronto para processar'}
            </p>
          )}
        </div>

        {/* Instructions */}
        <div className="bg-gray-50 dark:bg-gray-900/50 rounded-lg p-4">
          <h4 className="font-semibold text-gray-900 dark:text-white mb-2">
            {t('import.instructionsTitle') || 'Instruções:'}
          </h4>
          <ul className="text-sm text-gray-600 dark:text-gray-400 space-y-1 list-disc list-inside">
            <li>{t('import.instruction1') || 'Baixe o modelo CSV para ver o formato correto'}</li>
            <li>{t('import.instruction2') || 'Preencha todas as colunas obrigatórias: tipo, descricao, valor, data, conta'}</li>
            <li>{t('import.instruction3') || 'Categorias, tags e contas serão criadas automaticamente se não existirem'}</li>
            <li>{t('import.instruction4') || 'Para compras parceladas, informe numero_parcelas e intervalo_dias'}</li>
            <li>{t('import.instruction5') || 'Para transações recorrentes, use frequencia: SEMANAL, MENSAL ou ANUAL'}</li>
          </ul>
        </div>

        {/* Actions */}
        <div className="flex gap-2 justify-end">
          <button
            onClick={handleClose}
            className="btn-secondary"
            disabled={loading}
          >
            {t('common.cancel') || 'Cancelar'}
          </button>
          <button
            onClick={handleProcessCSV}
            className="btn-primary flex items-center gap-2"
            disabled={loading || !csvContent}
          >
            {loading ? (
              <>
                <Spinner size={16} className="text-white" />
                {t('import.processing') || 'Processando...'}
              </>
            ) : (
              <>
                <Upload className="w-4 h-4" />
                {t('import.process') || 'Processar CSV'}
              </>
            )}
          </button>
        </div>
      </div>
    </Modal>
  );
}

