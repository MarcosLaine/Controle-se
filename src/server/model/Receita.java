package server.model;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Arrays;

public class Receita implements Serializable {
    private static final long serialVersionUID = 7L;
    private int idReceita;
    private String descricao;
    private double valor;
    private LocalDate data;
    private String frequencia;
    private int idConta;
    private int idUsuario;
    private boolean ativo;
    private LocalDate proximaRecorrencia;
    private int idReceitaOriginal;
    private String[] observacoes;
    private Integer idGrupoParcela;
    private Integer numeroParcela;
    private Integer totalParcelas;
    
    public Receita(int idReceita, String descricao, double valor, LocalDate data, int idUsuario, int idConta) {
        this(idReceita, descricao, valor, data, "Único", idUsuario, idConta);
    }
    
    public Receita(int idReceita, String descricao, double valor, LocalDate data, String frequencia, int idUsuario, int idConta) {
        this.idReceita = idReceita;
        this.descricao = descricao;
        this.valor = valor;
        this.data = data;
        this.frequencia = frequencia;
        this.idUsuario = idUsuario;
        this.idConta = idConta;
        this.ativo = true;
        this.idReceitaOriginal = 0;
        this.proximaRecorrencia = calcularProximaRecorrencia(data, frequencia);
        this.observacoes = new String[0];
    }
    
    public int getIdReceita() { return idReceita; }
    public void setIdReceita(int idReceita) { this.idReceita = idReceita; }
    
    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }
    
    public double getValor() { return valor; }
    public void setValor(double valor) { this.valor = valor; }
    
    public LocalDate getData() { return data; }
    public void setData(LocalDate data) { this.data = data; }
    
    public int getIdConta() { return idConta; }
    public void setIdConta(int idConta) { this.idConta = idConta; }
    
    public int getIdUsuario() { return idUsuario; }
    public void setIdUsuario(int idUsuario) { this.idUsuario = idUsuario; }
    
    public String getFrequencia() { return frequencia; }
    public void setFrequencia(String frequencia) { this.frequencia = frequencia; }
    
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
    
    public LocalDate getProximaRecorrencia() { return proximaRecorrencia; }
    public void setProximaRecorrencia(LocalDate proximaRecorrencia) { this.proximaRecorrencia = proximaRecorrencia; }
    
    public int getIdReceitaOriginal() { return idReceitaOriginal; }
    public void setIdReceitaOriginal(int idReceitaOriginal) { this.idReceitaOriginal = idReceitaOriginal; }
    
    public String[] getObservacoes() { return observacoes; }
    public void setObservacoes(String[] observacoes) { this.observacoes = observacoes != null ? observacoes : new String[0]; }
    
    public Integer getIdGrupoParcela() { return idGrupoParcela; }
    public void setIdGrupoParcela(Integer idGrupoParcela) { this.idGrupoParcela = idGrupoParcela; }
    
    public Integer getNumeroParcela() { return numeroParcela; }
    public void setNumeroParcela(Integer numeroParcela) { this.numeroParcela = numeroParcela; }
    
    public Integer getTotalParcelas() { return totalParcelas; }
    public void setTotalParcelas(Integer totalParcelas) { this.totalParcelas = totalParcelas; }
    
    public boolean isParcela() {
        return idGrupoParcela != null && numeroParcela != null && totalParcelas != null;
    }
    
    public void adicionarObservacao(String observacao) {
        if (observacao == null || observacao.trim().isEmpty()) return;
        if (this.observacoes == null) this.observacoes = new String[0];
        String[] novasObservacoes = new String[this.observacoes.length + 1];
        System.arraycopy(this.observacoes, 0, novasObservacoes, 0, this.observacoes.length);
        novasObservacoes[this.observacoes.length] = observacao.trim();
        this.observacoes = novasObservacoes;
    }
    
    public String getObservacoesConcatenadas() {
        if (observacoes == null || observacoes.length == 0) return "";
        return String.join("; ", observacoes);
    }
    
    private LocalDate calcularProximaRecorrencia(LocalDate dataBase, String freq) {
        if (freq == null || freq.equals("Único")) return null;
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
        return "Receita{id=" + idReceita + ", descricao='" + descricao + "', valor=" + valor + 
               ", data=" + data + ", frequencia='" + frequencia + "', conta=" + idConta + ", usuario=" + idUsuario +
               ", observacoes=" + (observacoes != null ? Arrays.toString(observacoes) : "[]") + "}";
    }
}

