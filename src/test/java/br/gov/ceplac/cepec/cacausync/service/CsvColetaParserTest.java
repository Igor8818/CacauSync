package br.gov.ceplac.cepec.cacausync.service;

import br.gov.ceplac.cepec.cacausync.dto.LinhaColetaCsvDto;
import br.gov.ceplac.cepec.cacausync.exception.RegraNegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvColetaParserTest {

    private final CsvColetaParser parser = new CsvColetaParser();

    @Test
    @DisplayName("Deve ler o CSV de 9 colunas gerado por exportarCSVCEPEC_Personalizado (MainActivity.kt) incluindo RFC 4180 e Não Coletável")
    void deveFazerParseDoFormatoPersonalizado9ColunasDoAndroid() {
        String csvContent = """
                Campanha,ID_Gerado,Clone,Parcela,Planta,Resultado,Data,Lat,Lng
                "TC-1801, Safra 2026",SCP-16005_P1_Pl1,SCP-16005,1,1,18,27/10 14:20,-14.7891,-39.2781
                "TC-1801, Safra 2026",SCP-16005_P1_Pl4,SCP-16005,1,4,Não Coletável,27/10 14:25,0.0,0.0
                """;

        List<LinhaColetaCsvDto> linhas = parser.parse(
                new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8))
        );

        assertThat(linhas).hasSize(2);

        LinhaColetaCsvDto planta1 = linhas.get(0);
        assertThat(planta1.campanha()).isEqualTo("TC-1801, Safra 2026");
        assertThat(planta1.cloneCodigo()).isEqualTo("SCP-16005");
        assertThat(planta1.numeroParcela()).isEqualTo("1");
        assertThat(planta1.numeroPlanta()).isEqualTo(1);
        assertThat(planta1.coletavel()).isTrue();
        assertThat(planta1.qtdFrutos()).isEqualTo(18);
        assertThat(planta1.latitude()).isEqualTo(-14.7891);

        LinhaColetaCsvDto planta4Morta = linhas.get(1);
        assertThat(planta4Morta.coletavel()).isFalse();
        assertThat(planta4Morta.qtdFrutos()).isZero();
        assertThat(planta4Morta.resultadoBruto()).isEqualTo("Não Coletável");
    }

    @Test
    @DisplayName("Deve ler o CSV de 7 colunas gerado por exportarCSVFinal (FileUtils.kt) com UTF-8 BOM")
    void deveFazerParseDoFormato7ColunasComBomUtf8() {
        String csvComBom = "\uFEFFEnsaio,Parcela,Plant_ID,Diagnostico,Data,Lat,Lng\n"
                + "TC-1801,20,CCN-51_P20_Pl3,24,27/10 15:00,-14.7555,-39.2333\n";

        List<LinhaColetaCsvDto> linhas = parser.parse(
                new ByteArrayInputStream(csvComBom.getBytes(StandardCharsets.UTF_8))
        );

        assertThat(linhas).hasSize(1);
        LinhaColetaCsvDto item = linhas.get(0);
        assertThat(item.campanha()).isEqualTo("TC-1801");
        assertThat(item.cloneCodigo()).isEqualTo("CCN-51");
        assertThat(item.numeroParcela()).isEqualTo("20");
        assertThat(item.numeroPlanta()).isEqualTo(3);
        assertThat(item.qtdFrutos()).isEqualTo(24);
        assertThat(item.coletavel()).isTrue();
    }

    @Test
    @DisplayName("Deve lançar RegraNegocioException quando o arquivo CSV estiver vazio")
    void deveLancarExcecaoParaCsvVazio() {
        ByteArrayInputStream streamVazio = new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> parser.parse(streamVazio))
                .isInstanceOf(RegraNegocioException.class)
                .hasMessageContaining("vazia");
    }
}
