package server.security;

import java.io.File;
import java.io.IOException;

public class RSAKeyManager {
    private static final String CHAVES_RSA_FILE = "data/chaves_rsa.db";
    private static RSAEncryption instanciaRSA = null;
    
    public static RSAEncryption obterInstancia() {
        if (instanciaRSA == null) {
            try {
                File arquivoChaves = new File(CHAVES_RSA_FILE);
                if (arquivoChaves.exists()) {
                    instanciaRSA = RSAEncryption.carregarChaves(CHAVES_RSA_FILE);
                    System.out.println("Chaves RSA carregadas do arquivo");
                } else {
                    instanciaRSA = new RSAEncryption();
                    instanciaRSA.salvarChaves(CHAVES_RSA_FILE);
                    System.out.println("Novas chaves RSA geradas e salvas");
                }
            } catch (Exception e) {
                System.err.println("Erro ao gerenciar chaves RSA: " + e.getMessage());
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

