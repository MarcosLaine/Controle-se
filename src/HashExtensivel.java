import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementação de Hash Extensível com buckets para relacionamentos 1:N
 * Utilizada para mapear chaves estrangeiras para listas de registros relacionados
 */
class Bucket {
    private List<EntradaHash> entradas;
    private int profundidadeLocal;
    
    public Bucket(int profundidadeLocal) {
        this.entradas = new ArrayList<>();
        this.profundidadeLocal = profundidadeLocal;
    }
    
    public void adicionarEntrada(int chave, Object valor) {
        entradas.add(new EntradaHash(chave, valor));
    }
    
    public void removerEntrada(int chave) {
        entradas.removeIf(entrada -> entrada.chave == chave);
    }
    
    /**
     * Remove uma entrada específica (chave + valor)
     * Útil para remover entradas específicas quando o valor muda
     */
    public boolean removerEntradaEspecifica(int chave, Object valor) {
        return entradas.removeIf(entrada -> entrada.chave == chave && entrada.valor.equals(valor));
    }
    
    public List<Object> buscarValores(int chave) {
        List<Object> valores = new ArrayList<>();
        for (EntradaHash entrada : entradas) {
            if (entrada.chave == chave) {
                valores.add(entrada.valor);
            }
        }
        return valores;
    }
    
    public boolean contemChave(int chave) {
        return entradas.stream().anyMatch(entrada -> entrada.chave == chave);
    }
    
    public List<EntradaHash> getEntradas() {
        return new ArrayList<>(entradas);
    }
    
    public void limpar() {
        entradas.clear();
    }
    
    public int getProfundidadeLocal() {
        return profundidadeLocal;
    }
    
    public void setProfundidadeLocal(int profundidadeLocal) {
        this.profundidadeLocal = profundidadeLocal;
    }
    
    public boolean estaCheio(int capacidadeMaxima) {
        return entradas.size() >= capacidadeMaxima;
    }
    
    public int tamanho() {
        return entradas.size();
    }
}

class EntradaHash implements Serializable {
    private static final long serialVersionUID = 1L;
    int chave;
    Object valor;
    
    public EntradaHash(int chave, Object valor) {
        this.chave = chave;
        this.valor = valor;
    }
    
    public int getChave() {
        return chave;
    }
    
    public Object getValor() {
        return valor;
    }
}

public class HashExtensivel {
    private List<Bucket> diretorio;
    private int profundidadeGlobal;
    private int capacidadeMaximaBucket;
    
    public HashExtensivel(int capacidadeMaximaBucket) {
        this.profundidadeGlobal = 1;
        this.capacidadeMaximaBucket = capacidadeMaximaBucket;
        this.diretorio = new ArrayList<>();
        
        // Inicializa com 2 buckets
        diretorio.add(new Bucket(1));
        diretorio.add(new Bucket(1));
    }
    
    // Métodos públicos
    public void inserir(int chave, Object valor) {
        int indiceBucket = calcularIndiceBucket(chave);
        Bucket bucket = diretorio.get(indiceBucket);
        
        if (bucket.estaCheio(capacidadeMaximaBucket)) {
            if (bucket.getProfundidadeLocal() == profundidadeGlobal) {
                expandirDiretorio();
                indiceBucket = calcularIndiceBucket(chave);
                bucket = diretorio.get(indiceBucket);
            }
            
            dividirBucket(bucket, indiceBucket);
            indiceBucket = calcularIndiceBucket(chave);
            bucket = diretorio.get(indiceBucket);
        }
        
        bucket.adicionarEntrada(chave, valor);
    }
    
    public List<Object> buscar(int chave) {
        int indiceBucket = calcularIndiceBucket(chave);
        Bucket bucket = diretorio.get(indiceBucket);
        return bucket.buscarValores(chave);
    }
    
    public boolean remover(int chave) {
        int indiceBucket = calcularIndiceBucket(chave);
        Bucket bucket = diretorio.get(indiceBucket);
        
        if (bucket.contemChave(chave)) {
            bucket.removerEntrada(chave);
            return true;
        }
        
        return false;
    }
    
    /**
     * Remove uma entrada específica (chave + valor)
     * Útil para atualizar índices quando atributos mudam
     */
    public boolean removerEspecifico(int chave, Object valor) {
        int indiceBucket = calcularIndiceBucket(chave);
        Bucket bucket = diretorio.get(indiceBucket);
        return bucket.removerEntradaEspecifica(chave, valor);
    }
    
    public boolean contemChave(int chave) {
        int indiceBucket = calcularIndiceBucket(chave);
        Bucket bucket = diretorio.get(indiceBucket);
        return bucket.contemChave(chave);
    }
    
    public int tamanho() {
        int total = 0;
        for (Bucket bucket : diretorio) {
            total += bucket.tamanho();
        }
        return total;
    }
    
    public boolean vazia() {
        return tamanho() == 0;
    }
    
    public void imprimir() {
        System.out.println("=== HASH EXTENSÍVEL ===");
        System.out.println("Profundidade Global: " + profundidadeGlobal);
        System.out.println("Número de Buckets: " + diretorio.size());
        
        for (int i = 0; i < diretorio.size(); i++) {
            Bucket bucket = diretorio.get(i);
            System.out.print("Bucket " + i + " (prof. local: " + bucket.getProfundidadeLocal() + "): ");
            
            if (bucket.tamanho() == 0) {
                System.out.print("vazio");
            } else {
                for (EntradaHash entrada : bucket.getEntradas()) {
                    System.out.print("(" + entrada.chave + ", " + entrada.valor + ") ");
                }
            }
            System.out.println();
        }
    }
    
    public List<Object> listarTodos() {
        List<Object> resultado = new ArrayList<>();
        for (Bucket bucket : diretorio) {
            for (EntradaHash entrada : bucket.getEntradas()) {
                resultado.add(entrada.valor);
            }
        }
        return resultado;
    }
    
    /**
     * Retorna todas as entradas (chave, valor) para serialização
     */
    public List<EntradaHash> listarEntradas() {
        List<EntradaHash> resultado = new ArrayList<>();
        for (Bucket bucket : diretorio) {
            resultado.addAll(bucket.getEntradas());
        }
        return resultado;
    }
    
    // Métodos privados
    private int calcularIndiceBucket(int chave) {
        int mascara = (1 << profundidadeGlobal) - 1;
        return chave & mascara;
    }
    
    private void expandirDiretorio() {
        int tamanhoAnterior = diretorio.size();
        profundidadeGlobal++;
        
        // Duplica o diretório
        for (int i = 0; i < tamanhoAnterior; i++) {
            Bucket bucketOriginal = diretorio.get(i);
            Bucket novoBucket = new Bucket(bucketOriginal.getProfundidadeLocal());
            
            // Copia as entradas para o novo bucket
            for (EntradaHash entrada : bucketOriginal.getEntradas()) {
                novoBucket.adicionarEntrada(entrada.chave, entrada.valor);
            }
            
            diretorio.add(novoBucket);
        }
    }
    
    private void dividirBucket(Bucket bucketOriginal, int indiceBucket) {
        int novaProfundidadeLocal = bucketOriginal.getProfundidadeLocal() + 1;
        
        // Cria novo bucket
        Bucket novoBucket = new Bucket(novaProfundidadeLocal);
        
        // Salva todas as entradas antes de limpar
        List<EntradaHash> todasEntradas = new ArrayList<>(bucketOriginal.getEntradas());
        bucketOriginal.limpar();
        
        // Atualiza profundidade do bucket original
        bucketOriginal.setProfundidadeLocal(novaProfundidadeLocal);
        
        // Redistribui as entradas entre os dois buckets
        for (EntradaHash entrada : todasEntradas) {
            int novoIndice = calcularIndiceBucket(entrada.chave);
            if (novoIndice == indiceBucket) {
                bucketOriginal.adicionarEntrada(entrada.chave, entrada.valor);
            } else {
                novoBucket.adicionarEntrada(entrada.chave, entrada.valor);
            }
        }
        
        // Atualiza todas as posições do diretório que devem apontar para o novo bucket
        int step = 1 << novaProfundidadeLocal;
        for (int i = 0; i < diretorio.size(); i++) {
            if (diretorio.get(i) == bucketOriginal) {
                int bit = (i >> (novaProfundidadeLocal - 1)) & 1;
                if (bit == 1) {
                    diretorio.set(i, novoBucket);
                }
            }
        }
    }
}
