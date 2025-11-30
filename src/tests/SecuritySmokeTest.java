public class SecuritySmokeTest {
    public static void main(String[] args) {
        testPasswordHasher();
        testJwtUtil();
        System.out.println("Security smoke tests passed.");
    }
    
    private static void testPasswordHasher() {
        String password = "Teste123!";
        String hash = PasswordHasher.hashPassword(password);
        if (!PasswordHasher.verifyPassword(password, hash)) {
            throw new IllegalStateException("PasswordHasher falhou na validação");
        }
        if (PasswordHasher.verifyPassword("senha-invalida", hash)) {
            throw new IllegalStateException("PasswordHasher aceitou senha incorreta");
        }
    }
    
    private static void testJwtUtil() {
        Usuario usuario = new Usuario(1, "Usuário Teste", "teste@example.com", "placeholder");
        String token = JwtUtil.generateToken(usuario);
        JwtUtil.JwtValidationResult result = JwtUtil.validateToken(token);
        if (!result.isValid() || result.getUserId() != 1) {
            throw new IllegalStateException("JwtUtil falhou ao validar token");
        }
    }
}

