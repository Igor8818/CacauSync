package br.gov.ceplac.cepec.cacausync.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "ensaios")
public class Ensaio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String codigo;

    @Column(nullable = false, length = 160)
    private String nome;

    @Column(name = "estacao_experimental", nullable = false, length = 120)
    private String estacaoExperimental = "CEPEC/CEPLAC - Ilhéus/Itabuna, BA";

    @Column(nullable = false, length = 30)
    private String status = "EM_ANDAMENTO";

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm = LocalDateTime.now();

    public Ensaio() {
    }

    public Ensaio(String codigo, String nome, String estacaoExperimental) {
        this.codigo = codigo;
        this.nome = nome;
        this.estacaoExperimental = estacaoExperimental;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getEstacaoExperimental() {
        return estacaoExperimental;
    }

    public void setEstacaoExperimental(String estacaoExperimental) {
        this.estacaoExperimental = estacaoExperimental;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }
}
