package br.gov.ceplac.cepec.cacausync.repository;

import br.gov.ceplac.cepec.cacausync.domain.entity.Clone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CloneRepository extends JpaRepository<Clone, Long> {
    Optional<Clone> findByCodigoIgnoreCase(String codigo);
}
