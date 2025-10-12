import java.util.ArrayList;
import java.util.List;

/**
 * Implementação de Árvore B+ para índices primários
 * Utilizada para manter os registros ordenados por chave primária
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

class Registro {
    int chave;
    Object dados;
    
    public Registro(int chave, Object dados) {
        this.chave = chave;
        this.dados = dados;
    }
}

public class ArvoreBPlus {
    private NoBPlus raiz;
    private int ordem; // Ordem da árvore B+
    private int proximaChave;
    
    public ArvoreBPlus(int ordem) {
        this.raiz = new NoBPlus(true);
        this.ordem = ordem;
        this.proximaChave = 1;
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
        
        if (folha.chaves.size() > ordem) {
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
        
        if (pai.chaves.size() > ordem) {
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
                return true;
            }
            return false;
        }
        
        int indice = encontrarIndiceInsercao(no, chave);
        return removerRecursivo(no.filhos.get(indice), chave);
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
