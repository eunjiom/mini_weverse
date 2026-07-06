package com.miniweverse.auth;

import com.miniweverse.user.enums.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    public static final String CLAIM_TOKEN_TYPE = "type";
    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_REFRESH = "refresh";

    private final JwtProperties properties;
    private final PrivateKey privateKey;
    private final PublicKey publicKey;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
        this.privateKey = readPrivateKey(properties.privateKeyPath());
        this.publicKey = readPublicKey(properties.publicKeyPath());
    }

    public String createAccessToken(Long userId, String nickname, Role role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS)
                .claim("nickname", nickname)
                .claim("role", role.name())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + properties.accessTokenValidity()))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    public String createRefreshToken(Long userId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_REFRESH)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + properties.refreshTokenValidity()))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long getUserId(String token) {
        return Long.valueOf(parseClaims(token).getSubject());
    }

    private PrivateKey readPrivateKey(String path) {
        byte[] decoded;
        try {
            decoded = decodePem(Files.readString(Path.of(path)));
        } catch (IOException e) {
            throw new UncheckedIOException("JWT 개인키 파일을 읽을 수 없습니다: " + path, e);
        }
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePrivate(new PKCS8EncodedKeySpec(decoded));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("JWT 개인키를 파싱할 수 없습니다: " + path, e);
        }
    }

    private PublicKey readPublicKey(String path) {
        byte[] decoded;
        try {
            decoded = decodePem(Files.readString(Path.of(path)));
        } catch (IOException e) {
            throw new UncheckedIOException("JWT 공개키 파일을 읽을 수 없습니다: " + path, e);
        }
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePublic(new X509EncodedKeySpec(decoded));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("JWT 공개키를 파싱할 수 없습니다: " + path, e);
        }
    }

    private byte[] decodePem(String pem) {
        String base64 = pem
                .replaceAll("-----BEGIN (.*)-----", "")
                .replaceAll("-----END (.*)-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(base64);
    }
}
