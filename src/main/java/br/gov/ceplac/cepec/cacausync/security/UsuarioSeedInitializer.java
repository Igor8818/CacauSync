package br.gov.ceplac.cepec.cacausync.security;

import br.gov.ceplac.cepec.cacausync.domain.entity.Planta;
import br.gov.ceplac.cepec.cacausync.domain.entity.RoleUsuario;
import br.gov.ceplac.cepec.cacausync.domain.entity.Usuario;
import br.gov.ceplac.cepec.cacausync.repository.LeituraColetaRepository;
import br.gov.ceplac.cepec.cacausync.repository.PlantaRepository;
import br.gov.ceplac.cepec.cacausync.repository.UsuarioRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Garante que o usuário institucional Uilson Lopes (senha: ceplac123) esteja
 * cadastrado e corrige eventuais leituras com identificação cruzada.
 */
@Component
public class UsuarioSeedInitializer implements ApplicationRunner {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final LeituraColetaRepository leituraColetaRepository;
    private final PlantaRepository plantaRepository;

    public UsuarioSeedInitializer(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            LeituraColetaRepository leituraColetaRepository,
            PlantaRepository plantaRepository
    ) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.leituraColetaRepository = leituraColetaRepository;
        this.plantaRepository = plantaRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        String emailUilson = "uilson.lopes@ceplac.gov.br";
        if (!usuarioRepository.existsByEmail(emailUilson)) {
            Usuario uilson = new Usuario(
                    "Uilson Lopes",
                    emailUilson,
                    passwordEncoder.encode("ceplac123"),
                    "CEPEC-PESQ-UILSON",
                    RoleUsuario.ROLE_COORDENADOR
            );
            usuarioRepository.save(uilson);
        }

        // Correção de soberania de clone: se alguma leitura tiver CCN-51 na chave/diagnóstico
        // mas estiver associada à planta de SCP-16005, reatribui para a planta real do CCN-51.
        Optional<Planta> plantaCcn51 = plantaRepository.findByPlantCodeIgnoreCase("CCN-51_TC-1801_par20_pla1_c0226");
        if (plantaCcn51.isPresent()) {
            Planta ccn = plantaCcn51.get();
            leituraColetaRepository.findAll().forEach(l -> {
                String chave = (l.getChaveIdempotencia() != null) ? l.getChaveIdempotencia().toUpperCase() : "";
                if (chave.contains("CCN-51") && !l.getPlanta().getParcela().getClone().getCodigo().equalsIgnoreCase("CCN-51")) {
                    l.setPlanta(ccn);
                    leituraColetaRepository.save(l);
                }
            });
        }
    }
}
