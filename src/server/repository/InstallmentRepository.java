package server.repository;

import server.database.DatabaseConnection;
import server.model.InstallmentGroup;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class InstallmentRepository {
    
    private Connection getConnection() throws SQLException {
        return DatabaseConnection.getInstance().getConnection();
    }
    
    /**
     * Cria um novo grupo de parcelas
     */
    public int criarGrupoParcelas(InstallmentGroup grupo) throws SQLException {
        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);
            
            String sql = "INSERT INTO installment_groups " +
                        "(descricao, valor_total, numero_parcelas, valor_parcela, " +
                        "data_primeira_parcela, intervalo_dias, id_usuario, id_conta, tipo_transacao, ativo) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id_grupo";
            
            int idGrupo;
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, grupo.getDescricao());
                pstmt.setDouble(2, grupo.getValorTotal());
                pstmt.setInt(3, grupo.getNumeroParcelas());
                pstmt.setDouble(4, grupo.getValorParcela());
                pstmt.setDate(5, java.sql.Date.valueOf(grupo.getDataPrimeiraParcela()));
                pstmt.setInt(6, grupo.getIntervaloDias());
                pstmt.setInt(7, grupo.getIdUsuario());
                pstmt.setInt(8, grupo.getIdConta());
                pstmt.setString(9, grupo.getTipoTransacao());
                pstmt.setBoolean(10, grupo.isAtivo());
                
                ResultSet rs = pstmt.executeQuery();
                if (!rs.next()) {
                    throw new RuntimeException("Erro ao criar grupo de parcelas");
                }
                idGrupo = rs.getInt(1);
            }
            
            conn.commit();
            return idGrupo;
        } catch (SQLException e) {
            if (conn != null) {
                conn.rollback();
            }
            throw e;
        } finally {
            if (conn != null) {
                conn.setAutoCommit(true);
                conn.close();
            }
        }
    }
    
    /**
     * Busca um grupo de parcelas por ID
     */
    public InstallmentGroup buscarGrupoPorId(int idGrupo) throws SQLException {
        String sql = "SELECT id_grupo, descricao, valor_total, numero_parcelas, valor_parcela, " +
                    "data_primeira_parcela, intervalo_dias, id_usuario, id_conta, tipo_transacao, ativo " +
                    "FROM installment_groups WHERE id_grupo = ? AND ativo = TRUE";
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, idGrupo);
            
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return mapearGrupo(rs);
            }
            return null;
        }
    }
    
    /**
     * Lista todos os grupos de parcelas de um usuário
     */
    public List<InstallmentGroup> listarGruposPorUsuario(int idUsuario, String tipoTransacao) throws SQLException {
        String sql = "SELECT id_grupo, descricao, valor_total, numero_parcelas, valor_parcela, " +
                    "data_primeira_parcela, intervalo_dias, id_usuario, id_conta, tipo_transacao, ativo " +
                    "FROM installment_groups WHERE id_usuario = ? AND ativo = TRUE";
        
        if (tipoTransacao != null && !tipoTransacao.isEmpty()) {
            sql += " AND tipo_transacao = ?";
        }
        sql += " ORDER BY created_at DESC";
        
        List<InstallmentGroup> grupos = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, idUsuario);
            if (tipoTransacao != null && !tipoTransacao.isEmpty()) {
                pstmt.setString(2, tipoTransacao);
            }
            
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                grupos.add(mapearGrupo(rs));
            }
        }
        return grupos;
    }
    
    /**
     * Desativa um grupo de parcelas (soft delete)
     */
    public void desativarGrupo(int idGrupo) throws SQLException {
        String sql = "UPDATE installment_groups SET ativo = FALSE, updated_at = CURRENT_TIMESTAMP WHERE id_grupo = ?";
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, idGrupo);
            pstmt.executeUpdate();
        }
    }
    
    /**
     * Mapeia um ResultSet para InstallmentGroup
     */
    private InstallmentGroup mapearGrupo(ResultSet rs) throws SQLException {
        InstallmentGroup grupo = new InstallmentGroup();
        grupo.setIdGrupo(rs.getInt("id_grupo"));
        grupo.setDescricao(rs.getString("descricao"));
        grupo.setValorTotal(rs.getDouble("valor_total"));
        grupo.setNumeroParcelas(rs.getInt("numero_parcelas"));
        grupo.setValorParcela(rs.getDouble("valor_parcela"));
        grupo.setDataPrimeiraParcela(rs.getDate("data_primeira_parcela").toLocalDate());
        grupo.setIntervaloDias(rs.getInt("intervalo_dias"));
        grupo.setIdUsuario(rs.getInt("id_usuario"));
        grupo.setIdConta(rs.getInt("id_conta"));
        grupo.setTipoTransacao(rs.getString("tipo_transacao"));
        grupo.setAtivo(rs.getBoolean("ativo"));
        return grupo;
    }
}

