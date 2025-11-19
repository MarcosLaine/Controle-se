import java.io.*;
import java.util.*;

/**
 * Implementação do algoritmo de compressão Huffman
 */
public class HuffmanCompression {
    
    /**
     * Nó da árvore de Huffman
     */
    private static class HuffmanNode implements Comparable<HuffmanNode> {
        byte data;
        int frequency;
        HuffmanNode left;
        HuffmanNode right;
        
        HuffmanNode(byte data, int frequency) {
            this.data = data;
            this.frequency = frequency;
            this.left = null;
            this.right = null;
        }
        
        HuffmanNode(HuffmanNode left, HuffmanNode right) {
            this.data = 0;
            this.frequency = left.frequency + right.frequency;
            this.left = left;
            this.right = right;
        }
        
        boolean isLeaf() {
            return left == null && right == null;
        }
        
        @Override
        public int compareTo(HuffmanNode other) {
            return Integer.compare(this.frequency, other.frequency);
        }
    }
    
    /**
     * Comprime um array de bytes usando Huffman
     */
    public static byte[] compress(byte[] data) throws IOException {
        if (data.length == 0) {
            return new byte[0];
        }
        
        // 1. Calcula frequência de cada byte
        Map<Byte, Integer> frequencyMap = new HashMap<>();
        for (byte b : data) {
            frequencyMap.put(b, frequencyMap.getOrDefault(b, 0) + 1);
        }
        
        // 2. Constrói árvore de Huffman
        HuffmanNode root = buildHuffmanTree(frequencyMap);
        
        // 3. Gera códigos Huffman para cada byte
        Map<Byte, String> codes = new HashMap<>();
        generateCodes(root, "", codes);
        
        // 4. Serializa a árvore (necessária para descompressão)
        ByteArrayOutputStream treeStream = new ByteArrayOutputStream();
        serializeTree(root, treeStream);
        byte[] treeBytes = treeStream.toByteArray();
        
        // 5. Codifica os dados usando os códigos Huffman
        StringBuilder encoded = new StringBuilder();
        for (byte b : data) {
            encoded.append(codes.get(b));
        }
        
        // 6. Converte string binária para bytes
        byte[] encodedBytes = binaryStringToBytes(encoded.toString());
        
        // 7. Monta o arquivo comprimido:
        // [tamanho da árvore (4 bytes)][árvore][tamanho dos dados codificados (4 bytes)][dados codificados]
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(output);
        
        dos.writeInt(treeBytes.length);
        dos.write(treeBytes);
        dos.writeInt(encodedBytes.length);
        dos.write(encodedBytes);
        dos.writeInt(encoded.length()); // Tamanho original em bits (para padding)
        
        return output.toByteArray();
    }
    
    /**
     * Descomprime um array de bytes usando Huffman
     */
    public static byte[] decompress(byte[] compressed) throws IOException {
        if (compressed.length == 0) {
            return new byte[0];
        }
        
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(compressed));
        
        // 1. Lê tamanho da árvore
        int treeSize = dis.readInt();
        
        // 2. Lê árvore de Huffman
        byte[] treeBytes = new byte[treeSize];
        dis.readFully(treeBytes);
        HuffmanNode root = deserializeTree(new DataInputStream(new ByteArrayInputStream(treeBytes)));
        
        // 3. Lê tamanho dos dados codificados
        int encodedSize = dis.readInt();
        
        // 4. Lê dados codificados
        byte[] encodedBytes = new byte[encodedSize];
        dis.readFully(encodedBytes);
        
        // 5. Lê tamanho original em bits
        int originalBitLength = dis.readInt();
        
        // 6. Converte bytes para string binária
        String encoded = bytesToBinaryString(encodedBytes, originalBitLength);
        
        // 7. Decodifica usando a árvore de Huffman
        List<Byte> decoded = new ArrayList<>();
        HuffmanNode current = root;
        
        for (char bit : encoded.toCharArray()) {
            if (bit == '0') {
                current = current.left;
            } else {
                current = current.right;
            }
            
            if (current.isLeaf()) {
                decoded.add(current.data);
                current = root;
            }
        }
        
        // Converte lista para array
        byte[] result = new byte[decoded.size()];
        for (int i = 0; i < decoded.size(); i++) {
            result[i] = decoded.get(i);
        }
        
        return result;
    }
    
    /**
     * Constrói a árvore de Huffman a partir do mapa de frequências
     */
    private static HuffmanNode buildHuffmanTree(Map<Byte, Integer> frequencyMap) {
        PriorityQueue<HuffmanNode> queue = new PriorityQueue<>();
        
        // Cria nós folha para cada byte
        for (Map.Entry<Byte, Integer> entry : frequencyMap.entrySet()) {
            queue.offer(new HuffmanNode(entry.getKey(), entry.getValue()));
        }
        
        // Se houver apenas um tipo de byte, cria um nó auxiliar
        if (queue.size() == 1) {
            HuffmanNode node = queue.poll();
            queue.offer(new HuffmanNode(node, new HuffmanNode((byte)0, 0)));
        }
        
        // Constrói a árvore combinando os dois nós com menor frequência
        while (queue.size() > 1) {
            HuffmanNode left = queue.poll();
            HuffmanNode right = queue.poll();
            queue.offer(new HuffmanNode(left, right));
        }
        
        return queue.poll();
    }
    
    /**
     * Gera os códigos Huffman percorrendo a árvore
     */
    private static void generateCodes(HuffmanNode node, String code, Map<Byte, String> codes) {
        if (node.isLeaf()) {
            if (code.isEmpty()) {
                // Caso especial: apenas um tipo de byte
                codes.put(node.data, "0");
            } else {
                codes.put(node.data, code);
            }
        } else {
            if (node.left != null) {
                generateCodes(node.left, code + "0", codes);
            }
            if (node.right != null) {
                generateCodes(node.right, code + "1", codes);
            }
        }
    }
    
    /**
     * Serializa a árvore de Huffman para bytes
     */
    private static void serializeTree(HuffmanNode node, OutputStream out) throws IOException {
        if (node.isLeaf()) {
            out.write(1); // Marca como folha
            out.write(node.data);
        } else {
            out.write(0); // Marca como nó interno
            serializeTree(node.left, out);
            serializeTree(node.right, out);
        }
    }
    
    /**
     * Deserializa a árvore de Huffman a partir de bytes
     */
    private static HuffmanNode deserializeTree(DataInputStream in) throws IOException {
        int marker = in.readByte();
        if (marker == 1) {
            // É uma folha
            byte data = in.readByte();
            return new HuffmanNode(data, 0);
        } else {
            // É um nó interno
            HuffmanNode left = deserializeTree(in);
            HuffmanNode right = deserializeTree(in);
            return new HuffmanNode(left, right);
        }
    }
    
    /**
     * Converte string binária para array de bytes
     */
    private static byte[] binaryStringToBytes(String binary) {
        int length = binary.length();
        int byteCount = (length + 7) / 8;
        byte[] bytes = new byte[byteCount];
        
        for (int i = 0; i < length; i++) {
            if (binary.charAt(i) == '1') {
                bytes[i / 8] |= (1 << (7 - (i % 8)));
            }
        }
        
        return bytes;
    }
    
    /**
     * Converte array de bytes para string binária
     */
    private static String bytesToBinaryString(byte[] bytes, int bitLength) {
        StringBuilder sb = new StringBuilder();
        
        for (int i = 0; i < bytes.length && sb.length() < bitLength; i++) {
            for (int j = 7; j >= 0 && sb.length() < bitLength; j--) {
                if ((bytes[i] & (1 << j)) != 0) {
                    sb.append('1');
                } else {
                    sb.append('0');
                }
            }
        }
        
        return sb.toString();
    }
}

