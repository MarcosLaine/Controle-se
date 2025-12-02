package server.model;

import java.io.Serializable;

public class Tag implements Serializable {
    private static final long serialVersionUID = 11L;
    private int idTag;
    private String nome;
    private String cor;
    private int idUsuario;
    private boolean ativo;
    
    public Tag(int idTag, String nome, String cor, int idUsuario) {
        this.idTag = idTag;
        this.nome = nome;
        this.cor = cor;
        this.idUsuario = idUsuario;
        this.ativo = true;
    }
    
    public int getIdTag() { return idTag; }
    public void setIdTag(int idTag) { this.idTag = idTag; }
    
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    
    public String getCor() { return cor; }
    public void setCor(String cor) { this.cor = cor; }
    
    public int getIdUsuario() { return idUsuario; }
    public void setIdUsuario(int idUsuario) { this.idUsuario = idUsuario; }
    
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
    
    @Override
    public String toString() {
        return "Tag{id=" + idTag + ", nome='" + nome + "', cor='" + cor + "', usuario=" + idUsuario + "}";
    }
}

