package server.model;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Arrays;

public class Gasto implements Serializable {
    private static final long serialVersionUID = 6L;
    private int idGasto;
    private String descricao;
    private double valor;
    private LocalDate data;
    private String frequencia;
    private int idCategoria;
    private int idConta;
    private int idUsuario;
    private boolean ativo;
    private LocalDate proximaRecorrencia;
    private int idGastoOriginal;
    private String[] observacoes;
    
    public Gasto(int idGasto, String descricao, double valor, LocalDate data, String frequencia, int idUsuario, int idCategoria, int idConta) {
        this.idGasto = idGasto;
        this.descricao = descricao;
        this.valor = valor;
        this.data = data;
        this.frequencia = frequencia;
        this.idUsuario = idUsuario;
        this.idCategoria = idCategoria;
        this.idConta = idConta;
        this.ativo = true;
        this.idGastoOriginal = 0;
        this.proximaRecorrencia = calcularProximaRecorrencia(data, frequencia);
        this.observacoes = new String[0];
    }
    
    public Gasto(int idGasto, String descricao, double valor, LocalDate data, String frequencia, int idUsuario, int idCategoria, int idConta, String[] observacoes) {
        this(idGasto, descricao, valor, data, frequencia, idUsuario, idCategoria, idConta);
        this.observacoes = observacoes != null ? observacoes : new String[0];
    }
    
    public int getIdGasto() { return idGasto; }
    public void setIdGasto(int idGasto) { this.idGasto = idGasto; }
    
    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }
    
    public double getValor() { return valor; }
    public void setValor(double valor) { this.valor = valor; }
    
    public LocalDate getData() { return data; }
    public void setData(LocalDate data) { this.data = data; }
    
    public String getFrequencia() { return frequencia; }
    public void setFrequencia(String frequencia) { this.frequencia = frequencia; }
    
    public int getIdCategoria() { return idCategoria; }
    public void setIdCategoria(int idCategoria) { this.idCategoria = idCategoria; }
    
    public int getIdConta() { return idConta; }
    public void setIdConta(int idConta) { this.idConta = idConta; }
    
    public int getIdUsuario() { return idUsuario; }
    public void setIdUsuario(int idUsuario) { this.idUsuario = idUsuario; }
    
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
    
    public LocalDate getProximaRecorrencia() { return proximaRecorrencia; }
    public void setProximaRecorrencia(LocalDate proximaRecorrencia) { this.proximaRecorrencia = proximaRecorrencia; }
    
    public int getIdGastoOriginal() { return idGastoOriginal; }
    public void setIdGastoOriginal(int idGastoOriginal) { this.idGastoOriginal = idGastoOriginal; }
    
    public String[] getObservacoes() { return observacoes; }
    public void setObservacoes(String[] observacoes) { this.observacoes = observacoes; }
    
    public void adicionarObservacao(String observacao) {
        if (observacao != null && !observacao.trim().isEmpty()) {
            String[] novasObservacoes = new String[observacoes.length + 1];
            System.arraycopy(observacoes, 0, novasObservacoes, 0, observacoes.length);
            novasObservacoes[observacoes.length] = observacao.trim();
            this.observacoes = novasObservacoes;
        }
    }
    
    public String getObservacoesComoTexto() {
        if (observacoes == null || observacoes.length == 0) {
            return "";
        }
        return String.join("; ", observacoes);
    }
    
    private LocalDate calcularProximaRecorrencia(LocalDate dataBase, String freq) {
        if (freq == null || freq.equals("Único")) {
            return null;
        }
        switch (freq) {
            case "Semanal": return dataBase.plusWeeks(1);
            case "Mensal": return dataBase.plusMonths(1);
            case "Anual": return dataBase.plusYears(1);
            default: return null;
        }
    }
    
    public void avancarProximaRecorrencia() {
        if (this.proximaRecorrencia != null && !this.frequencia.equals("Único")) {
            this.proximaRecorrencia = calcularProximaRecorrencia(this.proximaRecorrencia, this.frequencia);
        }
    }
    
    @Override
    public String toString() {
        return "Gasto{id=" + idGasto + ", descricao='" + descricao + "', valor=" + valor + 
               ", data=" + data + ", frequencia='" + frequencia + "', categoria=" + idCategoria + 
               ", conta=" + idConta + ", usuario=" + idUsuario + ", observacoes=" + 
               (observacoes != null ? Arrays.toString(observacoes) : "[]") + "}";
    }
}

