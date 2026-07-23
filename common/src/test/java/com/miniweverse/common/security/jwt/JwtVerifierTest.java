package com.miniweverse.common.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.SignatureException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("unit")
class JwtVerifierTest {

    @TempDir
    Path tempDir;

    private KeyPair keyPair;
    private JwtVerifier verifier;

    @BeforeEach
    void setUp() throws NoSuchAlgorithmException, IOException {
        keyPair = generateKeyPair();
        Path publicKeyPath = writePem(tempDir.resolve("public.pem"), "PUBLIC KEY", keyPair.getPublic().getEncoded());
        verifier = new JwtVerifier(publicKeyPath.toString());
    }

    @Test
    void 개인키로_서명한_토큰을_공개키로_검증하면_클레임을_그대로_읽는다() {
        String token = Jwts.builder()
                .subject("1")
                .claim(JwtVerifier.CLAIM_TOKEN_TYPE, JwtVerifier.TOKEN_TYPE_ACCESS)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();

        Claims claims = verifier.parseClaims(token);

        assertThat(claims.getSubject()).isEqualTo("1");
        assertThat(claims.get(JwtVerifier.CLAIM_TOKEN_TYPE, String.class)).isEqualTo(JwtVerifier.TOKEN_TYPE_ACCESS);
    }

    @Test
    void 만료된_토큰을_검증하면_ExpiredJwtException이_발생한다() {
        String expiredToken = Jwts.builder()
                .subject("1")
                .issuedAt(new Date(System.currentTimeMillis() - 120_000))
                .expiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();

        assertThatThrownBy(() -> verifier.parseClaims(expiredToken))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void 다른_개인키로_서명된_토큰을_검증하면_SignatureException이_발생한다() throws NoSuchAlgorithmException {
        KeyPair otherKeyPair = generateKeyPair();
        String forgedToken = Jwts.builder()
                .subject("1")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(otherKeyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();

        assertThatThrownBy(() -> verifier.parseClaims(forgedToken))
                .isInstanceOf(SignatureException.class);
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
