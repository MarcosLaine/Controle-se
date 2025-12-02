package server.model;

import java.io.Serializable;

public class CategoriaGasto implements Serializable {
    private static final long serialVersionUID = 10L;
    private int idCategoriaGasto;
    private int idCategoria;
    private int idGasto;
    private boolean ativo;
    
    public CategoriaGasto(int idCategoriaGasto, int idCategoria, int idGasto) {
        this.idCategoriaGasto = idCategoriaGasto;
        this.idCategoria = idCategoria;
        this.idGasto = idGasto;
        this.ativo = true;
    }
    
    public int getIdCategoriaGasto() { return idCategoriaGasto; }
    public void setIdCategoriaGasto(int idCategoriaGasto) { this.idCategoriaGasto = idCategoriaGasto; }
    
    public int getIdCategoria() { return idCategoria; }
    public void setIdCategoria(int idCategoria) { this.idCategoria = idCategoria; }
    
    public int getIdGasto() { return idGasto; }
    public void setIdGasto(int idGasto) { this.idGasto = idGasto; }
    
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
    
    @Override
    public String toString() {
        return "CategoriaGasto{id=" + idCategoriaGasto + ", categoria=" + idCategoria + ", gasto=" + idGasto + "}";
    }
}

