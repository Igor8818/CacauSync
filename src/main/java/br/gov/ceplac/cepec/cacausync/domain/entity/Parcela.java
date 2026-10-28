package br.gov.ceplac.cepec.cacausync.domain.entity;

import jakarta.persistence.*;

@Entity
@Table(
    name = "parcelas",
    uniqueConstraints = @UniqueConstraint(name = "uk_ensaio_parcela", columnNames = {"ensaio_id", "numero_parcela"})
)
public class Parcela {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ensaio_id", nullable = false)
    private Ensaio ensaio;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "clone_id", nullable = false)
    private Clone clone;

    @Column(nullable = false)
    private Integer bloco = 1;

    @Column(name = "numero_parcela", nullable = false, length = 30)
    private String numeroParcela;

    public Parcela() {
    }

    public Parcela(Ensaio ensaio, Clone clone, Integer bloco, String numeroParcela) {
        this.ensaio = ensaio;
        this.clone = clone;
        this.bloco = bloco;
        this.numeroParcela = numeroParcela;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Ensaio getEnsaio() {
        return ensaio;
    }

    public void setEnsaio(Ensaio ensaio) {
        this.ensaio = ensaio;
    }

    public Clone getClone() {
        return clone;
    }

    public void setClone(Clone clone) {
        this.clone = clone;
    }

    public Integer getBloco() {
        return bloco;
    }

    public void setBloco(Integer bloco) {
        this.bloco = bloco;
    }

    public String getNumeroParcela() {
        return numeroParcela;
    }

    public void setNumeroParcela(String numeroParcela) {
        this.numeroParcela = numeroParcela;
    }
}
