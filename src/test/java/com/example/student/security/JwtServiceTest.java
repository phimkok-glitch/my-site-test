package com.example.student.security;

import com.example.student.model.Role;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTest {

    private final JwtService jwtService = new JwtService();

    @Test
    void usernameFromToken() {
        String token = jwtService.generateToken("admin@example.com", Set.of(Role.ADMIN));

        assertEquals("admin@example.com", jwtService.extractUsername(token));
    }

    @Test
    void rolesFromToken() {
        String token = jwtService.generateToken("admin@example.com", Set.of(Role.ADMIN, Role.TEACHER));

        assertEquals(Set.of(Role.ADMIN, Role.TEACHER), jwtService.extractRoles(token));
    }

    @Test
    void tokenWithWrongSignature() {
        String token = jwtService.generateToken("admin@example.com", Set.of(Role.ADMIN));
        String[] parts = token.split("\\.");
        String wrongToken = parts[0] + "." + parts[1] + ".abc" + parts[2].substring(3);

        assertThrows(JwtException.class, () -> jwtService.extractUsername(wrongToken));
    }
}
