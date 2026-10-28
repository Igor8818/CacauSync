package br.gov.ceplac.cepec.cacausync.security;

import br.gov.ceplac.cepec.cacausync.domain.entity.Usuario;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Service
public class JwtService {

    private final String secret;
    private final String issuer;
    private final long expirationHours;

    public JwtService(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.issuer:cacausync-api}") String issuer,
            @Value("${security.jwt.expiration-hours:12}") long expirationHours
    ) {
        this.secret = secret;
        this.issuer = issuer;
        this.expirationHours = expirationHours;
    }

    public String gerarToken(Usuario usuario) {
        Algorithm algorithm = Algorithm.HMAC256(secret);
        Instant expiracao = Instant.now().plus(expirationHours, ChronoUnit.HOURS);

        return JWT.create()
                .withIssuer(issuer)
                .withSubject(usuario.getEmail())
                .withClaim("nome", usuario.getNome())
                .withClaim("matricula", usuario.getMatriculaCepec())
                .withClaim("role", usuario.getRole().name())
                .withExpiresAt(expiracao)
                .sign(algorithm);
    }

    public Optional<String> validarEExtrairSubject(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            String subject = JWT.require(algorithm)
                    .withIssuer(issuer)
                    .build()
                    .verify(token)
                    .getSubject();
            return Optional.ofNullable(subject);
        } catch (JWTVerificationException ex) {
            return Optional.empty();
        }
    }

    public long getExpirationSeconds() {
        return expirationHours * 3600L;
    }
}
