package server.repository;

import server.database.DatabaseConnection;
import server.model.Conta;
import server.model.Investimento;
import server.services.QuoteService;
import server.validation.InputValidator;
import server.validation.ValidationResult;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class AccountRepository {

    private Connection getConnection() throws SQLException {
        return DatabaseConnection.getInstance().getConnection();
    }

    private void validateInput(String field, String value, int maxLength) {
        ValidationResult res = InputValidator.validateString(field, value, maxLength, true);
        if (!res.isValid()) throw new IllegalArgumentException(res.getErrors().get(0));
    }

    private void validateId(String field, int id) {
        ValidationResult res = InputValidator.validateId(field, id, true);
        if (!res.isValid()) throw new IllegalArgumentException(res.getErrors().get(0));
    }

    private void validateAmount(String field, double amount) {
        ValidationResult res = InputValidator.validateMoney(field, amount, true);
        if (!res.isValid()) throw new IllegalArgumentException(res.getErrors().get(0));
    }

    private void validateEnum(String field, String value, String[] allowed) {
        ValidationResult res = InputValidator.validateEnum(field, value, allowed, true);
        if (!res.isValid()) throw new IllegalArgumentException(res.getErrors().get(0));
    }

    private String sanitizeString(String value) {
        return InputValidator.sanitizeInput(value);
    }

    public int cadastrarConta(String nome, String tipo, double saldoAtual, int idUsuario) {
        return cadastrarConta(nome, tipo, saldoAtual, idUsuario, null, null);
    }

    public int cadastrarConta(String nome, String tipo, double saldoAtual, int idUsuario, Integer diaFechamento, Integer diaPagamento) {
        validateInput("Nome da conta", nome, 100);
        validateId("ID do usuário", idUsuario);
        validateAmount("Saldo inicial", saldoAtual);
        
        String tipoUpper = tipo != null ? tipo.toUpperCase() : "";
        String[] tiposPermitidos = {"CORRENTE", "POUPANCA", "INVESTIMENTO", "INVESTIMENTO (CORRETORA)", 
                                   "CARTAO_CREDITO", "CARTAO DE CREDITO", "CARTAO DE CRÉDITO", 
                                   "CARTÃO DE CRÉDITO", "CARTÃO DE CREDITO", "OUTROS"};
        
        String tipoNormalizado = tipoUpper.replace("É", "E").replace("À", "A").replace(" ", "_");
        boolean tipoValido = false;
        for (String tipoPermitido : tiposPermitidos) {
            String permitidoNormalizado = tipoPermitido.replace("É", "E").replace("À", "A").replace(" ", "_");
            if (tipoNormalizado.equals(permitidoNormalizado) || tipoUpper.contains("CARTAO") || tipoUpper.contains("CARTÃO")) {
                tipoValido = true;
                break;
            }
        }
        if (!tipoValido && !tipoUpper.contains("CARTAO") && !tipoUpper.contains("CARTÃO")) {
            validateEnum("Tipo de conta", tipo, tiposPermitidos);
        }
        
        if (tipoUpper.contains("CARTAO") || tipoUpper.contains("CARTÃO")) {
            tipo = "CARTAO_CREDITO";
        }
        
        boolean isCartao = tipoUpper.contains("CARTAO") || tipoUpper.contains("CARTÃO");
        if (isCartao) {
            if (diaFechamento != null && (diaFechamento < 1 || diaFechamento > 31)) {
                throw new IllegalArgumentException("Dia de fechamento deve estar entre 1 e 31");
            }
            if (diaPagamento != null && (diaPagamento < 1 || diaPagamento > 31)) {
                throw new IllegalArgumentException("Dia de pagamento deve estar entre 1 e 31");
            }
        }
        
        nome = sanitizeString(nome);
        
        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);

            if (hasCartaoCreditoColumns(conn) && (diaFechamento != null || diaPagamento != null)) {
                String sql = "INSERT INTO contas (nome, tipo, saldo_atual, id_usuario, dia_fechamento, dia_pagamento) VALUES (?, ?, ?, ?, ?, ?) RETURNING id_conta";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, nome);
                    pstmt.setString(2, tipo.toUpperCase());
                    pstmt.setDouble(3, saldoAtual);
                    pstmt.setInt(4, idUsuario);
                    if (diaFechamento != null) pstmt.setInt(5, diaFechamento); else pstmt.setNull(5, Types.INTEGER);
                    if (diaPagamento != null) pstmt.setInt(6, diaPagamento); else pstmt.setNull(6, Types.INTEGER);
                    
                    ResultSet rs = pstmt.executeQuery();
                    if (rs.next()) {
                        int idConta = rs.getInt(1);
                        conn.commit();
                        return idConta;
                    }
                    throw new RuntimeException("Erro ao cadastrar conta");
                } catch (SQLException e) {
                    if (e.getMessage().contains("dia_fechamento") || e.getMessage().contains("dia_pagamento")) {
                        // Fallback
                    } else {
                        try { conn.rollback(); } catch (SQLException ex) {}
                        throw new RuntimeException("Erro ao cadastrar conta: " + e.getMessage(), e);
                    }
                }
            }
            
            String sql = "INSERT INTO contas (nome, tipo, saldo_atual, id_usuario) VALUES (?, ?, ?, ?) RETURNING id_conta";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, nome);
                pstmt.setString(2, tipo.toUpperCase());
                pstmt.setDouble(3, saldoAtual);
                pstmt.setInt(4, idUsuario);
                
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    int idConta = rs.getInt(1);
                    conn.commit();
                    return idConta;
                }
                throw new RuntimeException("Erro ao cadastrar conta");
            }
        } catch (SQLException e) {
            if (conn != null) try { conn.rollback(); } catch (SQLException ex) {}
            throw new RuntimeException("Erro ao cadastrar conta: " + e.getMessage(), e);
        } finally {
            if (conn != null) try { conn.close(); } catch (SQLException e) {}
        }
    }
    
    public Conta buscarConta(int idConta) {
        String sql = "SELECT id_conta, nome, tipo, saldo_atual, id_usuario, ativo FROM contas WHERE id_conta = ? AND ativo = TRUE";
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, idConta);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                return mapConta(rs, conn);
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar conta: " + e.getMessage(), e);
        }
    }
    
    public List<Conta> buscarContasPorUsuario(int idUsuario) {
        String sql = "SELECT id_conta, nome, tipo, saldo_atual, id_usuario, ativo FROM contas WHERE id_usuario = ? AND ativo = TRUE ORDER BY nome";
        List<Conta> contas = new ArrayList<>();
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, idUsuario);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                contas.add(mapConta(rs, conn));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar contas: " + e.getMessage(), e);
        }
        return contas;
    }
    
    public List<Conta> buscarContasPorTipo(String tipo) {
        String sql = "SELECT id_conta, nome, tipo, saldo_atual, id_usuario, ativo FROM contas WHERE tipo = ? AND ativo = TRUE ORDER BY nome";
        List<Conta> contas = new ArrayList<>();
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, tipo);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                contas.add(mapConta(rs, conn));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar contas por tipo: " + e.getMessage(), e);
        }
        return contas;
    }
    
    public void atualizarConta(int idConta, String novoNome, String novoTipo, double novoSaldo, Integer diaFechamento, Integer diaPagamento) {
        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);
            
            if (hasCartaoCreditoColumns(conn) && (diaFechamento != null || diaPagamento != null)) {
                String sql = "UPDATE contas SET nome = ?, tipo = ?, saldo_atual = ?, dia_fechamento = ?, dia_pagamento = ? WHERE id_conta = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, novoNome);
                    pstmt.setString(2, novoTipo);
                    pstmt.setDouble(3, novoSaldo);
                    if (diaFechamento != null) pstmt.setInt(4, diaFechamento); else pstmt.setNull(4, Types.INTEGER);
                    if (diaPagamento != null) pstmt.setInt(5, diaPagamento); else pstmt.setNull(5, Types.INTEGER);
                    pstmt.setInt(6, idConta);
                    pstmt.executeUpdate();
                    conn.commit();
                    return;
                } catch (SQLException e) {
                    if (!e.getMessage().contains("dia_fechamento") && !e.getMessage().contains("dia_pagamento")) {
                        try { conn.rollback(); } catch (SQLException ex) {}
                        throw new RuntimeException("Erro ao atualizar conta: " + e.getMessage(), e);
                    }
                }
            }
            
            String sql = "UPDATE contas SET nome = ?, tipo = ?, saldo_atual = ? WHERE id_conta = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, novoNome);
                pstmt.setString(2, novoTipo);
                pstmt.setDouble(3, novoSaldo);
                pstmt.setInt(4, idConta);
                pstmt.executeUpdate();
                conn.commit();
            }
        } catch (SQLException e) {
            if (conn != null) try { conn.rollback(); } catch (SQLException ex) {}
            throw new RuntimeException("Erro ao atualizar conta: " + e.getMessage(), e);
        } finally {
            if (conn != null) try { conn.close(); } catch (SQLException e) {}
        }
    }
    
    public void excluirConta(int idConta) {
        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);
            
            // Verifica se a conta existe e está ativa
            String sqlVerificar = "SELECT id_conta, ativo FROM contas WHERE id_conta = ?";
            boolean contaExiste = false;
            boolean contaAtiva = false;
            try (PreparedStatement pstmt = conn.prepareStatement(sqlVerificar)) {
                pstmt.setInt(1, idConta);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    contaExiste = true;
                    contaAtiva = rs.getBoolean("ativo");
                }
            }
            
            if (!contaExiste) {
                throw new IllegalArgumentException("Conta não encontrada");
            }
            
            // Exclui logicamente todas as receitas ativas que referenciam esta conta
            String sqlReceitas = "UPDATE receitas SET ativo = FALSE WHERE id_conta = ? AND ativo = TRUE";
            try (PreparedStatement pstmt = conn.prepareStatement(sqlReceitas)) {
                pstmt.setInt(1, idConta);
                pstmt.executeUpdate();
            }
            
            // Exclui logicamente todos os gastos ativos que referenciam esta conta
            String sqlGastos = "UPDATE gastos SET ativo = FALSE WHERE id_conta = ? AND ativo = TRUE";
            try (PreparedStatement pstmt = conn.prepareStatement(sqlGastos)) {
                pstmt.setInt(1, idConta);
                pstmt.executeUpdate();
            }
            
            // Exclui logicamente todos os investimentos ativos que referenciam esta conta
            String sqlInvestimentos = "UPDATE investimentos SET ativo = FALSE WHERE id_conta = ? AND ativo = TRUE";
            try (PreparedStatement pstmt = conn.prepareStatement(sqlInvestimentos)) {
                pstmt.setInt(1, idConta);
                pstmt.executeUpdate();
            }
            
            // Exclui logicamente todos os grupos de parcelas ativos que referenciam esta conta
            // (ignora se a tabela não existir, pois pode ser uma instalação antiga)
            try {
                String sqlInstallmentGroups = "UPDATE installment_groups SET ativo = FALSE WHERE id_conta = ? AND ativo = TRUE";
                try (PreparedStatement pstmt = conn.prepareStatement(sqlInstallmentGroups)) {
                    pstmt.setInt(1, idConta);
                    pstmt.executeUpdate();
                }
            } catch (SQLException e) {
                // Ignora erro se a tabela não existir (instalação antiga sem suporte a parcelas)
                if (!e.getMessage().contains("does not exist") && !e.getMessage().contains("relation") && !e.getMessage().contains("table")) {
                    throw e; // Re-lança se for outro tipo de erro
                }
            }
            
            // Agora pode excluir a conta fisicamente
            String sql = "DELETE FROM contas WHERE id_conta = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, idConta);
                int deleted = pstmt.executeUpdate();
                if (deleted == 0) {
                    throw new IllegalArgumentException("Conta não encontrada");
                }
            }
            
            conn.commit();
        } catch (SQLException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    // Ignora erro no rollback
                }
            }
            throw new RuntimeException("Erro ao excluir conta: " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                try {
                    conn.close();
                } catch (SQLException e) {
                    // Ignora erro ao fechar conexão
                }
            }
        }
    }
    
    public double calcularSaldoContasUsuarioSemInvestimento(int idUsuario) {
        String sql = "SELECT COALESCE(SUM(saldo_atual), 0) as total " +
                    "FROM contas WHERE id_usuario = ? " +
                    "AND UPPER(tipo) NOT LIKE 'INVESTIMENTO%' " +
                    "AND UPPER(tipo) NOT LIKE 'CARTAO%' " +
                    "AND ativo = TRUE";
        return executeDoubleQuery(sql, idUsuario);
    }
    
    /**
     * Calcula o saldo disponível: (Conta Corrente + Dinheiro + Poupança) - Gastos
     * @param idUsuario ID do usuário
     * @param totalGastos Total de gastos do usuário (para subtrair)
     * @return Saldo disponível após subtrair os gastos
     */
    public double calcularSaldoDisponivel(int idUsuario, double totalGastos) {
        String sql = "SELECT COALESCE(SUM(saldo_atual), 0) as total " +
                    "FROM contas WHERE id_usuario = ? " +
                    "AND ativo = TRUE " +
                    "AND (UPPER(tipo) LIKE '%CORRENTE%' " +
                    "     OR UPPER(tipo) LIKE '%DINHEIRO%' " +
                    "     OR UPPER(tipo) LIKE '%POUPANÇA%' " +
                    "     OR UPPER(tipo) LIKE '%POUPANCA%')";
        double saldoContas = executeDoubleQuery(sql, idUsuario);
        return saldoContas - totalGastos;
    }
    
    public double calcularTotalCreditoDisponivelCartoes(int idUsuario) {
        String sql = "SELECT COALESCE(SUM(saldo_atual), 0) as total " +
                    "FROM contas WHERE id_usuario = ? " +
                    "AND (UPPER(tipo) LIKE 'CARTAO%' OR UPPER(tipo) = 'CARTAO_CREDITO') " +
                    "AND ativo = TRUE";
        return executeDoubleQuery(sql, idUsuario);
    }
    
    public double calcularTotalSaldoContasUsuario(int idUsuario) {
        // Exclui contas de investimento porque o saldo delas já inclui o valor atual dos investimentos
        // e será calculado dinamicamente no AccountsHandler
        String sql = "SELECT COALESCE(SUM(saldo_atual), 0) as total " +
                    "FROM contas WHERE id_usuario = ? " +
                    "AND ativo = TRUE " +
                    "AND (tipo IS NULL OR (UPPER(tipo) NOT LIKE 'CARTAO%' AND UPPER(tipo) != 'CARTAO_CREDITO' " +
                    "AND UPPER(tipo) NOT LIKE 'INVESTIMENTO%'))";
        return executeDoubleQuery(sql, idUsuario);
    }

    private double executeDoubleQuery(String sql, int id) {
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return rs.getDouble("total");
            return 0.0;
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao executar query: " + e.getMessage(), e);
        }
    }

    /**
     * Decrementa o saldo de uma conta
     */
    public void decrementarSaldo(int idConta, double valor) {
        if (valor <= 0) {
            throw new IllegalArgumentException("Valor deve ser maior que zero");
        }
        
        String sql = "UPDATE contas SET saldo_atual = saldo_atual - ? WHERE id_conta = ?";
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setDouble(1, valor);
                pstmt.setInt(2, idConta);
                int rowsAffected = pstmt.executeUpdate();
                if (rowsAffected == 0) {
                    throw new RuntimeException("Conta não encontrada ou não foi possível atualizar o saldo");
                }
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao decrementar saldo da conta: " + e.getMessage(), e);
        }
    }

    private Boolean hasCartaoCreditoColumnsCache = null;
    private boolean hasCartaoCreditoColumns(Connection conn) {
        if (hasCartaoCreditoColumnsCache != null) return hasCartaoCreditoColumnsCache;
        String sql = "SELECT dia_fechamento, dia_pagamento FROM contas LIMIT 1";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.executeQuery();
            hasCartaoCreditoColumnsCache = true;
            return true;
        } catch (SQLException e) {
            hasCartaoCreditoColumnsCache = false;
            return false;
        }
    }

    /**
     * Calcula o valor atual dos investimentos de uma conta de investimento
     * Retorna 0 se a conta não for de investimento ou não tiver investimentos
     */
    public double calcularValorAtualInvestimentos(int idConta) {
        try {
            Conta conta = buscarConta(idConta);
            if (conta == null) {
                return 0.0;
            }
            
            // Verifica se é conta de investimento
            String tipoConta = conta.getTipo() != null ? conta.getTipo().toLowerCase().trim() : "";
            if (!tipoConta.equals("investimento") && !tipoConta.equals("investimento (corretora)") && !tipoConta.startsWith("investimento")) {
                return 0.0;
            }
            
            // Busca investimentos da conta
            InvestmentRepository investmentRepository = new InvestmentRepository();
            List<Investimento> investments = investmentRepository.buscarInvestimentosPorConta(idConta);
            
            if (investments.isEmpty()) {
                return 0.0;
            }
            
            QuoteService quoteService = QuoteService.getInstance();
            double totalCurrent = 0.0;
            
            for (Investimento inv : investments) {
                try {
                    double currentValue = 0.0;
                    
                    // Converte valor do aporte para BRL se o investimento foi registrado em outra moeda
                    double valorAporteBRL = inv.getValorAporte();
                    
                    if (!"BRL".equals(inv.getMoeda())) {
                        double exchangeRate = quoteService.getExchangeRate(inv.getMoeda(), "BRL");
                        valorAporteBRL *= exchangeRate;
                    }
                    
                    // Para renda fixa, calcula valor atual baseado nos índices
                    if ("RENDA_FIXA".equals(inv.getCategoria())) {
                        currentValue = quoteService.calculateFixedIncomeValue(
                            valorAporteBRL,
                            inv.getTipoInvestimento(),
                            inv.getTipoRentabilidade(),
                            inv.getIndice(),
                            inv.getPercentualIndice(),
                            inv.getTaxaFixa(),
                            inv.getDataAporte(),
                            inv.getDataVencimento(),
                            LocalDate.now()
                        );
                    } else {
                        // Para outros tipos, busca cotação atual
                        QuoteService.QuoteResult quote = quoteService.getQuote(inv.getNome(), inv.getCategoria(), null);
                        double currentPrice = quote != null && quote.success ? quote.price : inv.getPrecoAporte();
                        
                        // Converte preço atual para BRL se a cotação vier em outra moeda
                        if (quote != null && quote.success && !"BRL".equals(quote.currency)) {
                            double exchangeRate = quoteService.getExchangeRate(quote.currency, "BRL");
                            currentPrice *= exchangeRate;
                        } else if (!"BRL".equals(inv.getMoeda())) {
                            double exchangeRate = quoteService.getExchangeRate(inv.getMoeda(), "BRL");
                            currentPrice *= exchangeRate;
                        }
                        
                        currentValue = inv.getQuantidade() * currentPrice;
                    }
                    
                    totalCurrent += currentValue;
                } catch (Exception e) {
                    // Continua processando os outros investimentos mesmo se um falhar
                    e.printStackTrace();
                }
            }
            
            return totalCurrent;
        } catch (Exception e) {
            e.printStackTrace();
            return 0.0;
        }
    }

    private Conta mapConta(ResultSet rs, Connection conn) throws SQLException {
        Conta conta = new Conta(
            rs.getInt("id_conta"),
            rs.getString("nome"),
            rs.getString("tipo"),
            rs.getDouble("saldo_atual"),
            rs.getInt("id_usuario")
        );
        conta.setAtivo(rs.getBoolean("ativo"));
        
        if (hasCartaoCreditoColumns(conn)) {
            try {
                // Valida se a conexão ainda está válida antes de usar
                if (conn.isClosed() || !conn.isValid(2)) {
                    // Se a conexão estiver inválida, retorna a conta sem os dados extras
                    return conta;
                }
                
                int idConta = conta.getIdConta();
                String sqlExtra = "SELECT dia_fechamento, dia_pagamento FROM contas WHERE id_conta = ?";
                try (PreparedStatement pstmtExtra = conn.prepareStatement(sqlExtra)) {
                    pstmtExtra.setInt(1, idConta);
                    ResultSet rsExtra = pstmtExtra.executeQuery();
                    if (rsExtra.next()) {
                        Object diaFechamentoObj = rsExtra.getObject("dia_fechamento");
                        if (diaFechamentoObj != null) conta.setDiaFechamento((Integer) diaFechamentoObj);
                        Object diaPagamentoObj = rsExtra.getObject("dia_pagamento");
                        if (diaPagamentoObj != null) conta.setDiaPagamento((Integer) diaPagamentoObj);
                    }
                }
            } catch (SQLException e) {
                // Se a conexão foi resetada ou está quebrada, ignora e retorna conta básica
                // O HikariCP vai detectar e substituir a conexão na próxima requisição
                if (e.getMessage() != null && 
                    (e.getMessage().contains("Connection reset") || 
                     e.getMessage().contains("broken") ||
                     e.getMessage().contains("closed"))) {
                    // Conexão quebrada - retorna conta sem dados extras
                    return conta;
                }
                // Para outros erros SQL, também ignora (comportamento original)
            }
        }
        return conta;
    }
}

