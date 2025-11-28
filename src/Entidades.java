import java.io.Serializable;
import java.time.LocalDate;
import java.util.Arrays;

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
    private static final long serialVersionUID = 6L;
    private int idGasto;
    private String descricao;
    private double valor;
    private LocalDate data;
    private String frequencia;
    private int idCategoria; // FK para Categoria
    private int idConta; // FK para Conta
    private int idUsuario; // FK para Usuario
    private boolean ativo;
    private LocalDate proximaRecorrencia; // Próxima data para gerar recorrência automática
    private int idGastoOriginal; // ID do gasto original (0 se for o original)
    private String[] observacoes; // ATRIBUTO MULTIVALORADO DO TIPO STRING
    
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
        this.idGastoOriginal = 0; // Este é o original
        this.proximaRecorrencia = calcularProximaRecorrencia(data, frequencia);
        this.observacoes = new String[0]; // Inicializa array vazio
    }
    
    // Construtor com observações
    public Gasto(int idGasto, String descricao, double valor, LocalDate data, String frequencia, int idUsuario, int idCategoria, int idConta, String[] observacoes) {
        this(idGasto, descricao, valor, data, frequencia, idUsuario, idCategoria, idConta);
        this.observacoes = observacoes != null ? observacoes : new String[0];
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
    
    public LocalDate getProximaRecorrencia() { return proximaRecorrencia; }
    public void setProximaRecorrencia(LocalDate proximaRecorrencia) { this.proximaRecorrencia = proximaRecorrencia; }
    
    public int getIdGastoOriginal() { return idGastoOriginal; }
    public void setIdGastoOriginal(int idGastoOriginal) { this.idGastoOriginal = idGastoOriginal; }
    
    public String[] getObservacoes() { return observacoes; }
    public void setObservacoes(String[] observacoes) { this.observacoes = observacoes; }
    
    /**
     * Adiciona uma observação ao array de observações
     */
    public void adicionarObservacao(String observacao) {
        if (observacao != null && !observacao.trim().isEmpty()) {
            String[] novasObservacoes = new String[observacoes.length + 1];
            System.arraycopy(observacoes, 0, novasObservacoes, 0, observacoes.length);
            novasObservacoes[observacoes.length] = observacao.trim();
            this.observacoes = novasObservacoes;
        }
    }
    
    /**
     * Retorna as observações como uma string concatenada
     */
    public String getObservacoesComoTexto() {
        if (observacoes == null || observacoes.length == 0) {
            return "";
        }
        return String.join("; ", observacoes);
    }
    
    /**
     * Calcula a próxima data de recorrência baseada na frequência
     */
    private LocalDate calcularProximaRecorrencia(LocalDate dataBase, String freq) {
        if (freq == null || freq.equals("Único")) {
            return null; // Não há recorrência
        }
        
        switch (freq) {
            case "Semanal":
                return dataBase.plusWeeks(1);
            case "Mensal":
                return dataBase.plusMonths(1);
            case "Anual":
                return dataBase.plusYears(1);
            default:
                return null;
        }
    }
    
    /**
     * Atualiza a próxima recorrência para a data seguinte
     */
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

/**
 * Entidade Receita - Representa uma receita/renda registrada pelo usuário
 */
class Receita implements Serializable {
    private static final long serialVersionUID = 7L;
    private int idReceita;
    private String descricao;
    private double valor;
    private LocalDate data;
    private String frequencia; // Único, Semanal, Mensal, Anual
    private int idConta; // FK para Conta
    private int idUsuario; // FK para Usuario
    private boolean ativo;
    private LocalDate proximaRecorrencia; // Próxima data para gerar recorrência automática
    private int idReceitaOriginal; // ID da receita original (0 se for a original)
    
    // Construtor compatível com versões antigas (sem frequência)
    public Receita(int idReceita, String descricao, double valor, LocalDate data, int idUsuario, int idConta) {
        this(idReceita, descricao, valor, data, "Único", idUsuario, idConta);
    }
    
    // Construtor completo com frequência
    public Receita(int idReceita, String descricao, double valor, LocalDate data, String frequencia, int idUsuario, int idConta) {
        this.idReceita = idReceita;
        this.descricao = descricao;
        this.valor = valor;
        this.data = data;
        this.frequencia = frequencia;
        this.idUsuario = idUsuario;
        this.idConta = idConta;
        this.ativo = true;
        this.idReceitaOriginal = 0; // Esta é a original
        this.proximaRecorrencia = calcularProximaRecorrencia(data, frequencia);
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
    
    public String getFrequencia() { return frequencia; }
    public void setFrequencia(String frequencia) { this.frequencia = frequencia; }
    
    public boolean isAtivo() { return ativo; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
    
    public LocalDate getProximaRecorrencia() { return proximaRecorrencia; }
    public void setProximaRecorrencia(LocalDate proximaRecorrencia) { this.proximaRecorrencia = proximaRecorrencia; }
    
    public int getIdReceitaOriginal() { return idReceitaOriginal; }
    public void setIdReceitaOriginal(int idReceitaOriginal) { this.idReceitaOriginal = idReceitaOriginal; }
    
    /**
     * Calcula a próxima data de recorrência baseada na frequência
     */
    private LocalDate calcularProximaRecorrencia(LocalDate dataBase, String freq) {
        if (freq == null || freq.equals("Único")) {
            return null; // Não há recorrência
        }
        
        switch (freq) {
            case "Semanal":
                return dataBase.plusWeeks(1);
            case "Mensal":
                return dataBase.plusMonths(1);
            case "Anual":
                return dataBase.plusYears(1);
            default:
                return null;
        }
    }
    
    /**
     * Atualiza a próxima recorrência para a data seguinte
     */
    public void avancarProximaRecorrencia() {
        if (this.proximaRecorrencia != null && !this.frequencia.equals("Único")) {
            this.proximaRecorrencia = calcularProximaRecorrencia(this.proximaRecorrencia, this.frequencia);
        }
    }
    
    @Override
    public String toString() {
        return "Receita{id=" + idReceita + ", descricao='" + descricao + "', valor=" + valor + 
               ", data=" + data + ", frequencia='" + frequencia + "', conta=" + idConta + ", usuario=" + idUsuario + "}";
    }
}

/**
 * Entidade Conta - Representa uma conta financeira do usuário
 */
class Conta implements Serializable {
    private static final long serialVersionUID = 8L;
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
    private static final long serialVersionUID = 9L;
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
    private static final long serialVersionUID = 10L;
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

/**
 * Entidade Tag - Etiquetas personalizadas para classificação flexível de transações
 * Atributo multivalorado implementado através de relacionamento N:N
 */
class Tag implements Serializable {
    private static final long serialVersionUID = 11L;
    private int idTag;
    private String nome;
    private String cor; // Cor hexadecimal para exibição
    private int idUsuario; // FK para Usuario
    private boolean ativo;
    
    public Tag(int idTag, String nome, String cor, int idUsuario) {
        this.idTag = idTag;
        this.nome = nome;
        this.cor = cor;
        this.idUsuario = idUsuario;
        this.ativo = true;
    }
    
    // Getters e Setters
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

/**
 * Entidade TransacaoTag - Relacionamento N:N entre Transações (Gasto/Receita) e Tags
 * Permite múltiplas tags por transação (atributo multivalorado)
 */
class TransacaoTag implements Serializable {
    private static final long serialVersionUID = 12L;
    private int idTransacaoTag;
    private int idTransacao; // ID do Gasto ou Receita
    private String tipoTransacao; // "GASTO" ou "RECEITA"
    private int idTag; // FK para Tag
    private boolean ativo;
    
    public TransacaoTag(int idTransacaoTag, int idTransacao, String tipoTransacao, int idTag) {
        this.idTransacaoTag = idTransacaoTag;
        this.idTransacao = idTransacao;
        this.tipoTransacao = tipoTransacao;
        this.idTag = idTag;
        this.ativo = true;
    }
    
    // Getters e Setters
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

/**
 * Entidade Investimento - Representa um investimento do usuário
 */
class Investimento implements Serializable {
    private static final long serialVersionUID = 16L;
    private int idInvestimento;
    private String nome; // Ex: ITUB4, AAPL, BTC
    private String nomeAtivo; // Nome completo do ativo (ex: "Itaú Unibanco", "Apple Inc.", "Bitcoin")
    private String categoria; // RENDA_FIXA, ACAO, STOCK, CRYPTO, FII
    private double quantidade; // Número de ações/cotas/tokens (permite frações)
    private double precoAporte; // Preço unitário no momento do aporte
    private double valorAporte; // Valor total investido (quantidade * precoAporte + corretagem)
    private double corretagem; // Taxa de corretagem
    private String corretora; // Nome da corretora
    private LocalDate dataAporte; // Data do aporte
    private int idUsuario; // FK para Usuario
    private int idConta; // FK para Conta (conta de origem do investimento)
    private boolean ativo;
    private String moeda; // BRL, USD, EUR, etc.
    // Campos específicos para Renda Fixa
    private String tipoInvestimento; // CDB, LCI, LCA, TESOURO, DEBENTURE, OUTROS
    private String tipoRentabilidade; // PRE_FIXADO, POS_FIXADO, POS_FIXADO_TAXA
    private String indice; // SELIC, CDI, IPCA, PRE
    private Double percentualIndice; // Percentual do índice (ex: 100% = 100.0, 115% = 115.0)
    private Double taxaFixa; // Taxa fixa adicional (para POS_FIXADO_TAXA) ou taxa pré-fixada (para PRE_FIXADO)
    private LocalDate dataVencimento; // Data de vencimento do título
    
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
    
    // Getters e Setters
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
