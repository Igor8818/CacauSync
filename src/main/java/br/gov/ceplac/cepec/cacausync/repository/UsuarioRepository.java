package br.gov.ceplac.cepec.cacausync.repository;

import br.gov.ceplac.cepec.cacausync.domain.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByMatriculaCepec(String matriculaCepec);
}
