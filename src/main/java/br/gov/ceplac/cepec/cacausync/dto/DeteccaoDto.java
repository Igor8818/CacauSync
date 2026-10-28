package br.gov.ceplac.cepec.cacausync.dto;

/**
 * Uma caixa (quadradinho) que o YOLO desenhou na foto.
 * Todas as medidas sao normalizadas entre 0 e 1 em relacao a largura/altura da imagem,
 * para funcionar em qualquer resolucao: x,y = canto superior esquerdo.
 */
public record DeteccaoDto(
    String classe,
    double confianca,
    double x,
    double y,
    double largura,
    double altura
) {}
