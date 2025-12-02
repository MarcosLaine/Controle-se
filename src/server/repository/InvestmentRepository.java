package server.repository;

import server.database.DatabaseConnection;
import server.model.Investimento;
import server.validation.InputValidator;
import server.validation.ValidationResult;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class InvestmentRepository {

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

    public int cadastrarInvestimento(String nome, String nomeAtivo, String categoria, double quantidade, 
                                     double precoAporte, double corretagem, String corretora, 
                                     LocalDate dataAporte, int idUsuario, int idConta, String moeda) {
        
        validateInput("Nome do investimento", nome, 50);
        validateInput("Categoria", categoria, 50);
        validateId("ID do usuário", idUsuario);
        validateId("ID da conta", idConta);
        
        // Permite quantidades negativas para vendas, mas não permite zero
        if (quantidade == 0 || Double.isNaN(quantidade) || Double.isInfinite(quantidade)) {
            throw new IllegalArgumentException("A quantidade deve ser diferente de zero");
        }
        if (precoAporte < 0) throw new IllegalArgumentException("Preço de aporte não pode ser negativo");
        if (corretagem < 0) throw new IllegalArgumentException("Corretagem não pode ser negativa");
        if (dataAporte == null) throw new IllegalArgumentException("Data de aporte é obrigatória");
        
        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);
            
            String sql = "INSERT INTO investimentos (nome, nome_ativo, categoria, quantidade, preco_aporte, corretagem, corretora, data_aporte, id_usuario, id_conta, moeda) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id_investimento";
            
            int idInvestimento;
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, nome);
                pstmt.setString(2, nomeAtivo);
                pstmt.setString(3, categoria.toUpperCase());
                pstmt.setDouble(4, quantidade);
                pstmt.setDouble(5, precoAporte);
                pstmt.setDouble(6, corretagem);
                pstmt.setString(7, corretora);
                pstmt.setDate(8, java.sql.Date.valueOf(dataAporte));
                pstmt.setInt(9, idUsuario);
                pstmt.setInt(10, idConta);
                pstmt.setString(11, moeda != null ? moeda : "BRL");
                
                ResultSet rs = pstmt.executeQuery();
                if (!rs.next()) throw new RuntimeException("Erro ao cadastrar investimento");
                idInvestimento = rs.getInt(1);
            }
            
            // Debita da conta
            String sqlConta = "UPDATE contas SET saldo_atual = saldo_atual - ? WHERE id_conta = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sqlConta)) {
                double total = (quantidade * precoAporte) + corretagem;
                pstmt.setDouble(1, total);
                pstmt.setInt(2, idConta);
                pstmt.executeUpdate();
            }
            
            conn.commit();
            return idInvestimento;
            
        } catch (SQLException e) {
            if (conn != null) try { conn.rollback(); } catch (SQLException ex) {}
            throw new RuntimeException("Erro ao cadastrar investimento: " + e.getMessage(), e);
        } finally {
            if (conn != null) try { conn.close(); } catch (SQLException e) {}
        }
    }

    public int cadastrarInvestimento(String nome, String nomeAtivo, String categoria, double quantidade, 
                                    double precoAporte, double corretagem, String corretora,
                                    LocalDate dataAporte, int idUsuario, int idConta, String moeda,
                                    String tipoInvestimento, String tipoRentabilidade, String indice, 
                                    Double percentualIndice, Double taxaFixa, LocalDate dataVencimento) {
        validateInput("Nome do investimento", nome, 50);
        validateId("ID do usuário", idUsuario);
        validateId("ID da conta", idConta);
        
        // Permite quantidades negativas para vendas, mas não permite zero
        if (quantidade == 0 || Double.isNaN(quantidade) || Double.isInfinite(quantidade)) {
            throw new IllegalArgumentException("A quantidade deve ser diferente de zero");
        }
        if (precoAporte < 0) throw new IllegalArgumentException("Preço de aporte não pode ser negativo");
        if (corretagem < 0) throw new IllegalArgumentException("Corretagem não pode ser negativa");
        if (dataAporte == null) throw new IllegalArgumentException("Data de aporte é obrigatória");
        
        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);
            
            String sql = "INSERT INTO investimentos (nome, nome_ativo, categoria, quantidade, preco_aporte, " +
                        "corretagem, corretora, data_aporte, id_usuario, id_conta, moeda, tipo_investimento, " +
                        "tipo_rentabilidade, indice, percentual_indice, taxa_fixa, data_vencimento, ativo) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE) RETURNING id_investimento";
            
            int idInvestimento;
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, nome);
                pstmt.setString(2, nomeAtivo);
                pstmt.setString(3, categoria);
                pstmt.setDouble(4, quantidade);
                pstmt.setDouble(5, precoAporte);
                pstmt.setDouble(6, corretagem);
                pstmt.setString(7, corretora);
                pstmt.setDate(8, java.sql.Date.valueOf(dataAporte));
                pstmt.setInt(9, idUsuario);
                pstmt.setInt(10, idConta);
                pstmt.setString(11, moeda);
                pstmt.setString(12, tipoInvestimento);
                pstmt.setString(13, tipoRentabilidade);
                pstmt.setString(14, indice);
                pstmt.setObject(15, percentualIndice);
                pstmt.setObject(16, taxaFixa);
                pstmt.setObject(17, dataVencimento != null ? java.sql.Date.valueOf(dataVencimento) : null);
                
                ResultSet rs = pstmt.executeQuery();
                if (!rs.next()) throw new RuntimeException("Erro ao cadastrar investimento");
                idInvestimento = rs.getInt(1);
            }
            
            // Debita da conta
            String sqlConta = "UPDATE contas SET saldo_atual = saldo_atual - ? WHERE id_conta = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sqlConta)) {
                double total = (quantidade * precoAporte) + corretagem;
                pstmt.setDouble(1, total);
                pstmt.setInt(2, idConta);
                pstmt.executeUpdate();
            }
            
            conn.commit();
            return idInvestimento;
            
        } catch (SQLException e) {
            if (conn != null) try { conn.rollback(); } catch (SQLException ex) {}
            throw new RuntimeException("Erro ao cadastrar investimento: " + e.getMessage(), e);
        } finally {
            if (conn != null) try { conn.close(); } catch (SQLException e) {}
        }
    }

    // Overloads...
    public int cadastrarInvestimento(String nome, String categoria, double quantidade, 
                                     double precoAporte, double corretagem, String corretora, 
                                     LocalDate dataAporte, int idUsuario, int idConta, String moeda) {
        return cadastrarInvestimento(nome, null, categoria, quantidade, precoAporte, corretagem, corretora, dataAporte, idUsuario, idConta, moeda, null, null, null, null, null, null);
    }

    public Investimento buscarInvestimento(int idInvestimento) {
        String sql = "SELECT * FROM investimentos WHERE id_investimento = ? AND ativo = TRUE";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, idInvestimento);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return mapInvestimento(rs);
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar investimento: " + e.getMessage(), e);
        }
    }

    public List<Investimento> buscarInvestimentosPorUsuario(int idUsuario) {
        String sql = "SELECT * FROM investimentos WHERE id_usuario = ? AND ativo = TRUE ORDER BY data_aporte DESC";
        List<Investimento> investimentos = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, idUsuario);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) investimentos.add(mapInvestimento(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar investimentos: " + e.getMessage(), e);
        }
        return investimentos;
    }

    public void atualizarInvestimento(int idInvestimento, String nome, String nomeAtivo, String categoria, 
                                      double quantidade, double precoAporte, double corretagem, 
                                      String corretora, LocalDate dataAporte, String moeda) {
        String sql = "UPDATE investimentos SET nome = ?, nome_ativo = ?, categoria = ?, quantidade = ?, preco_aporte = ?, corretagem = ?, corretora = ?, data_aporte = ?, moeda = ? WHERE id_investimento = ?";
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, nome);
                pstmt.setString(2, nomeAtivo);
                pstmt.setString(3, categoria);
                pstmt.setDouble(4, quantidade);
                pstmt.setDouble(5, precoAporte);
                pstmt.setDouble(6, corretagem);
                pstmt.setString(7, corretora);
                pstmt.setDate(8, java.sql.Date.valueOf(dataAporte));
                pstmt.setString(9, moeda);
                pstmt.setInt(10, idInvestimento);
                pstmt.executeUpdate();
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao atualizar investimento: " + e.getMessage(), e);
        }
    }

    public void excluirInvestimento(int idInvestimento) {
        // Should reverse balance? Usually yes.
        // I'll implement delete logic.
        Investimento inv = buscarInvestimento(idInvestimento);
        if (inv == null) throw new IllegalArgumentException("Investimento não encontrado");
        
        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);
            
            String sqlDelete = "DELETE FROM investimentos WHERE id_investimento = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sqlDelete)) {
                pstmt.setInt(1, idInvestimento);
                pstmt.executeUpdate();
            }
            
            // Estorna valor na conta
            String sqlConta = "UPDATE contas SET saldo_atual = saldo_atual + ? WHERE id_conta = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sqlConta)) {
                pstmt.setDouble(1, inv.getValorAporte());
                pstmt.setInt(2, inv.getIdConta());
                pstmt.executeUpdate();
            }
            
            conn.commit();
        } catch (SQLException e) {
            if (conn != null) try { conn.rollback(); } catch (SQLException ex) {}
            throw new RuntimeException("Erro ao excluir investimento: " + e.getMessage(), e);
        } finally {
            if (conn != null) try { conn.close(); } catch (SQLException e) {}
        }
    }

    private Investimento mapInvestimento(ResultSet rs) throws SQLException {
        Investimento inv = new Investimento(
            rs.getInt("id_investimento"),
            rs.getString("nome"),
            rs.getString("nome_ativo"),
            rs.getString("categoria"),
            rs.getDouble("quantidade"),
            rs.getDouble("preco_aporte"),
            rs.getDouble("corretagem"),
            rs.getString("corretora"),
            rs.getDate("data_aporte").toLocalDate(),
            rs.getInt("id_usuario"),
            rs.getInt("id_conta"),
            rs.getString("moeda")
        );
        
        try {
            inv.setTipoInvestimento(rs.getString("tipo_investimento"));
            inv.setTipoRentabilidade(rs.getString("tipo_rentabilidade"));
            inv.setIndice(rs.getString("indice"));
            
            Object pct = rs.getObject("percentual_indice");
            if (pct != null) inv.setPercentualIndice(((Number) pct).doubleValue());
            
            Object taxa = rs.getObject("taxa_fixa");
            if (taxa != null) inv.setTaxaFixa(((Number) taxa).doubleValue());
            
            java.sql.Date venc = rs.getDate("data_vencimento");
            if (venc != null) inv.setDataVencimento(venc.toLocalDate());
        } catch (SQLException e) {
            // Campos opcionais - ignora se não existirem
        }
        
        inv.setAtivo(rs.getBoolean("ativo"));
        return inv;
    }
}

