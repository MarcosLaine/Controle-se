package server.repository;

import server.database.DatabaseConnection;
import server.model.CompoundInterestCalculation;
import server.utils.JsonUtil;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CompoundInterestRepository {

    private Connection getConnection() throws SQLException {
        return DatabaseConnection.getInstance().getConnection();
    }

    public int salvarCalculo(CompoundInterestCalculation calculo) {
        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);
            
            // Serializa monthlyData como JSON
            String monthlyDataJson = JsonUtil.toJson(calculo.getMonthlyData());
            
            String sql = "INSERT INTO compound_interest_calculations " +
                        "(id_usuario, aporte_inicial, aporte_mensal, frequencia_aporte, " +
                        "taxa_juros, tipo_taxa, prazo, tipo_prazo, total_investido, " +
                        "saldo_final, total_juros, monthly_data, data_calculo, ativo) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, true) " +
                        "RETURNING id_calculo";
            
            int idCalculo;
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, calculo.getIdUsuario());
                pstmt.setDouble(2, calculo.getAporteInicial());
                pstmt.setDouble(3, calculo.getAporteMensal());
                pstmt.setString(4, calculo.getFrequenciaAporte());
                pstmt.setDouble(5, calculo.getTaxaJuros());
                pstmt.setString(6, calculo.getTipoTaxa());
                pstmt.setInt(7, calculo.getPrazo());
                pstmt.setString(8, calculo.getTipoPrazo());
                pstmt.setDouble(9, calculo.getTotalInvestido());
                pstmt.setDouble(10, calculo.getSaldoFinal());
                pstmt.setDouble(11, calculo.getTotalJuros());
                pstmt.setString(12, monthlyDataJson);
                pstmt.setTimestamp(13, Timestamp.valueOf(calculo.getDataCalculo()));
                
                ResultSet rs = pstmt.executeQuery();
                if (!rs.next()) throw new RuntimeException("Erro ao salvar cálculo");
                idCalculo = rs.getInt(1);
            }
            
            conn.commit();
            return idCalculo;
            
        } catch (SQLException e) {
            if (conn != null) try { conn.rollback(); } catch (SQLException ex) {}
            throw new RuntimeException("Erro ao salvar cálculo: " + e.getMessage(), e);
        } finally {
            if (conn != null) try { conn.close(); } catch (SQLException e) {}
        }
    }

    public List<CompoundInterestCalculation> buscarCalculosPorUsuario(int idUsuario) {
        List<CompoundInterestCalculation> calculos = new ArrayList<>();
        
        String sql = "SELECT id_calculo, id_usuario, aporte_inicial, aporte_mensal, frequencia_aporte, " +
                    "taxa_juros, tipo_taxa, prazo, tipo_prazo, total_investido, saldo_final, " +
                    "total_juros, monthly_data, data_calculo " +
                    "FROM compound_interest_calculations " +
                    "WHERE id_usuario = ? AND ativo = true " +
                    "ORDER BY data_calculo DESC";
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, idUsuario);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    CompoundInterestCalculation calculo = new CompoundInterestCalculation(
                        rs.getInt("id_calculo"),
                        rs.getInt("id_usuario"),
                        rs.getDouble("aporte_inicial"),
                        rs.getDouble("aporte_mensal"),
                        rs.getString("frequencia_aporte"),
                        rs.getDouble("taxa_juros"),
                        rs.getString("tipo_taxa"),
                        rs.getInt("prazo"),
                        rs.getString("tipo_prazo"),
                        rs.getDouble("total_investido"),
                        rs.getDouble("saldo_final"),
                        rs.getDouble("total_juros")
                    );
                    
                    Timestamp timestamp = rs.getTimestamp("data_calculo");
                    if (timestamp != null) {
                        calculo.setDataCalculo(timestamp.toLocalDateTime());
                    }
                    
                    // Deserializa monthlyData do JSON
                    String monthlyDataJson = rs.getString("monthly_data");
                    if (monthlyDataJson != null && !monthlyDataJson.isEmpty()) {
                        try {
                            List<Object> arrayData = JsonUtil.parseJsonArray(monthlyDataJson);
                            List<Map<String, Object>> monthlyData = new ArrayList<>();
                            for (Object item : arrayData) {
                                if (item instanceof Map) {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> map = (Map<String, Object>) item;
                                    monthlyData.add(map);
                                }
                            }
                            calculo.setMonthlyData(monthlyData);
                        } catch (Exception e) {
                            // Se falhar, deixa null
                        }
                    }
                    
                    calculos.add(calculo);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar cálculos: " + e.getMessage(), e);
        }
        
        return calculos;
    }

    public boolean excluirCalculo(int idCalculo, int idUsuario) {
        String sql = "UPDATE compound_interest_calculations SET ativo = false " +
                    "WHERE id_calculo = ? AND id_usuario = ?";
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, idCalculo);
            pstmt.setInt(2, idUsuario);
            
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao excluir cálculo: " + e.getMessage(), e);
        }
    }
}

