package server.repository;

import server.database.DatabaseConnection;
import server.model.Tag;
import server.validation.InputValidator;
import server.validation.ValidationResult;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TagRepository {

    private Connection getConnection() throws SQLException {
        return DatabaseConnection.getInstance().getConnection();
    }

    private void validateInput(String field, String value, int maxLength) {
        ValidationResult res = InputValidator.validateString(field, value, maxLength, true);
        if (!res.isValid()) throw new IllegalArgumentException(res.getErrors().get(0));
    }
    
    private void validateColor(String field, String color) {
        ValidationResult res = InputValidator.validateColor(field, color, true);
        if (!res.isValid()) throw new IllegalArgumentException(res.getErrors().get(0));
    }

    public int cadastrarTag(String nome, String cor, int idUsuario) {
        validateInput("Nome da tag", nome, 50);
        validateColor("Cor da tag", cor);
        
        String sql = "INSERT INTO tags (nome, cor, id_usuario) VALUES (?, ?, ?) RETURNING id_tag";
        
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, nome);
                pstmt.setString(2, cor);
                pstmt.setInt(3, idUsuario);
                
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    int idTag = rs.getInt(1);
                    conn.commit();
                    return idTag;
                }
                throw new RuntimeException("Erro ao cadastrar tag");
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao cadastrar tag: " + e.getMessage(), e);
        }
    }

    public Tag buscarTag(int idTag) {
        String sql = "SELECT id_tag, nome, cor, id_usuario, ativo FROM tags WHERE id_tag = ? AND ativo = TRUE";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, idTag);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) return mapTag(rs);
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar tag: " + e.getMessage(), e);
        }
    }

    public List<Tag> buscarTagsPorUsuario(int idUsuario) {
        String sql = "SELECT id_tag, nome, cor, id_usuario, ativo FROM tags WHERE id_usuario = ? AND ativo = TRUE ORDER BY nome";
        List<Tag> tags = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, idUsuario);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) tags.add(mapTag(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar tags: " + e.getMessage(), e);
        }
        return tags;
    }

    public void associarTagTransacao(int idTransacao, String tipoTransacao, int idTag) {
        String sql = "INSERT INTO transacao_tag (id_transacao, tipo_transacao, id_tag) VALUES (?, ?, ?) " +
                    "ON CONFLICT (id_transacao, tipo_transacao, id_tag) DO NOTHING";
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, idTransacao);
                pstmt.setString(2, tipoTransacao);
                pstmt.setInt(3, idTag);
                pstmt.executeUpdate();
                conn.commit();
            } catch (Exception e) {
                if (!e.getMessage().contains("duplicate") && !e.getMessage().contains("unique")) {
                    conn.rollback();
                    throw e;
                }
                // Ignore duplicates
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao associar tag: " + e.getMessage(), e);
        }
    }

    public List<Tag> buscarTagsGasto(int idGasto) {
        return buscarTagsTransacao(idGasto, "GASTO");
    }
    
    public List<Tag> buscarTagsReceita(int idReceita) {
        return buscarTagsTransacao(idReceita, "RECEITA");
    }
    
    private List<Tag> buscarTagsTransacao(int idTransacao, String tipo) {
        String sql = "SELECT t.id_tag, t.nome, t.cor, t.id_usuario, t.ativo " +
                    "FROM tags t " +
                    "INNER JOIN transacao_tag tt ON t.id_tag = tt.id_tag " +
                    "WHERE tt.id_transacao = ? AND tt.tipo_transacao = ? AND t.ativo = TRUE AND tt.ativo = TRUE";
        List<Tag> tags = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, idTransacao);
            pstmt.setString(2, tipo);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) tags.add(mapTag(rs));
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar tags da transação: " + e.getMessage(), e);
        }
        return tags;
    }
    
    public Map<Integer, List<Tag>> buscarTagsDeGastos(List<Integer> idsGastos) {
        return buscarTagsDeTransacoes(idsGastos, "GASTO");
    }
    
    public Map<Integer, List<Tag>> buscarTagsDeReceitas(List<Integer> idsReceitas) {
        return buscarTagsDeTransacoes(idsReceitas, "RECEITA");
    }
    
    private Map<Integer, List<Tag>> buscarTagsDeTransacoes(List<Integer> ids, String tipo) {
        if (ids == null || ids.isEmpty()) return new HashMap<>();
        Map<Integer, List<Tag>> map = new HashMap<>();
        StringBuilder ph = new StringBuilder();
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) ph.append(",");
            ph.append("?");
        }
        String sql = "SELECT tt.id_transacao, t.id_tag, t.nome, t.cor, t.id_usuario, t.ativo " +
                    "FROM tags t " +
                    "INNER JOIN transacao_tag tt ON t.id_tag = tt.id_tag " +
                    "WHERE tt.id_transacao IN (" + ph + ") AND tt.tipo_transacao = ? AND t.ativo = TRUE AND tt.ativo = TRUE";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < ids.size(); i++) pstmt.setInt(i + 1, ids.get(i));
            pstmt.setString(ids.size() + 1, tipo);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                int idTransacao = rs.getInt("id_transacao");
                Tag tag = mapTag(rs);
                map.computeIfAbsent(idTransacao, k -> new ArrayList<>()).add(tag);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar tags em batch: " + e.getMessage(), e);
        }
        return map;
    }

    public void atualizarTag(int idTag, String novoNome, String novaCor) {
        validateInput("Nome da tag", novoNome, 50);
        validateColor("Cor da tag", novaCor);
        
        String sql = "UPDATE tags SET nome = ?, cor = ? WHERE id_tag = ?";
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, novoNome);
                pstmt.setString(2, novaCor);
                pstmt.setInt(3, idTag);
                pstmt.executeUpdate();
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao atualizar tag: " + e.getMessage(), e);
        }
    }

    public void excluirTag(int idTag) {
        String sql = "DELETE FROM tags WHERE id_tag = ?";
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, idTag);
                int deleted = pstmt.executeUpdate();
                if (deleted == 0) throw new IllegalArgumentException("Tag não encontrada");
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao excluir tag: " + e.getMessage(), e);
        }
    }

    private Tag mapTag(ResultSet rs) throws SQLException {
        Tag tag = new Tag(
            rs.getInt("id_tag"),
            rs.getString("nome"),
            rs.getString("cor"),
            rs.getInt("id_usuario")
        );
        tag.setAtivo(rs.getBoolean("ativo"));
        return tag;
    }
}

