package br.gov.ceplac.cepec.cacausync.repository;

import br.gov.ceplac.cepec.cacausync.domain.entity.LeituraColeta;
import br.gov.ceplac.cepec.cacausync.domain.entity.StatusValidacao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LeituraColetaRepository extends JpaRepository<LeituraColeta, Long> {
    Optional<LeituraColeta> findByChaveIdempotencia(String chaveIdempotencia);
    List<LeituraColeta> findByPlantaParcelaEnsaioCodigoIgnoreCaseOrderByImportadoEmDesc(String codigoEnsaio);
    Page<LeituraColeta> findByPlantaParcelaEnsaioCodigoIgnoreCase(String codigoEnsaio, Pageable pageable);
    Page<LeituraColeta> findByPlantaParcelaEnsaioCodigoIgnoreCaseAndStatusValidacao(
            String codigoEnsaio, StatusValidacao statusValidacao, Pageable pageable);
}
