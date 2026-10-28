package br.gov.ceplac.cepec.cacausync.repository;

import br.gov.ceplac.cepec.cacausync.domain.entity.Ensaio;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EnsaioRepository extends JpaRepository<Ensaio, Long> {
    Optional<Ensaio> findByCodigoIgnoreCase(String codigo);
}
