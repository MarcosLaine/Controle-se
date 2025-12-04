import java.util.Arrays;

/**
 * Utilitário para algoritmos de Casamento de Padrão em Strings.
 * Implementa KMP e Boyer-Moore (Bad Character).
 */
public class PatternMatching {

    /**
     * Algoritmo Knuth-Morris-Pratt (KMP)
     * Complexidade: O(n + m)
     * @param text O texto onde procurar
     * @param pattern O padrão a ser encontrado
     * @return true se o padrão existir no texto
     */
    public static boolean searchKMP(String text, String pattern) {
        if (pattern == null || pattern.length() == 0) return false;
        if (text == null || pattern.length() > text.length()) return false;

        int n = text.length();
        int m = pattern.length();
        
        // Pré-processamento do padrão (LPS array)
        int[] lps = computeLPSArray(pattern);

        int i = 0; // índice para text
        int j = 0; // índice para pattern

        while (i < n) {
            if (pattern.charAt(j) == text.charAt(i)) {
                j++;
                i++;
            }
            if (j == m) {
                return true; // Padrão encontrado
                // Para encontrar todas as ocorrências, faríamos: j = lps[j - 1];
            } else if (i < n && pattern.charAt(j) != text.charAt(i)) {
                if (j != 0) {
                    j = lps[j - 1];
                } else {
                    i = i + 1;
                }
            }
        }
        return false;
    }

    /**
     * Calcula o array LPS (Longest Prefix Suffix) para o KMP
     */
    private static int[] computeLPSArray(String pattern) {
        int m = pattern.length();
        int[] lps = new int[m];
        int len = 0; // comprimento do prefixo anterior mais longo
        int i = 1;
        lps[0] = 0;

        while (i < m) {
            if (pattern.charAt(i) == pattern.charAt(len)) {
                len++;
                lps[i] = len;
                i++;
            } else {
                if (len != 0) {
                    len = lps[len - 1];
                } else {
                    lps[i] = len;
                    i++;
                }
            }
        }
        return lps;
    }

    /**
     * Algoritmo Boyer-Moore (Versão Bad Character Heuristic)
     * Melhor caso: O(n/m)
     * @param text O texto onde procurar
     * @param pattern O padrão a ser encontrado
     * @return true se o padrão existir no texto
     */
    public static boolean searchBoyerMoore(String text, String pattern) {
        if (pattern == null || pattern.length() == 0) return false;
        if (text == null || pattern.length() > text.length()) return false;

        int m = pattern.length();
        int n = text.length();

        int[] badChar = new int[256]; // Suporta ASCII estendido
        badCharHeuristic(pattern, m, badChar);

        int s = 0; // s é o deslocamento (shift) do padrão em relação ao texto

        while (s <= (n - m)) {
            int j = m - 1;

            // Move da direita para a esquerda enquanto os caracteres combinam
            while (j >= 0 && pattern.charAt(j) == text.charAt(s + j)) {
                j--;
            }

            if (j < 0) {
                return true; // Padrão encontrado
                // Para múltiplas ocorrências: s += (s + m < n) ? m - badChar[text.charAt(s + m)] : 1;
            } else {
                // Desloca o padrão baseado no caractere ruim
                // Math.max garante que o deslocamento seja sempre positivo
                s += Math.max(1, j - badChar[text.charAt(s + j)]);
            }
        }
        return false;
    }

    /**
     * Pré-processamento da heurística Bad Character para Boyer-Moore
     */
    private static void badCharHeuristic(String str, int size, int[] badChar) {
        // Inicializa todas as ocorrências como -1
        Arrays.fill(badChar, -1);

        // Preenche o valor real da última ocorrência de um caractere
        for (int i = 0; i < size; i++) {
            // Garante que não estoure o array se for caractere unicode > 255
            if (str.charAt(i) < 256) {
                badChar[(int) str.charAt(i)] = i;
            }
        }
    }
}