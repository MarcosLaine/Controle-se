package server.repository;

import server.database.DatabaseConnection;
import server.model.Orcamento;
import server.validation.InputValidator;
import server.validation.ValidationResult;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class BudgetRepository {

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

    public int cadastrarOrcamento(double valorPlanejado, String periodo, int idCategoria, int idUsuario) {
        validateAmount("Valor planejado", valorPlanejado);
        validateId("ID da categoria", idCategoria);
        validateId("ID do usuário", idUsuario);
        
        String[] periodos = {"MENSAL", "ANUAL"};
        ValidationResult enumRes = InputValidator.validateEnum("Período", periodo, periodos, true);
        if (!enumRes.isValid()) throw new IllegalArgumentException(enumRes.getErrors().get(0));
        
        // Check duplicate
        String sqlCheck = "SELECT id_orcamento FROM orcamentos WHERE id_usuario = ? AND id_categoria = ? AND periodo = ? AND ativo = TRUE";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sqlCheck)) {
            pstmt.setInt(1, idUsuario);
            pstmt.setInt(2, idCategoria);
            pstmt.setString(3, periodo);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) throw new IllegalArgumentException("Já existe um orçamento ativo para esta categoria e período");
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao verificar duplicidade: " + e.getMessage(), e);
        }
        
        String sql = "INSERT INTO orcamentos (valor_planejado, periodo, id_categoria, id_usuario) VALUES (?, ?, ?, ?) RETURNING id_orcamento";
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setDouble(1, valorPlanejado);
                pstmt.setString(2, periodo);
                pstmt.setInt(3, idCategoria);
                pstmt.setInt(4, idUsuario);
                
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    int idOrcamento = rs.getInt(1);
                    conn.commit();
                    return idOrcamento;
                }
                throw new RuntimeException("Erro ao cadastrar orçamento");
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao cadastrar orçamento: " + e.getMessage(), e);
        }
    }

    public Orcamento buscarOrcamento(int idOrcamento) {
        String sql = "SELECT id_orcamento, valor_planejado, periodo, id_categoria, id_usuario, ativo FROM orcamentos WHERE id_orcamento = ? AND ativo = TRUE";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, idOrcamento);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return mapOrcamento(rs);
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar orçamento: " + e.getMessage(), e);
        }
    }

    public List<Orcamento> buscarOrcamentosPorUsuario(int idUsuario) {
        String sql = "SELECT id_orcamento, valor_planejado, periodo, id_categoria, id_usuario, ativo FROM orcamentos WHERE id_usuario = ? AND ativo = TRUE";
        List<Orcamento> orcamentos = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, idUsuario);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) orcamentos.add(mapOrcamento(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar orçamentos: " + e.getMessage(), e);
        }
        return orcamentos;
    }

    public void atualizarOrcamento(int idOrcamento, double novoValorPlanejado, String novoPeriodo) {
        validateAmount("Novo valor planejado", novoValorPlanejado);
        if (novoPeriodo != null) {
            String[] periodos = {"MENSAL", "ANUAL"};
            ValidationResult enumRes = InputValidator.validateEnum("Período", novoPeriodo, periodos, true);
            if (!enumRes.isValid()) throw new IllegalArgumentException(enumRes.getErrors().get(0));
        }
        
        String sql = "UPDATE orcamentos SET valor_planejado = ?, periodo = ? WHERE id_orcamento = ?";
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setDouble(1, novoValorPlanejado);
                pstmt.setString(2, novoPeriodo);
                pstmt.setInt(3, idOrcamento);
                pstmt.executeUpdate();
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao atualizar orçamento: " + e.getMessage(), e);
        }
    }

    public void excluirOrcamento(int idOrcamento) {
        String sql = "DELETE FROM orcamentos WHERE id_orcamento = ?";
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, idOrcamento);
                int deleted = pstmt.executeUpdate();
                if (deleted == 0) throw new IllegalArgumentException("Orçamento não encontrado");
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao excluir orçamento: " + e.getMessage(), e);
        }
    }

    private Orcamento mapOrcamento(ResultSet rs) throws SQLException {
        Orcamento orcamento = new Orcamento(
            rs.getInt("id_orcamento"),
            rs.getDouble("valor_planejado"),
            rs.getString("periodo"),
            rs.getInt("id_categoria"),
            rs.getInt("id_usuario")
        );
        orcamento.setAtivo(rs.getBoolean("ativo"));
        return orcamento;
    }
}

