package br.gov.ceplac.cepec.cacausync.dto;

/** Estimativa agronômica indicativa (SPEC-003 §2.3). Valores em kg/ha e @/ha (1 @ = 15 kg). */
public record EstimativaAgronomicaDto(
    double mediaSadiosPorPlantaAvaliada,
    double potencialKgHa,
    double produtividadeRealKgHa,
    double produtividadeRealArrobasHa,
    double perdaDoencasKgHa,
    double perdaDoencasArrobasHa
) {
    public static EstimativaAgronomicaDto zerada() {
        return new EstimativaAgronomicaDto(0, 0, 0, 0, 0, 0);
    }
}
