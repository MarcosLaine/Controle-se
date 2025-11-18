import java.io.File;
import java.io.IOException;

/**
 * Gerenciador de chaves RSA
 * Garante que as chaves sejam geradas uma vez e reutilizadas
 */
public class RSAKeyManager {
    private static final String CHAVES_RSA_FILE = "data/chaves_rsa.db";
    private static RSAEncryption instanciaRSA = null;
    
    /**
     * Obtém a instância única de RSAEncryption
     * Gera novas chaves se não existirem, ou carrega as existentes
     */
    public static RSAEncryption obterInstancia() {
        if (instanciaRSA == null) {
            try {
                // Tenta carregar chaves existentes
                File arquivoChaves = new File(CHAVES_RSA_FILE);
                if (arquivoChaves.exists()) {
                    instanciaRSA = RSAEncryption.carregarChaves(CHAVES_RSA_FILE);
                    System.out.println("Chaves RSA carregadas do arquivo");
                } else {
                    // Gera novas chaves
                    instanciaRSA = new RSAEncryption();
                    instanciaRSA.salvarChaves(CHAVES_RSA_FILE);
                    System.out.println("Novas chaves RSA geradas e salvas");
                }
            } catch (Exception e) {
                System.err.println("Erro ao gerenciar chaves RSA: " + e.getMessage());
                // Em caso de erro, gera novas chaves
                instanciaRSA = new RSAEncryption();
                try {
                    instanciaRSA.salvarChaves(CHAVES_RSA_FILE);
                } catch (IOException ioException) {
                    System.err.println("Erro ao salvar chaves RSA: " + ioException.getMessage());
                }
            }
        }
        return instanciaRSA;
    }
}

