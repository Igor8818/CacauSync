package br.gov.ceplac.cepec.cacausync.domain.entity;

import jakarta.persistence.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Entity
@Table(name = "usuarios")
public class Usuario implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(name = "senha_hash", nullable = false)
    private String senhaHash;

    @Column(name = "matricula_cepec", nullable = false, unique = true, length = 40)
    private String matriculaCepec;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RoleUsuario role;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();

    public Usuario() {
    }

    public Usuario(String nome, String email, String senhaHash, String matriculaCepec, RoleUsuario role) {
        this.nome = nome;
        this.email = email;
        this.senhaHash = senhaHash;
        this.matriculaCepec = matriculaCepec;
        this.role = role;
        this.criadoEm = LocalDateTime.now();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.name()));
    }

    @Override
    public String getPassword() {
        return senhaHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getSenhaHash() {
        return senhaHash;
    }

    public void setSenhaHash(String senhaHash) {
        this.senhaHash = senhaHash;
    }

    public String getMatriculaCepec() {
        return matriculaCepec;
    }

    public void setMatriculaCepec(String matriculaCepec) {
        this.matriculaCepec = matriculaCepec;
    }

    public RoleUsuario getRole() {
        return role;
    }

    public void setRole(RoleUsuario role) {
        this.role = role;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }
}
