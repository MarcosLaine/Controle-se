package server.model;

import java.io.Serializable;

public class TransacaoTag implements Serializable {
    private static final long serialVersionUID = 12L;
    private int idTransacaoTag;
    private int idTransacao;
    private String tipoTransacao;
    private int idTag;
    private boolean ativo;
    
    public TransacaoTag(int idTransacaoTag, int idTransacao, String tipoTransacao, int idTag) {
        this.idTransacaoTag = idTransacaoTag;
        this.idTransacao = idTransacao;
        this.tipoTransacao = tipoTransacao;
        this.idTag = idTag;
        this.ativo = true;
    }
    
    public int getIdTransacaoTag() { return idTransacaoTag; }
    public void setIdTransacaoTag(int idTransacaoTag) { this.idTransacaoTag = idTransacaoTag; }
    
    public int getIdTransacao() { return idTransacao; }
    public void setIdTransacao(int idTransacao) { this.idTransacao = idTransacao; }
    
    public String getTipoTransacao() { return tipoTransacao; }
    public void setTipoTransacao(String tipoTransacao) { this.tipoTransacao = tipoTransacao; }
    
    public int getIdTag() { return idTag; }
    public void setIdTag(int idTag) { this.idTag = idTag; }
    
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
    
    @Override
    public String toString() {
        return "TransacaoTag{id=" + idTransacaoTag + ", transacao=" + idTransacao + 
               ", tipo='" + tipoTransacao + "', tag=" + idTag + "}";
    }
}

