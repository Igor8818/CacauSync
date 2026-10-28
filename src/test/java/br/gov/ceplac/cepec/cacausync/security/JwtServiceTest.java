package br.gov.ceplac.cepec.cacausync.security;

import br.gov.ceplac.cepec.cacausync.domain.entity.RoleUsuario;
import br.gov.ceplac.cepec.cacausync.domain.entity.Usuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private final JwtService jwtService = new JwtService(
            "chave-secreta-teste-unitario-cepec-cacausync-2026",
            "cacausync-api",
            12
    );

    @Test
    @DisplayName("Deve gerar token JWT válido para pesquisador do CEPEC e extrair o e-mail (subject)")
    void deveGerarEValidarTokenJwtComSucesso() {
        Usuario pesquisador = new Usuario(
                "Pesquisador Campo CEPEC",
                "pesquisador@cepec.gov.br",
                "hash-bcrypt",
                "CEPEC-042",
                RoleUsuario.ROLE_PESQUISADOR
        );

        String token = jwtService.gerarToken(pesquisador);

        assertThat(token).isNotBlank();
        Optional<String> subjectExtraido = jwtService.validarEExtrairSubject(token);
        assertThat(subjectExtraido).isPresent().contains("pesquisador@cepec.gov.br");
    }

    @Test
    @DisplayName("Deve rejeitar token JWT adulterado ou assinado com outra chave secreta")
    void deveRejeitarTokenInvalidoOuAdulterado() {
        JwtService outroEmissor = new JwtService("outra-chave-invalida", "cacausync-api", 12);
        Usuario coordenador = new Usuario(
                "Coordenador",
                "coordenador@cepec.gov.br",
                "hash",
                "CEPEC-001",
                RoleUsuario.ROLE_COORDENADOR
        );

        String tokenComOutraAssinatura = outroEmissor.gerarToken(coordenador);

        Optional<String> resultado = jwtService.validarEExtrairSubject(tokenComOutraAssinatura);
        assertThat(resultado).isEmpty();
    }
}
