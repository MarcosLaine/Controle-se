package server.repository;

import server.database.DatabaseConnection;
import server.model.Categoria;
import server.validation.InputValidator;
import server.validation.ValidationResult;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CategoryRepository {

    private Connection getConnection() throws SQLException {
        return DatabaseConnection.getInstance().getConnection();
    }

    private void validateInput(String field, String value, int maxLength) {
        ValidationResult res = InputValidator.validateString(field, value, maxLength, true);
        if (!res.isValid()) throw new IllegalArgumentException(res.getErrors().get(0));
    }

    public int cadastrarCategoria(String nome, int idUsuario) {
        validateInput("Nome da categoria", nome, 50);
        
        String sql = "INSERT INTO categorias (nome, id_usuario) VALUES (?, ?) RETURNING id_categoria";
        
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, nome);
                pstmt.setInt(2, idUsuario);
                
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    int idCategoria = rs.getInt(1);
                    conn.commit();
                    return idCategoria;
                }
                throw new RuntimeException("Erro ao cadastrar categoria");
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao cadastrar categoria: " + e.getMessage(), e);
        }
    }

    public int cadastrarCategoriaComOrcamento(String nome, int idUsuario, double valorOrcamento, String periodoOrcamento) {
        validateInput("Nome da categoria", nome, 50);
        
        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);
            
            // 1. Cadastra a categoria
            int idCategoria;
            String sqlCat = "INSERT INTO categorias (nome, id_usuario) VALUES (?, ?) RETURNING id_categoria";
            try (PreparedStatement pstmt = conn.prepareStatement(sqlCat)) {
                pstmt.setString(1, nome);
                pstmt.setInt(2, idUsuario);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    idCategoria = rs.getInt(1);
                } else {
                    throw new RuntimeException("Erro ao cadastrar categoria");
                }
            }
            
            // 2. Cadastra o orçamento se valor > 0
            if (valorOrcamento > 0) {
                String sqlOrc = "INSERT INTO orcamentos (valor_planejado, periodo, id_categoria, id_usuario) VALUES (?, ?, ?, ?)";
                try (PreparedStatement pstmt = conn.prepareStatement(sqlOrc)) {
                    pstmt.setDouble(1, valorOrcamento);
                    pstmt.setString(2, periodoOrcamento);
                    pstmt.setInt(3, idCategoria);
                    pstmt.setInt(4, idUsuario);
                    pstmt.executeUpdate();
                }
            }
            
            conn.commit();
            return idCategoria;
        } catch (SQLException e) {
            if (conn != null) try { conn.rollback(); } catch (SQLException ex) {}
            throw new RuntimeException("Erro ao cadastrar categoria com orçamento: " + e.getMessage(), e);
        } finally {
            if (conn != null) try { conn.close(); } catch (SQLException e) {}
        }
    }

    public Categoria buscarCategoria(int idCategoria) {
        String sql = "SELECT id_categoria, nome, id_usuario, ativo FROM categorias WHERE id_categoria = ? AND ativo = TRUE";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, idCategoria);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return mapCategoria(rs);
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar categoria: " + e.getMessage(), e);
        }
    }

    public int obterOuCriarCategoriaSemCategoria(int idUsuario) {
        String nome = "Sem Categoria";
        // Busca usando UPPER para garantir que encontra mesmo com variações de case
        String sqlBusca = "SELECT id_categoria FROM categorias WHERE id_usuario = ? AND UPPER(nome) = UPPER(?) AND ativo = TRUE";
        
        try (Connection conn = getConnection()) {
            try (PreparedStatement pstmt = conn.prepareStatement(sqlBusca)) {
                pstmt.setInt(1, idUsuario);
                pstmt.setString(2, nome);
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) return rs.getInt(1);
            }
            
            // Se não existe, cria
            return cadastrarCategoria(nome, idUsuario);
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao obter categoria padrao: " + e.getMessage(), e);
        }
    }

    public List<Categoria> buscarCategoriasPorUsuario(int idUsuario) {
        // Exclui a categoria "Sem Categoria" da listagem para o usuário
        String sql = "SELECT id_categoria, nome, id_usuario, ativo FROM categorias WHERE id_usuario = ? AND ativo = TRUE AND UPPER(nome) != 'SEM CATEGORIA' ORDER BY nome";
        List<Categoria> categorias = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, idUsuario);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) categorias.add(mapCategoria(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar categorias: " + e.getMessage(), e);
        }
        return categorias;
    }

    public void atualizarCategoria(int idCategoria, String novoNome) {
        validateInput("Nome da categoria", novoNome, 50);
        String sql = "UPDATE categorias SET nome = ? WHERE id_categoria = ?";
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, novoNome);
                pstmt.setInt(2, idCategoria);
                pstmt.executeUpdate();
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao atualizar categoria: " + e.getMessage(), e);
        }
    }

    public void excluirCategoria(int idCategoria) {
        String sql = "DELETE FROM categorias WHERE id_categoria = ?";
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, idCategoria);
                int deleted = pstmt.executeUpdate();
                if (deleted == 0) throw new IllegalArgumentException("Categoria não encontrada");
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao excluir categoria: " + e.getMessage(), e);
        }
    }

    public double calcularTotalGastosPorCategoria(int idCategoria) {
        String sql = "SELECT COALESCE(SUM(g.valor), 0) as total " +
                     "FROM gastos g " +
                     "JOIN categoria_gasto cg ON g.id_gasto = cg.id_gasto " +
                     "WHERE cg.id_categoria = ? AND g.ativo = TRUE AND cg.ativo = TRUE";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, idCategoria);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return rs.getDouble("total");
            return 0.0;
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao calcular total: " + e.getMessage(), e);
        }
    }

    public double calcularTotalGastosPorCategoriaEUsuario(int idCategoria, int idUsuario) {
        String sql = "SELECT COALESCE(SUM(g.valor), 0) as total " +
                     "FROM gastos g " +
                     "JOIN categoria_gasto cg ON g.id_gasto = cg.id_gasto " +
                     "WHERE cg.id_categoria = ? AND g.id_usuario = ? AND g.ativo = TRUE AND cg.ativo = TRUE";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, idCategoria);
            pstmt.setInt(2, idUsuario);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return rs.getDouble("total");
            return 0.0;
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao calcular total: " + e.getMessage(), e);
        }
    }

    public Map<Integer, Double> calcularTotalGastosPorTodasCategoriasEUsuario(int idUsuario) {
        Map<Integer, Double> totais = new HashMap<>();
        String sql = "SELECT cg.id_categoria, COALESCE(SUM(g.valor), 0) as total " +
                     "FROM gastos g " +
                     "JOIN categoria_gasto cg ON g.id_gasto = cg.id_gasto " +
                     "WHERE g.id_usuario = ? AND g.ativo = TRUE AND cg.ativo = TRUE " +
                     "GROUP BY cg.id_categoria";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, idUsuario);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                totais.put(rs.getInt("id_categoria"), rs.getDouble("total"));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao calcular totais: " + e.getMessage(), e);
        }
        return totais;
    }

    public List<Categoria> buscarCategoriasDoGasto(int idGasto) {
        String sql = "SELECT c.id_categoria, c.nome, c.id_usuario, c.ativo " +
                    "FROM categorias c " +
                    "INNER JOIN categoria_gasto cg ON c.id_categoria = cg.id_categoria " +
                    "WHERE cg.id_gasto = ? AND cg.ativo = TRUE AND c.ativo = TRUE";
        List<Categoria> categorias = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, idGasto);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) categorias.add(mapCategoria(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar categorias do gasto: " + e.getMessage(), e);
        }
        return categorias;
    }

    private Categoria mapCategoria(ResultSet rs) throws SQLException {
        Categoria categoria = new Categoria(
            rs.getInt("id_categoria"),
            rs.getString("nome"),
            rs.getInt("id_usuario")
        );
        categoria.setAtivo(rs.getBoolean("ativo"));
        return categoria;
    }
}

