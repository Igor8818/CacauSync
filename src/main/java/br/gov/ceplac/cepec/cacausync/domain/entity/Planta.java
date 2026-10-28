package br.gov.ceplac.cepec.cacausync.domain.entity;

import jakarta.persistence.*;

@Entity
@Table(
    name = "plantas",
    uniqueConstraints = @UniqueConstraint(name = "uk_parcela_planta", columnNames = {"parcela_id", "numero_planta"})
)
public class Planta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "parcela_id", nullable = false)
    private Parcela parcela;

    @Column(name = "plant_code", nullable = false, unique = true, length = 120)
    private String plantCode;

    @Column(name = "numero_planta", nullable = false)
    private Integer numeroPlanta;

    @Column(name = "ordem_rota", nullable = false)
    private Integer ordemRota = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SituacaoPlanta situacao = SituacaoPlanta.NORMAL;

    @Column(name = "sit_pla_consolidado", length = 40)
    private String sitPlaConsolidado = "N11_P0_E0_M0_V0";

    @Column(name = "sit_pla_historico", length = 40)
    private String sitPlaHistorico = "nnnnnnnnnnn";

    public Planta() {
    }

    public Planta(Parcela parcela, String plantCode, Integer numeroPlanta, Integer ordemRota, SituacaoPlanta situacao) {
        this.parcela = parcela;
        this.plantCode = plantCode;
        this.numeroPlanta = numeroPlanta;
        this.ordemRota = ordemRota;
        this.situacao = situacao;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Parcela getParcela() {
        return parcela;
    }

    public void setParcela(Parcela parcela) {
        this.parcela = parcela;
    }

    public String getPlantCode() {
        return plantCode;
    }

    public void setPlantCode(String plantCode) {
        this.plantCode = plantCode;
    }

    public Integer getNumeroPlanta() {
        return numeroPlanta;
    }

    public void setNumeroPlanta(Integer numeroPlanta) {
        this.numeroPlanta = numeroPlanta;
    }

    public Integer getOrdemRota() {
        return ordemRota;
    }

    public void setOrdemRota(Integer ordemRota) {
        this.ordemRota = ordemRota;
    }

    public SituacaoPlanta getSituacao() {
        return situacao;
    }

    public void setSituacao(SituacaoPlanta situacao) {
        this.situacao = situacao;
    }

    public String getSitPlaConsolidado() {
        return sitPlaConsolidado;
    }

    public void setSitPlaConsolidado(String sitPlaConsolidado) {
        this.sitPlaConsolidado = sitPlaConsolidado;
    }

    public String getSitPlaHistorico() {
        return sitPlaHistorico;
    }

    public void setSitPlaHistorico(String sitPlaHistorico) {
        this.sitPlaHistorico = sitPlaHistorico;
    }
}
