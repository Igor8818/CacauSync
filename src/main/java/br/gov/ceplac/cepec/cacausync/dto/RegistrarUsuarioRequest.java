package br.gov.ceplac.cepec.cacausync.dto;

import br.gov.ceplac.cepec.cacausync.domain.entity.RoleUsuario;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegistrarUsuarioRequest(
    @NotBlank(message = "O nome do pesquisador é obrigatório")
    String nome,

    @NotBlank(message = "O e-mail institucional é obrigatório")
    @Email(message = "Formato de e-mail inválido")
    String email,

    @NotBlank(message = "A senha é obrigatória")
    @Size(min = 6, message = "A senha deve ter no mínimo 6 caracteres")
    String senha,

    @NotBlank(message = "A matrícula CEPEC é obrigatória")
    String matriculaCepec,

    RoleUsuario role
) {}
