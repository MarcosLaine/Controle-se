import java.io.*;
import java.util.*;
import java.text.SimpleDateFormat;

/**
 * Classe para comprimir e descomprimir todos os arquivos .db do sistema
 * Cria um único arquivo compactado contendo todos os arquivos de dados
 */
public class FileCompressor {
    
    private static final String DATA_DIR = "data";
    private static final String IDX_DIR = DATA_DIR + "/idx";
    private static final String COMPRESSED_DIR_LZW = DATA_DIR + "/compressed/lzw";
    private static final String COMPRESSED_DIR_HUFFMAN = DATA_DIR + "/compressed/huffman";
    private static final String COMPRESSED_EXT_LZW = ".lzw";
    private static final String COMPRESSED_EXT_HUFFMAN = ".huffman";
    
    /**
     * Comprime todos os arquivos .db usando o algoritmo especificado
     * @param algorithm "LZW" ou "HUFFMAN"
     * @return caminho do arquivo comprimido
     */
    public static String compressAllFiles(String algorithm) throws IOException {
        File dataDir = new File(DATA_DIR);
        if (!dataDir.exists() || !dataDir.isDirectory()) {
            throw new IOException("Diretório 'data' não encontrado");
        }
        
        // Lista todos os arquivos .db em data/ (excluindo idx/)
        File[] dbFiles = dataDir.listFiles((dir, name) -> name.endsWith(".db") && !name.startsWith("idx_"));
        if (dbFiles == null) {
            dbFiles = new File[0];
        }
        
        // Lista todos os arquivos .db em data/idx/
        File[] idxFiles = new File[0];
        File idxDir = new File(IDX_DIR);
        if (idxDir.exists() && idxDir.isDirectory()) {
            File[] tempIdxFiles = idxDir.listFiles((dir, name) -> name.endsWith(".db"));
            if (tempIdxFiles != null) {
                idxFiles = tempIdxFiles;
            }
        }
        
        int totalFiles = dbFiles.length + idxFiles.length;
        if (totalFiles == 0) {
            throw new IOException("Nenhum arquivo .db encontrado para comprimir");
        }
        
        System.out.println("Encontrados " + totalFiles + " arquivos .db para comprimir...");
        System.out.println("  - " + dbFiles.length + " arquivos em " + DATA_DIR);
        System.out.println("  - " + idxFiles.length + " arquivos em " + IDX_DIR);
        
        // Cria um arquivo temporário para armazenar todos os arquivos
        ByteArrayOutputStream archiveStream = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(archiveStream);
        
        // Escreve número total de arquivos
        dos.writeInt(totalFiles);
        
        // Para cada arquivo em data/, escreve: [caminho relativo][tamanho do nome][nome][tamanho dos dados][dados]
        for (File file : dbFiles) {
            String relativePath = DATA_DIR + "/" + file.getName();
            byte[] fileData = readFile(file);
            
            // Escreve caminho relativo (para saber onde restaurar)
            byte[] pathBytes = relativePath.getBytes("UTF-8");
            dos.writeInt(pathBytes.length);
            dos.write(pathBytes);
            
            // Escreve dados do arquivo
            dos.writeInt(fileData.length);
            dos.write(fileData);
            
            System.out.println("  - " + relativePath + " (" + fileData.length + " bytes)");
        }
        
        // Para cada arquivo em data/idx/, escreve: [caminho relativo][tamanho dos dados][dados]
        for (File file : idxFiles) {
            String relativePath = IDX_DIR + "/" + file.getName();
            byte[] fileData = readFile(file);
            
            // Escreve caminho relativo (para saber onde restaurar)
            byte[] pathBytes = relativePath.getBytes("UTF-8");
            dos.writeInt(pathBytes.length);
            dos.write(pathBytes);
            
            // Escreve dados do arquivo
            dos.writeInt(fileData.length);
            dos.write(fileData);
            
            System.out.println("  - " + relativePath + " (" + fileData.length + " bytes)");
        }
        
        dos.flush();
        byte[] archiveData = archiveStream.toByteArray();
        
        System.out.println("\nTamanho total antes da compressão: " + archiveData.length + " bytes");
        
        // Comprime o arquivo usando o algoritmo especificado
        byte[] compressedData;
        String extension;
        
        if ("LZW".equalsIgnoreCase(algorithm)) {
            compressedData = LZWCompression.compress(archiveData);
            extension = COMPRESSED_EXT_LZW;
            System.out.println("Comprimindo com LZW...");
        } else if ("HUFFMAN".equalsIgnoreCase(algorithm)) {
            compressedData = HuffmanCompression.compress(archiveData);
            extension = COMPRESSED_EXT_HUFFMAN;
            System.out.println("Comprimindo com Huffman...");
        } else {
            throw new IllegalArgumentException("Algoritmo inválido. Use 'LZW' ou 'HUFFMAN'");
        }
        
        System.out.println("Tamanho após compressão: " + compressedData.length + " bytes");
        double ratio = (1.0 - (double) compressedData.length / archiveData.length) * 100;
        System.out.println(String.format("Taxa de compressão: %.2f%%", ratio));
        
        // Determina diretório de destino baseado no algoritmo
        String compressedDir;
        if ("LZW".equalsIgnoreCase(algorithm)) {
            compressedDir = COMPRESSED_DIR_LZW;
        } else {
            compressedDir = COMPRESSED_DIR_HUFFMAN;
        }
        
        // Cria diretório se não existir
        File dir = new File(compressedDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        
        // Gera nome do arquivo no formato: compressed-data-hora.método
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
        String timestamp = dateFormat.format(new Date());
        String methodName = algorithm.toLowerCase();
        String compressedFileName = "compressed-" + timestamp + "." + methodName;
        File compressedFile = new File(dir, compressedFileName);
        
        try (FileOutputStream fos = new FileOutputStream(compressedFile)) {
            fos.write(compressedData);
        }
        
        System.out.println("\nArquivo comprimido criado: " + compressedFileName);
        System.out.println("Localização: " + compressedFile.getAbsolutePath());
        
        return compressedFile.getAbsolutePath();
    }
    
    /**
     * Descomprime um arquivo e restaura todos os arquivos .db
     * @param compressedFilePath caminho do arquivo comprimido
     */
    public static void decompressAllFiles(String compressedFilePath) throws IOException {
        File compressedFile = new File(compressedFilePath);
        if (!compressedFile.exists()) {
            throw new IOException("Arquivo comprimido não encontrado: " + compressedFilePath);
        }
        
        System.out.println("Descomprimindo arquivo: " + compressedFilePath);
        
        // Lê arquivo comprimido
        byte[] compressedData = readFile(compressedFile);
        
        // Determina algoritmo pelo nome do arquivo
        String algorithm;
        if (compressedFilePath.endsWith(COMPRESSED_EXT_LZW)) {
            algorithm = "LZW";
            System.out.println("Usando algoritmo LZW para descompressão...");
        } else if (compressedFilePath.endsWith(COMPRESSED_EXT_HUFFMAN)) {
            algorithm = "HUFFMAN";
            System.out.println("Usando algoritmo Huffman para descompressão...");
        } else {
            throw new IllegalArgumentException("Extensão de arquivo não reconhecida. Use .lzw ou .huffman");
        }
        
        // Descomprime
        byte[] archiveData;
        if ("LZW".equals(algorithm)) {
            archiveData = LZWCompression.decompress(compressedData);
        } else {
            archiveData = HuffmanCompression.decompress(compressedData);
        }
        
        System.out.println("Tamanho após descompressão: " + archiveData.length + " bytes");
        
        // Lê arquivos do arquivo
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(archiveData));
        
        // Lê número de arquivos
        int fileCount = dis.readInt();
        System.out.println("\nRestaurando " + fileCount + " arquivos...");
        
        // Garante que os diretórios existem
        File dataDir = new File(DATA_DIR);
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
        File idxDir = new File(IDX_DIR);
        if (!idxDir.exists()) {
            idxDir.mkdirs();
        }
        
        // Restaura cada arquivo
        for (int i = 0; i < fileCount; i++) {
            // Lê caminho relativo do arquivo
            int pathLength = dis.readInt();
            byte[] pathBytes = new byte[pathLength];
            dis.readFully(pathBytes);
            String relativePath = new String(pathBytes, "UTF-8");
            
            // Lê dados do arquivo
            int dataLength = dis.readInt();
            byte[] fileData = new byte[dataLength];
            dis.readFully(fileData);
            
            // Salva arquivo no caminho correto
            File outputFile = new File(relativePath);
            File parentDir = outputFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                fos.write(fileData);
            }
            
            System.out.println("  - " + relativePath + " (" + fileData.length + " bytes)");
        }
        
        System.out.println("\nDescompressão concluída! Todos os arquivos foram restaurados.");
    }
    
    /**
     * Lê um arquivo e retorna seu conteúdo como array de bytes
     */
    private static byte[] readFile(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
            
            return baos.toByteArray();
        }
    }
    
    /**
     * Lista todos os arquivos comprimidos disponíveis
     */
    public static List<String> listCompressedFiles() {
        List<String> files = new ArrayList<>();
        
        // Busca arquivos em compressed/lzw/
        File lzwDir = new File(COMPRESSED_DIR_LZW);
        if (lzwDir.exists() && lzwDir.isDirectory()) {
            File[] lzwFiles = lzwDir.listFiles((dir, name) -> name.endsWith(COMPRESSED_EXT_LZW));
            if (lzwFiles != null) {
                for (File file : lzwFiles) {
                    files.add(file.getAbsolutePath());
                }
            }
        }
        
        // Busca arquivos em compressed/huffman/
        File huffmanDir = new File(COMPRESSED_DIR_HUFFMAN);
        if (huffmanDir.exists() && huffmanDir.isDirectory()) {
            File[] huffmanFiles = huffmanDir.listFiles((dir, name) -> name.endsWith(COMPRESSED_EXT_HUFFMAN));
            if (huffmanFiles != null) {
                for (File file : huffmanFiles) {
                    files.add(file.getAbsolutePath());
                }
            }
        }
        
        return files;
    }
}

