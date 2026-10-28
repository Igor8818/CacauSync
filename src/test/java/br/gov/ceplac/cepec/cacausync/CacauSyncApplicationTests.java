package br.gov.ceplac.cepec.cacausync;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class CacauSyncApplicationTests {

    @Test
    @DisplayName("Deve inicializar o contexto completo do Spring Boot 3 (JPA, Security, Controllers e OpenAPI)")
    void contextLoads() {
    }
}
