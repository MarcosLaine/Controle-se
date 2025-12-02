package server.model;

import java.io.Serializable;

public class Categoria implements Serializable {
    private static final long serialVersionUID = 2L;
    private int idCategoria;
    private String nome;
    private int idUsuario;
    private boolean ativo;
    
    public Categoria(int idCategoria, String nome, int idUsuario) {
        this.idCategoria = idCategoria;
        this.nome = nome;
        this.idUsuario = idUsuario;
        this.ativo = true;
    }
    
    public int getIdCategoria() { return idCategoria; }
    public void setIdCategoria(int idCategoria) { this.idCategoria = idCategoria; }
    
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    
    public int getIdUsuario() { return idUsuario; }
    public void setIdUsuario(int idUsuario) { this.idUsuario = idUsuario; }
    
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
    
    @Override
    public String toString() {
        return "Categoria{id=" + idCategoria + ", nome='" + nome + "', usuario=" + idUsuario + "}";
    }
}

