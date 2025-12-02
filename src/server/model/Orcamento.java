package server.model;

import java.io.Serializable;

public class Orcamento implements Serializable {
    private static final long serialVersionUID = 9L;
    private int idOrcamento;
    private double valorPlanejado;
    private String periodo;
    private int idCategoria;
    private int idUsuario;
    private boolean ativo;
    
    public Orcamento(int idOrcamento, double valorPlanejado, String periodo, int idCategoria, int idUsuario) {
        this.idOrcamento = idOrcamento;
        this.valorPlanejado = valorPlanejado;
        this.periodo = periodo;
        this.idCategoria = idCategoria;
        this.idUsuario = idUsuario;
        this.ativo = true;
    }
    
    public int getIdOrcamento() { return idOrcamento; }
    public void setIdOrcamento(int idOrcamento) { this.idOrcamento = idOrcamento; }
    
    public double getValorPlanejado() { return valorPlanejado; }
    public void setValorPlanejado(double valorPlanejado) { this.valorPlanejado = valorPlanejado; }
    
    public String getPeriodo() { return periodo; }
    public void setPeriodo(String periodo) { this.periodo = periodo; }
    
    public int getIdCategoria() { return idCategoria; }
    public void setIdCategoria(int idCategoria) { this.idCategoria = idCategoria; }
    
    public int getIdUsuario() { return idUsuario; }
    public void setIdUsuario(int idUsuario) { this.idUsuario = idUsuario; }
    
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
    
    @Override
    public String toString() {
        return "Orcamento{id=" + idOrcamento + ", valorPlanejado=" + valorPlanejado + 
               ", periodo='" + periodo + "', categoria=" + idCategoria + ", usuario=" + idUsuario + "}";
    }
}

