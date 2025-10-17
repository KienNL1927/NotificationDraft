package com.example.notificationservice.util;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class to generate JWT tokens for testing purposes.
 * This should ONLY be used in development/testing environments.
 */
public class JwtTestToken {

    // Use the same secret as configured in application.yml
    private static final String SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private static final long EXPIRATION_TIME = 86400000; // 24 hours

    public static void main(String[] args) {
        // Generate tokens for different user types
        String userToken = generateToken("john.doe", 123, List.of("ROLE_USER"));
        String adminToken = generateToken("admin", 1, List.of("ROLE_ADMIN", "ROLE_USER"));
        String proctorToken = generateToken("proctor", 456, List.of("ROLE_PROCTOR", "ROLE_USER"));

        System.out.println("=== JWT Test Tokens ===\n");

        System.out.println("Regular User Token:");
        System.out.println("Username: john.doe, UserId: 123, Roles: [ROLE_USER]");
        System.out.println(userToken);
        System.out.println();

        System.out.println("Admin Token:");
        System.out.println("Username: admin, UserId: 1, Roles: [ROLE_ADMIN, ROLE_USER]");
        System.out.println(adminToken);
        System.out.println();

        System.out.println("Proctor Token:");
        System.out.println("Username: proctor, UserId: 456, Roles: [ROLE_PROCTOR, ROLE_USER]");
        System.out.println(proctorToken);
        System.out.println();

        System.out.println("=== Use these tokens in the Authorization header ===");
        System.out.println("Authorization: Bearer <token>");
    }

    public static String generateToken(String username, Integer userId, List<String> roles) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("roles", roles);

        return createToken(claims, username);
    }

    private static String createToken(Map<String, Object> claims, String subject) {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes());

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(key, SignatureAlgorithm.HS512)
                .compact();
    }

    /**
     * Generate a token with custom claims
     */
    public static String generateCustomToken(String username, Integer userId,
                                             List<String> roles, Map<String, Object> additionalClaims) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("roles", roles);
        claims.putAll(additionalClaims);

        return createToken(claims, username);
    }

    /**
     * Generate an expired token for testing
     */
    public static String generateExpiredToken(String username, Integer userId, List<String> roles) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("roles", roles);

        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes());

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setIssuedAt(new Date(System.currentTimeMillis() - EXPIRATION_TIME - 1000))
                .setExpiration(new Date(System.currentTimeMillis() - 1000))
                .signWith(key, SignatureAlgorithm.HS512)
                .compact();
    }
}