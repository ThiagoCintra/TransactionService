package com.transactionservice;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class JwtTestHelper {

    private static final String SECRET = "bXlTdXBlclNlY3JldEtleUZvckpXVFRva2VuR2VuZXJhdGlvbjEyMzQ1Njc4OTA=";
    private static final SecretKey KEY;

    static {
        byte[] keyBytes = Base64.getDecoder().decode(SECRET);
        KEY = Keys.hmacShaKeyFor(keyBytes);
    }

    public static String generateToken(String username, String channel, List<String> roles) {
        return Jwts.builder()
                .subject(username)
                .claim("channel", channel)
                .claim("roles", roles)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600_000))
                .signWith(KEY)
                .compact();
    }

    public static String generateExpiredToken(String username) {
        return Jwts.builder()
                .subject(username)
                .claim("channel", "MOBILE")
                .claim("roles", List.of("USER"))
                .issuedAt(new Date(System.currentTimeMillis() - 7200_000))
                .expiration(new Date(System.currentTimeMillis() - 3600_000))
                .signWith(KEY)
                .compact();
    }

    public static String generateMobileToken(String username) {
        return generateToken(username, "MOBILE", List.of("USER"));
    }

    public static String generateNonMobileToken(String username) {
        return generateToken(username, "WEB", List.of("USER"));
    }
}
