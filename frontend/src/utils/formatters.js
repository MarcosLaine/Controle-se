export const formatCurrency = (value) => {
  if (value === null || value === undefined || isNaN(value)) {
    return 'R$ 0,00';
  }
  return new Intl.NumberFormat('pt-BR', {
    style: 'currency',
    currency: 'BRL',
  }).format(value);
};

export const formatDate = (dateString) => {
  if (!dateString) return '';
  
  // Se a string for apenas data (YYYY-MM-DD), adiciona o horário para evitar problemas de fuso horário
  // O Date.parse("YYYY-MM-DD") interpreta como UTC, o que pode cair no dia anterior dependendo do fuso
  if (typeof dateString === 'string' && dateString.length === 10 && /^\d{4}-\d{2}-\d{2}$/.test(dateString)) {
    dateString += 'T12:00:00';
  }

  const date = new Date(dateString);
  const day = String(date.getDate()).padStart(2, '0');
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const year = String(date.getFullYear()).slice(-2); // Últimos 2 dígitos do ano
  
  return `${day}-${month}-${year}`;
};

export const formatDateTime = (dateString) => {
  if (!dateString) return '';
  const date = new Date(dateString);
  const day = String(date.getDate()).padStart(2, '0');
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const year = String(date.getFullYear()).slice(-2); // Últimos 2 dígitos do ano
  const hours = String(date.getHours()).padStart(2, '0');
  const minutes = String(date.getMinutes()).padStart(2, '0');
  
  return `${day}-${month}-${year} ${hours}:${minutes}`;
};

/**
 * Normaliza números no formato brasileiro (vírgula como separador decimal) 
 * para formato internacional (ponto como separador decimal)
 * Exemplos: "1.234,56" -> "1234.56", "1,5" -> "1.5", "1000" -> "1000"
 * 
 * @param {string} numberStr - String com número no formato brasileiro
 * @returns {string} - String com número no formato internacional
 */
export function normalizeBrazilianNumber(numberStr) {
  if (!numberStr || typeof numberStr !== 'string') {
    return numberStr;
  }
  
  const trimmed = numberStr.trim();
  
  // Se tem vírgula: formato brasileiro
  // Remove pontos (separadores de milhar) e substitui vírgula por ponto
  if (trimmed.includes(',')) {
    return trimmed.replace(/\./g, '').replace(',', '.');
  }
  
  // Se tem ponto mas não vírgula: pode ser formato internacional ou separador de milhar
  if (trimmed.includes('.')) {
    const dotCount = (trimmed.match(/\./g) || []).length;
    // Múltiplos pontos: remove todos (separadores de milhar)
    if (dotCount > 1) {
      return trimmed.replace(/\./g, '');
    }
    // Um ponto: pode ser decimal, mantém
    return trimmed;
  }
  
  // Sem ponto nem vírgula: número inteiro
  return trimmed;
}

/**
 * Parse de número com suporte a formato brasileiro
 * 
 * @param {string} numberStr - String com número no formato brasileiro
 * @returns {number} - Número parseado
 */
export function parseFloatBrazilian(numberStr) {
  const normalized = normalizeBrazilianNumber(numberStr);
  return parseFloat(normalized);
}

/**
 * Parse de número inteiro com suporte a formato brasileiro
 * 
 * @param {string} numberStr - String com número no formato brasileiro
 * @returns {number} - Número inteiro parseado
 */
export function parseIntBrazilian(numberStr) {
  const normalized = normalizeBrazilianNumber(numberStr);
  return parseInt(normalized, 10);
}

