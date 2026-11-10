package br.gov.ceplac.cepec.cacausync.repository;

import br.gov.ceplac.cepec.cacausync.domain.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByEmail(String email);
    Optional<Usuario> findByEmailIgnoreCase(String email);
    Optional<Usuario> findByNomeIgnoreCase(String nome);
    boolean existsByEmail(String email);
    boolean existsByMatriculaCepec(String matriculaCepec);
}
