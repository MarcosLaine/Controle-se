import java.io.Serializable;
import java.time.LocalDate;

/**
 * Classes de entidades do sistema Controle-se baseadas no diagrama ER
 */

/**
 * Entidade Usuario - Representa um usuário do sistema
 */
class Usuario implements Serializable {
    private static final long serialVersionUID = 1L;
    private int idUsuario;
    private String nome;
    private String email;
    private String senha;
    private boolean ativo;
    
    public Usuario(int idUsuario, String nome, String email, String senha) {
        this.idUsuario = idUsuario;
        this.nome = nome;
        this.email = email;
        this.senha = senha;
        this.ativo = true; // Por padrão, usuário é ativo
    }
    
    // Getters e Setters
    public int getIdUsuario() { return idUsuario; }
    public void setIdUsuario(int idUsuario) { this.idUsuario = idUsuario; }
    
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    
    public String getSenha() { return senha; }
    public void setSenha(String senha) { this.senha = senha; }
    
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
    
    @Override
    public String toString() {
        return "Usuario{id=" + idUsuario + ", nome='" + nome + "', email='" + email + "'}";
    }
}

/**
 * Entidade Categoria - Representa uma categoria para classificar gastos e orçamentos
 */
class Categoria implements Serializable {
    private static final long serialVersionUID = 2L;
    private int idCategoria;
    private String nome;
    private int idUsuario; // FK para Usuario
    private boolean ativo;
    
    public Categoria(int idCategoria, String nome, int idUsuario) {
        this.idCategoria = idCategoria;
        this.nome = nome;
        this.idUsuario = idUsuario;
        this.ativo = true;
    }
    
    // Getters e Setters
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

/**
 * Entidade Gasto - Representa uma despesa registrada pelo usuário
 */
class Gasto implements Serializable {
    private static final long serialVersionUID = 3L;
    private int idGasto;
    private String descricao;
    private double valor;
    private LocalDate data;
    private String frequencia;
    private int idCategoria; // FK para Categoria
    private int idConta; // FK para Conta
    private int idUsuario; // FK para Usuario
    private boolean ativo;
    
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
    }
    
    // Getters e Setters
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
    
    @Override
    public String toString() {
        return "Gasto{id=" + idGasto + ", descricao='" + descricao + "', valor=" + valor + 
               ", data=" + data + ", frequencia='" + frequencia + "', categoria=" + idCategoria + 
               ", conta=" + idConta + ", usuario=" + idUsuario + "}";
    }
}

/**
 * Entidade Receita - Representa uma receita/renda registrada pelo usuário
 */
class Receita implements Serializable {
    private static final long serialVersionUID = 4L;
    private int idReceita;
    private String descricao;
    private double valor;
    private LocalDate data;
    private int idConta; // FK para Conta
    private int idUsuario; // FK para Usuario
    private boolean ativo;
    
    public Receita(int idReceita, String descricao, double valor, LocalDate data, int idUsuario, int idConta) {
        this.idReceita = idReceita;
        this.descricao = descricao;
        this.valor = valor;
        this.data = data;
        this.idUsuario = idUsuario;
        this.idConta = idConta;
        this.ativo = true;
    }
    
    // Getters e Setters
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
    
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
    
    @Override
    public String toString() {
        return "Receita{id=" + idReceita + ", descricao='" + descricao + "', valor=" + valor + 
               ", data=" + data + ", conta=" + idConta + ", usuario=" + idUsuario + "}";
    }
}

/**
 * Entidade Conta - Representa uma conta financeira do usuário
 */
class Conta implements Serializable {
    private static final long serialVersionUID = 5L;
    private int idConta;
    private String nome;
    private String tipo;
    private double saldoAtual;
    private int idUsuario; // FK para Usuario
    private boolean ativo;
    
    public Conta(int idConta, String nome, String tipo, double saldoAtual, int idUsuario) {
        this.idConta = idConta;
        this.nome = nome;
        this.tipo = tipo;
        this.saldoAtual = saldoAtual;
        this.idUsuario = idUsuario;
        this.ativo = true;
    }
    
    // Getters e Setters
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
    
    @Override
    public String toString() {
        return "Conta{id=" + idConta + ", nome='" + nome + "', tipo='" + tipo + 
               "', saldo=" + saldoAtual + ", usuario=" + idUsuario + "}";
    }
}

/**
 * Entidade Orcamento - Representa um orçamento definido pelo usuário para uma categoria
 */
class Orcamento implements Serializable {
    private static final long serialVersionUID = 6L;
    private int idOrcamento;
    private double valorPlanejado;
    private String periodo;
    private int idCategoria; // FK para Categoria
    private int idUsuario; // FK para Usuario
    private boolean ativo;
    
    public Orcamento(int idOrcamento, double valorPlanejado, String periodo, int idCategoria, int idUsuario) {
        this.idOrcamento = idOrcamento;
        this.valorPlanejado = valorPlanejado;
        this.periodo = periodo;
        this.idCategoria = idCategoria;
        this.idUsuario = idUsuario;
        this.ativo = true;
    }
    
    // Getters e Setters
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

/**
 * Entidade CategoriaGasto - Relacionamento N:N entre Categoria e Gasto
 */
class CategoriaGasto implements Serializable {
    private static final long serialVersionUID = 7L;
    private int idCategoriaGasto;
    private int idCategoria; // FK para Categoria
    private int idGasto; // FK para Gasto
    private boolean ativo;
    
    public CategoriaGasto(int idCategoriaGasto, int idCategoria, int idGasto) {
        this.idCategoriaGasto = idCategoriaGasto;
        this.idCategoria = idCategoria;
        this.idGasto = idGasto;
        this.ativo = true;
    }
    
    // Getters e Setters
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
