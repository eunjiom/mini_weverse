package com.miniweverse.auth.jwt;

import com.miniweverse.common.security.jwt.JwtVerifier;
import com.miniweverse.common.security.jwt.Role;
import io.jsonwebtoken.Jwts;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Date;
import org.springframework.stereotype.Component;

/**
 * 발급 전용 — 개인키로 서명한다. 검증(공개키)은 common의 {@link JwtVerifier}가 담당한다.
 */
@Component
public class JwtTokenProvider {

    public static final String TOKEN_TYPE_REFRESH = "refresh";

    private final JwtProperties properties;
    private final PrivateKey privateKey;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
        this.privateKey = readPrivateKey(properties.privateKeyPath());
    }

    public String createAccessToken(Long userId, String nickname, Role role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(JwtVerifier.CLAIM_TOKEN_TYPE, JwtVerifier.TOKEN_TYPE_ACCESS)
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
                .claim(JwtVerifier.CLAIM_TOKEN_TYPE, TOKEN_TYPE_REFRESH)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + properties.refreshTokenValidity()))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
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

    private byte[] decodePem(String pem) {
        String base64 = pem
                .replaceAll("-----BEGIN (.*)-----", "")
                .replaceAll("-----END (.*)-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(base64);
    }
}
