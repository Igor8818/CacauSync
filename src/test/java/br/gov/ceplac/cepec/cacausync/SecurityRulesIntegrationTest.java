package br.gov.ceplac.cepec.cacausync;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prova, de ponta a ponta (filtros reais do Spring Security), que as portas estao trancadas.
 * Antes desta etapa GET /api/v1/ensaios/** e POST /auth/registrar estavam abertos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityRulesIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("Sem token: dados, relatório, rota e fotos respondem 401")
    void semTokenNaoEntra() throws Exception {
        mvc.perform(get("/api/v1/ensaios")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/ensaios/TC-1801/relatorio-clones")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/ensaios/TC-1801/rota-csv")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/leituras/1/foto")).andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/v1/coletas/importar-csv")
                        .file(new MockMultipartFile("arquivo", "x.csv", "text/csv", "a".getBytes())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Token com token inválido/adulterado também é 401")
    void tokenInvalidoNaoEntra() throws Exception {
        mvc.perform(get("/api/v1/ensaios").header("Authorization", "Bearer isto.nao.e.um.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Página de login e documentação continuam públicas")
    void vitrineContinuaPublica() throws Exception {
        mvc.perform(get("/index.html")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "ROLE_PESQUISADOR")
    @DisplayName("Pesquisador logado NÃO pode cadastrar usuários nem criar ensaios (só coordenador): 403")
    void pesquisadorNaoCriaUsuarioNemEnsaio() throws Exception {
        mvc.perform(post("/api/v1/auth/registrar").contentType("application/json")
                        .content("{\"nome\":\"x\",\"email\":\"x@x.com\",\"senha\":\"123456\",\"matriculaCepec\":\"M1\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/ensaios").contentType("application/json")
                        .content("{\"codigo\":\"TC-9\",\"nome\":\"x\"}"))
                .andExpect(status().isForbidden());
    }
}
