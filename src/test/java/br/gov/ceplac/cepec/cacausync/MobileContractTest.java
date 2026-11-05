package br.gov.ceplac.cepec.cacausync;

import br.gov.ceplac.cepec.cacausync.dto.SyncJsonRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guarda de compatibilidade com o CacauApp (SPEC-001 / SPEC-003 §2.7): o JSON montado por
 * CacauSyncClient.criarPayloadSyncJson (app/src/main/.../network/CacauSyncClient.kt) precisa ser aceito
 * pelo contrato SyncJsonRequest. Se alguém mudar o DTO do servidor de forma incompatível, este teste quebra.
 */
class MobileContractTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    /** Payload idêntico ao gerado pelo app: planta coletável com as 4 classes + planta "Não Coletável". */
    private static final String PAYLOAD_DO_APP = """
            {
              "codigoEnsaio": "TC-1801",
              "leituras": [
                {
                  "campanha": "TC-1801", "plantId": "CCN-51_P20_Pl3", "clone": "CCN-51", "parcela": "20", "planta": 3,
                  "resultado": "4", "data": "05/11/2026 11:02:45", "lat": -14.789, "lng": -39.278,
                  "sadios": 1, "vassoura": 1, "podridao": 1, "naoAvaliavel": 1,
                  "deteccoes": [
                    {"classe": "Sadio", "confianca": 0.94, "x": 0.12, "y": 0.22, "largura": 0.15, "altura": 0.30},
                    {"classe": "Vassoura de Bruxa", "confianca": 0.83, "x": 0.62, "y": 0.38, "largura": 0.13, "altura": 0.26},
                    {"classe": "Podridão", "confianca": 0.76, "x": 0.80, "y": 0.20, "largura": 0.13, "altura": 0.27},
                    {"classe": "Não Avaliável", "confianca": 1.0, "x": 0.40, "y": 0.60, "largura": 0.16, "altura": 0.16}
                  ]
                },
                {
                  "campanha": "TC-1801", "plantId": "SCP-17202_P2_Pl5", "clone": "SCP-17202", "parcela": "2", "planta": 5,
                  "resultado": "Não Coletável", "data": "05/11 11:05", "lat": 0.0, "lng": 0.0,
                  "sadios": 0, "vassoura": 0, "podridao": 0, "naoAvaliavel": 0,
                  "deteccoes": []
                }
              ]
            }
            """;

    @Test
    @DisplayName("O payload do CacauApp é desserializado e passa na validação do SyncJsonRequest")
    void payloadDoAppEhAceito() throws Exception {
        SyncJsonRequest req = mapper.readValue(PAYLOAD_DO_APP, SyncJsonRequest.class);

        assertThat(validator.validate(req)).isEmpty();
        assertThat(req.leituras()).hasSize(2);
        SyncJsonRequest.ItemSync coletavel = req.leituras().get(0);
        assertThat(coletavel.cloneCodigo()).isEqualTo("CCN-51");     // chave JSON "clone"
        assertThat(coletavel.resultado()).isEqualTo("4");
        assertThat(coletavel.deteccoes()).hasSize(4);
        assertThat(req.leituras().get(1).resultado()).isEqualTo("Não Coletável");
    }

    @Test
    @DisplayName("Resultado fora do contrato (texto livre) continua sendo rejeitado")
    void resultadoInvalidoEhRejeitado() throws Exception {
        String ruim = PAYLOAD_DO_APP.replace("\"resultado\": \"4\"", "\"resultado\": \"4 frutos (3 sadios)\"");
        SyncJsonRequest req = mapper.readValue(ruim, SyncJsonRequest.class);

        assertThat(validator.validate(req)).isNotEmpty();
    }
}
