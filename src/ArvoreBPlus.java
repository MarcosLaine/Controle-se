import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementação de Árvore B+ para índices primários
 * Utilizada para manter os registros ordenados por chave primária
 * 
 * DEFINIÇÃO DE ORDEM:
 * - ordem m = número máximo de FILHOS que um nó pode ter
 * - Número máximo de chaves por nó = m - 1
 * - Número mínimo de filhos (nós internos não-raiz) = ⌈m/2⌉
 * - Número mínimo de chaves (nós internos não-raiz) = ⌈m/2⌉ - 1
 * 
 * Exemplo: ordem = 4
 * - Máximo de 4 filhos
 * - Máximo de 3 chaves
 * - Mínimo de 2 filhos (nós internos)
 * - Mínimo de 1 chave (nós internos)
 */
class NoBPlus {
    boolean ehFolha;
    List<Integer> chaves;
    List<NoBPlus> filhos;
    List<Registro> registros; // Apenas para nós folha
    NoBPlus proximo; // Para navegação sequencial nas folhas
    NoBPlus pai;
    
    public NoBPlus(boolean ehFolha) {
        this.ehFolha = ehFolha;
        this.chaves = new ArrayList<>();
        this.filhos = new ArrayList<>();
        this.registros = new ArrayList<>();
        this.proximo = null;
        this.pai = null;
    }
}

class Registro implements Serializable {
    private static final long serialVersionUID = 1L;
    int chave;
    Object dados;
    
    public Registro(int chave, Object dados) {
        this.chave = chave;
        this.dados = dados;
    }
    
    public int getChave() {
        return chave;
    }
    
    public Object getDados() {
        return dados;
    }
}

public class ArvoreBPlus {
    private NoBPlus raiz;
    private final int ordem; // Ordem da árvore B+ (número máximo de filhos)
    private final int maxChaves; // Número máximo de chaves = ordem - 1
    private int proximaChave;
    
    public ArvoreBPlus(int ordem) {
        this.raiz = new NoBPlus(true);
        this.ordem = ordem;
        this.maxChaves = ordem - 1;
        this.proximaChave = 1;
    }
    
    // Getter para ordem (útil para informações sobre a árvore)
    public int getOrdem() {
        return ordem;
    }
    
    // Métodos públicos
    public void inserir(Object dados) {
        inserir(proximaChave++, dados);
    }
    
    public void inserir(int chave, Object dados) {
        Registro novoRegistro = new Registro(chave, dados);
        
        if (raiz.chaves.isEmpty()) {
            // Primeira inserção
            raiz.chaves.add(chave);
            raiz.registros.add(novoRegistro);
            return;
        }
        
        inserirRecursivo(raiz, chave, novoRegistro);
    }
    
    public Object buscar(int chave) {
        NoBPlus no = buscarNo(raiz, chave);
        if (no != null) {
            int indice = no.chaves.indexOf(chave);
            if (indice != -1) {
                return no.registros.get(indice).dados;
            }
        }
        return null;
    }
    
    public boolean remover(int chave) {
        return removerRecursivo(raiz, chave);
    }
    
    public List<Object> buscarIntervalo(int chaveInicio, int chaveFim) {
        List<Object> resultado = new ArrayList<>();
        NoBPlus noInicio = buscarNo(raiz, chaveInicio);
        
        if (noInicio == null) return resultado;
        
        int indiceInicio = encontrarIndice(noInicio, chaveInicio);
        if (indiceInicio == -1) return resultado;
        
        NoBPlus atual = noInicio;
        int indiceAtual = indiceInicio;
        
        while (atual != null) {
            while (indiceAtual < atual.chaves.size()) {
                int chaveAtual = atual.chaves.get(indiceAtual);
                if (chaveAtual > chaveFim) {
                    return resultado;
                }
                if (chaveAtual >= chaveInicio) {
                    resultado.add(atual.registros.get(indiceAtual).dados);
                }
                indiceAtual++;
            }
            
            atual = atual.proximo;
            indiceAtual = 0;
        }
        
        return resultado;
    }
    
    public List<Object> listarTodos() {
        List<Object> resultado = new ArrayList<>();
        NoBPlus atual = encontrarPrimeiraFolha();
        
        while (atual != null) {
            for (Registro registro : atual.registros) {
                resultado.add(registro.dados);
            }
            atual = atual.proximo;
        }
        
        return resultado;
    }
    
    /**
     * Retorna todos os pares (chave, dados) para serialização
     */
    public List<Registro> listarRegistros() {
        List<Registro> resultado = new ArrayList<>();
        NoBPlus atual = encontrarPrimeiraFolha();
        
        while (atual != null) {
            resultado.addAll(atual.registros);
            atual = atual.proximo;
        }
        
        return resultado;
    }
    
    public int tamanho() {
        int total = 0;
        NoBPlus atual = encontrarPrimeiraFolha();
        
        while (atual != null) {
            total += atual.registros.size();
            atual = atual.proximo;
        }
        
        return total;
    }
    
    public boolean vazia() {
        return tamanho() == 0;
    }
    
    public void imprimir() {
        System.out.println("=== ÁRVORE B+ ===");
        imprimirRecursivo(raiz, 0);
    }
    
    // Métodos privados
    private void inserirRecursivo(NoBPlus no, int chave, Registro registro) {
        if (no.ehFolha) {
            inserirEmFolha(no, chave, registro);
        } else {
            int indice = encontrarIndiceInsercao(no, chave);
            inserirRecursivo(no.filhos.get(indice), chave, registro);
        }
    }
    
    private void inserirEmFolha(NoBPlus folha, int chave, Registro registro) {
        int indice = encontrarIndiceInsercao(folha, chave);
        folha.chaves.add(indice, chave);
        folha.registros.add(indice, registro);
        
        // Divide se exceder o número máximo de chaves (ordem - 1)
        if (folha.chaves.size() > maxChaves) {
            dividirFolha(folha);
        }
    }
    
    private void dividirFolha(NoBPlus folha) {
        int meio = folha.chaves.size() / 2;
        
        NoBPlus novaFolha = new NoBPlus(true);
        
        // Move metade dos elementos para a nova folha
        for (int i = meio; i < folha.chaves.size(); i++) {
            novaFolha.chaves.add(folha.chaves.get(i));
            novaFolha.registros.add(folha.registros.get(i));
        }
        
        // Remove elementos movidos da folha original
        for (int i = folha.chaves.size() - 1; i >= meio; i--) {
            folha.chaves.remove(i);
            folha.registros.remove(i);
        }
        
        // Atualiza ponteiros
        novaFolha.proximo = folha.proximo;
        folha.proximo = novaFolha;
        novaFolha.pai = folha.pai;
        
        if (folha.pai == null) {
            // Criar nova raiz
            NoBPlus novaRaiz = new NoBPlus(false);
            novaRaiz.chaves.add(novaFolha.chaves.get(0));
            novaRaiz.filhos.add(folha);
            novaRaiz.filhos.add(novaFolha);
            folha.pai = novaRaiz;
            novaFolha.pai = novaRaiz;
            raiz = novaRaiz;
        } else {
            inserirChaveNoPai(folha.pai, novaFolha.chaves.get(0), novaFolha);
        }
    }
    
    private void inserirChaveNoPai(NoBPlus pai, int chave, NoBPlus novoFilho) {
        int indice = encontrarIndiceInsercao(pai, chave);
        pai.chaves.add(indice, chave);
        pai.filhos.add(indice + 1, novoFilho);
        novoFilho.pai = pai;
        
        // Divide se exceder o número máximo de chaves (ordem - 1)
        if (pai.chaves.size() > maxChaves) {
            dividirNoInterno(pai);
        }
    }
    
    private void dividirNoInterno(NoBPlus no) {
        int meio = no.chaves.size() / 2;
        int chavePromovida = no.chaves.get(meio);
        
        NoBPlus novoNo = new NoBPlus(false);
        
        // Move metade dos elementos para o novo nó
        for (int i = meio + 1; i < no.chaves.size(); i++) {
            novoNo.chaves.add(no.chaves.get(i));
        }
        for (int i = meio + 1; i < no.filhos.size(); i++) {
            novoNo.filhos.add(no.filhos.get(i));
            no.filhos.get(i).pai = novoNo;
        }
        
        // Remove elementos movidos do nó original
        for (int i = no.chaves.size() - 1; i >= meio; i--) {
            no.chaves.remove(i);
        }
        for (int i = no.filhos.size() - 1; i >= meio + 1; i--) {
            no.filhos.remove(i);
        }
        
        novoNo.pai = no.pai;
        
        if (no.pai == null) {
            // Criar nova raiz
            NoBPlus novaRaiz = new NoBPlus(false);
            novaRaiz.chaves.add(chavePromovida);
            novaRaiz.filhos.add(no);
            novaRaiz.filhos.add(novoNo);
            no.pai = novaRaiz;
            novoNo.pai = novaRaiz;
            raiz = novaRaiz;
        } else {
            inserirChaveNoPai(no.pai, chavePromovida, novoNo);
        }
    }
    
    private NoBPlus buscarNo(NoBPlus no, int chave) {
        if (no.ehFolha) {
            return no.chaves.contains(chave) ? no : null;
        }
        
        int indice = encontrarIndiceInsercao(no, chave);
        return buscarNo(no.filhos.get(indice), chave);
    }
    
    private int encontrarIndiceInsercao(NoBPlus no, int chave) {
        int indice = 0;
        while (indice < no.chaves.size() && chave > no.chaves.get(indice)) {
            indice++;
        }
        return indice;
    }
    
    private int encontrarIndice(NoBPlus no, int chave) {
        return no.chaves.indexOf(chave);
    }
    
    private NoBPlus encontrarPrimeiraFolha() {
        NoBPlus atual = raiz;
        while (!atual.ehFolha) {
            atual = atual.filhos.get(0);
        }
        return atual;
    }
    
    private boolean removerRecursivo(NoBPlus no, int chave) {
        if (no.ehFolha) {
            int indice = no.chaves.indexOf(chave);
            if (indice != -1) {
                no.chaves.remove(indice);
                no.registros.remove(indice);
                
                // Trata underflow em folha (exceto raiz)
                if (no.pai != null && no.chaves.size() < 1 && raiz != no) {
                    tratarUnderflowFolha(no);
                }
                
                return true;
            }
            return false;
        }
        
        // Nó interno: encontra o filho apropriado
        int indice = encontrarIndiceInsercao(no, chave);
        boolean removido = removerRecursivo(no.filhos.get(indice), chave);
        
        if (removido) {
            // Atualiza chave no nó interno se necessário
            if (indice < no.chaves.size() && no.filhos.get(indice).ehFolha) {
                NoBPlus filho = no.filhos.get(indice);
                if (!filho.chaves.isEmpty()) {
                    // Atualiza a chave de roteamento no nó interno
                    if (indice > 0) {
                        no.chaves.set(indice - 1, filho.chaves.get(0));
                    }
                }
            }
            
            // Trata underflow em nó interno (exceto raiz)
            int minChaves = (ordem / 2) - 1; // Mínimo: ⌈ordem/2⌉ - 1
            if (no.pai != null && no.chaves.size() < minChaves && raiz != no) {
                tratarUnderflowInterno(no);
            }
            
            // Se a raiz ficou com apenas 1 filho, reduz a altura
            if (raiz == no && !raiz.ehFolha && raiz.chaves.isEmpty() && raiz.filhos.size() == 1) {
                raiz = raiz.filhos.get(0);
                raiz.pai = null;
            }
        }
        
        return removido;
    }
    
    /**
     * Trata underflow em nó folha através de empréstimo ou fusão
     */
    private void tratarUnderflowFolha(NoBPlus folha) {
        if (folha.pai == null) return; // Raiz pode ter menos elementos
        
        NoBPlus pai = folha.pai;
        int indiceNoPai = encontrarIndiceFilhoNoPai(pai, folha);
        
        // Tenta emprestar do irmão esquerdo
        if (indiceNoPai > 0) {
            NoBPlus irmaoEsquerdo = pai.filhos.get(indiceNoPai - 1);
            if (irmaoEsquerdo.chaves.size() > 1) {
                // Empresta do irmão esquerdo
                int ultimaChaveIrmao = irmaoEsquerdo.chaves.remove(irmaoEsquerdo.chaves.size() - 1);
                Registro ultimoRegistroIrmao = irmaoEsquerdo.registros.remove(irmaoEsquerdo.registros.size() - 1);
                folha.chaves.add(0, ultimaChaveIrmao);
                folha.registros.add(0, ultimoRegistroIrmao);
                // Atualiza chave no pai
                if (indiceNoPai > 0) {
                    pai.chaves.set(indiceNoPai - 1, folha.chaves.get(0));
                }
                return;
            }
        }
        
        // Tenta emprestar do irmão direito
        if (indiceNoPai < pai.filhos.size() - 1) {
            NoBPlus irmaoDireito = pai.filhos.get(indiceNoPai + 1);
            if (irmaoDireito.chaves.size() > 1) {
                // Empresta do irmão direito
                int primeiraChaveIrmao = irmaoDireito.chaves.remove(0);
                Registro primeiroRegistroIrmao = irmaoDireito.registros.remove(0);
                folha.chaves.add(primeiraChaveIrmao);
                folha.registros.add(primeiroRegistroIrmao);
                // Atualiza chave no pai
                if (indiceNoPai < pai.chaves.size()) {
                    pai.chaves.set(indiceNoPai, irmaoDireito.chaves.get(0));
                }
                return;
            }
        }
        
        // Se não pode emprestar, faz fusão com irmão esquerdo
        if (indiceNoPai > 0) {
            NoBPlus irmaoEsquerdo = pai.filhos.get(indiceNoPai - 1);
            // Move todos os elementos para o irmão esquerdo
            irmaoEsquerdo.chaves.addAll(folha.chaves);
            irmaoEsquerdo.registros.addAll(folha.registros);
            irmaoEsquerdo.proximo = folha.proximo;
            // Remove do pai
            pai.filhos.remove(indiceNoPai);
            if (indiceNoPai > 0) {
                pai.chaves.remove(indiceNoPai - 1);
            }
        } else if (indiceNoPai < pai.filhos.size() - 1) {
            // Fusão com irmão direito
            NoBPlus irmaoDireito = pai.filhos.get(indiceNoPai + 1);
            folha.chaves.addAll(irmaoDireito.chaves);
            folha.registros.addAll(irmaoDireito.registros);
            folha.proximo = irmaoDireito.proximo;
            // Remove do pai
            pai.filhos.remove(indiceNoPai + 1);
            if (indiceNoPai < pai.chaves.size()) {
                pai.chaves.remove(indiceNoPai);
            }
        }
    }
    
    /**
     * Trata underflow em nó interno através de empréstimo ou fusão
     */
    private void tratarUnderflowInterno(NoBPlus no) {
        if (no.pai == null) return; // Raiz pode ter menos elementos
        
        NoBPlus pai = no.pai;
        int indiceNoPai = encontrarIndiceFilhoNoPai(pai, no);
        int minChaves = (ordem / 2) - 1;
        
        // Tenta emprestar do irmão esquerdo
        if (indiceNoPai > 0) {
            NoBPlus irmaoEsquerdo = pai.filhos.get(indiceNoPai - 1);
            if (irmaoEsquerdo.chaves.size() > minChaves) {
                // Empresta do irmão esquerdo
                int chavePai = pai.chaves.get(indiceNoPai - 1);
                int ultimaChaveIrmao = irmaoEsquerdo.chaves.remove(irmaoEsquerdo.chaves.size() - 1);
                NoBPlus ultimoFilhoIrmao = irmaoEsquerdo.filhos.remove(irmaoEsquerdo.filhos.size() - 1);
                ultimoFilhoIrmao.pai = no;
                
                no.chaves.add(0, chavePai);
                no.filhos.add(0, ultimoFilhoIrmao);
                pai.chaves.set(indiceNoPai - 1, ultimaChaveIrmao);
                return;
            }
        }
        
        // Tenta emprestar do irmão direito
        if (indiceNoPai < pai.filhos.size() - 1) {
            NoBPlus irmaoDireito = pai.filhos.get(indiceNoPai + 1);
            if (irmaoDireito.chaves.size() > minChaves) {
                // Empresta do irmão direito
                int chavePai = pai.chaves.get(indiceNoPai);
                int primeiraChaveIrmao = irmaoDireito.chaves.remove(0);
                NoBPlus primeiroFilhoIrmao = irmaoDireito.filhos.remove(0);
                primeiroFilhoIrmao.pai = no;
                
                no.chaves.add(chavePai);
                no.filhos.add(primeiroFilhoIrmao);
                pai.chaves.set(indiceNoPai, primeiraChaveIrmao);
                return;
            }
        }
        
        // Se não pode emprestar, faz fusão
        if (indiceNoPai > 0) {
            // Fusão com irmão esquerdo
            NoBPlus irmaoEsquerdo = pai.filhos.get(indiceNoPai - 1);
            int chavePai = pai.chaves.get(indiceNoPai - 1);
            irmaoEsquerdo.chaves.add(chavePai);
            irmaoEsquerdo.chaves.addAll(no.chaves);
            irmaoEsquerdo.filhos.addAll(no.filhos);
            for (NoBPlus filho : no.filhos) {
                filho.pai = irmaoEsquerdo;
            }
            pai.filhos.remove(indiceNoPai);
            pai.chaves.remove(indiceNoPai - 1);
        } else if (indiceNoPai < pai.filhos.size() - 1) {
            // Fusão com irmão direito
            NoBPlus irmaoDireito = pai.filhos.get(indiceNoPai + 1);
            int chavePai = pai.chaves.get(indiceNoPai);
            no.chaves.add(chavePai);
            no.chaves.addAll(irmaoDireito.chaves);
            no.filhos.addAll(irmaoDireito.filhos);
            for (NoBPlus filho : irmaoDireito.filhos) {
                filho.pai = no;
            }
            pai.filhos.remove(indiceNoPai + 1);
            pai.chaves.remove(indiceNoPai);
        }
    }
    
    /**
     * Encontra o índice de um filho no array de filhos do pai
     */
    private int encontrarIndiceFilhoNoPai(NoBPlus pai, NoBPlus filho) {
        for (int i = 0; i < pai.filhos.size(); i++) {
            if (pai.filhos.get(i) == filho) {
                return i;
            }
        }
        return -1;
    }
    
    private void imprimirRecursivo(NoBPlus no, int nivel) {
        if (no == null) return;
        
        System.out.print("Nível " + nivel + ": ");
        if (no.ehFolha) {
            System.out.print("Folha - ");
            for (int i = 0; i < no.chaves.size(); i++) {
                System.out.print("(" + no.chaves.get(i) + ", " + no.registros.get(i).dados + ") ");
            }
        } else {
            System.out.print("Interno - ");
            for (int i = 0; i < no.chaves.size(); i++) {
                System.out.print(no.chaves.get(i) + " ");
            }
        }
        System.out.println();
        
        if (!no.ehFolha) {
            for (NoBPlus filho : no.filhos) {
                imprimirRecursivo(filho, nivel + 1);
            }
        }
    }
}
