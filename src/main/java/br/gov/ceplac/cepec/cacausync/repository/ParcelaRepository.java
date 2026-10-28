package br.gov.ceplac.cepec.cacausync.repository;

import br.gov.ceplac.cepec.cacausync.domain.entity.Ensaio;
import br.gov.ceplac.cepec.cacausync.domain.entity.Parcela;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ParcelaRepository extends JpaRepository<Parcela, Long> {
    Optional<Parcela> findByEnsaioAndNumeroParcela(Ensaio ensaio, String numeroParcela);
    List<Parcela> findByEnsaioCodigoIgnoreCase(String codigoEnsaio);
}
