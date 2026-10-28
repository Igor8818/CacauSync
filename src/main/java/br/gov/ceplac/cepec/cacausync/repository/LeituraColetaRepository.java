package br.gov.ceplac.cepec.cacausync.repository;

import br.gov.ceplac.cepec.cacausync.domain.entity.LeituraColeta;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LeituraColetaRepository extends JpaRepository<LeituraColeta, Long> {
    boolean existsByChaveIdempotencia(String chaveIdempotencia);
    List<LeituraColeta> findByPlantaParcelaEnsaioCodigoIgnoreCaseOrderByImportadoEmDesc(String codigoEnsaio);
    Page<LeituraColeta> findByPlantaParcelaEnsaioCodigoIgnoreCase(String codigoEnsaio, Pageable pageable);
}
