package br.gov.ceplac.cepec.cacausync.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Contrato JSON para sincronizar direto do app (futuro botao "Enviar ao laboratorio").
 * Diferente do CSV, ja carrega as caixas do YOLO (deteccoes).
 */
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
        // "clone" e nome proibido para componente de record em Java; no JSON continua se chamando "clone".
        @JsonProperty("clone") String cloneCodigo,
        String parcela,
        Integer planta,
        @NotBlank(message = "resultado obrigatorio (numero ou 'Não Coletável')") String resultado,
        @NotBlank(message = "data obrigatoria") String data,
        Double lat,
        Double lng,
        List<DeteccaoDto> deteccoes
    ) {}
}
