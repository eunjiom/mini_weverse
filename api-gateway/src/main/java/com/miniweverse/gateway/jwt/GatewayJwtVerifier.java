package com.miniweverse.gateway.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.springframework.stereotype.Component;

/**
 * community-service가 발급한 JWT의 서명을 공개키로 검증하고 role 클레임을 읽는다.
 * 게이트웨이는 검증만 하므로 개인키는 필요 없다.
 */
@Component
public class GatewayJwtVerifier {

    private final PublicKey publicKey;

    public GatewayJwtVerifier(JwtProperties properties) {
        this.publicKey = readPublicKey(properties.publicKeyPath());
    }

    public Claims verify(String token) {
        return Jwts.parser()
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
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
