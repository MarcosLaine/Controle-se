package server.model;

import java.io.Serializable;
import java.time.LocalDate;

public class Investimento implements Serializable {
    private static final long serialVersionUID = 16L;
    private int idInvestimento;
    private String nome;
    private String nomeAtivo;
    private String categoria;
    private double quantidade;
    private double precoAporte;
    private double valorAporte;
    private double corretagem;
    private String corretora;
    private LocalDate dataAporte;
    private int idUsuario;
    private int idConta;
    private boolean ativo;
    private String moeda;
    private String tipoInvestimento;
    private String tipoRentabilidade;
    private String indice;
    private Double percentualIndice;
    private Double taxaFixa;
    private LocalDate dataVencimento;
    
    public Investimento(int idInvestimento, String nome, String categoria, double quantidade, 
                       double precoAporte, double corretagem, String corretora, 
                       LocalDate dataAporte, int idUsuario, int idConta, String moeda) {
        this(idInvestimento, nome, null, categoria, quantidade, precoAporte, corretagem, corretora, dataAporte, idUsuario, idConta, moeda);
    }
    
    public Investimento(int idInvestimento, String nome, String nomeAtivo, String categoria, double quantidade, 
                       double precoAporte, double corretagem, String corretora, 
                       LocalDate dataAporte, int idUsuario, int idConta, String moeda) {
        this.idInvestimento = idInvestimento;
        this.nome = nome;
        this.nomeAtivo = nomeAtivo;
        this.categoria = categoria;
        this.quantidade = quantidade;
        this.precoAporte = precoAporte;
        this.corretagem = corretagem;
        this.corretora = corretora;
        this.dataAporte = dataAporte;
        this.idUsuario = idUsuario;
        this.idConta = idConta;
        this.ativo = true;
        this.moeda = moeda != null ? moeda : "BRL";
        this.valorAporte = (quantidade * precoAporte) + corretagem;
    }
    
    public int getIdInvestimento() { return idInvestimento; }
    public void setIdInvestimento(int idInvestimento) { this.idInvestimento = idInvestimento; }
    
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    
    public String getNomeAtivo() { return nomeAtivo; }
    public void setNomeAtivo(String nomeAtivo) { this.nomeAtivo = nomeAtivo; }
    
    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }
    
    public double getQuantidade() { return quantidade; }
    public void setQuantidade(double quantidade) { 
        this.quantidade = quantidade;
        this.valorAporte = (quantidade * precoAporte) + corretagem;
    }
    
    public double getPrecoAporte() { return precoAporte; }
    public void setPrecoAporte(double precoAporte) { 
        this.precoAporte = precoAporte;
        this.valorAporte = (quantidade * precoAporte) + corretagem;
    }
    
    public double getValorAporte() { return valorAporte; }
    public void setValorAporte(double valorAporte) { this.valorAporte = valorAporte; }
    
    public double getCorretagem() { return corretagem; }
    public void setCorretagem(double corretagem) { 
        this.corretagem = corretagem;
        this.valorAporte = (quantidade * precoAporte) + corretagem;
    }
    
    public String getCorretora() { return corretora; }
    public void setCorretora(String corretora) { this.corretora = corretora; }
    
    public LocalDate getDataAporte() { return dataAporte; }
    public void setDataAporte(LocalDate dataAporte) { this.dataAporte = dataAporte; }
    
    public int getIdUsuario() { return idUsuario; }
    public void setIdUsuario(int idUsuario) { this.idUsuario = idUsuario; }
    
    public int getIdConta() { return idConta; }
    public void setIdConta(int idConta) { this.idConta = idConta; }
    
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
    
    public String getMoeda() { return moeda; }
    public void setMoeda(String moeda) { this.moeda = moeda; }
    
    public String getTipoInvestimento() { return tipoInvestimento; }
    public void setTipoInvestimento(String tipoInvestimento) { this.tipoInvestimento = tipoInvestimento; }
    
    public String getTipoRentabilidade() { return tipoRentabilidade; }
    public void setTipoRentabilidade(String tipoRentabilidade) { this.tipoRentabilidade = tipoRentabilidade; }
    
    public String getIndice() { return indice; }
    public void setIndice(String indice) { this.indice = indice; }
    
    public Double getPercentualIndice() { return percentualIndice; }
    public void setPercentualIndice(Double percentualIndice) { this.percentualIndice = percentualIndice; }
    
    public Double getTaxaFixa() { return taxaFixa; }
    public void setTaxaFixa(Double taxaFixa) { this.taxaFixa = taxaFixa; }
    
    public LocalDate getDataVencimento() { return dataVencimento; }
    public void setDataVencimento(LocalDate dataVencimento) { this.dataVencimento = dataVencimento; }
    
    @Override
    public String toString() {
        return "Investimento{id=" + idInvestimento + ", nome='" + nome + "', categoria='" + categoria + 
               "', quantidade=" + quantidade + ", precoAporte=" + precoAporte + 
               ", valorAporte=" + valorAporte + ", corretagem=" + corretagem + 
               ", corretora='" + corretora + "', dataAporte=" + dataAporte + 
               ", usuario=" + idUsuario + ", conta=" + idConta + ", moeda='" + moeda + "'}";
    }
}

