package br.gov.ceplac.cepec.cacausync.service;

import br.gov.ceplac.cepec.cacausync.domain.entity.LeituraColeta;
import br.gov.ceplac.cepec.cacausync.dto.DeteccaoDto;
import br.gov.ceplac.cepec.cacausync.dto.LeituraColetaResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;

/** Traduz a entidade do banco para o JSON que o painel web consome (e cuida do JSON das caixas do YOLO). */
@Component
public class LeituraMapper {

    private final ObjectMapper objectMapper;

    public LeituraMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public LeituraColetaResponse paraResponse(LeituraColeta l) {
        return new LeituraColetaResponse(
                l.getId(),
                l.getPlanta().getParcela().getEnsaio().getCodigo(),
                l.getCampanha(),
                l.getPlanta().getPlantCode(),
                l.getPlanta().getParcela().getClone().getCodigo(),
                l.getPlanta().getParcela().getNumeroParcela(),
                l.getPlanta().getNumeroPlanta(),
                l.getQtdFrutos(),
                Boolean.TRUE.equals(l.getColetavel()),
                l.getDiagnosticoBruto(),
                l.getDataColetaStr(),
                l.getLatitude(),
                l.getLongitude(),
                Boolean.TRUE.equals(l.getAlertaGps()),
                l.getPesquisadorEmail(),
                l.getImportadoEm(),
                l.temFoto(),
                l.getStatusValidacao(),
                l.getQtdFrutosValidada(),
                l.qtdEfetiva(),
                l.getObservacaoValidacao(),
                l.getValidadoPor(),
                l.getValidadoEm(),
                lerDeteccoes(l.getDeteccoesJson())
        );
    }

    public String serializarDeteccoes(List<DeteccaoDto> deteccoes) {
        if (deteccoes == null || deteccoes.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(deteccoes);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private List<DeteccaoDto> lerDeteccoes(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<DeteccaoDto>>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }
}
