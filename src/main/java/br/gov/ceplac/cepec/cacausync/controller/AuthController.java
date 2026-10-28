package br.gov.ceplac.cepec.cacausync.controller;

import br.gov.ceplac.cepec.cacausync.domain.entity.RoleUsuario;
import br.gov.ceplac.cepec.cacausync.domain.entity.Usuario;
import br.gov.ceplac.cepec.cacausync.dto.LoginRequest;
import br.gov.ceplac.cepec.cacausync.dto.RegistrarUsuarioRequest;
import br.gov.ceplac.cepec.cacausync.dto.TokenResponse;
import br.gov.ceplac.cepec.cacausync.exception.RegraNegocioException;
import br.gov.ceplac.cepec.cacausync.repository.UsuarioRepository;
import br.gov.ceplac.cepec.cacausync.security.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "1. Autenticação (JWT)", description = "Autenticação institucional de Coordenadores e Pesquisadores do CEPEC/CEPLAC")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthController(
            AuthenticationManager authenticationManager,
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.authenticationManager = authenticationManager;
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    @Operation(
            summary = "Autenticar pesquisador e obter token JWT",
            description = "Credenciais padrão do seed: coordenador@cepec.gov.br / cepec123 ou pesquisador@cepec.gov.br / cepec123"
    )
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        var authToken = new UsernamePasswordAuthenticationToken(request.email(), request.senha());
        var authentication = authenticationManager.authenticate(authToken);
        Usuario usuario = (Usuario) authentication.getPrincipal();

        String jwt = jwtService.gerarToken(usuario);
        return ResponseEntity.ok(new TokenResponse(
                jwt,
                "Bearer",
                usuario.getNome(),
                usuario.getEmail(),
                usuario.getRole().name(),
                jwtService.getExpirationSeconds()
        ));
    }

    @PostMapping("/registrar")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Cadastrar novo pesquisador ou coordenador (exige ROLE_COORDENADOR)")
    public ResponseEntity<TokenResponse> registrar(@Valid @RequestBody RegistrarUsuarioRequest request) {
        if (usuarioRepository.existsByEmail(request.email())) {
            throw new RegraNegocioException("Já existe um pesquisador cadastrado com o e-mail: " + request.email());
        }
        if (usuarioRepository.existsByMatriculaCepec(request.matriculaCepec())) {
            throw new RegraNegocioException("Já existe um pesquisador com a matrícula CEPEC: " + request.matriculaCepec());
        }

        RoleUsuario role = request.role() != null ? request.role() : RoleUsuario.ROLE_PESQUISADOR;
        Usuario novoUsuario = new Usuario(
                request.nome(),
                request.email(),
                passwordEncoder.encode(request.senha()),
                request.matriculaCepec(),
                role
        );
        usuarioRepository.save(novoUsuario);

        String jwt = jwtService.gerarToken(novoUsuario);
        return ResponseEntity.status(HttpStatus.CREATED).body(new TokenResponse(
                jwt,
                "Bearer",
                novoUsuario.getNome(),
                novoUsuario.getEmail(),
                novoUsuario.getRole().name(),
                jwtService.getExpirationSeconds()
        ));
    }
}
