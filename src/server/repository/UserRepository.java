package server.repository;

import server.database.DatabaseConnection;
import server.validation.InputValidator;
import server.validation.ValidationResult;
import server.model.Usuario;
import server.security.PasswordHasher;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

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

