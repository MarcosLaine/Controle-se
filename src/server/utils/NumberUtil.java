package server.utils;

/**
 * Utilitários para manipulação de números, especialmente formato brasileiro
 */
public class NumberUtil {
    
    /**
     * Normaliza números no formato brasileiro (vírgula como separador decimal) 
     * para formato internacional (ponto como separador decimal)
     * Exemplos: "1.234,56" -> "1234.56", "1,5" -> "1.5", "1000" -> "1000"
     */
    public static String normalizeBrazilianNumber(String numberStr) {
        if (numberStr == null || numberStr.trim().isEmpty()) {
            return numberStr;
        }
        String trimmed = numberStr.trim();
        // Remove separadores de milhar (pontos) e substitui vírgula por ponto
        // Exemplo: "1.234,56" -> remove pontos -> "1234,56" -> substitui vírgula -> "1234.56"
        if (trimmed.contains(",")) {
            // Tem vírgula: formato brasileiro
            // Remove pontos (separadores de milhar) e substitui vírgula por ponto
            return trimmed.replace(".", "").replace(",", ".");
        } else if (trimmed.contains(".")) {
            // Tem ponto mas não vírgula: pode ser formato internacional ou separador de milhar
            // Se tiver mais de um ponto, provavelmente é separador de milhar (formato brasileiro sem vírgula)
            // Exemplo: "1.234" -> "1234"
            long dotCount = trimmed.chars().filter(ch -> ch == '.').count();
            if (dotCount > 1) {
                // Múltiplos pontos: remove todos (separadores de milhar)
                return trimmed.replace(".", "");
            }
            // Um ponto: pode ser decimal, mantém
            return trimmed;
        }
        // Sem ponto nem vírgula: número inteiro
        return trimmed;
    }
    
    /**
     * Parse de número com suporte a formato brasileiro
     */
    public static double parseDoubleBrazilian(String numberStr) throws NumberFormatException {
        if (numberStr == null || numberStr.trim().isEmpty() || numberStr.equals("null") || numberStr.equals("NaN")) {
            throw new NumberFormatException("Não é possível fazer parse de string nula ou vazia");
        }
        String normalized = normalizeBrazilianNumber(numberStr);
        if (normalized == null || normalized.trim().isEmpty()) {
            throw new NumberFormatException("Não é possível fazer parse de string nula ou vazia");
        }
        return Double.parseDouble(normalized);
    }
}

