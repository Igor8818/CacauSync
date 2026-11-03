package br.gov.ceplac.cepec.cacausync.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Uma caixa (quadradinho) que o YOLO desenhou na foto.
 * Todas as medidas sao normalizadas entre 0 e 1 em relacao a largura/altura da imagem,
 * para funcionar em qualquer resolucao: x,y = canto superior esquerdo.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DeteccaoDto(
    @NotBlank(message = "Classe da deteccao e obrigatoria")
    @Pattern(regexp = "^(Sadio|Vassoura de Bruxa|Podridão|Não Avaliável|cacau|outro)$", message = "Classe de deteccao invalida")
    String classe,
    @DecimalMin("0.0") @DecimalMax("1.0") double confianca,
    @DecimalMin("0.0") @DecimalMax("1.0") double x,
    @DecimalMin("0.0") @DecimalMax("1.0") double y,
    @DecimalMin("0.0") @DecimalMax("1.0") double largura,
    @DecimalMin("0.0") @DecimalMax("1.0") double altura
) {
    @AssertTrue(message = "Geometria da caixa extrapola os limites da imagem (x + largura deve ser <= 1.0001 e y + altura <= 1.0001)")
    public boolean isGeometriaValida() {
        return (x + largura <= 1.0001) && (y + altura <= 1.0001);
    }
}
