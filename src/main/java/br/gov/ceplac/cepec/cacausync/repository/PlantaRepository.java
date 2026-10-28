package br.gov.ceplac.cepec.cacausync.repository;

import br.gov.ceplac.cepec.cacausync.domain.entity.Parcela;
import br.gov.ceplac.cepec.cacausync.domain.entity.Planta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PlantaRepository extends JpaRepository<Planta, Long> {
    Optional<Planta> findByPlantCodeIgnoreCase(String plantCode);
    Optional<Planta> findByParcelaAndNumeroPlanta(Parcela parcela, Integer numeroPlanta);
    List<Planta> findByParcelaEnsaioCodigoIgnoreCaseOrderByOrdemRotaAsc(String codigoEnsaio);
}
