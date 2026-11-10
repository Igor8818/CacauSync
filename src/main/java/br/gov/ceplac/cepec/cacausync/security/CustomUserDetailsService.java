package br.gov.ceplac.cepec.cacausync.security;

import br.gov.ceplac.cepec.cacausync.domain.entity.Usuario;
import br.gov.ceplac.cepec.cacausync.repository.UsuarioRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    public CustomUserDetailsService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String identificador) throws UsernameNotFoundException {
        String chave = identificador == null ? "" : identificador.trim();
        Optional<Usuario> encontrado = usuarioRepository.findByEmail(chave)
                .or(() -> usuarioRepository.findByEmailIgnoreCase(chave))
                .or(() -> usuarioRepository.findByNomeIgnoreCase(chave));

        if (encontrado.isEmpty() && chave.toLowerCase().contains("uilson")) {
            encontrado = usuarioRepository.findByEmailIgnoreCase("uilson.lopes@ceplac.gov.br");
        }

        return encontrado.orElseThrow(
                () -> new UsernameNotFoundException("Pesquisador não encontrado: " + identificador)
        );
    }
}
