package br.gov.ceplac.cepec.cacausync.repository;

import br.gov.ceplac.cepec.cacausync.domain.entity.LeituraColeta;
import br.gov.ceplac.cepec.cacausync.domain.entity.StatusValidacao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LeituraColetaRepository extends JpaRepository<LeituraColeta, Long> {
    Optional<LeituraColeta> findByChaveIdempotencia(String chaveIdempotencia);
    /** Mais recentes primeiro; empate de importadoEm desempata por id (SPEC-003 §2.1). */
    @Query("select l from LeituraColeta l where lower(l.planta.parcela.ensaio.codigo) = lower(:codigoEnsaio) "
            + "order by l.importadoEm desc, l.id desc")
    List<LeituraColeta> findByPlantaParcelaEnsaioCodigoIgnoreCaseOrderByImportadoEmDesc(@Param("codigoEnsaio") String codigoEnsaio);
    Page<LeituraColeta> findByPlantaParcelaEnsaioCodigoIgnoreCase(String codigoEnsaio, Pageable pageable);
    Page<LeituraColeta> findByPlantaParcelaEnsaioCodigoIgnoreCaseAndStatusValidacao(
            String codigoEnsaio, StatusValidacao statusValidacao, Pageable pageable);
}
