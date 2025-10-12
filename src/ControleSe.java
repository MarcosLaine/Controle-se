import java.util.Scanner;

/**
 * Sistema de Controle AEDS3 - Implementação em Java
 * Sistema Financeiro Controle-se com Banco de Dados
 * Implementação com Estruturas de Dados Avançadas
 */
public class ControleSe {
    private static Scanner scanner = new Scanner(System.in);
    
    public static void main(String[] args) {
        System.out.println("=== Sistema Controle-se ===");
        System.out.println("Sistema Financeiro com Banco de Dados");
        System.out.println("Implementação com Estruturas de Dados Avançadas");
        System.out.println();
        
        SistemaFinanceiro sistemaFinanceiro = new SistemaFinanceiro();
        sistemaFinanceiro.executar();
        
        scanner.close();
    }
    
    public static int lerInteiro(String mensagem) {
        System.out.print(mensagem);
        return scanner.nextInt();
    }
    
    public static String lerString(String mensagem) {
        System.out.print(mensagem);
        scanner.nextLine(); // Limpa o buffer
        return scanner.nextLine();
    }
    
    public static void pausar() {
        System.out.println("Pressione Enter para continuar...");
        scanner.nextLine();
    }
}
