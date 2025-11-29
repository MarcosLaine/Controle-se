import React, { useState, useEffect } from 'react';
import { Plus, Edit, Trash2, Building2 } from 'lucide-react';
import { useAuth } from '../../contexts/AuthContext';
import api from '../../services/api';
import { formatCurrency } from '../../utils/formatters';
import Modal from '../common/Modal';
import toast from 'react-hot-toast';

export default function Accounts() {
  const { user } = useAuth();
  const [accounts, setAccounts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showModal, setShowModal] = useState(false);
  const [editingAccount, setEditingAccount] = useState(null);
  const [formData, setFormData] = useState({
    nome: '',
    tipo: 'Corrente',
    saldoInicial: '',
  });

  useEffect(() => {
    loadAccounts();
  }, [user]);

  const loadAccounts = async () => {
    if (!user) return;
    setLoading(true);
    try {
      const response = await api.get(`/accounts?userId=${user.id}`);
      if (response.success) {
        setAccounts(response.data || []);
      }
    } catch (error) {
      toast.error('Erro ao carregar contas');
    } finally {
      setLoading(false);
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!formData.nome.trim()) {
      toast.error('Nome da conta é obrigatório');
      return;
    }

    try {
      const data = {
        nome: formData.nome,
        tipo: formData.tipo,
        saldoInicial: parseFloat(formData.saldoInicial) || 0,
        userId: user.id,
      };

      if (editingAccount) {
        const response = await api.put(`/accounts/${editingAccount.idConta}`, data);
        if (response.success) {
          toast.success('Conta atualizada!');
          loadAccounts();
          handleCloseModal();
        }
      } else {
        const response = await api.post('/accounts', data);
        if (response.success) {
          toast.success('Conta criada!');
          loadAccounts();
          handleCloseModal();
        }
      }
    } catch (error) {
      toast.error(error.message || 'Erro ao salvar conta');
    }
  };

  const handleEdit = (account) => {
    setEditingAccount(account);
    setFormData({
      nome: account.nome,
      tipo: account.tipo,
      saldoInicial: account.saldoAtual?.toString() || '',
    });
    setShowModal(true);
  };

  const handleDelete = async (id) => {
    if (!confirm('Tem certeza que deseja excluir esta conta?')) return;

    try {
      const response = await api.delete(`/accounts/${id}?userId=${user.id}`);
      if (response.success) {
        toast.success('Conta excluída!');
        loadAccounts();
      }
    } catch (error) {
      toast.error(error.message || 'Erro ao excluir conta');
    }
  };

  const handleCloseModal = () => {
    setShowModal(false);
    setEditingAccount(null);
    setFormData({ nome: '', tipo: 'Corrente', saldoInicial: '' });
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="animate-spin rounded-full h-12 w-12 border-4 border-primary-600 border-t-transparent"></div>
      </div>
    );
  }

  return (
    <div className="space-y-6 animate-fade-in">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-3xl font-bold text-gray-900 dark:text-white">
            Contas
          </h2>
          <p className="text-gray-600 dark:text-gray-400 mt-1">
            Gerencie suas contas bancárias
          </p>
        </div>
        <button onClick={() => setShowModal(true)} className="btn-primary">
          <Plus className="w-4 h-4" />
          Nova Conta
        </button>
      </div>

      {accounts.length === 0 ? (
        <div className="card text-center py-12">
          <Building2 className="w-16 h-16 text-gray-400 mx-auto mb-4" />
          <p className="text-gray-600 dark:text-gray-400">
            Nenhuma conta cadastrada
          </p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {accounts.map((account) => (
            <div key={account.idConta} className="card">
              <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-2">
                {account.nome}
              </h3>
              <p className="text-sm text-gray-600 dark:text-gray-400 mb-1">
                {account.tipo}
              </p>
              <p className="text-2xl font-bold text-primary-600 dark:text-primary-400 mb-4">
                {formatCurrency(account.saldoAtual || 0)}
              </p>
              <div className="flex gap-2">
                <button
                  onClick={() => handleEdit(account)}
                  className="btn-secondary flex-1"
                >
                  <Edit className="w-4 h-4" />
                  Editar
                </button>
                <button
                  onClick={() => handleDelete(account.idConta)}
                  className="btn-danger flex-1"
                >
                  <Trash2 className="w-4 h-4" />
                  Excluir
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      <Modal
        isOpen={showModal}
        onClose={handleCloseModal}
        title={editingAccount ? 'Editar Conta' : 'Nova Conta'}
      >
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="label">Nome da Conta</label>
            <input
              type="text"
              value={formData.nome}
              onChange={(e) => setFormData({ ...formData, nome: e.target.value })}
              className="input"
              placeholder="Ex: Conta Corrente, Poupança..."
              required
            />
          </div>
          <div>
            <label className="label">Tipo</label>
            <select
              value={formData.tipo}
              onChange={(e) => setFormData({ ...formData, tipo: e.target.value })}
              className="input"
            >
              <option value="Corrente">Corrente</option>
              <option value="Poupança">Poupança</option>
              <option value="Investimento">Investimento</option>
              <option value="Dinheiro">Dinheiro</option>
              <option value="Cartão de Crédito">Cartão de Crédito</option>
            </select>
          </div>
          <div>
            <label className="label">Saldo Inicial</label>
            <input
              type="number"
              step="0.01"
              value={formData.saldoInicial}
              onChange={(e) => setFormData({ ...formData, saldoInicial: e.target.value })}
              className="input"
              placeholder="0.00"
            />
          </div>
          <div className="flex gap-2 justify-end">
            <button
              type="button"
              onClick={handleCloseModal}
              className="btn-secondary"
            >
              Cancelar
            </button>
            <button type="submit" className="btn-primary">
              {editingAccount ? 'Atualizar' : 'Criar'}
            </button>
          </div>
        </form>
      </Modal>
    </div>
  );
}

