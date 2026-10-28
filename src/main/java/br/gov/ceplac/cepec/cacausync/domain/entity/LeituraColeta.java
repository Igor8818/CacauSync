package br.gov.ceplac.cepec.cacausync.domain.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "leituras_coleta")
public class LeituraColeta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "planta_id", nullable = false)
    private Planta planta;

    @Column(name = "pesquisador_email", length = 150)
    private String pesquisadorEmail;

    @Column(nullable = false, length = 80)
    private String campanha;

    @Column(name = "qtd_frutos", nullable = false)
    private Integer qtdFrutos = 0;

    @Column(nullable = false)
    private Boolean coletavel = true;

    @Column(name = "diagnostico_bruto", nullable = false, length = 80)
    private String diagnosticoBruto;

    @Column(name = "data_coleta_str", nullable = false, length = 40)
    private String dataColetaStr;

    @Column(nullable = false)
    private Double latitude = 0.0;

    @Column(nullable = false)
    private Double longitude = 0.0;

    @Column(name = "alerta_gps", nullable = false)
    private Boolean alertaGps = false;

    @Column(name = "chave_idempotencia", nullable = false, unique = true, length = 200)
    private String chaveIdempotencia;

    @Column(name = "importado_em", nullable = false)
    private LocalDateTime importadoEm = LocalDateTime.now();

    public LeituraColeta() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Planta getPlanta() {
        return planta;
    }

    public void setPlanta(Planta planta) {
        this.planta = planta;
    }

    public String getPesquisadorEmail() {
        return pesquisadorEmail;
    }

    public void setPesquisadorEmail(String pesquisadorEmail) {
        this.pesquisadorEmail = pesquisadorEmail;
    }

    public String getCampanha() {
        return campanha;
    }

    public void setCampanha(String campanha) {
        this.campanha = campanha;
    }

    public Integer getQtdFrutos() {
        return qtdFrutos;
    }

    public void setQtdFrutos(Integer qtdFrutos) {
        this.qtdFrutos = qtdFrutos;
    }

    public Boolean getColetavel() {
        return coletavel;
    }

    public void setColetavel(Boolean coletavel) {
        this.coletavel = coletavel;
    }

    public String getDiagnosticoBruto() {
        return diagnosticoBruto;
    }

    public void setDiagnosticoBruto(String diagnosticoBruto) {
        this.diagnosticoBruto = diagnosticoBruto;
    }

    public String getDataColetaStr() {
        return dataColetaStr;
    }

    public void setDataColetaStr(String dataColetaStr) {
        this.dataColetaStr = dataColetaStr;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Boolean getAlertaGps() {
        return alertaGps;
    }

    public void setAlertaGps(Boolean alertaGps) {
        this.alertaGps = alertaGps;
    }

    public String getChaveIdempotencia() {
        return chaveIdempotencia;
    }

    public void setChaveIdempotencia(String chaveIdempotencia) {
        this.chaveIdempotencia = chaveIdempotencia;
    }

    public LocalDateTime getImportadoEm() {
        return importadoEm;
    }
}
