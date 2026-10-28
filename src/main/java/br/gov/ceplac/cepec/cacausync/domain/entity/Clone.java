package br.gov.ceplac.cepec.cacausync.domain.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "clones")
public class Clone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String codigo;

    @Column(length = 200)
    private String descricao;

    @Column(name = "origem_genetica", length = 100)
    private String origemGenetica = "Programa de Melhoramento Genético CEPEC";

    public Clone() {
    }

    public Clone(String codigo, String descricao, String origemGenetica) {
        this.codigo = codigo;
        this.descricao = descricao;
        this.origemGenetica = origemGenetica;
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

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public String getOrigemGenetica() {
        return origemGenetica;
    }

    public void setOrigemGenetica(String origemGenetica) {
        this.origemGenetica = origemGenetica;
    }
}
