package server.repository;

import server.database.DatabaseConnection;
import server.validation.InputValidator;
import server.validation.ValidationResult;
import server.model.Usuario;
import server.security.PasswordHasher;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

public class UserRepository {
    
    private Connection getConnection() throws SQLException {
        return DatabaseConnection.getInstance().getConnection();
    }

    public int cadastrarUsuario(String nome, String email, String senha) {
        ValidationResult nameRes = InputValidator.validateName("Nome", nome, true);
        if (!nameRes.isValid()) throw new IllegalArgumentException(nameRes.getErrors().get(0));
        
        ValidationResult emailRes = InputValidator.validateEmail(email, true);
        if (!emailRes.isValid()) throw new IllegalArgumentException(emailRes.getErrors().get(0));

        ValidationResult passRes = InputValidator.validatePassword(senha, true);
        if (!passRes.isValid()) throw new IllegalArgumentException(passRes.getErrors().get(0));

        synchronized (this) {
            String emailNormalizado = email.toLowerCase().trim();
            String senhaHash = PasswordHasher.hashPassword(senha);
            return salvarUsuario(nome, emailNormalizado, senhaHash);
        }
    }

    private int salvarUsuario(String nome, String email, String senhaArmazenada) {
        String sql = "INSERT INTO usuarios (nome, email, senha, ativo) VALUES (?, ?, ?, TRUE) RETURNING id_usuario";
        
        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false); // Transaction start
            
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, nome.trim());
                pstmt.setString(2, email);
                pstmt.setString(3, senhaArmazenada);
                
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    int idUsuario = rs.getInt(1);
                    conn.commit();
                    return idUsuario;
                }
                throw new RuntimeException("Erro ao cadastrar usuário");
            }
        } catch (SQLException e) {
            if (conn != null) {
                try { conn.rollback(); } catch (SQLException ex) {}
            }
            if (e.getSQLState() != null && e.getSQLState().equals("23505")) {
                throw new RuntimeException("Email já cadastrado!");
            }
            throw new RuntimeException("Erro ao cadastrar usuário: " + e.getMessage(), e);
        } finally {
            if (conn != null) {
                try { conn.close(); } catch (SQLException e) {}
            }
        }
    }

    public Usuario buscarUsuario(int idUsuario) {
        String sql = "SELECT id_usuario, nome, email, senha, ativo FROM usuarios WHERE id_usuario = ? AND ativo = TRUE";
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, idUsuario);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                return mapUsuario(rs);
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar usuário: " + e.getMessage(), e);
        }
    }

    public Usuario buscarUsuarioPorEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return null;
        }
        
        String emailNormalizado = email.toLowerCase().trim();
        String sql = "SELECT id_usuario, nome, email, senha, ativo FROM usuarios WHERE email = ? AND ativo = TRUE";
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, emailNormalizado);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                return mapUsuario(rs);
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar usuário por email: " + e.getMessage(), e);
        }
    }

    public Usuario buscarUsuarioSemAtivo(int idUsuario) {
        String sql = "SELECT id_usuario, nome, email, senha, ativo FROM usuarios WHERE id_usuario = ?";
        
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, idUsuario);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                return mapUsuario(rs);
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar usuário: " + e.getMessage(), e);
        }
    }

    public boolean autenticarUsuario(String email, String senha) {
        Usuario usuario = buscarUsuarioPorEmail(email);
        if (usuario == null) {
            return false;
        }
        return PasswordHasher.verifyPassword(senha, usuario.getSenha());
    }

    public void atualizarSenhaUsuario(int idUsuario, String senhaAtual, String novaSenha) {
        ValidationResult passRes = InputValidator.validatePassword(novaSenha, true);
        if (!passRes.isValid()) throw new IllegalArgumentException(passRes.getErrors().get(0));
        
        if (novaSenha.length() < 8) {
            throw new IllegalArgumentException("A nova senha deve ter pelo menos 8 caracteres");
        }

        Usuario usuario = buscarUsuario(idUsuario);
        if (usuario == null) {
            throw new IllegalArgumentException("Usuário não encontrado");
        }

        if (!PasswordHasher.verifyPassword(senhaAtual, usuario.getSenha())) {
            throw new IllegalArgumentException("Senha atual incorreta");
        }

        String novaSenhaHash = PasswordHasher.hashPassword(novaSenha);
        String sql = "UPDATE usuarios SET senha = ? WHERE id_usuario = ?";

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, novaSenhaHash);
                pstmt.setInt(2, idUsuario);
                int updated = pstmt.executeUpdate();
                if (updated == 0) {
                    throw new RuntimeException("Não foi possível atualizar a senha");
                }
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao atualizar senha: " + e.getMessage(), e);
        }
    }

    public void excluirUsuario(int idUsuario) {
        if (idUsuario <= 0) throw new IllegalArgumentException("ID inválido");
        
        String sql = "DELETE FROM usuarios WHERE id_usuario = ?";

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, idUsuario);
                int deleted = pstmt.executeUpdate();
                if (deleted == 0) {
                    throw new IllegalArgumentException("Usuário não encontrado");
                }
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao excluir usuário: " + e.getMessage(), e);
        }
    }

    // --- Recuperação de senha ---

    private static String hashToken(String token) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 não disponível", e);
        }
    }

    /**
     * Gera um token seguro para recuperação de senha (ex.: 32 bytes em hex = 64 caracteres).
     */
    public static String gerarTokenRecuperacaoSenha() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * Cria um token de recuperação de senha para o usuário. Invalida tokens anteriores do mesmo usuário.
     */
    public void criarTokenRecuperacaoSenha(int idUsuario, String token, Instant expiresAt) {
        String tokenHash = hashToken(token);
        invalidarTokensRecuperacaoUsuario(idUsuario);
        String sql = "INSERT INTO password_reset_tokens (id_usuario, token_hash, expires_at) VALUES (?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, idUsuario);
            pstmt.setString(2, tokenHash);
            pstmt.setTimestamp(3, java.sql.Timestamp.from(expiresAt));
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao criar token de recuperação: " + e.getMessage(), e);
        }
    }

    public void invalidarTokensRecuperacaoUsuario(int idUsuario) {
        String sql = "UPDATE password_reset_tokens SET used = TRUE WHERE id_usuario = ? AND used = FALSE";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, idUsuario);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            // não falha o fluxo
        }
    }

    /**
     * Retorna id_usuario se o token for válido (existe, não usado, não expirado). Caso contrário, null.
     */
    public Integer buscarUsuarioIdPorTokenRecuperacao(String token) {
        if (token == null || token.isBlank()) return null;
        String tokenHash = hashToken(token.trim());
        String sql = "SELECT id_usuario FROM password_reset_tokens WHERE token_hash = ? AND used = FALSE AND expires_at > CURRENT_TIMESTAMP";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, tokenHash);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("id_usuario");
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar token de recuperação: " + e.getMessage(), e);
        }
    }

    /**
     * Redefine a senha usando o token e marca o token como usado.
     */
    public void redefinirSenhaComToken(String token, String novaSenha) {
        ValidationResult passRes = InputValidator.validatePassword(novaSenha, true);
        if (!passRes.isValid()) throw new IllegalArgumentException(passRes.getErrors().get(0));
        if (novaSenha.length() < 8) {
            throw new IllegalArgumentException("A nova senha deve ter pelo menos 8 caracteres");
        }
        Integer idUsuario = buscarUsuarioIdPorTokenRecuperacao(token);
        if (idUsuario == null) {
            throw new IllegalArgumentException("Link inválido ou expirado. Solicite uma nova redefinição de senha.");
        }
        String novaSenhaHash = PasswordHasher.hashPassword(novaSenha);
        String updateUser = "UPDATE usuarios SET senha = ? WHERE id_usuario = ?";
        String markUsed = "UPDATE password_reset_tokens SET used = TRUE WHERE token_hash = ?";
        String tokenHash = hashToken(token.trim());
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement pstmtUser = conn.prepareStatement(updateUser);
                 PreparedStatement pstmtToken = conn.prepareStatement(markUsed)) {
                pstmtUser.setString(1, novaSenhaHash);
                pstmtUser.setInt(2, idUsuario);
                pstmtUser.executeUpdate();
                pstmtToken.setString(1, tokenHash);
                pstmtToken.executeUpdate();
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao redefinir senha: " + e.getMessage(), e);
        }
    }
    
    private Usuario mapUsuario(ResultSet rs) throws SQLException {
        Usuario usuario = new Usuario(
            rs.getInt("id_usuario"),
            rs.getString("nome"),
            rs.getString("email"),
            rs.getString("senha")
        );
        usuario.setAtivo(rs.getBoolean("ativo"));
        return usuario;
    }
}

