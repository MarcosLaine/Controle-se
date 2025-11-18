import java.io.*;
import java.math.BigInteger;
import java.util.Random;

/**
 * Implementação do algoritmo RSA do zero
 * Gera chaves pública e privada, criptografa e descriptografa dados
 */
public class RSAEncryption {
    private BigInteger modPrim1Prim2;
    private BigInteger expoPublico;
    private BigInteger expoPrivado;
    private BigInteger prim1;
    private BigInteger prim2;
    private BigInteger totienteEuler; // Função totiente de Euler (prim1-1)(prim2-1)
    
    private static final int BIT_LENGTH = 256; // Tamanho das chaves (256 bits para performance)
    private static final BigInteger PUBLIC_EXPONENT = new BigInteger("65537"); // Expoente público padrão
    
    /**
     * Construtor que gera um novo par de chaves RSA
     */
    public RSAEncryption() {
        gerarChaves();
    }
    
    /**
     * Construtor que carrega chaves existentes
     */
    public RSAEncryption(BigInteger modPrim1Prim2, BigInteger expoPublico, BigInteger expoPrivado) {
        this.modPrim1Prim2 = modPrim1Prim2;
        this.expoPublico = expoPublico;
        this.expoPrivado = expoPrivado;
    }
    
    /**
     * Gera um par de chaves RSA (pública e privada)
     */
    private void gerarChaves() {
        Random random = new Random();
        
        // Gera dois números primos grandes
        prim1 = gerarNumeroPrimo(BIT_LENGTH / 2, random);
        prim2 = gerarNumeroPrimo(BIT_LENGTH / 2, random);
        
        // Garante que p e q são diferentes
        while (prim1.equals(prim2)) {
            prim2 = gerarNumeroPrimo(BIT_LENGTH / 2, random);
        }
        
        // Calcula modPrim1Prim2 = prim1 * prim2
        modPrim1Prim2 = prim1.multiply(prim2);
        
        // Calcula phi(n) = (p-1) * (q-1)
        BigInteger prim1MenosUm = prim1.subtract(BigInteger.ONE);
        BigInteger prim2MenosUm = prim2.subtract(BigInteger.ONE);
        totienteEuler = prim1MenosUm.multiply(prim2MenosUm);
        
        // Define o expoente público (geralmente 65537)
        expoPublico = PUBLIC_EXPONENT;
        
        // Garante que e e phi são coprimos
        while (!expoPublico.gcd(totienteEuler).equals(BigInteger.ONE)) {
            expoPublico = expoPublico.add(BigInteger.ONE);
        }
        
        // Calcula o expoente privado d usando o algoritmo estendido de Euclides
        expoPrivado = calcularInversoModular(expoPublico, totienteEuler);
    }
    
    /**
     * Gera um número primo provável usando o teste de Miller-Rabin
     */
    private BigInteger gerarNumeroPrimo(int bitLength, Random random) {
        BigInteger primo;
        do {
            // Gera um número ímpar aleatório com o tamanho especificado
            primo = new BigInteger(bitLength, random);
            if (primo.mod(BigInteger.valueOf(2)).equals(BigInteger.ZERO)) {
                primo = primo.add(BigInteger.ONE);
            }
        } while (!testeMillerRabin(primo, 20)); // 20 iterações para alta confiança
        
        return primo;
    }
    
    /**
     * Teste de primalidade de Miller-Rabin
     */
    private boolean testeMillerRabin(BigInteger n, int iteracoes) {
        if (n.equals(BigInteger.valueOf(2)) || n.equals(BigInteger.valueOf(3))) {
            return true;
        }
        if (n.mod(BigInteger.valueOf(2)).equals(BigInteger.ZERO)) {
            return false;
        }
        
        // Escreve n-1 como d * 2^r
        BigInteger d = n.subtract(BigInteger.ONE);
        int r = 0;
        while (d.mod(BigInteger.valueOf(2)).equals(BigInteger.ZERO)) {
            d = d.divide(BigInteger.valueOf(2));
            r++;
        }
        
        Random random = new Random();
        
        for (int i = 0; i < iteracoes; i++) {
            BigInteger a = new BigInteger(n.bitLength() - 1, random).add(BigInteger.ONE);
            BigInteger x = modPow(a, d, n);
            
            if (x.equals(BigInteger.ONE) || x.equals(n.subtract(BigInteger.ONE))) {
                continue;
            }
            
            boolean continua = false;
            for (int j = 0; j < r - 1; j++) {
                x = modPow(x, BigInteger.valueOf(2), n);
                if (x.equals(n.subtract(BigInteger.ONE))) {
                    continua = true;
                    break;
                }
            }
            
            if (!continua) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Calcula o inverso modular usando o algoritmo estendido de Euclides
     */
    private BigInteger calcularInversoModular(BigInteger a, BigInteger m) {
        BigInteger m0 = m;
        BigInteger y = BigInteger.ZERO;
        BigInteger x = BigInteger.ONE;
        
        if (m.equals(BigInteger.ONE)) {
            return BigInteger.ZERO;
        }
        
        while (a.compareTo(BigInteger.ONE) > 0) {
            BigInteger q = a.divide(m);
            BigInteger t = m;
            
            m = a.mod(m);
            a = t;
            t = y;
            
            y = x.subtract(q.multiply(y));
            x = t;
        }
        
        if (x.compareTo(BigInteger.ZERO) < 0) {
            x = x.add(m0);
        }
        
        return x;
    }
    
    /**
     * Calcula (base^exponent) mod modulus de forma eficiente (exponenciação modular)
     */
    private BigInteger modPow(BigInteger base, BigInteger exponent, BigInteger modulus) {
        BigInteger result = BigInteger.ONE;
        base = base.mod(modulus);
        
        while (exponent.compareTo(BigInteger.ZERO) > 0) {
            if (exponent.mod(BigInteger.valueOf(2)).equals(BigInteger.ONE)) {
                result = result.multiply(base).mod(modulus);
            }
            exponent = exponent.shiftRight(1);
            base = base.multiply(base).mod(modulus);
        }
        
        return result;
    }
    
    /**
     * Criptografa uma string usando a chave pública
     */
    public String criptografar(String texto) {
        if (texto == null || texto.isEmpty()) {
            return texto;
        }
        
        // Converte a string em bytes e depois em BigInteger
        byte[] bytes = texto.getBytes();
        BigInteger mensagem = new BigInteger(1, bytes);
        
        // Verifica se a mensagem é menor que n
        if (mensagem.compareTo(modPrim1Prim2) >= 0) {
            // Se a mensagem for muito grande, divide em blocos
            return criptografarBlocos(texto);
        }
        
        // Criptografa: c = m^e mod n
        BigInteger criptografado = modPow(mensagem, expoPublico, modPrim1Prim2);
        
        // Converte para string hexadecimal
        return criptografado.toString(16);
    }
    
    /**
     * Criptografa texto em blocos quando a mensagem é maior que n
     */
    private String criptografarBlocos(String texto) {
        StringBuilder resultado = new StringBuilder();
        int tamanhoBloco = (modPrim1Prim2.bitLength() - 1) / 8; // Tamanho do bloco em bytes
        
        byte[] bytes = texto.getBytes();
        int offset = 0;
        
        while (offset < bytes.length) {
            int tamanho = Math.min(tamanhoBloco, bytes.length - offset);
            byte[] bloco = new byte[tamanho];
            System.arraycopy(bytes, offset, bloco, 0, tamanho);
            
            BigInteger mensagem = new BigInteger(1, bloco);
            BigInteger criptografado = modPow(mensagem, expoPublico, modPrim1Prim2);
            
            if (resultado.length() > 0) {
                resultado.append(":");
            }
            resultado.append(criptografado.toString(16));
            
            offset += tamanho;
        }
        
        return resultado.toString();
    }
    
    /**
     * Descriptografa uma string usando a chave privada
     */
    public String descriptografar(String textoCriptografado) {
        if (textoCriptografado == null || textoCriptografado.isEmpty()) {
            return textoCriptografado;
        }
        
        // Verifica se está em blocos (contém ':')
        if (textoCriptografado.contains(":")) {
            return descriptografarBlocos(textoCriptografado);
        }
        
        // Converte de hexadecimal para BigInteger
        BigInteger criptografado = new BigInteger(textoCriptografado, 16);
        
        // Descriptografa: m = c^d mod n
        BigInteger descriptografado = modPow(criptografado, expoPrivado, modPrim1Prim2);
        
        // Converte de volta para string
        byte[] bytes = descriptografado.toByteArray();
        
        // Remove o byte zero à esquerda se existir
        if (bytes.length > 0 && bytes[0] == 0) {
            byte[] bytesCorrigidos = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, bytesCorrigidos, 0, bytesCorrigidos.length);
            bytes = bytesCorrigidos;
        }
        
        return new String(bytes);
    }
    
    /**
     * Descriptografa texto em blocos
     */
    private String descriptografarBlocos(String textoCriptografado) {
        StringBuilder resultado = new StringBuilder();
        String[] blocos = textoCriptografado.split(":");
        
        for (String blocoHex : blocos) {
            BigInteger criptografado = new BigInteger(blocoHex, 16);
            BigInteger descriptografado = modPow(criptografado, expoPrivado, modPrim1Prim2);
            
            byte[] bytes = descriptografado.toByteArray();
            
            // Remove o byte zero à esquerda se existir
            if (bytes.length > 0 && bytes[0] == 0) {
                byte[] bytesCorrigidos = new byte[bytes.length - 1];
                System.arraycopy(bytes, 1, bytesCorrigidos, 0, bytesCorrigidos.length);
                bytes = bytesCorrigidos;
            }
            
            resultado.append(new String(bytes));
        }
        
        return resultado.toString();
    }
    
    /**
     * Getters para as chaves (para persistência)
     */
    public BigInteger getN() {
        return modPrim1Prim2;
    }
    
    public BigInteger getE() {
        return expoPublico;
    }
    
    public BigInteger getD() {
        return expoPrivado;
    }
    
    /**
     * Salva as chaves em arquivo
     */
    public void salvarChaves(String arquivoChaves) throws IOException {
        File diretorio = new File(arquivoChaves).getParentFile();
        if (diretorio != null && !diretorio.exists()) {
            diretorio.mkdirs();
        }
        
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(arquivoChaves))) {
            oos.writeObject(modPrim1Prim2);
            oos.writeObject(expoPublico);
            oos.writeObject(expoPrivado);
        }
    }
    
    /**
     * Carrega as chaves de um arquivo
     */
    public static RSAEncryption carregarChaves(String arquivoChaves) throws IOException, ClassNotFoundException {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(arquivoChaves))) {
            BigInteger modPrim1Prim2 = (BigInteger) ois.readObject();
            BigInteger expoPublico = (BigInteger) ois.readObject();
            BigInteger expoPrivado = (BigInteger) ois.readObject();
            return new RSAEncryption(modPrim1Prim2, expoPublico, expoPrivado);
        }
    }
}

