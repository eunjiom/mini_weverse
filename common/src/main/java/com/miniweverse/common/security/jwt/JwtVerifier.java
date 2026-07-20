package com.miniweverse.common.security.jwt;

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

/**
 * community-service가 개인키로 서명한 JWT를 공개키로 검증한다(발급은 없음).
 * InternalServiceAuthFilter와 같은 이유로 {@code @Component}로 두지 않고, 각 서비스가 자기 설정값
 * (공개키 경로)으로 직접 빈을 등록한다 — api-gateway는 컴포넌트 스캔 베이스 패키지가
 * {@code com.miniweverse.gateway}라 common의 {@code @Component}가 항상 스캔된다고 보장할 수 없다.
 */
public class JwtVerifier {

    public static final String CLAIM_TOKEN_TYPE = "type";
    public static final String TOKEN_TYPE_ACCESS = "access";

    private final PublicKey publicKey;

    public JwtVerifier(String publicKeyPath) {
        this.publicKey = readPublicKey(publicKeyPath);
    }

    public Claims parseClaims(String token) {
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
