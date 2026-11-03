package br.gov.ceplac.cepec.cacausync.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record SyncJsonRequest(
    @NotBlank(message = "Informe o codigo do ensaio (ex: TC-1801)")
    String codigoEnsaio,

    @NotEmpty(message = "Envie ao menos uma leitura")
    @Valid
    List<ItemSync> leituras
) {
    public record ItemSync(
        @NotBlank(message = "campanha obrigatoria") String campanha,
        @NotBlank(message = "plantId obrigatorio") String plantId,
        @JsonProperty("clone") String cloneCodigo,
        String parcela,
        Integer planta,
        @NotBlank(message = "resultado obrigatorio (numero ou 'Não Coletável')") String resultado,
        @NotBlank(message = "data obrigatoria") String data,
        Double lat,
        Double lng,
        Integer sadios,
        Integer vassoura,
        Integer podridao,
        Integer naoAvaliavel,
        List<DeteccaoDto> deteccoes
    ) {
        /** Construtor retrocompatível de 10 argumentos. */
        public ItemSync(
                String campanha, String plantId, String cloneCodigo, String parcela, Integer planta,
                String resultado, String data, Double lat, Double lng, List<DeteccaoDto> deteccoes
        ) {
            this(campanha, plantId, cloneCodigo, parcela, planta, resultado, data, lat, lng,
                    null, null, null, null, deteccoes);
        }
    }
}
