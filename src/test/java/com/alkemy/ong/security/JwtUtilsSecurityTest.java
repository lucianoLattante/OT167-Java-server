package com.alkemy.ong.security;

import com.alkemy.ong.security.service.JwtUtils;
import io.jsonwebtoken.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import static org.junit.jupiter.api.Assertions.*;

class JwtUtilsSecurityTest {
    private static String key() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
    @Test void missingKeyFailsClosed() {
        assertThrows(IllegalArgumentException.class, () -> new JwtUtils(null));
        assertThrows(IllegalArgumentException.class, () -> new JwtUtils(""));
    }
    @Test void malformedOrShortKeyIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new JwtUtils("not base64!"));
        assertThrows(IllegalArgumentException.class, () -> new JwtUtils(Base64.getEncoder().encodeToString(new byte[16])));
    }
    @Test void validTokenMatchesOnlyItsSubject() {
        JwtUtils service = new JwtUtils(key());
        var alice = User.withUsername("alice@example.test").password("unused").roles("USER").build();
        var bob = User.withUsername("bob@example.test").password("unused").roles("USER").build();
        String token = service.generateToken(alice);
        assertTrue(service.validateToken(token, alice));
        assertFalse(service.validateToken(token, bob));
    }
    @Test void anotherSigningKeyIsRejected() {
        JwtUtils issuer = new JwtUtils(key());
        JwtUtils verifier = new JwtUtils(key());
        var user = User.withUsername("alice@example.test").password("unused").roles("USER").build();
        assertThrows(SignatureException.class, () -> verifier.extractUsername(issuer.generateToken(user)));
    }
    @Test void expiredTokenIsRejected() {
        String key = key();
        String token = Jwts.builder().setSubject("alice@example.test")
            .setExpiration(new Date(System.currentTimeMillis() - 60000))
            .signWith(SignatureAlgorithm.HS256, Base64.getDecoder().decode(key)).compact();
        assertThrows(ExpiredJwtException.class, () -> new JwtUtils(key).extractUsername(token));
    }
    @Test void unexpectedAlgorithmIsRejected() {
        String key = key();
        String token = Jwts.builder().setSubject("alice@example.test")
            .setExpiration(new Date(System.currentTimeMillis() + 60000))
            .signWith(SignatureAlgorithm.HS512, Base64.getDecoder().decode(key)).compact();
        assertThrows(UnsupportedJwtException.class, () -> new JwtUtils(key).extractUsername(token));
    }
    @Test void expirationIsRequired() {
        String key = key();
        String token = Jwts.builder().setSubject("alice@example.test")
            .signWith(SignatureAlgorithm.HS256, Base64.getDecoder().decode(key)).compact();
        assertThrows(MalformedJwtException.class, () -> new JwtUtils(key).extractUsername(token));
    }
    @Test void subjectIsRequired() {
        String key = key();
        String token = Jwts.builder().setExpiration(new Date(System.currentTimeMillis() + 60000))
            .signWith(SignatureAlgorithm.HS256, Base64.getDecoder().decode(key)).compact();
        assertThrows(MalformedJwtException.class, () -> new JwtUtils(key).extractUsername(token));
    }
}
