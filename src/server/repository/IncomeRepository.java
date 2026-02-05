package server.repository;

import server.database.DatabaseConnection;
import server.model.Conta;
import server.model.Receita;
import server.validation.InputValidator;
import server.validation.ValidationResult;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IncomeRepository {

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

    public int cadastrarReceita(String descricao, double valor, LocalDate data, int idUsuario, int idConta) {
        return cadastrarReceita(descricao, valor, data, idUsuario, idConta, null);
    }
    
    /**
     * Cadastra uma receita com opção de não incrementar o saldo da conta
     * @param incrementarSaldo Se false, apenas registra a receita sem alterar o saldo (útil para receitas do sistema)
     */
    public int cadastrarReceita(String descricao, double valor, LocalDate data, int idUsuario, int idConta, String[] observacoes, boolean incrementarSaldo) {
        ValidationResult descValidation = InputValidator.validateDescription("Descrição da receita", descricao, true);
        if (!descValidation.isValid()) throw new IllegalArgumentException(descValidation.getErrors().get(0));
        
        validateAmount("Valor da receita", valor);
        validateId("ID do usuário", idUsuario);
        validateId("ID da conta", idConta);
        
        if (data == null) throw new IllegalArgumentException("Data não pode ser nula");
        
        AccountRepository accountRepo = new AccountRepository();
        Conta conta = accountRepo.buscarConta(idConta);
        if (conta == null) throw new IllegalArgumentException("Conta não encontrada");
        if (conta.getTipo() != null && conta.getTipo().equalsIgnoreCase("INVESTIMENTO")) {
            throw new IllegalArgumentException("Contas de investimento não podem ser usadas para receitas");
        }
        
        descricao = InputValidator.sanitizeDescription(descricao);
        
        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);
            
            String sql = "INSERT INTO receitas (descricao, valor, data, id_usuario, id_conta) VALUES (?, ?, ?, ?, ?) RETURNING id_receita";
            
            int idReceita;
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, descricao);
                pstmt.setDouble(2, valor);
                pstmt.setDate(3, java.sql.Date.valueOf(data));
                pstmt.setInt(4, idUsuario);
                pstmt.setInt(5, idConta);
                
                ResultSet rs = pstmt.executeQuery();
                if (!rs.next()) throw new RuntimeException("Erro ao cadastrar receita");
                idReceita = rs.getInt(1);
            }
            
            if (observacoes != null && observacoes.length > 0) {
                String sqlObs = "INSERT INTO receita_observacoes (id_receita, observacao, ordem) VALUES (?, ?, ?)";
                try (PreparedStatement pstmt = conn.prepareStatement(sqlObs)) {
                    for (int i = 0; i < observacoes.length; i++) {
                        if (observacoes[i] != null && !observacoes[i].trim().isEmpty()) {
                            pstmt.setInt(1, idReceita);
                            pstmt.setString(2, observacoes[i]);
                            pstmt.setInt(3, i);
                            pstmt.executeUpdate();
                        }
                    }
                }
            }
            
            // Só incrementa o saldo se solicitado
            if (incrementarSaldo) {
                String sqlConta = "UPDATE contas SET saldo_atual = saldo_atual + ? WHERE id_conta = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(sqlConta)) {
                    pstmt.setDouble(1, valor);
                    pstmt.setInt(2, idConta);
                    pstmt.executeUpdate();
                }
            }
            
            conn.commit();
            return idReceita;
            
        } catch (SQLException e) {
            if (conn != null) try { conn.rollback(); } catch (SQLException ex) {}
            throw new RuntimeException("Erro ao cadastrar receita: " + e.getMessage(), e);
        } finally {
            if (conn != null) try { conn.close(); } catch (SQLException e) {}
        }
    }

    public int cadastrarReceita(String descricao, double valor, LocalDate data, int idUsuario, int idConta, String[] observacoes) {
        ValidationResult descValidation = InputValidator.validateDescription("Descrição da receita", descricao, true);
        if (!descValidation.isValid()) throw new IllegalArgumentException(descValidation.getErrors().get(0));
        
        validateAmount("Valor da receita", valor);
        validateId("ID do usuário", idUsuario);
        validateId("ID da conta", idConta);
        
        if (data == null) throw new IllegalArgumentException("Data não pode ser nula");
        
        AccountRepository accountRepo = new AccountRepository();
        Conta conta = accountRepo.buscarConta(idConta);
        if (conta == null) throw new IllegalArgumentException("Conta não encontrada");
        if (conta.getTipo() != null && conta.getTipo().equalsIgnoreCase("INVESTIMENTO")) {
            throw new IllegalArgumentException("Contas de investimento não podem ser usadas para receitas");
        }
        
        descricao = InputValidator.sanitizeDescription(descricao);
        
        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);
            
            String sql = "INSERT INTO receitas (descricao, valor, data, id_usuario, id_conta) VALUES (?, ?, ?, ?, ?) RETURNING id_receita";
            
            int idReceita;
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, descricao);
                pstmt.setDouble(2, valor);
                pstmt.setDate(3, java.sql.Date.valueOf(data));
                pstmt.setInt(4, idUsuario);
                pstmt.setInt(5, idConta);
                
                ResultSet rs = pstmt.executeQuery();
                if (!rs.next()) throw new RuntimeException("Erro ao cadastrar receita");
                idReceita = rs.getInt(1);
            }
            
            if (observacoes != null && observacoes.length > 0) {
                String sqlObs = "INSERT INTO receita_observacoes (id_receita, observacao, ordem) VALUES (?, ?, ?)";
                try (PreparedStatement pstmt = conn.prepareStatement(sqlObs)) {
                    for (int i = 0; i < observacoes.length; i++) {
                        if (observacoes[i] != null && !observacoes[i].trim().isEmpty()) {
                            pstmt.setInt(1, idReceita);
                            pstmt.setString(2, observacoes[i]);
                            pstmt.setInt(3, i);
                            pstmt.executeUpdate();
                        }
                    }
                }
            }
            
            // Sempre incrementa o saldo ao cadastrar receita
            // (para receitas do sistema que não devem incrementar, use o método com parâmetro incrementarSaldo)
            String sqlConta = "UPDATE contas SET saldo_atual = saldo_atual + ? WHERE id_conta = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sqlConta)) {
                pstmt.setDouble(1, valor);
                pstmt.setInt(2, idConta);
                pstmt.executeUpdate();
            }
            
            conn.commit();
            return idReceita;
            
        } catch (SQLException e) {
            if (conn != null) try { conn.rollback(); } catch (SQLException ex) {}
            throw new RuntimeException("Erro ao cadastrar receita: " + e.getMessage(), e);
        } finally {
            if (conn != null) try { conn.close(); } catch (SQLException e) {}
        }
    }

    public Receita buscarReceita(int idReceita) {
        // NOTA: Este método não valida userId - os handlers devem validar antes de usar
        String sql = "SELECT id_receita, descricao, valor, data, frequencia, id_usuario, id_conta, " +
                    "proxima_recorrencia, id_receita_original, ativo " +
                    "FROM receitas WHERE id_receita = ? AND ativo = TRUE";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, idReceita);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                Receita receita = mapReceita(rs);
                receita.setObservacoes(buscarObservacoesReceita(idReceita));
                return receita;
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar receita: " + e.getMessage(), e);
        }
    }
    
    public Receita buscarReceitaPorUsuario(int idReceita, int idUsuario) {
        // Busca receita validando que pertence ao usuário
        String sql = "SELECT id_receita, descricao, valor, data, frequencia, id_usuario, id_conta, " +
                    "proxima_recorrencia, id_receita_original, ativo " +
                    "FROM receitas WHERE id_receita = ? AND id_usuario = ? AND ativo = TRUE";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, idReceita);
            pstmt.setInt(2, idUsuario);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                Receita receita = mapReceita(rs);
                receita.setObservacoes(buscarObservacoesReceita(idReceita));
                return receita;
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar receita: " + e.getMessage(), e);
        }
    }

    public List<Receita> buscarReceitasPorUsuario(int idUsuario) {
        String sql = "SELECT id_receita, descricao, valor, data, frequencia, id_usuario, id_conta, " +
                    "proxima_recorrencia, id_receita_original, ativo " +
                    "FROM receitas WHERE id_usuario = ? AND ativo = TRUE " +
                    "AND descricao NOT LIKE '[SISTEMA]%' " +
                    "ORDER BY data DESC";
        List<Receita> receitas = new ArrayList<>();
        List<Integer> idsReceitas = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, idUsuario);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                Receita receita = mapReceita(rs);
                idsReceitas.add(receita.getIdReceita());
                receitas.add(receita);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar receitas: " + e.getMessage(), e);
        }
        
        // Busca observações em lote para evitar N+1 queries
        if (!idsReceitas.isEmpty()) {
            Map<Integer, String[]> observacoesMap = buscarObservacoesDeReceitas(idsReceitas);
            for (Receita receita : receitas) {
                receita.setObservacoes(observacoesMap.getOrDefault(receita.getIdReceita(), new String[0]));
            }
        }
        
        return receitas;
    }

    private String[] buscarObservacoesReceita(int idReceita) {
        String sql = "SELECT observacao FROM receita_observacoes WHERE id_receita = ? ORDER BY ordem";
        List<String> obsList = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, idReceita);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) obsList.add(rs.getString("observacao"));
        } catch (SQLException e) {}
        return obsList.toArray(new String[0]);
    }

    private Receita mapReceita(ResultSet rs) throws SQLException {
        Receita receita = new Receita(
            rs.getInt("id_receita"),
            rs.getString("descricao"),
            rs.getDouble("valor"),
            rs.getDate("data").toLocalDate(),
            rs.getInt("id_usuario"),
            rs.getInt("id_conta")
        );
        
        String freq = rs.getString("frequencia");
        if (freq != null) receita.setFrequencia(freq);
        
        java.sql.Date proxRec = rs.getDate("proxima_recorrencia");
        if (proxRec != null) receita.setProximaRecorrencia(proxRec.toLocalDate());
        
        int idOriginal = rs.getInt("id_receita_original");
        if (idOriginal > 0) receita.setIdReceitaOriginal(idOriginal);
        
        receita.setAtivo(rs.getBoolean("ativo"));
        
        // Campos de parcelas (podem ser NULL)
        try {
            int idGrupoParcela = rs.getInt("id_grupo_parcela");
            if (!rs.wasNull()) {
                receita.setIdGrupoParcela(idGrupoParcela);
            }
        } catch (SQLException e) {
            // Coluna não existe na query, ignora
        }
        
        try {
            int numeroParcela = rs.getInt("numero_parcela");
            if (!rs.wasNull()) {
                receita.setNumeroParcela(numeroParcela);
            }
        } catch (SQLException e) {
            // Coluna não existe na query, ignora
        }
        
        try {
            int totalParcelas = rs.getInt("total_parcelas");
            if (!rs.wasNull()) {
                receita.setTotalParcelas(totalParcelas);
            }
        } catch (SQLException e) {
            // Coluna não existe na query, ignora
        }
        
        return receita;
    }

    public List<Receita> buscarReceitasComFiltros(int idUsuario, LocalDate data, String type) {
        // Para compatibilidade: se data não for null, usa como dateStart e dateEnd (filtro exato)
        return buscarReceitasComFiltros(idUsuario, data, data, type, Integer.MAX_VALUE, 0);
    }
    
    public List<Receita> buscarReceitasComFiltros(int idUsuario, LocalDate dateStart, LocalDate dateEnd, String type, int limit, int offset) {
        StringBuilder sql = new StringBuilder(
            "SELECT id_receita, descricao, valor, data, frequencia, id_usuario, id_conta, " +
            "proxima_recorrencia, id_receita_original, ativo, id_grupo_parcela, numero_parcela, total_parcelas " +
            "FROM receitas " +
            "WHERE id_usuario = ? AND ativo = TRUE " +
            "AND descricao NOT LIKE '[SISTEMA]%'"
        );
        if (dateStart != null && dateEnd != null) {
            sql.append(" AND data >= ? AND data <= ?");
        } else if (dateStart != null) {
            sql.append(" AND data >= ?");
        } else if (dateEnd != null) {
            sql.append(" AND data <= ?");
        }
        if (type != null && !type.isEmpty()) {
            if ("parceladas".equals(type)) {
                sql.append(" AND id_grupo_parcela IS NOT NULL");
            } else if ("unicas".equals(type)) {
                sql.append(" AND id_grupo_parcela IS NULL");
            }
        }
        sql.append(" ORDER BY data DESC");
        sql.append(" LIMIT ? OFFSET ?");
        
        List<Receita> receitas = new ArrayList<>();
        List<Integer> idsReceitas = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {
            int paramIndex = 1;
            pstmt.setInt(paramIndex++, idUsuario);
            if (dateStart != null && dateEnd != null) {
                pstmt.setDate(paramIndex++, java.sql.Date.valueOf(dateStart));
                pstmt.setDate(paramIndex++, java.sql.Date.valueOf(dateEnd));
            } else if (dateStart != null) {
                pstmt.setDate(paramIndex++, java.sql.Date.valueOf(dateStart));
            } else if (dateEnd != null) {
                pstmt.setDate(paramIndex++, java.sql.Date.valueOf(dateEnd));
            }
            pstmt.setInt(paramIndex++, limit);
            pstmt.setInt(paramIndex++, offset);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                Receita receita = mapReceita(rs);
                idsReceitas.add(receita.getIdReceita());
                receitas.add(receita);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar receitas com filtros: " + e.getMessage(), e);
        }
        
        // Busca observações em lote para evitar N+1 queries
        if (!idsReceitas.isEmpty()) {
            Map<Integer, String[]> observacoesMap = buscarObservacoesDeReceitas(idsReceitas);
            for (Receita receita : receitas) {
                receita.setObservacoes(observacoesMap.getOrDefault(receita.getIdReceita(), new String[0]));
            }
        }
        
        return receitas;
    }

    public List<Receita> buscarReceitasPorPeriodo(int idUsuario, LocalDate dataInicio, LocalDate dataFim) {
        String sql = "SELECT id_receita, descricao, valor, data, frequencia, id_usuario, id_conta, " +
                    "proxima_recorrencia, id_receita_original, ativo " +
                    "FROM receitas " +
                    "WHERE id_usuario = ? AND ativo = TRUE " +
                    "AND descricao NOT LIKE '[SISTEMA]%' " +
                    "AND data >= ? AND data <= ? " +
                    "ORDER BY data DESC";
        List<Receita> receitas = new ArrayList<>();
        List<Integer> idsReceitas = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, idUsuario);
            pstmt.setDate(2, java.sql.Date.valueOf(dataInicio));
            pstmt.setDate(3, java.sql.Date.valueOf(dataFim));
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                Receita receita = mapReceita(rs);
                idsReceitas.add(receita.getIdReceita());
                receitas.add(receita);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar receitas por período: " + e.getMessage(), e);
        }
        
        // Busca observações em lote
        if (!idsReceitas.isEmpty()) {
            Map<Integer, String[]> observacoesMap = buscarObservacoesDeReceitas(idsReceitas);
            for (Receita receita : receitas) {
                receita.setObservacoes(observacoesMap.getOrDefault(receita.getIdReceita(), new String[0]));
            }
        }
        
        return receitas;
    }

    public Map<Integer, String[]> buscarObservacoesDeReceitas(List<Integer> idsReceitas) {
        if (idsReceitas == null || idsReceitas.isEmpty()) return new HashMap<>();
        
        Map<Integer, List<String>> obsMap = new HashMap<>();
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < idsReceitas.size(); i++) {
            if (i > 0) placeholders.append(",");
            placeholders.append("?");
        }
        
        String sql = "SELECT id_receita, observacao FROM receita_observacoes " +
                    "WHERE id_receita IN (" + placeholders + ") " +
                    "ORDER BY id_receita, ordem";
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < idsReceitas.size(); i++) {
                pstmt.setInt(i + 1, idsReceitas.get(i));
            }
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                int idReceita = rs.getInt("id_receita");
                String observacao = rs.getString("observacao");
                obsMap.computeIfAbsent(idReceita, k -> new ArrayList<>()).add(observacao);
            }
        } catch (SQLException e) {}
        
        Map<Integer, String[]> resultado = new HashMap<>();
        for (Map.Entry<Integer, List<String>> entry : obsMap.entrySet()) {
            resultado.put(entry.getKey(), entry.getValue().toArray(new String[0]));
        }
        return resultado;
    }

    public void excluirReceita(int idReceita, int idUsuario) {
        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);
            
            String sqlBuscar = "SELECT id_receita, descricao, valor, data, frequencia, id_usuario, id_conta, " +
                              "proxima_recorrencia, id_receita_original, ativo " +
                              "FROM receitas WHERE id_receita = ?";
            Receita receita = null;
            try (PreparedStatement pstmt = conn.prepareStatement(sqlBuscar)) {
                pstmt.setInt(1, idReceita);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) receita = mapReceita(rs);
            }
            
            if (receita == null) throw new IllegalArgumentException("Receita não encontrada");
            if (!receita.isAtivo()) throw new IllegalArgumentException("Receita já foi excluída");
            
            // Valida que a receita pertence ao usuário
            if (receita.getIdUsuario() != idUsuario) {
                throw new IllegalArgumentException("Você não tem permissão para excluir esta receita");
            }
            
            String sql = "UPDATE receitas SET ativo = FALSE WHERE id_receita = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, idReceita);
                pstmt.executeUpdate();
            }
            
            String sqlConta = "UPDATE contas SET saldo_atual = saldo_atual - ? WHERE id_conta = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sqlConta)) {
                pstmt.setDouble(1, receita.getValor());
                pstmt.setInt(2, receita.getIdConta());
                pstmt.executeUpdate();
            }
            
            conn.commit();
        } catch (SQLException e) {
            if (conn != null) try { conn.rollback(); } catch (SQLException ex) {}
            throw new RuntimeException("Erro ao excluir receita: " + e.getMessage(), e);
        } finally {
            if (conn != null) try { conn.close(); } catch (SQLException e) {}
        }
    }

    public double calcularTotalReceitasUsuario(int idUsuario) {
        // Exclui pagamentos de faturas (receitas em cartão de crédito que não são parcelas)
        // e receitas do sistema (com prefixo [SISTEMA])
        String sql = "SELECT COALESCE(SUM(r.valor), 0) as total " +
                    "FROM receitas r " +
                    "INNER JOIN contas c ON r.id_conta = c.id_conta " +
                    "WHERE r.id_usuario = ? " +
                    "AND r.ativo = TRUE " +
                    "AND r.descricao NOT LIKE '[SISTEMA]%' " +
                    "AND (c.tipo IS NULL OR " +
                    "     (UPPER(c.tipo) NOT LIKE 'CARTAO%' AND UPPER(c.tipo) != 'CARTAO_CREDITO') OR " +
                    "     r.id_grupo_parcela IS NOT NULL)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, idUsuario);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return rs.getDouble("total");
            return 0.0;
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao calcular total: " + e.getMessage(), e);
        }
    }
    
    /**
     * Calcula o total de receitas de uma conta em um período específico
     * Usado para verificar quanto já foi pago de uma fatura de cartão de crédito
     */
    public double calcularTotalReceitasPorPeriodoEConta(int idUsuario, int idConta, LocalDate dataInicio, LocalDate dataFim) {
        String sql = "SELECT COALESCE(SUM(valor), 0) as total " +
                    "FROM receitas " +
                    "WHERE id_usuario = ? AND id_conta = ? AND ativo = TRUE " +
                    "AND data >= ? AND data < ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, idUsuario);
            pstmt.setInt(2, idConta);
            pstmt.setDate(3, java.sql.Date.valueOf(dataInicio));
            pstmt.setDate(4, java.sql.Date.valueOf(dataFim));
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return rs.getDouble("total");
            return 0.0;
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao calcular total de receitas por período: " + e.getMessage(), e);
        }
    }
    
    /**
     * Calcula o total já pago de uma fatura considerando receitas e parcelas pagas antecipadamente
     * @param expenseRepository Repositório de gastos para calcular parcelas pagas
     */
    public double calcularTotalPagoFatura(int idUsuario, int idConta, LocalDate dataInicio, LocalDate dataFim, ExpenseRepository expenseRepository) {
        double totalReceitas = calcularTotalReceitasPorPeriodoEConta(idUsuario, idConta, dataInicio, dataFim);
        double totalParcelasPagas = expenseRepository.calcularTotalParcelasPagasPorPeriodoEConta(idConta, idUsuario, dataInicio, dataFim);
        return totalReceitas + totalParcelasPagas;
    }

    public List<Receita> buscarReceitasComRecorrenciaPendente(LocalDate hoje) {
        String sql = "SELECT id_receita, descricao, valor, data, frequencia, id_usuario, id_conta, " +
                    "proxima_recorrencia, id_receita_original, ativo " +
                    "FROM receitas " +
                    "WHERE proxima_recorrencia IS NOT NULL " +
                    "AND proxima_recorrencia <= ? " +
                    "AND ativo = TRUE";
        List<Receita> receitas = new ArrayList<>();
        List<Integer> idsReceitas = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setDate(1, java.sql.Date.valueOf(hoje));
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                Receita receita = mapReceita(rs);
                idsReceitas.add(receita.getIdReceita());
                receitas.add(receita);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar receitas recorrentes: " + e.getMessage(), e);
        }
        
        // Busca observações em lote para evitar N+1 queries
        if (!idsReceitas.isEmpty()) {
            Map<Integer, String[]> observacoesMap = buscarObservacoesDeReceitas(idsReceitas);
            for (Receita receita : receitas) {
                receita.setObservacoes(observacoesMap.getOrDefault(receita.getIdReceita(), new String[0]));
            }
        }
        
        return receitas;
    }

    public void marcarComoRecorrencia(int idReceita, int idReceitaOriginal, String frequencia, LocalDate proximaRecorrencia) {
        String sql = "UPDATE receitas SET id_receita_original = ?, frequencia = ?, proxima_recorrencia = ? WHERE id_receita = ?";
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, idReceitaOriginal);
                pstmt.setString(2, frequencia);
                pstmt.setDate(3, proximaRecorrencia != null ? java.sql.Date.valueOf(proximaRecorrencia) : null);
                pstmt.setInt(4, idReceita);
                pstmt.executeUpdate();
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao marcar como recorrência: " + e.getMessage(), e);
        }
    }

    public void atualizarProximaRecorrencia(int idReceita, LocalDate novaRecorrencia) {
        String sql = "UPDATE receitas SET proxima_recorrencia = ? WHERE id_receita = ?";
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setDate(1, novaRecorrencia != null ? java.sql.Date.valueOf(novaRecorrencia) : null);
                pstmt.setInt(2, idReceita);
                pstmt.executeUpdate();
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao atualizar recorrência: " + e.getMessage(), e);
        }
    }

    public LocalDate calcularProximaRecorrencia(LocalDate dataBase, String freq) {
        if (freq == null || freq.equals("Único") || freq.equals("UNICA")) return null;
        switch (freq.toUpperCase()) {
            case "SEMANAL": return dataBase.plusWeeks(1);
            case "MENSAL": return dataBase.plusMonths(1);
            case "ANUAL": return dataBase.plusYears(1);
            default: return null;
        }
    }
    
    /**
     * Atualiza informações de parcela em uma receita
     */
    public void atualizarInformacoesParcela(int idReceita, int idGrupoParcela, int numeroParcela, int totalParcelas) {
        String sql = "UPDATE receitas SET id_grupo_parcela = ?, numero_parcela = ?, total_parcelas = ? WHERE id_receita = ?";
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, idGrupoParcela);
                pstmt.setInt(2, numeroParcela);
                pstmt.setInt(3, totalParcelas);
                pstmt.setInt(4, idReceita);
                pstmt.executeUpdate();
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao atualizar informações de parcela: " + e.getMessage(), e);
        }
    }
    
    /**
     * Busca receitas de um grupo de parcelas
     */
    public List<Receita> buscarReceitasPorGrupoParcela(int idGrupoParcela) {
        // Retorna TODAS as parcelas do grupo, incluindo pagas e excluídas
        // O frontend decide quais são pagas baseado na data (parcelas com data anterior à data atual são consideradas pagas)
        String sql = "SELECT id_receita, descricao, valor, data, frequencia, id_usuario, id_conta, " +
                    "proxima_recorrencia, id_receita_original, ativo, id_grupo_parcela, numero_parcela, total_parcelas " +
                    "FROM receitas " +
                    "WHERE id_grupo_parcela = ? " +
                    "ORDER BY numero_parcela ASC";
        List<Receita> receitas = new ArrayList<>();
        List<Integer> idsReceitas = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, idGrupoParcela);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                Receita receita = mapReceita(rs);
                idsReceitas.add(receita.getIdReceita());
                receitas.add(receita);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar receitas do grupo de parcelas: " + e.getMessage(), e);
        }
        
        // Busca observações em lote para evitar N+1 queries
        if (!idsReceitas.isEmpty()) {
            Map<Integer, String[]> observacoesMap = buscarObservacoesDeReceitas(idsReceitas);
            for (Receita receita : receitas) {
                receita.setObservacoes(observacoesMap.getOrDefault(receita.getIdReceita(), new String[0]));
            }
        }
        
        return receitas;
    }
    
    /**
     * Cancela parcelas futuras de um grupo (soft delete)
     */
    public int cancelarParcelasFuturas(int idGrupoParcela, LocalDate dataLimite) {
        String sql = "UPDATE receitas SET ativo = FALSE WHERE id_grupo_parcela = ? AND data > ? AND ativo = TRUE";
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, idGrupoParcela);
                pstmt.setDate(2, java.sql.Date.valueOf(dataLimite));
                int canceladas = pstmt.executeUpdate();
                conn.commit();
                return canceladas;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao cancelar parcelas futuras: " + e.getMessage(), e);
        }
    }
}

