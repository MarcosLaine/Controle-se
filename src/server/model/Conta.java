package server.model;

import java.io.Serializable;

public class Conta implements Serializable {
    private static final long serialVersionUID = 8L;
    private int idConta;
    private String nome;
    private String tipo;
    private double saldoAtual;
    private int idUsuario;
    private boolean ativo;
    private Integer diaFechamento;
    private Integer diaPagamento;
    
    public Conta(int idConta, String nome, String tipo, double saldoAtual, int idUsuario) {
        this.idConta = idConta;
        this.nome = nome;
        this.tipo = tipo;
        this.saldoAtual = saldoAtual;
        this.idUsuario = idUsuario;
        this.ativo = true;
        this.diaFechamento = null;
        this.diaPagamento = null;
    }
    
    public int getIdConta() { return idConta; }
    public void setIdConta(int idConta) { this.idConta = idConta; }
    
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    
    public double getSaldoAtual() { return saldoAtual; }
    public void setSaldoAtual(double saldoAtual) { this.saldoAtual = saldoAtual; }
    
    public int getIdUsuario() { return idUsuario; }
    public void setIdUsuario(int idUsuario) { this.idUsuario = idUsuario; }
    
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
    
    public Integer getDiaFechamento() { return diaFechamento; }
    public void setDiaFechamento(Integer diaFechamento) { this.diaFechamento = diaFechamento; }
    
    public Integer getDiaPagamento() { return diaPagamento; }
    public void setDiaPagamento(Integer diaPagamento) { this.diaPagamento = diaPagamento; }
    
    public boolean isCartaoCredito() {
        return tipo != null && (tipo.toUpperCase().equals("CARTAO_CREDITO") || 
                                tipo.toUpperCase().equals("CARTAO DE CREDITO") ||
                                tipo.toUpperCase().contains("CARTAO"));
    }
    
    @Override
    public String toString() {
        return "Conta{id=" + idConta + ", nome='" + nome + "', tipo='" + tipo + 
               "', saldo=" + saldoAtual + ", usuario=" + idUsuario + "}";
    }
}

