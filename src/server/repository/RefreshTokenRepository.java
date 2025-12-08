package server.repository;

import server.database.DatabaseConnection;
import java.security.MessageDigest;
import java.sql.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class RefreshTokenRepository {
    
    private Connection getConnection() throws SQLException {
        return DatabaseConnection.getInstance().getConnection();
    }
    
    /**
     * Gera hash SHA-256 do token para armazenamento seguro
     */
    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Erro ao gerar hash do token", e);
        }
    }
    
    /**
     * Salva um refresh token no banco de dados
     * @param userId ID do usuário
     * @param token Token em texto plano (será hasheado antes de salvar)
     * @param expiresAt Data de expiração
     * @return ID do refresh token salvo
     */
    public int saveRefreshToken(int userId, String token, Instant expiresAt) {
        String tokenHash = hashToken(token);
        String sql = "INSERT INTO refresh_tokens (id_usuario, token_hash, expires_at, revoked) VALUES (?, ?, ?, FALSE) RETURNING id_refresh_token";
        
        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);
            
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, userId);
                pstmt.setString(2, tokenHash);
                pstmt.setTimestamp(3, Timestamp.from(expiresAt));
                
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    int tokenId = rs.getInt(1);
                    conn.commit();
                    return tokenId;
                }
                throw new RuntimeException("Erro ao salvar refresh token");
            }
        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) {}
            }
            throw new RuntimeException("Erro ao salvar refresh token: " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                try { conn.close(); } catch (SQLException e) {}
            }
        }
    }
    
    /**
     * Valida um refresh token
     * @param token Token em texto plano
     * @return ID do usuário se o token for válido, -1 caso contrário
     */
    public int validateRefreshToken(String token) {
        String tokenHash = hashToken(token);
        String sql = "SELECT id_usuario FROM refresh_tokens WHERE token_hash = ? AND revoked = FALSE AND expires_at > CURRENT_TIMESTAMP";
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, tokenHash);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                return rs.getInt("id_usuario");
            }
            return -1;
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao validar refresh token: " + e.getMessage(), e);
        }
    }
    
    /**
     * Revoga um refresh token específico
     * @param token Token em texto plano
     */
    public void revokeToken(String token) {
        String tokenHash = hashToken(token);
        String sql = "UPDATE refresh_tokens SET revoked = TRUE WHERE token_hash = ?";
        
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, tokenHash);
                pstmt.executeUpdate();
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao revogar refresh token: " + e.getMessage(), e);
        }
    }
    
    /**
     * Revoga todos os refresh tokens de um usuário (útil em caso de comprometimento)
     * @param userId ID do usuário
     */
    public void revokeAllUserTokens(int userId) {
        String sql = "UPDATE refresh_tokens SET revoked = TRUE WHERE id_usuario = ? AND revoked = FALSE";
        
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, userId);
                pstmt.executeUpdate();
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao revogar tokens do usuário: " + e.getMessage(), e);
        }
    }
    
    /**
     * Remove tokens expirados (limpeza periódica)
     */
    public void deleteExpiredTokens() {
        String sql = "DELETE FROM refresh_tokens WHERE expires_at < CURRENT_TIMESTAMP";
        
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.executeUpdate();
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao deletar tokens expirados: " + e.getMessage(), e);
        }
    }
    
    /**
     * Implementa rotação de tokens: revoga o token antigo e retorna o userId
     * @param oldToken Token antigo a ser revogado
     * @return ID do usuário se o token for válido, -1 caso contrário
     */
    public int rotateToken(String oldToken) {
        int userId = validateRefreshToken(oldToken);
        if (userId > 0) {
            revokeToken(oldToken);
        }
        return userId;
    }
}

