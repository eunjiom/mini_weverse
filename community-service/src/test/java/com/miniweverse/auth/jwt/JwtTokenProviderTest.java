package com.miniweverse.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.miniweverse.common.security.jwt.JwtVerifier;
import com.miniweverse.common.security.jwt.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("unit")
class JwtTokenProviderTest {

    private static final long ACCESS_TOKEN_VALIDITY = TimeUnit.MINUTES.toMillis(30);
    private static final long REFRESH_TOKEN_VALIDITY = TimeUnit.DAYS.toMillis(7);

    @TempDir
    Path tempDir;

    private KeyPair keyPair;
    private JwtTokenProvider tokenProvider;

    @BeforeEach
    void setUp() throws NoSuchAlgorithmException, IOException {
        keyPair = generateKeyPair();
        Path privateKeyPath = writePem(tempDir.resolve("private.pem"), "PRIVATE KEY", keyPair.getPrivate().getEncoded());
        JwtProperties properties = new JwtProperties(privateKeyPath.toString(), ACCESS_TOKEN_VALIDITY, REFRESH_TOKEN_VALIDITY);
        tokenProvider = new JwtTokenProvider(properties);
    }

    @Test
    void AccessToken은_발급_시점으로부터_accessTokenValidity_만큼_후에_만료된다() {
        long before = System.currentTimeMillis();
        String token = tokenProvider.createAccessToken(1L, "닉네임", Role.FAN);

        Claims claims = parse(token);

        assertThat(claims.getSubject()).isEqualTo("1");
        assertThat(claims.get(JwtVerifier.CLAIM_TOKEN_TYPE, String.class)).isEqualTo(JwtVerifier.TOKEN_TYPE_ACCESS);
        assertThat(claims.get("nickname", String.class)).isEqualTo("닉네임");
        assertThat(claims.get("role", String.class)).isEqualTo("FAN");
        assertThat(claims.getExpiration().getTime() - before)
                .isCloseTo(ACCESS_TOKEN_VALIDITY, within(2_000L));
    }

    @Test
    void RefreshToken은_발급_시점으로부터_refreshTokenValidity_만큼_후에_만료된다() {
        long before = System.currentTimeMillis();
        String token = tokenProvider.createRefreshToken(1L);

        Claims claims = parse(token);

        assertThat(claims.getSubject()).isEqualTo("1");
        assertThat(claims.get(JwtVerifier.CLAIM_TOKEN_TYPE, String.class)).isEqualTo(JwtTokenProvider.TOKEN_TYPE_REFRESH);
        assertThat(claims.getExpiration().getTime() - before)
                .isCloseTo(REFRESH_TOKEN_VALIDITY, within(2_000L));
    }

    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(keyPair.getPublic())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private KeyPair generateKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private Path writePem(Path path, String label, byte[] derEncoded) throws IOException {
        String base64 = Base64.getEncoder().encodeToString(derEncoded);
        String pem = "-----BEGIN " + label + "-----\n" + base64 + "\n-----END " + label + "-----\n";
        Files.writeString(path, pem);
        return path;
    }
}
