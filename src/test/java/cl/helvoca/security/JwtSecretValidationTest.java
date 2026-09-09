package cl.helvoca.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtSecretValidationTest {
    @Test
    void rejectsShortSecrets() {
        JwtConfig config = new JwtConfig();
        JwtProperties properties = new JwtProperties("issuer", "c2hvcnQ=", 60);
        assertThrows(IllegalStateException.class, () -> config.jwtSecretKey(properties));
    }
}
