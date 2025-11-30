import { Info } from 'lucide-react';
import React, { useEffect, useState } from 'react';
import toast from 'react-hot-toast';
import { useAuth } from '../../contexts/AuthContext';
import api from '../../services/api';
import Modal from '../common/Modal';

export default function InvestmentModal({ isOpen, onClose, onSuccess, investmentToEdit }) {
  const { user } = useAuth();
  const [mode, setMode] = useState('VARIABLE'); // VARIABLE or FIXED
  const [accounts, setAccounts] = useState([]);
  const [loading, setLoading] = useState(false);
  const [showPriceInfo, setShowPriceInfo] = useState(false);
  
  // Variable Income State
  const [variableForm, setVariableForm] = useState({
    operationType: 'BUY', // BUY or SELL
    name: '',
    category: '',
    quantity: '',
    date: new Date().toISOString().split('T')[0],
    brokerage: '0',
    accountId: '',
    currency: 'BRL',
    price: ''
  });

  // Fixed Income State
  const [fixedForm, setFixedForm] = useState({
    name: '',
    investmentType: '', // CDB, LCI, etc
    issuer: '',
    profitabilityType: '', // PRE, POS, POS_FIXED
    index: '', // CDI, IPCA, SELIC
    indexPercent: '100',
    fixedRate: '',
    preRate: '',
    amount: '',
    date: new Date().toISOString().split('T')[0],
    maturityDate: '',
    accountId: ''
  });

  useEffect(() => {
    if (isOpen && user) {
      loadAccounts();
      if (investmentToEdit) {
        populateForm(investmentToEdit);
      } else {
        resetForms();
      }
    }
  }, [isOpen, user, investmentToEdit]);

  useEffect(() => {
    if (!isOpen) {
      setShowPriceInfo(false);
    }
  }, [isOpen]);

  useEffect(() => {
    if (mode !== 'VARIABLE' && showPriceInfo) {
      setShowPriceInfo(false);
    }
  }, [mode, showPriceInfo]);

  const populateForm = (inv) => {
    if (inv.categoria === 'RENDA_FIXA') {
      setMode('FIXED');
      // ... existing fixed logic ...
      setFixedForm({
        name: inv.nome.split(' - ')[0], // Remove issuer if present
        issuer: inv.nome.includes(' - ') ? inv.nome.split(' - ')[1] : '',
        investmentType: inv.tipoInvestimento || '',
        profitabilityType: inv.tipoRentabilidade || '',
        index: inv.indice || '',
        indexPercent: inv.percentualIndice ? inv.percentualIndice.toString() : '100',
        fixedRate: inv.taxaFixa ? inv.taxaFixa.toString() : '',
        preRate: inv.taxaFixa ? inv.taxaFixa.toString() : '', // Might overlap, need logic
        amount: inv.valorAporte ? inv.valorAporte.toString() : '',
        date: inv.dataAporte ? inv.dataAporte.split('T')[0] : '',
        maturityDate: inv.dataVencimento ? inv.dataVencimento.split('T')[0] : '',
        accountId: '' // Account ID usually not returned in list, might need to fetch or guess
      });
      // Logic to determine preRate vs fixedRate based on type
      if (inv.tipoRentabilidade === 'PRE_FIXADO') {
         setFixedForm(prev => ({ ...prev, preRate: inv.taxaFixa ? inv.taxaFixa.toString() : '' }));
      } else if (inv.tipoRentabilidade === 'POS_FIXADO_TAXA') {
         setFixedForm(prev => ({ ...prev, fixedRate: inv.taxaFixa ? inv.taxaFixa.toString() : '' }));
      }
    } else {
      setMode('VARIABLE');
      setVariableForm({
        operationType: (inv.quantidade && inv.quantidade < 0) ? 'SELL' : 'BUY',
        name: inv.nome,
        category: inv.categoria,
        quantity: inv.quantidade ? Math.abs(inv.quantidade).toString() : '',
        date: inv.dataAporte ? inv.dataAporte.split('T')[0] : '',
        brokerage: inv.corretagem ? inv.corretagem.toString() : '0',
        accountId: '', // Need to find a way to get account ID
        currency: inv.moeda || 'BRL',
        price: inv.precoAporte ? inv.precoAporte.toString() : ''
      });
    }
  };

  const loadAccounts = async () => {
    try {
      const response = await api.get(`/accounts?userId=${user.id}`);
      if (response.success) {
        // Filter only investment accounts
        const investmentAccounts = response.data.filter(
          acc => acc.tipo && acc.tipo.toLowerCase() === 'investimento'
        );
        setAccounts(investmentAccounts);
        
        if (investmentAccounts.length === 0) {
          toast.error('Você precisa criar uma conta do tipo "Investimento" primeiro');
          onClose();
        }
      }
    } catch (error) {
      console.error('Erro ao carregar contas:', error);
      toast.error('Erro ao carregar contas');
    }
  };

  const handleVariableSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    try {
      let quantity = parseFloat(variableForm.quantity);
      
      // If selling, quantity should be negative
      if (variableForm.operationType === 'SELL') {
        quantity = -Math.abs(quantity);
      } else {
        quantity = Math.abs(quantity);
      }

      const manualPriceInput = (variableForm.price || '').toString().trim();
      let manualPrice = null;
      if (manualPriceInput) {
        manualPrice = parseFloat(manualPriceInput.replace(',', '.'));
        if (isNaN(manualPrice) || manualPrice <= 0) {
          toast.error('Informe um preço válido maior que zero');
          setLoading(false);
          return;
        }
      }

      const data = {
        ...variableForm,
        quantity: quantity,
        brokerage: parseFloat(variableForm.brokerage),
        accountId: parseInt(variableForm.accountId),
        userId: user.id
      };
      
      // Backend expects "nome" not "symbol"
      const payload = {
        ...(investmentToEdit ? { id: investmentToEdit.idInvestimento } : {}),
        nome: data.name,
        categoria: data.category,
        quantidade: data.quantity,
        dataAporte: data.date,
        corretagem: data.brokerage,
        accountId: data.accountId,
        moeda: data.currency,
        userId: data.userId
      };

      if (manualPrice !== null) {
        payload.precoAporte = manualPrice;
      }

      const method = investmentToEdit ? 'PUT' : 'POST';
      const response = await api[method.toLowerCase()]('/investments', payload);
      
      if (response.success) {
        toast.success(`Investimento ${investmentToEdit ? 'atualizado' : 'criado'} com sucesso!`);
        onSuccess();
        onClose();
        resetForms();
      }
    } catch (error) {
      console.error(error);
      toast.error('Erro ao criar investimento');
    } finally {
      setLoading(false);
    }
  };

  const handleFixedSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    try {
      const data = {
        ...fixedForm,
        amount: parseFloat(fixedForm.amount),
        indexPercent: fixedForm.indexPercent ? parseFloat(fixedForm.indexPercent) : null,
        fixedRate: fixedForm.fixedRate ? parseFloat(fixedForm.fixedRate) : null,
        preRate: fixedForm.preRate ? parseFloat(fixedForm.preRate) : null,
        accountId: parseInt(fixedForm.accountId),
        userId: user.id
      };

      // Construct payload for backend
      // Backend expects: nome, categoria=RENDA_FIXA, valorAporte, etc.
      
      // Determine taxaFixa based on profitability type
      let taxaFixaToSend = null;
      if (data.profitabilityType === 'POS_FIXADO_TAXA') {
        taxaFixaToSend = data.fixedRate;
      } else if (data.profitabilityType === 'PRE_FIXADO') {
        taxaFixaToSend = data.preRate; // Using same field for simplicity if backend supports or map correctly
        // Backend uses taxaFixa for fixed rate part. For PRE_FIXADO, usually the rate is the taxaFixa itself.
        // Let's assume taxaFixa is used for both "Taxa Fixa Adicional" and "Taxa Pré-fixada"
        taxaFixaToSend = data.preRate;
      }

      // Determine index and percent
      let indiceToSend = null;
      let percentualIndiceToSend = null;
      if (data.profitabilityType !== 'PRE_FIXADO') {
        indiceToSend = data.index;
        percentualIndiceToSend = data.indexPercent;
      }

      const payload = {
        ...(investmentToEdit ? { id: investmentToEdit.idInvestimento } : {}),
        nome: data.name + (data.issuer ? ` - ${data.issuer}` : ''), // Combine name and issuer
        categoria: 'RENDA_FIXA',
        tipoInvestimento: data.investmentType,
        tipoRentabilidade: data.profitabilityType,
        indice: indiceToSend,
        percentualIndice: percentualIndiceToSend,
        taxaFixa: taxaFixaToSend,
        valorAporte: data.amount,
        dataAporte: data.date,
        dataVencimento: data.maturityDate,
        accountId: data.accountId,
        userId: data.userId,
        quantidade: 1 // Fixed income usually treated as 1 unit of total value
      };

      const method = investmentToEdit ? 'PUT' : 'POST';
      const response = await api[method.toLowerCase()]('/investments', payload);
      
      if (response.success) {
        toast.success(`Investimento de Renda Fixa ${investmentToEdit ? 'atualizado' : 'criado'}!`);
        onSuccess();
        onClose();
        resetForms();
      }
    } catch (error) {
      console.error(error);
      toast.error('Erro ao criar investimento');
    } finally {
      setLoading(false);
    }
  };

  const resetForms = () => {
    setVariableForm({
      operationType: 'BUY',
      name: '',
      category: '',
      quantity: '',
      date: new Date().toISOString().split('T')[0],
      brokerage: '0',
      accountId: '',
      currency: 'BRL',
      price: ''
    });
    setFixedForm({
      name: '',
      investmentType: '',
      issuer: '',
      profitabilityType: '',
      index: '',
      indexPercent: '100',
      fixedRate: '',
      preRate: '',
      amount: '',
      date: new Date().toISOString().split('T')[0],
      maturityDate: '',
      accountId: ''
    });
    setMode('VARIABLE');
  };

  const handleCategoryChange = (e) => {
    const value = e.target.value;
    setVariableForm({ ...variableForm, category: value });
    if (value === 'RENDA_FIXA') {
      setMode('FIXED');
    }
  };

  if (!isOpen) return null;

  return (
    <Modal isOpen={isOpen} onClose={onClose} title={investmentToEdit ? "Editar Investimento" : (mode === 'VARIABLE' ? "Novo Investimento" : "Novo Investimento - Renda Fixa")}>
      {mode === 'VARIABLE' ? (
        <form onSubmit={handleVariableSubmit} className="space-y-4">
          
          <div className="flex bg-gray-100 dark:bg-gray-700 p-1 rounded-lg mb-4">
            <button
              type="button"
              onClick={() => setVariableForm({ ...variableForm, operationType: 'BUY' })}
              className={`flex-1 py-2 text-sm font-medium rounded-md transition-colors ${
                variableForm.operationType === 'BUY'
                  ? 'bg-white dark:bg-gray-600 text-green-600 shadow-sm'
                  : 'text-gray-500 hover:text-gray-700 dark:text-gray-400'
              }`}
            >
              Compra
            </button>
            <button
              type="button"
              onClick={() => setVariableForm({ ...variableForm, operationType: 'SELL' })}
              className={`flex-1 py-2 text-sm font-medium rounded-md transition-colors ${
                variableForm.operationType === 'SELL'
                  ? 'bg-white dark:bg-gray-600 text-red-600 shadow-sm'
                  : 'text-gray-500 hover:text-gray-700 dark:text-gray-400'
              }`}
            >
              Venda
            </button>
          </div>

          <div>
            <label className="label">Nome/Símbolo</label>
            <input
              type="text"
              className="input"
              value={variableForm.name}
              onChange={e => setVariableForm({...variableForm, name: e.target.value})}
              placeholder="Ex: ITUB4, AAPL, BTC"
              required
            />
            <p className="text-xs text-gray-500 mt-1">
              O preço será obtido automaticamente com base no fechamento do dia, mas você pode informá-lo manualmente abaixo se preferir.
            </p>
          </div>

          <div className="relative">
            <div className="flex items-center gap-2">
              <label className="label">Preço do Ativo</label>
              <span className="text-xs text-gray-500 dark:text-gray-400 flex items-center gap-1">
                (Opcional)
                <button
                  type="button"
                  onClick={() => setShowPriceInfo(prev => !prev)}
                  className="p-1 rounded-full text-primary-600 hover:bg-primary-50 dark:hover:bg-gray-700 transition-colors"
                  aria-label="Saiba como o preço do ativo é definido"
                >
                  <Info size={14} />
                </button>
              </span>
            </div>
            <input
              type="number"
              className="input"
              step="0.00000001"
              min="0"
              value={variableForm.price}
              onChange={e => setVariableForm({...variableForm, price: e.target.value})}
              placeholder="Insira o preço pago por unidade"
            />
            {showPriceInfo && (
              <div className="absolute top-full right-0 mt-2 w-64 bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg shadow-lg p-3 text-xs text-gray-600 dark:text-gray-300 z-20">
                <p>
                  Caso você não informe o preço, ele será definido automaticamente pela base de dados com a cotação do dia do aporte.
                </p>
                <button
                  type="button"
                  onClick={() => setShowPriceInfo(false)}
                  className="mt-2 text-primary-600 hover:underline text-xs font-medium"
                >
                  Entendi
                </button>
              </div>
            )}
          </div>

          <div>
            <label className="label">Categoria</label>
            <select
              className="input"
              value={variableForm.category}
              onChange={handleCategoryChange}
              required
            >
              <option value="">Selecione...</option>
              <option value="ACAO">Ação (B3)</option>
              <option value="STOCK">Stock (Ações Internacionais)</option>
              <option value="CRYPTO">Criptomoeda</option>
              <option value="FII">FII</option>
              <option value="RENDA_FIXA">Renda Fixa</option>
            </select>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="label">Quantidade</label>
              <input
                type="number"
                className="input"
                step="0.00000001"
                min="0"
                value={variableForm.quantity}
                onChange={e => setVariableForm({...variableForm, quantity: e.target.value})}
                required
              />
            </div>
            <div>
              <label className="label">Corretagem</label>
              <input
                type="number"
                className="input"
                step="0.01"
                min="0"
                value={variableForm.brokerage}
                onChange={e => setVariableForm({...variableForm, brokerage: e.target.value})}
              />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="label">Data do Aporte</label>
              <input
                type="date"
                className="input"
                value={variableForm.date}
                onChange={e => setVariableForm({...variableForm, date: e.target.value})}
                required
              />
            </div>
            <div>
              <label className="label">Moeda</label>
              <select
                className="input"
                value={variableForm.currency}
                onChange={e => setVariableForm({...variableForm, currency: e.target.value})}
              >
                <option value="BRL">BRL (Real)</option>
                <option value="USD">USD (Dólar)</option>
                <option value="EUR">EUR (Euro)</option>
              </select>
            </div>
          </div>

          <div>
            <label className="label">Conta de Investimento</label>
            <select
              className="input"
              value={variableForm.accountId}
              onChange={e => setVariableForm({...variableForm, accountId: e.target.value})}
              required
            >
              <option value="">Selecione...</option>
              {accounts.map(acc => (
                <option key={acc.idConta} value={acc.idConta}>
                  {acc.nome} ({acc.tipo})
                </option>
              ))}
            </select>
          </div>

          <div className="flex justify-end gap-2 mt-6">
            <button type="button" onClick={onClose} className="btn-secondary">Cancelar</button>
            <button type="submit" className="btn-primary" disabled={loading}>
              {loading ? 'Salvando...' : 'Salvar'}
            </button>
          </div>
        </form>
      ) : (
        <form onSubmit={handleFixedSubmit} className="space-y-4">
           <div className="flex justify-between items-center mb-2">
              <button 
                type="button" 
                onClick={() => setMode('VARIABLE')}
                className="text-sm text-primary-600 hover:underline flex items-center gap-1"
              >
                <Info size={14} /> Voltar para Renda Variável
              </button>
           </div>

           <div>
            <label className="label">Nome do Ativo</label>
            <input
              type="text"
              className="input"
              value={fixedForm.name}
              onChange={e => setFixedForm({...fixedForm, name: e.target.value})}
              placeholder="Ex: CDB Banco XYZ"
              required
            />
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
               <label className="label">Tipo de Investimento</label>
               <select
                 className="input"
                 value={fixedForm.investmentType}
                 onChange={e => setFixedForm({...fixedForm, investmentType: e.target.value})}
                 required
               >
                 <option value="">Selecione...</option>
                 <option value="CDB">CDB</option>
                 <option value="LCI">LCI</option>
                 <option value="LCA">LCA</option>
                 <option value="TESOURO">Tesouro Direto</option>
                 <option value="DEBENTURE">Debênture</option>
                 <option value="OUTROS">Outros</option>
               </select>
            </div>
            <div>
               <label className="label">Emissor (Opcional)</label>
               <input
                 type="text"
                 className="input"
                 value={fixedForm.issuer}
                 onChange={e => setFixedForm({...fixedForm, issuer: e.target.value})}
                 placeholder="Banco XYZ"
               />
            </div>
          </div>

          <div>
             <label className="label">Tipo de Rentabilidade</label>
             <select
               className="input"
               value={fixedForm.profitabilityType}
               onChange={e => setFixedForm({...fixedForm, profitabilityType: e.target.value})}
               required
             >
               <option value="">Selecione...</option>
               <option value="PRE_FIXADO">Pré-fixado</option>
               <option value="POS_FIXADO">Pós-fixado</option>
               <option value="POS_FIXADO_TAXA">Pós-fixado + Taxa Fixa</option>
             </select>
          </div>

          {/* Conditional Fields */}
          {(fixedForm.profitabilityType === 'POS_FIXADO' || fixedForm.profitabilityType === 'POS_FIXADO_TAXA') && (
            <div className="grid grid-cols-2 gap-4">
              <div>
                 <label className="label">Índice</label>
                 <select
                   className="input"
                   value={fixedForm.index}
                   onChange={e => setFixedForm({...fixedForm, index: e.target.value})}
                   required
                 >
                   <option value="">Selecione...</option>
                   <option value="CDI">CDI</option>
                   <option value="SELIC">SELIC</option>
                   <option value="IPCA">IPCA</option>
                 </select>
              </div>
              <div>
                 <label className="label">% do Índice</label>
                 <input
                   type="number"
                   className="input"
                   value={fixedForm.indexPercent}
                   onChange={e => setFixedForm({...fixedForm, indexPercent: e.target.value})}
                   placeholder="100"
                   required
                 />
              </div>
            </div>
          )}

          {fixedForm.profitabilityType === 'POS_FIXADO_TAXA' && (
             <div>
               <label className="label">Taxa Fixa Adicional (% a.a.)</label>
               <input
                 type="number"
                 className="input"
                 value={fixedForm.fixedRate}
                 onChange={e => setFixedForm({...fixedForm, fixedRate: e.target.value})}
                 placeholder="Ex: 1.5"
                 step="0.01"
                 required
               />
             </div>
          )}

          {fixedForm.profitabilityType === 'PRE_FIXADO' && (
             <div>
               <label className="label">Taxa Pré-fixada (% a.a.)</label>
               <input
                 type="number"
                 className="input"
                 value={fixedForm.preRate}
                 onChange={e => setFixedForm({...fixedForm, preRate: e.target.value})}
                 placeholder="Ex: 12.5"
                 step="0.01"
                 required
               />
             </div>
          )}

          <div>
            <label className="label">Valor Aportado (R$)</label>
            <input
              type="number"
              className="input"
              value={fixedForm.amount}
              onChange={e => setFixedForm({...fixedForm, amount: e.target.value})}
              min="0.01"
              step="0.01"
              required
            />
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="label">Data do Aporte</label>
              <input
                type="date"
                className="input"
                value={fixedForm.date}
                onChange={e => setFixedForm({...fixedForm, date: e.target.value})}
                required
              />
            </div>
            <div>
              <label className="label">Vencimento</label>
              <input
                type="date"
                className="input"
                value={fixedForm.maturityDate}
                onChange={e => setFixedForm({...fixedForm, maturityDate: e.target.value})}
                required
              />
            </div>
          </div>

          <div>
            <label className="label">Conta de Investimento</label>
            <select
              className="input"
              value={fixedForm.accountId}
              onChange={e => setFixedForm({...fixedForm, accountId: e.target.value})}
              required
            >
              <option value="">Selecione...</option>
              {accounts.map(acc => (
                <option key={acc.idConta} value={acc.idConta}>
                  {acc.nome} ({acc.tipo})
                </option>
              ))}
            </select>
          </div>

          <div className="flex justify-end gap-2 mt-6">
            <button type="button" onClick={onClose} className="btn-secondary">Cancelar</button>
            <button type="submit" className="btn-primary" disabled={loading}>
              {loading ? 'Salvando...' : 'Salvar'}
            </button>
          </div>
        </form>
      )}
    </Modal>
  );
}

