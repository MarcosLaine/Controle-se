import java.io.*;
import java.util.*;

/**
 * Implementação customizada do algoritmo de compressão LZW (Lempel-Ziv-Welch)
 * Não utiliza métodos prontos - implementação do zero
 */
public class LZWCompression {
    
    private static final int DICT_SIZE = 4096; // Tamanho máximo do dicionário (12 bits)
    
    /**
     * Comprime um array de bytes usando LZW
     */
    public static byte[] compress(byte[] data) throws IOException {
        if (data.length == 0) {
            return new byte[0];
        }
        
        // Inicializa dicionário com todos os bytes possíveis (0-255)
        Map<List<Byte>, Integer> dictionary = new HashMap<>();
        for (int i = 0; i < 256; i++) {
            List<Byte> entry = new ArrayList<>();
            entry.add((byte) i);
            dictionary.put(entry, i);
        }
        
        int nextCode = 256;
        List<Byte> current = new ArrayList<>();
        List<Integer> output = new ArrayList<>();
        
        // Algoritmo LZW
        for (byte b : data) {
            List<Byte> next = new ArrayList<>(current);
            next.add(b);
            
            if (dictionary.containsKey(next)) {
                current = next;
            } else {
                // Adiciona código atual à saída
                output.add(dictionary.get(current));
                
                // Adiciona nova sequência ao dicionário se houver espaço
                if (nextCode < DICT_SIZE) {
                    dictionary.put(new ArrayList<>(next), nextCode++);
                }
                
                current.clear();
                current.add(b);
            }
        }
        
        // Adiciona último código
        if (!current.isEmpty()) {
            output.add(dictionary.get(current));
        }
        
        // Converte códigos para bytes
        // Cada código é armazenado em 12 bits (1.5 bytes)
        return codesToBytes(output);
    }
    
    /**
     * Descomprime um array de bytes usando LZW
     */
    public static byte[] decompress(byte[] compressed) throws IOException {
        if (compressed.length == 0) {
            return new byte[0];
        }
        
        // Converte bytes para códigos
        List<Integer> codes = bytesToCodes(compressed);
        
        // Inicializa dicionário com todos os bytes possíveis (0-255)
        Map<Integer, List<Byte>> dictionary = new HashMap<>();
        for (int i = 0; i < 256; i++) {
            List<Byte> entry = new ArrayList<>();
            entry.add((byte) i);
            dictionary.put(i, entry);
        }
        
        int nextCode = 256;
        List<Byte> result = new ArrayList<>();
        
        // Primeiro código
        int oldCode = codes.get(0);
        List<Byte> oldEntry = new ArrayList<>(dictionary.get(oldCode));
        result.addAll(oldEntry);
        
        // Algoritmo LZW de descompressão
        for (int i = 1; i < codes.size(); i++) {
            int newCode = codes.get(i);
            List<Byte> newEntry;
            
            if (dictionary.containsKey(newCode)) {
                newEntry = new ArrayList<>(dictionary.get(newCode));
            } else {
                // Caso especial: código ainda não está no dicionário
                newEntry = new ArrayList<>(oldEntry);
                if (!oldEntry.isEmpty()) {
                    newEntry.add(oldEntry.get(0));
                }
            }
            
            result.addAll(newEntry);
            
            // Adiciona nova sequência ao dicionário
            if (nextCode < DICT_SIZE && !oldEntry.isEmpty()) {
                List<Byte> toAdd = new ArrayList<>(oldEntry);
                toAdd.add(newEntry.get(0));
                dictionary.put(nextCode++, toAdd);
            }
            
            oldCode = newCode;
            oldEntry = newEntry;
        }
        
        // Converte lista para array
        byte[] output = new byte[result.size()];
        for (int i = 0; i < result.size(); i++) {
            output[i] = result.get(i);
        }
        
        return output;
    }
    
    /**
     * Converte lista de códigos para array de bytes
     * Cada código usa 12 bits (1.5 bytes)
     */
    private static byte[] codesToBytes(List<Integer> codes) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        
        // Escreve número de códigos
        dos.writeInt(codes.size());
        
        // Escreve códigos em 12 bits cada
        int bitBuffer = 0;
        int bitsInBuffer = 0;
        
        for (int code : codes) {
            // Adiciona código ao buffer (12 bits)
            bitBuffer = (bitBuffer << 12) | (code & 0xFFF);
            bitsInBuffer += 12;
            
            // Escreve bytes completos
            while (bitsInBuffer >= 8) {
                int byteValue = (bitBuffer >> (bitsInBuffer - 8)) & 0xFF;
                dos.writeByte(byteValue);
                bitsInBuffer -= 8;
            }
        }
        
        // Escreve bits restantes (padding)
        if (bitsInBuffer > 0) {
            int byteValue = (bitBuffer << (8 - bitsInBuffer)) & 0xFF;
            dos.writeByte(byteValue);
            dos.writeByte(bitsInBuffer); // Salva quantos bits são válidos no último byte
        } else {
            dos.writeByte(0); // Nenhum bit de padding
        }
        
        return baos.toByteArray();
    }
    
    /**
     * Converte array de bytes para lista de códigos
     */
    private static List<Integer> bytesToCodes(byte[] data) throws IOException {
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));
        
        // Lê número de códigos
        int codeCount = dis.readInt();
        List<Integer> codes = new ArrayList<>();
        
        // Lê códigos em 12 bits cada
        int bitBuffer = 0;
        int bitsInBuffer = 0;
        
        // Lê todos os bytes exceto os últimos 2 (que contêm padding info)
        byte[] allBytes = new byte[data.length - 4 - 2];
        dis.readFully(allBytes);
        
        int paddingBits = dis.readByte() & 0xFF;
        dis.readByte(); // Ignora último byte
        
        // Processa bytes
        for (byte b : allBytes) {
            bitBuffer = (bitBuffer << 8) | (b & 0xFF);
            bitsInBuffer += 8;
            
            while (bitsInBuffer >= 12 && codes.size() < codeCount) {
                int code = (bitBuffer >> (bitsInBuffer - 12)) & 0xFFF;
                codes.add(code);
                bitsInBuffer -= 12;
            }
        }
        
        // Processa último byte se necessário
        if (bitsInBuffer > 0 && codes.size() < codeCount) {
            // Ajusta para o padding
            int remainingBits = bitsInBuffer - (8 - paddingBits);
            if (remainingBits >= 12) {
                int code = (bitBuffer >> (bitsInBuffer - 12)) & 0xFFF;
                codes.add(code);
            }
        }
        
        return codes;
    }
}

