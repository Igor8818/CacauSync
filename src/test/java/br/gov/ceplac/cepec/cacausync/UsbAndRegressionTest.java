package br.gov.ceplac.cepec.cacausync;

import br.gov.ceplac.cepec.cacausync.domain.entity.StatusValidacao;
import br.gov.ceplac.cepec.cacausync.dto.DeteccaoDto;
import br.gov.ceplac.cepec.cacausync.dto.LinhaColetaCsvDto;
import br.gov.ceplac.cepec.cacausync.dto.ResultadoRotaResponse;
import br.gov.ceplac.cepec.cacausync.dto.UsbStatusResponse;
import br.gov.ceplac.cepec.cacausync.dto.ValidarLeituraRequest;
import br.gov.ceplac.cepec.cacausync.service.CsvColetaParser;
import br.gov.ceplac.cepec.cacausync.service.RotaPlanejamentoService;
import br.gov.ceplac.cepec.cacausync.service.UsbAdbSyncService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UsbAndRegressionTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UsbAdbSyncService usbAdbSyncService;

    @Autowired
    private CsvColetaParser csvColetaParser;

    @Autowired
    private RotaPlanejamentoService rotaPlanejamentoService;

    @Autowired
    private Validator validator;

    @Test
    @DisplayName("Endpoint /api/v1/coletas/usb-status exige autenticacao e responde 401 sem token")
    void usbStatusExigeAuth() throws Exception {
        mvc.perform(get("/api/v1/coletas/usb-status"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "ROLE_PESQUISADOR")
    @DisplayName("Endpoint /api/v1/coletas/usb-status retorna JSON 200 seguro mesmo sem aparelho conectado")
    void usbStatusRetornaOkSeguro() throws Exception {
        UsbStatusResponse direta = usbAdbSyncService.obterStatusUsb();
        assertNotNull(direta);
        assertNotNull(direta.mensagem());
        assertFalse(direta.mensagem().isBlank());

        mvc.perform(get("/api/v1/coletas/usb-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adbDisponivel").isBoolean())
                .andExpect(jsonPath("$.dispositivoConectado").isBoolean())
                .andExpect(jsonPath("$.registrosNoCelular").isNumber())
                .andExpect(jsonPath("$.rotaNoCelular").isBoolean())
                .andExpect(jsonPath("$.mensagem").isNotEmpty());
    }

    @Test
    @WithMockUser(authorities = "ROLE_PESQUISADOR")
    @DisplayName("Endpoint DELETE /api/v1/leituras/{id} existe e retorna 404 para ID inexistente")
    void deleteLeituraExiste() throws Exception {
        mvc.perform(delete("/api/v1/leituras/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DeteccaoDto aceita 'Podridão' com status AJUSTADA e rejeita 'Podridão Parda'")
    void regressaoClassesDeteccaoDto() {
        DeteccaoDto valida = new DeteccaoDto("Podridão", 0.91, 0.1, 0.2, 0.2, 0.3);
        ValidarLeituraRequest reqValida = new ValidarLeituraRequest(
                StatusValidacao.AJUSTADA, 1, "Foco ajustado", 0, 0, 1, 0, List.of(valida)
        );
        Set<ConstraintViolation<ValidarLeituraRequest>> vOk = validator.validate(reqValida);
        assertTrue(vOk.isEmpty(), "Podridão deveria ser aceita sem violações");

        DeteccaoDto invalida = new DeteccaoDto("Podridão Parda", 0.91, 0.1, 0.2, 0.2, 0.3);
        Set<ConstraintViolation<DeteccaoDto>> vErr = validator.validate(invalida);
        assertFalse(vErr.isEmpty(), "Podridão Parda deve ser rejeitada pelo DTO");
    }

    @Test
    @DisplayName("ColetaSyncService.java não contém mojibake UTF-8 duplo")
    void coletaSyncServiceSemMojibake() throws Exception {
        Path p = Path.of("src/main/java/br/gov/ceplac/cepec/cacausync/service/ColetaSyncService.java");
        String src = Files.readString(p, StandardCharsets.UTF_8);
        assertFalse(src.contains("\u00C3\u00A7"), "Encontrado mojibake de ç");
        assertFalse(src.contains("\u00C3\u00A3"), "Encontrado mojibake de ã");
        assertFalse(src.contains("\u00C3\u00A9"), "Encontrado mojibake de é");
        assertFalse(src.contains("\u00C3\u00B3"), "Encontrado mojibake de ó");
        assertFalse(src.contains("\u00E2\u20AC\u201D"), "Encontrado mojibake de em-dash");
    }

    @Test
    @WithMockUser(authorities = "ROLE_PESQUISADOR")
    @DisplayName("PUT /api/v1/leituras/{id}/validacao rejeita classe invalida ('Podridao Parda') com HTTP 400 (@Valid)")
    void validacaoRejeitaPodridaoPardaViaHttp400() throws Exception {
        String body = """
                {
                  "status": "AJUSTADA",
                  "qtdFrutosValidada": 1,
                  "qtdSadios": 0,
                  "qtdVassoura": 0,
                  "qtdPodridao": 1,
                  "qtdNaoAvaliavel": 0,
                  "deteccoes": [
                    { "classe": "Podridão Parda", "confianca": 0.9, "x": 0.1, "y": 0.1, "largura": 0.2, "altura": 0.2 }
                  ]
                }
                """;
        mvc.perform(put("/api/v1/leituras/1/validacao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("CsvColetaParser prioriza Total_Frutos (col 9) sobre texto descritivo na col 5 (defesa P0-04)")
    void csvParserPriorizaTotalFrutosEmColuna9() {
        String csv = "Campanha,ID_Gerado,Clone,Parcela,Planta,Resultado,Data,Lat,Lng,Total_Frutos,Sadios,Vassoura,Podridao,Nao_Avaliavel,Deteccoes_YOLO\n"
                + "TC-1801,CCN-51_P1_Pl1,CCN-51,1,1,\"Podridão e Vassoura (2 Podr / 3 Vass)\",20/11/2026 10:00:00,-14.77,-39.23,10,5,3,2,0,\n";
        List<LinhaColetaCsvDto> linhas = csvColetaParser.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
        assertEquals(1, linhas.size());
        assertEquals(10, linhas.get(0).qtdFrutos());
        assertEquals(5, linhas.get(0).qtdSadios());
        assertEquals(3, linhas.get(0).qtdVassoura());
        assertEquals(2, linhas.get(0).qtdPodridao());
    }

    @Test
    @DisplayName("RotaPlanejamentoService importa CSV PT-BR com ';' e virgula decimal sem quebrar colunas")
    void rotaParserSuportaCsvPtBrPontoEVirgulaComVirgulaDecimal() {
        String csv = "Clone;Parcela;Planta;Ordem;Obs\n"
                + "CCN-51;1;1;1;Lat -14,77\n"
                + "PS-1319;2;3;2;Lat -14,78\n";
        ResultadoRotaResponse resp = rotaPlanejamentoService.importarPlanilhaRota(
                "TC-ROTA-TEST",
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)),
                "rota_ptbr.csv",
                false
        );
        assertEquals(2, resp.totalPlantasRota());
    }
}
